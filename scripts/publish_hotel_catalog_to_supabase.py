#!/usr/bin/env python3
from __future__ import annotations

import json
import math
import re
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import quote

import psycopg
import requests
from psycopg.types.json import Json


ROOT = Path(__file__).resolve().parents[1]
HOTEL_ROOT = ROOT / "HotelList"
SUPABASE_ENV_PATH = ROOT / "supabase.env"
SUPABASE_DB_URL_PATH = ROOT / "supabase-db-url.txt"
BUCKET_NAME = "hotel-catalog"

CITY_BASE_PRICES = {
    "ho chi minh city": 1_800_000,
    "hanoi": 1_700_000,
    "da nang": 1_450_000,
    "nha trang": 1_350_000,
}

ROOM_TYPE_MULTIPLIERS = [
    ("presidential", 4.1),
    ("penthouse", 4.0),
    ("4 bedroom", 4.0),
    ("4-bedroom", 4.0),
    ("3 bedroom", 3.7),
    ("3-bedroom", 3.7),
    ("2 bedroom", 3.0),
    ("2-bedroom", 3.0),
    ("villa", 3.2),
    ("grand suite", 2.9),
    ("suite", 2.5),
    ("apartment", 2.4),
    ("family", 2.1),
    ("executive", 1.8),
    ("premier", 1.65),
    ("club", 1.6),
    ("deluxe", 1.4),
    ("superior", 1.2),
    ("standard", 1.0),
]

VIEW_MULTIPLIERS = {
    "ocean": 1.18,
    "sea": 1.18,
    "river": 1.14,
    "lake": 1.12,
    "mountain": 1.12,
    "pool": 1.06,
    "city": 1.04,
    "garden": 1.03,
}

DEFAULT_ROOM_AMENITIES = [
    "Air conditioning",
    "Wi-Fi [free]",
    "TV",
    "Mini bar",
    "Free bottled water",
    "Closet",
]

DEFAULT_BATHROOM_AMENITIES = [
    "Shower",
    "Toiletries",
    "Towels",
    "Hair dryer",
]

DEFAULT_FEATURED_AMENITIES = [
    "Free Wi-Fi",
    "Front desk [24-hour]",
    "Swimming pool",
    "Restaurant",
    "Airport transfer",
]

DEFAULT_GENERAL_AMENITIES = [
    "Air conditioning in public area",
    "Daily housekeeping",
    "Elevator",
    "Laundry service",
    "Luggage storage",
]


@dataclass
class PublishContext:
    supabase_url: str
    service_role_key: str
    db_url: str


def now_utc_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def log(message: str) -> None:
    print(f"[{now_utc_iso()}] {message}")


def load_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        raw = line.strip()
        if not raw or raw.startswith("#") or "=" not in raw:
            continue
        key, value = raw.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def normalize_db_url(raw: str, env: dict[str, str]) -> str:
    if raw and "[YOUR-PASSWORD]" not in raw:
        return raw
    password = env.get("SUPABASE_DB_PASSWORD", "")
    project_ref = env.get("SUPABASE_PROJECT_REF", "")
    if not password or not project_ref:
        raise ValueError("Missing SUPABASE_DB_PASSWORD or SUPABASE_PROJECT_REF")
    encoded_password = quote(password, safe="")
    return f"postgresql://postgres:{encoded_password}@db.{project_ref}.supabase.co:5432/postgres?sslmode=require"


def load_publish_context() -> PublishContext:
    env = load_env(SUPABASE_ENV_PATH)
    db_url = normalize_db_url(SUPABASE_DB_URL_PATH.read_text(encoding="utf-8").strip(), env)
    supabase_url = env.get("SUPABASE_URL", "").strip()
    service_role_key = env.get("SUPABASE_SERVICE_ROLE_KEY", "").strip()
    if not supabase_url or not service_role_key:
        raise ValueError("Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY")
    return PublishContext(supabase_url=supabase_url.rstrip("/"), service_role_key=service_role_key, db_url=db_url)


def headers(service_role_key: str, json_content: bool = True) -> dict[str, str]:
    out = {
        "apikey": service_role_key,
        "Authorization": f"Bearer {service_role_key}",
    }
    if json_content:
        out["Content-Type"] = "application/json"
    return out


def ensure_bucket(context: PublishContext, bucket_name: str) -> None:
    endpoint = f"{context.supabase_url}/storage/v1/bucket"
    payload = {"name": bucket_name, "public": True}
    response = requests.post(endpoint, headers=headers(context.service_role_key), json=payload, timeout=30)
    if response.status_code in {200, 201, 409}:
        return
    if response.status_code == 400 and "already" in response.text.lower():
        return
    raise RuntimeError(f"Create bucket failed: HTTP {response.status_code} {response.text[:400]}")


def upload_bytes(context: PublishContext, bucket_name: str, object_path: str, content: bytes) -> str:
    safe_path = quote(object_path, safe="/")
    endpoint = f"{context.supabase_url}/storage/v1/object/{bucket_name}/{safe_path}"
    last_error: RuntimeError | None = None
    for attempt in range(1, 6):
        response = requests.post(
            endpoint,
            headers={
                "apikey": context.service_role_key,
                "Authorization": f"Bearer {context.service_role_key}",
                "Content-Type": "application/octet-stream",
                "x-upsert": "true",
            },
            data=content,
            timeout=180,
        )
        if response.status_code in range(200, 300):
            return f"{context.supabase_url}/storage/v1/object/public/{bucket_name}/{object_path}"
        if response.status_code < 500 and response.status_code not in {408, 429}:
            raise RuntimeError(f"Upload failed {object_path}: HTTP {response.status_code} {response.text[:400]}")
        last_error = RuntimeError(
            f"Upload failed {object_path}: HTTP {response.status_code} {response.text[:400]}"
        )
        time.sleep(min(12, attempt * 2))
    raise last_error or RuntimeError(f"Upload failed {object_path}")


def round_vnd(value: float) -> int:
    return int(round(value / 50_000.0) * 50_000)


def parse_double(value: Any) -> float | None:
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        return float(value) if value.strip() else None
    return None


def parse_int(value: Any) -> int | None:
    if value is None:
        return None
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        return int(value)
    if isinstance(value, str):
        text = value.strip()
        return int(text) if text.isdigit() else None
    return None


def stable_suffix(text: str, digits: int = 8) -> str:
    total = 0
    for char in text:
        total = (total * 131 + ord(char)) % (10**digits)
    return str(total).zfill(digits)


def slugify(value: str) -> str:
    text = value.lower()
    text = re.sub(r"[^a-z0-9]+", "_", text)
    text = re.sub(r"_+", "_", text).strip("_")
    return text or "item"


def infer_capacity(room_name: str, bed_summary: str | None) -> tuple[int, list[str]]:
    synthetic: list[str] = []
    name = room_name.lower()
    if any(token in name for token in ["family", "connecting"]):
        synthetic.append("max_capacity")
        return 4, synthetic
    bedroom_match = re.search(r"([2-5])[\s-]*bedroom", name)
    if bedroom_match:
        synthetic.append("max_capacity")
        return int(bedroom_match.group(1)) * 2, synthetic
    if "villa" in name:
        synthetic.append("max_capacity")
        return 6, synthetic
    if "suite" in name or "apartment" in name:
        synthetic.append("max_capacity")
        return 4, synthetic
    if "triple" in name:
        synthetic.append("max_capacity")
        return 3, synthetic
    if bed_summary:
        summary = bed_summary.lower()
        count_match = re.search(r"(\d+)\s+(king|queen|single|double|twin)", summary)
        if count_match:
            count = int(count_match.group(1))
            synthetic.append("max_capacity")
            return max(2, min(6, count * 2)), synthetic
        if any(token in summary for token in ["king", "queen", "double", "twin", "single"]):
            synthetic.append("max_capacity")
            return 2, synthetic
    synthetic.append("max_capacity")
    return 2, synthetic


def infer_bed_count(room_name: str, bed_summary: str | None, capacity: int) -> tuple[int, list[str]]:
    synthetic: list[str] = []
    text = (bed_summary or room_name).lower()
    count_match = re.search(r"(\d+)\s+(king|queen|single|double|twin)", text)
    if count_match:
        synthetic.append("number_of_beds")
        return int(count_match.group(1)), synthetic
    if "twin" in text:
        synthetic.append("number_of_beds")
        return 2, synthetic
    synthetic.append("number_of_beds")
    return max(1, math.ceil(capacity / 2)), synthetic


def infer_view(room_name: str, existing_view: str | None) -> tuple[str | None, list[str]]:
    if existing_view:
        return existing_view, []
    synthetic: list[str] = []
    lower = room_name.lower()
    for token in VIEW_MULTIPLIERS:
        if token in lower:
            synthetic.append("view")
            return f"{token.title()} view", synthetic
    return None, synthetic


def infer_room_size(room_name: str, capacity: int, existing_size: dict[str, Any] | None) -> tuple[dict[str, Any], list[str]]:
    if existing_size and (existing_size.get("square_meters") or existing_size.get("square_feet")):
        return existing_size, []
    synthetic: list[str] = ["room_size"]
    lower = room_name.lower()
    sqm = 28.0
    if "standard" in lower:
        sqm = 24.0
    elif "superior" in lower:
        sqm = 26.0
    elif "deluxe" in lower:
        sqm = 32.0
    elif "executive" in lower or "premier" in lower:
        sqm = 38.0
    elif "suite" in lower:
        sqm = 52.0
    elif "apartment" in lower:
        sqm = 72.0
    elif "villa" in lower:
        sqm = 120.0
    elif "family" in lower:
        sqm = 56.0
    sqm = max(sqm, 20.0 + (capacity - 2) * 10.0)
    sqft = round(sqm * 10.7639, 1)
    return {
        "source_text": f"Synthetic estimate: {sqm:.0f} m²/{sqft:.0f} ft²",
        "square_meters": sqm,
        "square_feet": sqft,
    }, synthetic


def infer_breakfast(room_name: str, existing: Any) -> tuple[bool, list[str]]:
    if isinstance(existing, bool):
        return existing, []
    synthetic = ["breakfast_included"]
    lower = room_name.lower()
    if any(token in lower for token in ["club", "suite", "executive", "premier"]):
        return True, synthetic
    return False, synthetic


def infer_cancellation(existing: Any) -> tuple[str, list[str]]:
    if isinstance(existing, str) and existing.strip():
        return existing, []
    return "Demo policy: Free cancellation up to 72 hours before check-in. Generated for catalog completeness.", [
        "cancellation_policy"
    ]


def infer_price(hotel: dict[str, Any], room: dict[str, Any]) -> tuple[dict[str, Any], list[str]]:
    price = room.get("price") or {}
    current_amount = parse_double(price.get("current_amount"))
    original_amount = parse_double(price.get("original_amount"))
    discounted_amount = parse_double(price.get("discounted_amount"))
    currency = (price.get("currency") or "VND").strip() or "VND"
    if current_amount is not None and current_amount > 0:
        if original_amount is None:
            original_amount = round_vnd(current_amount * 1.15)
        if discounted_amount is None:
            discounted_amount = current_amount
        return {
            "currency": currency,
            "current_amount": current_amount,
            "original_amount": original_amount,
            "discounted_amount": discounted_amount,
            "status": price.get("status") or "direct_or_derived",
        }, []

    synthetic = ["price.current_amount", "price.original_amount", "price.discounted_amount"]
    city = ((hotel.get("address") or {}).get("city") or "").strip().lower()
    base = CITY_BASE_PRICES.get(city, 1_600_000)
    name = (room.get("name") or "").lower()
    multiplier = 1.0
    for token, value in ROOM_TYPE_MULTIPLIERS:
        if token in name:
            multiplier = value
            break
    for token, value in VIEW_MULTIPLIERS.items():
        if token in name or token in str(room.get("view") or "").lower():
            multiplier *= value
            break
    sqm = parse_double((room.get("room_size") or {}).get("square_meters"))
    if sqm:
        multiplier *= 1 + max(0.0, (sqm - 28.0) / 120.0)
    star = parse_double(hotel.get("star_rating")) or 4.0
    multiplier *= 1 + max(0.0, (star - 3.0) * 0.12)
    if room.get("breakfast_included"):
        multiplier *= 1.05
    current = round_vnd(base * multiplier)
    original = round_vnd(current * 1.18)
    return {
        "currency": currency,
        "current_amount": float(current),
        "original_amount": float(original),
        "discounted_amount": float(current),
        "status": "synthetic_estimate",
    }, synthetic


def infer_short_description(room: dict[str, Any]) -> tuple[str, list[str]]:
    existing = (room.get("short_description") or "").strip()
    if existing:
        return existing, []
    synthetic = ["short_description"]
    name = room.get("name") or "Room"
    size = (room.get("room_size") or {}).get("square_meters")
    view = room.get("view")
    bits = [name]
    if size:
        bits.append(f"{int(round(float(size)))} m²")
    if view:
        bits.append(str(view))
    return " | ".join(bits), synthetic


def infer_amenities(room: dict[str, Any], hotel: dict[str, Any]) -> tuple[list[str], list[str], list[str]]:
    amenities = [value.strip() for value in room.get("amenities", []) if value and value.strip()]
    bathroom = [value.strip() for value in room.get("bathroom_amenities", []) if value and value.strip()]
    synthetic: list[str] = []
    hotel_amenities = [value.strip() for value in hotel.get("featured_amenities", []) if value and value.strip()]
    room_name = (room.get("name") or "").lower()
    if not amenities:
        synthetic.append("amenities")
        amenities = list(DEFAULT_ROOM_AMENITIES)
        if "suite" in room_name or "apartment" in room_name:
            amenities.extend(["Seating area", "Sofa", "Dining table"])
        if "villa" in room_name:
            amenities.extend(["Private pool", "Private entrance", "Kitchen"])
        if "family" in room_name:
            amenities.extend(["Extra bedding", "Family seating area"])
        amenities.extend(hotel_amenities[:3])
    if not bathroom:
        synthetic.append("bathroom_amenities")
        bathroom = list(DEFAULT_BATHROOM_AMENITIES)
        if "suite" in room_name or "villa" in room_name:
            bathroom.append("Bathtub")
    amenities = sorted(dict.fromkeys(amenities))
    bathroom = sorted(dict.fromkeys(bathroom))
    return amenities, bathroom, synthetic


def infer_hotel_short_description(hotel: dict[str, Any]) -> str:
    name = hotel.get("display_name") or hotel.get("name") or hotel.get("folder_name") or "Hotel"
    city = ((hotel.get("address") or {}).get("city") or "").strip()
    star = parse_double(hotel.get("star_rating")) or 4.0
    score = parse_double((hotel.get("review") or {}).get("score")) or 0.0
    summary_bits = [name]
    if city:
        summary_bits.append(city)
    summary_bits.append(f"{star:.1f} stars")
    if score > 0:
        summary_bits.append(f"review {score:.1f}")
    return " • ".join(summary_bits)


def infer_contact(hotel: dict[str, Any]) -> tuple[dict[str, Any], list[str]]:
    contact = dict(hotel.get("contact") or {})
    synthetic: list[str] = []
    slug = hotel.get("slug") or hotel.get("folder_name") or "hotel"
    suffix = stable_suffix(slug)
    if not contact.get("website"):
        contact["website"] = hotel.get("source_url")
        synthetic.append("contact.website")
    if not contact.get("email"):
        contact["email"] = f"reservations+{slug}@catalog.hotelgo.demo"
        synthetic.append("contact.email")
    if not contact.get("phone"):
        contact["phone"] = f"+84-000-{suffix[:4]}-{suffix[4:8]}"
        synthetic.append("contact.phone")
    return contact, synthetic


def infer_hotel_amenities(hotel: dict[str, Any]) -> tuple[list[str], list[str], list[str]]:
    featured = [value.strip() for value in hotel.get("featured_amenities", []) if value and value.strip()]
    general = [value.strip() for value in hotel.get("general_amenities", []) if value and value.strip()]
    synthetic: list[str] = []
    if not featured:
        featured = list(DEFAULT_FEATURED_AMENITIES)
        synthetic.append("featured_amenities")
    if not general:
        general = list(DEFAULT_GENERAL_AMENITIES)
        synthetic.append("general_amenities")
    return sorted(dict.fromkeys(featured)), sorted(dict.fromkeys(general)), synthetic


def infer_policy_notes(hotel: dict[str, Any]) -> tuple[list[str], list[str]]:
    existing = [value.strip() for value in hotel.get("policy_notes", []) if value and value.strip()]
    if existing:
        return sorted(dict.fromkeys(existing)), []
    check_in_from = ((hotel.get("check_in") or {}).get("from") or "14:00").strip()
    check_out_until = ((hotel.get("check_out") or {}).get("until") or "12:00").strip()
    return [
        f"Check-in from {check_in_from}.",
        f"Check-out until {check_out_until}.",
        "Breakfast inclusion may vary by booking date.",
        "Catalog-only pricing was estimated for demo completeness.",
    ], ["policy_notes"]


def infer_coordinates(hotel: dict[str, Any]) -> tuple[dict[str, Any], list[str]]:
    coordinates = dict(hotel.get("coordinates") or {})
    latitude = parse_double(coordinates.get("latitude"))
    longitude = parse_double(coordinates.get("longitude"))
    if latitude is not None and longitude is not None:
        return {"latitude": latitude, "longitude": longitude}, []

    city = ((hotel.get("address") or {}).get("city") or "").strip().lower()
    defaults = {
        "ho chi minh city": (10.7769, 106.7009),
        "hanoi": (21.0285, 105.8542),
        "da nang": (16.0544, 108.2022),
        "nha trang": (12.2388, 109.1967),
    }
    lat, lng = defaults.get(city, (10.0, 106.0))
    return {"latitude": lat, "longitude": lng}, ["coordinates.latitude", "coordinates.longitude"]


def enrich_room(hotel: dict[str, Any], room: dict[str, Any]) -> dict[str, Any]:
    synthetic_fields: list[str] = list(room.get("synthetic_fields") or [])
    capacity, capacity_fields = infer_capacity(room.get("name") or "", room.get("bed_configuration", {}).get("summary"))
    if not room.get("max_capacity"):
        room["max_capacity"] = capacity
        synthetic_fields.extend(capacity_fields)

    bed_count, bed_fields = infer_bed_count(room.get("name") or "", room.get("bed_configuration", {}).get("summary"), room["max_capacity"])
    if not room.get("number_of_beds"):
        room["number_of_beds"] = bed_count
        synthetic_fields.extend(bed_fields)

    view, view_fields = infer_view(room.get("name") or "", room.get("view"))
    if view and not room.get("view"):
        room["view"] = view
        synthetic_fields.extend(view_fields)

    room_size, size_fields = infer_room_size(room.get("name") or "", room["max_capacity"], room.get("room_size"))
    room["room_size"] = room_size
    synthetic_fields.extend(size_fields)

    breakfast, breakfast_fields = infer_breakfast(room.get("name") or "", room.get("breakfast_included"))
    room["breakfast_included"] = breakfast
    synthetic_fields.extend(breakfast_fields)

    cancellation_policy, cancellation_fields = infer_cancellation(room.get("cancellation_policy"))
    room["cancellation_policy"] = cancellation_policy
    synthetic_fields.extend(cancellation_fields)

    amenities, bathroom_amenities, amenity_fields = infer_amenities(room, hotel)
    room["amenities"] = amenities
    room["bathroom_amenities"] = bathroom_amenities
    synthetic_fields.extend(amenity_fields)

    price, price_fields = infer_price(hotel, room)
    room["price"] = price
    synthetic_fields.extend(price_fields)

    short_description, short_description_fields = infer_short_description(room)
    room["short_description"] = short_description
    synthetic_fields.extend(short_description_fields)

    room["capacity"] = room["max_capacity"]
    room["status"] = room.get("status") or "available"
    tags = [value for value in room.get("tags", []) if value]
    if not tags:
        room_size_sqm = (room.get("room_size") or {}).get("square_meters")
        if room_size_sqm:
            tags.append(f"{int(round(float(room_size_sqm)))} m2")
        if room.get("view"):
            tags.append(str(room["view"]))
        if room.get("breakfast_included"):
            tags.append("Breakfast included")
        bed_summary = room.get("bed_configuration", {}).get("summary")
        if bed_summary:
            tags.append(bed_summary)
        synthetic_fields.append("tags")
    room["tags"] = sorted(dict.fromkeys(tags))
    room["synthetic_fields"] = sorted(dict.fromkeys(synthetic_fields))
    room["catalog_quality"] = {
        "is_inferred_room": room.get("source_room_id") in {None, "", "null"} or str(room.get("room_id", "")).startswith("inferred:"),
        "has_uploaded_images": False,
        "price_status": room["price"]["status"],
    }
    return room


def enrich_hotel(hotel: dict[str, Any], rooms: list[dict[str, Any]]) -> dict[str, Any]:
    synthetic_fields: list[str] = list(hotel.get("synthetic_fields") or [])
    hotel["short_description"] = infer_hotel_short_description(hotel)
    hotel["room_count"] = len(rooms)
    hotel["contact"], contact_fields = infer_contact(hotel)
    hotel["featured_amenities"], hotel["general_amenities"], amenity_fields = infer_hotel_amenities(hotel)
    hotel["policy_notes"], policy_fields = infer_policy_notes(hotel)
    hotel["coordinates"], coordinate_fields = infer_coordinates(hotel)
    synthetic_fields.extend(contact_fields)
    synthetic_fields.extend(amenity_fields)
    synthetic_fields.extend(policy_fields)
    synthetic_fields.extend(coordinate_fields)
    hotel["synthetic_fields"] = sorted(dict.fromkeys(synthetic_fields))
    hotel["catalog_quality"] = {
        "synthetic_fields": hotel["synthetic_fields"],
        "has_uploaded_images": False,
        "room_count": len(rooms),
    }
    return hotel


def read_hotel_dataset(hotel_dir: Path) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    hotel = json.loads((hotel_dir / "data" / "hotel.json").read_text(encoding="utf-8"))
    rooms_root = json.loads((hotel_dir / "data" / "rooms.json").read_text(encoding="utf-8"))
    rooms = list(rooms_root.get("rooms") or [])
    hotel = enrich_hotel(hotel, rooms)
    rooms = [enrich_room(hotel, room) for room in rooms]
    hotel["room_count"] = len(rooms)
    return hotel, rooms


def create_public_schema(conn: psycopg.Connection) -> None:
    conn.execute(
        """
        create table if not exists public.hotels (
            id text primary key,
            folder_name text not null,
            slug text not null,
            name text not null,
            display_name text not null,
            city text,
            area text,
            country text,
            address_full text,
            short_description text,
            description text,
            star_rating double precision,
            review_score double precision,
            review_count integer,
            room_count integer not null default 0,
            check_in_from text,
            check_out_until text,
            latitude double precision,
            longitude double precision,
            contact_phone text,
            contact_email text,
            hero_image text,
            gallery_images jsonb not null default '[]'::jsonb,
            featured_amenities jsonb not null default '[]'::jsonb,
            general_amenities jsonb not null default '[]'::jsonb,
            policy_notes jsonb not null default '[]'::jsonb,
            source_url text,
            raw jsonb not null default '{}'::jsonb,
            created_at timestamptz not null default now(),
            updated_at timestamptz not null default now()
        );
        """
    )
    conn.execute(
        """
        create table if not exists public.hotel_rooms (
            id text primary key,
            hotel_id text not null,
            hotel_slug text not null,
            room_code text not null,
            name text not null,
            slug text not null,
            price double precision not null default 0,
            original_price double precision,
            currency text not null default 'VND',
            rating double precision not null default 0,
            review_count integer not null default 0,
            status text not null default 'available',
            capacity integer not null default 2,
            images jsonb not null default '[]'::jsonb,
            hero_image text,
            room_size_sqm double precision,
            room_size_sqft double precision,
            view text,
            breakfast_included boolean not null default false,
            cancellation_policy text,
            bed_summary text,
            bed_count integer,
            amenities jsonb not null default '[]'::jsonb,
            bathroom_amenities jsonb not null default '[]'::jsonb,
            tags jsonb not null default '[]'::jsonb,
            short_description text,
            raw jsonb not null default '{}'::jsonb,
            created_at timestamptz not null default now(),
            updated_at timestamptz not null default now()
        );
        """
    )
    conn.execute(
        """
        create table if not exists public.rooms (
            id text primary key,
            code text,
            type text,
            price double precision,
            rating double precision,
            review_count integer,
            status text,
            capacity integer,
            images jsonb,
            created_at timestamptz,
            raw jsonb not null default '{}'::jsonb
        );
        """
    )
    conn.execute("alter table public.hotels add column if not exists room_count integer not null default 0;")
    conn.execute("alter table public.hotels add column if not exists latitude double precision;")
    conn.execute("alter table public.hotels add column if not exists longitude double precision;")
    conn.execute("alter table public.hotels add column if not exists contact_phone text;")
    conn.execute("alter table public.hotels add column if not exists contact_email text;")
    conn.execute("alter table public.hotels add column if not exists policy_notes jsonb not null default '[]'::jsonb;")
    conn.execute("alter table public.rooms add column if not exists raw jsonb not null default '{}'::jsonb;")
    conn.execute('alter table public.hotels disable row level security;')
    conn.execute('alter table public.hotel_rooms disable row level security;')
    conn.execute('alter table public.rooms disable row level security;')
    conn.execute('grant usage on schema public to anon, authenticated;')
    conn.execute('grant select on public.hotels to anon, authenticated;')
    conn.execute('grant select on public.hotel_rooms to anon, authenticated;')
    conn.execute('grant select on public.rooms to anon, authenticated;')
    conn.commit()


def upsert(conn: psycopg.Connection, table: str, row: dict[str, Any]) -> None:
    columns = list(row.keys())
    values: list[Any] = []
    for column in columns:
        value = row[column]
        values.append(Json(value) if isinstance(value, (dict, list)) else value)
    cols_sql = ", ".join(f'"{column}"' for column in columns)
    placeholders = ", ".join(["%s"] * len(columns))
    updates = ", ".join(f'"{column}" = excluded."{column}"' for column in columns if column != "id")
    conn.execute(
        f'insert into public."{table}" ({cols_sql}) values ({placeholders}) on conflict (id) do update set {updates};',
        values,
    )


def publish_images(context: PublishContext, hotel_dir: Path, hotel: dict[str, Any], rooms: list[dict[str, Any]]) -> dict[str, str]:
    mapping: dict[str, str] = {}
    image_dir = hotel_dir / "images"
    for local_file in sorted(image_dir.rglob("*")):
        if not local_file.is_file():
            continue
        relative = local_file.relative_to(hotel_dir).as_posix()
        object_path = f"{hotel_dir.name}/{relative}"
        public = upload_bytes(context, BUCKET_NAME, object_path, local_file.read_bytes())
        mapping[relative] = public
    for image in hotel.get("images", []):
        local_path = image.get("local_path")
        if local_path and local_path in mapping:
            image["public_url"] = mapping[local_path]
    for room in rooms:
        for image in room.get("images", []):
            local_path = image.get("local_path")
            if local_path and local_path in mapping:
                image["public_url"] = mapping[local_path]
    hero = next((image.get("public_url") for image in hotel.get("images", []) if image.get("public_url")), None)
    hotel["hero_image_url"] = hero
    hotel["gallery_image_urls"] = [image.get("public_url") for image in hotel.get("images", []) if image.get("public_url")]
    hotel["catalog_quality"]["has_uploaded_images"] = bool(hotel["gallery_image_urls"])
    for room in rooms:
        room_urls = [image.get("public_url") for image in room.get("images", []) if image.get("public_url")]
        room["hero_image_url"] = room_urls[0] if room_urls else None
        room["image_public_urls"] = room_urls
        room["image_count"] = len(room_urls)
        room["catalog_quality"]["has_uploaded_images"] = bool(room_urls)
    return mapping


def write_back_dataset(
    hotel_dir: Path,
    hotel: dict[str, Any],
    rooms: list[dict[str, Any]],
    publish_manifest: dict[str, Any],
) -> None:
    (hotel_dir / "data" / "hotel.json").write_text(json.dumps(hotel, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    rooms_payload = {
        "schema_version": "1.0",
        "hotel_slug": hotel["slug"],
        "room_count": len(rooms),
        "rooms": rooms,
    }
    (hotel_dir / "data" / "rooms.json").write_text(json.dumps(rooms_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    app_seed: list[dict[str, Any]] = []
    now_millis = int(datetime.now(timezone.utc).timestamp() * 1000)
    review_score = parse_double((hotel.get("review") or {}).get("score")) or 0.0
    review_count = parse_int((hotel.get("review") or {}).get("review_count")) or 0
    for room in rooms:
        app_seed.append(
            {
                "id": f"catalog:{hotel['slug']}:{room['slug']}",
                "code": f"{hotel['folder_name']}-{room['slug']}",
                "type": f"{room['name']} - {hotel['display_name']}",
                "price": float(room["price"]["current_amount"]),
                "rating": review_score,
                "reviewCount": review_count,
                "status": room.get("status") or "available",
                "capacity": room.get("max_capacity") or 2,
                "images": room.get("image_public_urls") or [],
                "createdAt": now_millis,
            }
        )
    (hotel_dir / "data" / "app_room_seed.json").write_text(json.dumps(app_seed, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (hotel_dir / "manifest" / "publish_manifest.json").write_text(
        json.dumps(publish_manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def publish_hotel_dir(context: PublishContext, conn: psycopg.Connection, hotel_dir: Path) -> dict[str, Any]:
    hotel, rooms = read_hotel_dataset(hotel_dir)
    image_mapping = publish_images(context, hotel_dir, hotel, rooms)

    hotel_row = {
        "id": hotel["slug"],
        "folder_name": hotel["folder_name"],
        "slug": hotel["slug"],
        "name": hotel["name"],
        "display_name": hotel["display_name"],
        "city": (hotel.get("address") or {}).get("city"),
        "area": (hotel.get("address") or {}).get("area"),
        "country": (hotel.get("address") or {}).get("country"),
        "address_full": (hotel.get("address") or {}).get("full"),
        "short_description": hotel.get("short_description"),
        "description": (hotel.get("description") or {}).get("overview_text"),
        "star_rating": parse_double(hotel.get("star_rating")),
        "review_score": parse_double((hotel.get("review") or {}).get("score")),
        "review_count": parse_int((hotel.get("review") or {}).get("review_count")) or 0,
        "room_count": len(rooms),
        "check_in_from": (hotel.get("check_in") or {}).get("from"),
        "check_out_until": (hotel.get("check_out") or {}).get("until"),
        "latitude": parse_double((hotel.get("coordinates") or {}).get("latitude")),
        "longitude": parse_double((hotel.get("coordinates") or {}).get("longitude")),
        "contact_phone": (hotel.get("contact") or {}).get("phone"),
        "contact_email": (hotel.get("contact") or {}).get("email"),
        "hero_image": hotel.get("hero_image_url"),
        "gallery_images": hotel.get("gallery_image_urls") or [],
        "featured_amenities": hotel.get("featured_amenities") or [],
        "general_amenities": hotel.get("general_amenities") or [],
        "policy_notes": hotel.get("policy_notes") or [],
        "source_url": hotel.get("source_url"),
        "raw": hotel,
        "updated_at": datetime.now(timezone.utc),
    }
    upsert(conn, "hotels", hotel_row)

    review_score = parse_double((hotel.get("review") or {}).get("score")) or 0.0
    review_count = parse_int((hotel.get("review") or {}).get("review_count")) or 0
    for room in rooms:
        room_id = f"{hotel['slug']}:{room['slug']}"
        room_code = f"{hotel['folder_name']}-{room['slug']}"
        room_row = {
            "id": room_id,
            "hotel_id": hotel["slug"],
            "hotel_slug": hotel["slug"],
            "room_code": room_code,
            "name": room["name"],
            "slug": room["slug"],
            "price": float(room["price"]["current_amount"]),
            "original_price": parse_double(room["price"].get("original_amount")),
            "currency": room["price"].get("currency") or "VND",
            "rating": review_score,
            "review_count": review_count,
            "status": room.get("status") or "available",
            "capacity": room.get("max_capacity") or 2,
            "images": room.get("image_public_urls") or [],
            "hero_image": room.get("hero_image_url"),
            "room_size_sqm": parse_double((room.get("room_size") or {}).get("square_meters")),
            "room_size_sqft": parse_double((room.get("room_size") or {}).get("square_feet")),
            "view": room.get("view"),
            "breakfast_included": bool(room.get("breakfast_included")),
            "cancellation_policy": room.get("cancellation_policy"),
            "bed_summary": (room.get("bed_configuration") or {}).get("summary"),
            "bed_count": parse_int(room.get("number_of_beds")) or 1,
            "amenities": room.get("amenities") or [],
            "bathroom_amenities": room.get("bathroom_amenities") or [],
            "tags": room.get("tags") or [],
            "short_description": room.get("short_description"),
            "raw": room,
            "updated_at": datetime.now(timezone.utc),
        }
        upsert(conn, "hotel_rooms", room_row)
        legacy_row = {
            "id": f"catalog:{hotel['slug']}:{room['slug']}",
            "code": room_code,
            "type": f"{room['name']} - {hotel['display_name']}",
            "price": float(room["price"]["current_amount"]),
            "rating": review_score,
            "review_count": review_count,
            "status": room.get("status") or "available",
            "capacity": room.get("max_capacity") or 2,
            "images": room.get("image_public_urls") or [],
            "created_at": datetime.now(timezone.utc),
            "raw": {
                "hotel_id": hotel["slug"],
                "hotel_name": hotel["display_name"],
                "room_name": room["name"],
                "room_slug": room["slug"],
                "hero_image": room.get("hero_image_url"),
                "synthetic_fields": room.get("synthetic_fields") or [],
            },
        }
        upsert(conn, "rooms", legacy_row)
    conn.commit()

    publish_manifest = {
        "published_at_utc": now_utc_iso(),
        "bucket": BUCKET_NAME,
        "hotel_id": hotel["slug"],
        "hotel_folder": hotel_dir.name,
        "image_upload_count": len(image_mapping),
        "hotel_room_count": len(rooms),
        "legacy_room_seed_count": len(rooms),
        "hotel_synthetic_fields": hotel.get("synthetic_fields") or [],
    }
    write_back_dataset(hotel_dir, hotel, rooms, publish_manifest)
    return publish_manifest


def main() -> int:
    context = load_publish_context()
    ensure_bucket(context, BUCKET_NAME)
    conn = psycopg.connect(context.db_url, connect_timeout=20)
    create_public_schema(conn)

    summaries: list[dict[str, Any]] = []
    for hotel_dir in sorted([path for path in HOTEL_ROOT.iterdir() if path.is_dir()]):
        if not (hotel_dir / "data" / "hotel.json").exists():
            continue
        log(f"Publishing hotel catalog: {hotel_dir.name}")
        summary = publish_hotel_dir(context, conn, hotel_dir)
        summaries.append(summary)
        log(f"Published {hotel_dir.name}: rooms={summary['hotel_room_count']} images={summary['image_upload_count']}")

    report_path = ROOT / "docs" / "codex_publish_report.json"
    report_path.write_text(json.dumps({"generated_at_utc": now_utc_iso(), "hotels": summaries}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    conn.close()
    log(f"Publish report written to: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
