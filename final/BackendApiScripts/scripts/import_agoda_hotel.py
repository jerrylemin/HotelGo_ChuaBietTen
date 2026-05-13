#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import html
import json
import mimetypes
import re
import shutil
import sys
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import parse_qsl, urlencode, urljoin, urlparse, urlunparse

import requests


USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/124.0.0.0 Safari/537.36"
)

ROOM_IMAGE_TRAILING_SEGMENTS = {
    "bed",
    "bedroom",
    "view",
    "room plan",
    "bathroom",
    "suite room",
    "interior",
    "exterior",
    "living room",
    "recreational facilities",
}

GENERIC_ROOM_IMAGE_TITLES = {
    "guestroom",
    "balcony terrace",
    "room",
}


def now_utc_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class SessionLog:
    def __init__(self) -> None:
        self.lines: list[str] = []

    def write(self, message: str) -> None:
        line = f"[{now_utc_iso()}] {message}"
        self.lines.append(line)
        print(line)

    def dump(self, path: Path) -> None:
        path.write_text("\n".join(self.lines) + "\n", encoding="utf-8")


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def reset_dir(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def normalize_scheme_url(value: str) -> str:
    if not value:
        return value
    if value.startswith("//"):
        return f"https:{value}"
    return value


def slugify(value: str) -> str:
    text = unicodedata.normalize("NFKD", value)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = text.lower()
    text = re.sub(r"[^a-z0-9]+", "_", text)
    text = re.sub(r"_+", "_", text).strip("_")
    return text or "item"


def clean_html_text(value: str | None) -> str:
    if not value:
        return ""
    text = value.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
    text = re.sub(r"</p\s*>", "\n\n", text, flags=re.IGNORECASE)
    text = re.sub(r"<li\s*>", "- ", text, flags=re.IGNORECASE)
    text = re.sub(r"</li\s*>", "\n", text, flags=re.IGNORECASE)
    text = re.sub(r"<[^>]+>", "", text)
    text = html.unescape(text)
    text = text.replace("\r", "")
    text = re.sub(r"\n{3,}", "\n\n", text)
    lines = [line.strip() for line in text.splitlines()]
    return "\n".join(line for line in lines if line).strip()


def clean_policy_note(value: str) -> str:
    text = clean_html_text(value)
    text = text.replace("\u2022", "- ")
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def dedupe_strings(values: list[str]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for value in values:
        cleaned = value.strip()
        if not cleaned:
            continue
        key = cleaned.casefold()
        if key in seen:
            continue
        seen.add(key)
        out.append(cleaned)
    return out


def parse_money_text(value: str | None) -> dict[str, Any] | None:
    if not value:
        return None
    raw = value.strip()
    if not raw:
        return None
    match = re.search(r"([-+]?[0-9][0-9.,]*)\s*([A-Z]{3}|VND|USD)?", raw)
    if not match:
        return {"raw": raw, "amount": None, "currency": None}
    number_text = match.group(1).replace(",", "")
    amount = None
    try:
        amount = float(number_text)
    except ValueError:
        amount = None
    currency = match.group(2)
    return {"raw": raw, "amount": amount, "currency": currency}


def parse_room_size(feature_title: str) -> dict[str, Any] | None:
    if "room size" not in feature_title.lower():
        return None
    sqm_match = re.search(r"([0-9]+(?:\.[0-9]+)?)\s*m", feature_title, flags=re.IGNORECASE)
    sqft_match = re.search(r"([0-9]+(?:\.[0-9]+)?)\s*ft", feature_title, flags=re.IGNORECASE)
    sqm = float(sqm_match.group(1)) if sqm_match else None
    sqft = float(sqft_match.group(1)) if sqft_match else None
    return {
        "source_text": feature_title,
        "square_meters": sqm,
        "square_feet": sqft,
    }


def normalize_text_match(value: str) -> str:
    return slugify(value).replace("_", " ").strip()


def match_policy_room_name(policy_name: str, room_name: str) -> bool:
    left = re.sub(r"\broom\b", "", normalize_text_match(policy_name)).strip()
    right = re.sub(r"\broom\b", "", normalize_text_match(room_name)).strip()
    if not left or not right:
        return False
    return left == right or left in right or right in left


def extract_breakfast_room_names(policy_notes: list[str]) -> list[str]:
    extracted: list[str] = []
    for note in policy_notes:
        flat = clean_policy_note(note)
        match = re.search(
            r"room types:\s*(.+?)\s+with details as below",
            flat,
            flags=re.IGNORECASE | re.DOTALL,
        )
        if not match:
            continue
        segment = match.group(1).replace("\n", " ")
        parts = re.split(r",| and ", segment)
        for part in parts:
            cleaned = re.sub(r"\s+", " ", part).strip(" .")
            if cleaned:
                extracted.append(cleaned)
    return dedupe_strings(extracted)


def normalize_room_image_title(title: str | None) -> str | None:
    if not title:
        return None
    text = re.sub(r"\s+", " ", title).strip(" -")
    if not text:
        return None
    parts = [part.strip() for part in text.split(" - ") if part.strip()]
    while parts:
        tail = normalize_text_match(parts[-1])
        if tail in ROOM_IMAGE_TRAILING_SEGMENTS:
            parts.pop()
            continue
        break
    candidate = " - ".join(parts) if parts else text
    candidate = re.sub(r"\s+", " ", candidate).strip(" -")
    if not candidate:
        return None
    normalized = normalize_text_match(candidate)
    if normalized in GENERIC_ROOM_IMAGE_TITLES:
        return None
    return candidate


def resolve_room_slug(room_name: str, existing_rooms: list[dict[str, Any]]) -> str | None:
    for room in existing_rooms:
        existing_name = room.get("name") or ""
        if match_policy_room_name(room_name, existing_name) or match_policy_room_name(existing_name, room_name):
            return room.get("slug")
    return None


def build_inferred_room_record(room_name: str, hotel_record: dict[str, Any]) -> dict[str, Any]:
    room_slug = slugify(room_name)
    return {
        "room_id": f"inferred:{room_slug}",
        "name": room_name,
        "slug": room_slug,
        "provider": "agoda",
        "source_room_id": None,
        "price": {
            "currency": get_default_currency(hotel_record),
            "current_amount": None,
            "original_amount": None,
            "discounted_amount": None,
            "status": "unavailable_room_grid_blocked",
        },
        "bed_configuration": {
            "summary": None,
            "layouts": [],
        },
        "number_of_beds": None,
        "max_capacity": None,
        "room_size": None,
        "view": None,
        "breakfast_included": None,
        "cancellation_policy": None,
        "amenities": [],
        "bathroom_amenities": [],
        "facility_groups": [],
        "image_count": 0,
        "images": [],
        "short_description": None,
        "tags": ["inferred_from_room_image_title"],
        "raw_features": [],
        "raw_propaganda_messages": [],
    }


def get_default_currency(hotel_record: dict[str, Any]) -> str | None:
    airport_transfer_fee = hotel_record.get("property_facts", {}).get("airport_transfer_fee") or {}
    breakfast_charge = hotel_record.get("breakfast", {}).get("charge") or {}
    return airport_transfer_fee.get("currency") or breakfast_charge.get("currency")


def update_query_params(url: str, replacements: dict[str, str | None]) -> str:
    parsed = urlparse(url)
    params = dict(parse_qsl(parsed.query, keep_blank_values=True))
    for key, value in replacements.items():
        if value is None or value == "":
            continue
        params[key] = value
    return urlunparse(parsed._replace(query=urlencode(params)))


def build_session() -> requests.Session:
    session = requests.Session()
    session.headers.update(
        {
            "User-Agent": USER_AGENT,
            "Accept-Language": "vi-VN,vi;q=0.9,en;q=0.8",
        }
    )
    return session


def fetch_text(session: requests.Session, url: str, referer: str | None, log: SessionLog) -> str:
    headers = {"Referer": referer} if referer else {}
    response = session.get(url, headers=headers, timeout=60)
    response.raise_for_status()
    log.write(f"Fetched text payload: {url} ({response.status_code}, {len(response.text)} chars)")
    return response.text


def fetch_json(session: requests.Session, url: str, referer: str | None, log: SessionLog) -> dict[str, Any]:
    headers = {"Referer": referer} if referer else {}
    response = session.get(url, headers=headers, timeout=60)
    response.raise_for_status()
    log.write(f"Fetched JSON payload: {url} ({response.status_code})")
    return response.json()


def discover_secondary_url(page_html: str, page_url: str, args: argparse.Namespace) -> str:
    match = re.search(r'var apiUrl="([^"]+)"', page_html)
    if not match:
        raise RuntimeError("Could not locate Agoda secondary apiUrl in page HTML")
    relative = html.unescape(match.group(1))
    secondary_url = urljoin(page_url, relative)
    replacements = {
        "checkIn": args.check_in,
        "los": str(args.los) if args.los is not None else None,
        "adults": str(args.adults) if args.adults is not None else None,
        "rooms": str(args.rooms) if args.rooms is not None else None,
        "children": str(args.children) if args.children is not None else None,
        "travellerType": str(args.traveller_type) if args.traveller_type is not None else None,
    }
    return update_query_params(secondary_url, replacements)


def probe_room_grid(
    session: requests.Session,
    referer: str,
    hotel_id: int,
    log: SessionLog,
) -> dict[str, Any]:
    headers = {"Referer": referer, "Content-Type": "application/json"}
    payload = {"hotelId": hotel_id}
    response = session.post(
        "https://www.agoda.com/api/v1/property/room-grid",
        headers=headers,
        json=payload,
        timeout=60,
    )
    body = response.text.strip()
    probe = {
        "attempted": True,
        "url": "https://www.agoda.com/api/v1/property/room-grid",
        "request_payload": payload,
        "status_code": response.status_code,
        "response_body_preview": body[:500],
        "accessible": response.ok,
        "blocked_reason": None,
    }
    if response.status_code == 400 and "api key used is invalid" in body.lower():
        probe["accessible"] = False
        probe["blocked_reason"] = "invalid_api_key"
    log.write(
        "Probed room-grid endpoint: "
        f"status={response.status_code}, accessible={probe['accessible']}, "
        f"blocked_reason={probe['blocked_reason']}"
    )
    return probe


def summarize_useful_info(useful_info_groups: list[dict[str, Any]]) -> dict[str, Any]:
    item_lookup: dict[str, str] = {}
    groups_out: list[dict[str, Any]] = []
    for group in useful_info_groups:
        normalized_items: list[dict[str, Any]] = []
        for item in group.get("items", []):
            title = (item.get("title") or "").strip()
            description = (item.get("description") or "").strip()
            if title:
                item_lookup[title.casefold()] = description
            normalized_items.append(
                {
                    "title": title,
                    "description": description,
                    "icon": item.get("fontIcon"),
                }
            )
        groups_out.append(
            {
                "name": group.get("name"),
                "items": normalized_items,
            }
        )
    return {"lookup": item_lookup, "groups": groups_out}


def flatten_feature_groups(feature_groups: list[dict[str, Any]]) -> list[str]:
    values: list[str] = []
    for group in feature_groups:
        for feature in group.get("feature", []):
            if feature.get("available") is False:
                continue
            name = (feature.get("name") or "").strip()
            if name:
                values.append(name)
    return dedupe_strings(values)


def collect_hotel_image_sources(payload: dict[str, Any]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for image in payload.get("mosaicInitData", {}).get("images", []):
        if image.get("isRoomImage"):
            continue
        source_url = normalize_scheme_url(image.get("location") or "")
        if not source_url:
            continue
        out.append(
            {
                "group": "hotel",
                "room_slug": None,
                "title": image.get("title") or "",
                "source_url": source_url,
                "source_group": image.get("group"),
                "source_group_id": image.get("groupId"),
                "source_image_id": image.get("id"),
            }
        )
    return out


def build_bedroom_layouts(layouts: list[dict[str, Any]]) -> list[dict[str, Any]]:
    normalized_layouts: list[dict[str, Any]] = []
    for layout in layouts:
        bedrooms_out: list[dict[str, Any]] = []
        for bedroom in layout.get("bedrooms", []):
            beds_out: list[dict[str, Any]] = []
            for bed in bedroom.get("beds", []):
                beds_out.append(
                    {
                        "name": bed.get("name"),
                        "symbol": bed.get("symbol"),
                        "quantity": bed.get("quantity"),
                    }
                )
            bedrooms_out.append({"title": bedroom.get("title"), "beds": beds_out})
        normalized_layouts.append({"title": layout.get("title"), "bedrooms": bedrooms_out})
    return normalized_layouts


def extract_room_feature_values(room_payload: dict[str, Any]) -> dict[str, Any]:
    room_size = None
    view = None
    tags: list[str] = []
    for feature in room_payload.get("features", []):
        title = (feature.get("title") or "").strip()
        if not title:
            continue
        tags.append(title)
        if room_size is None:
            room_size = parse_room_size(title)
        if view is None and "view" in title.lower():
            view = title
    return {
        "room_size": room_size,
        "view": view,
        "tags": dedupe_strings(tags),
    }


def split_room_amenities(room_payload: dict[str, Any]) -> tuple[list[str], list[str], list[dict[str, Any]]]:
    all_amenities: list[str] = []
    bathroom_amenities: list[str] = []
    groups_out: list[dict[str, Any]] = []
    for group in room_payload.get("facilityGroups", []):
        group_name = (group.get("name") or "").strip()
        facilities = []
        for facility in group.get("facilities", []):
            title = (facility.get("title") or "").strip()
            if not title:
                continue
            facilities.append(title)
            all_amenities.append(title)
            if "bathroom" in group_name.lower():
                bathroom_amenities.append(title)
        groups_out.append({"name": group_name, "amenities": dedupe_strings(facilities)})
    return dedupe_strings(all_amenities), dedupe_strings(bathroom_amenities), groups_out


def parse_hotel_record(
    payload: dict[str, Any],
    source_url: str,
    secondary_url: str,
    fetched_at: str,
    folder_name: str,
    room_grid_probe: dict[str, Any],
) -> tuple[dict[str, Any], list[dict[str, Any]], list[dict[str, Any]]]:
    hotel_info = payload.get("hotelInfo", {})
    about_hotel = payload.get("aboutHotel", {})
    reviews = payload.get("reviews", {})
    map_params = payload.get("mapParams", {})
    useful_info = summarize_useful_info(about_hotel.get("usefulInfoGroups", []))
    feature_groups = about_hotel.get("featureGroups", [])
    hotel_name = hotel_info.get("name") or hotel_info.get("englishName") or ""
    address = hotel_info.get("address", {})
    review_payload = reviews.get("combinedReview", {}).get("score") or {}
    policy_notes = [clean_policy_note(note) for note in about_hotel.get("otherPolicies", [])]
    policy_notes.extend(clean_policy_note(note) for note in about_hotel.get("importantNotes", []))
    general_amenities = flatten_feature_groups(feature_groups)
    highlight_amenities = dedupe_strings(
        [(item.get("text") or "").strip() for item in payload.get("featuresYouLove", {}).get("features", [])]
    )
    breakfast_charge = parse_money_text(useful_info["lookup"].get("breakfast charge (unless included in room price)"))
    airport_transfer_fee = parse_money_text(useful_info["lookup"].get("airport transfer fee"))
    description_html = about_hotel.get("hotelDesc", {}).get("overview", "")
    description_text = clean_html_text(description_html)
    hotel_images = collect_hotel_image_sources(payload)
    field_sources = [
        {"field": "hotel.name", "source_path": "hotelInfo.name", "source_type": "direct"},
        {"field": "hotel.star_rating", "source_path": "hotelInfo.starRating.value", "source_type": "direct"},
        {"field": "hotel.address.full", "source_path": "hotelInfo.address.full", "source_type": "direct"},
        {"field": "hotel.coordinates", "source_path": "mapParams.latlng", "source_type": "direct"},
        {"field": "hotel.review.score", "source_path": "reviews.combinedReview.score.score", "source_type": "direct"},
        {"field": "hotel.description", "source_path": "aboutHotel.hotelDesc.overview", "source_type": "direct"},
        {
            "field": "hotel.check_in.from",
            "source_path": "aboutHotel.usefulInfoGroups[].items[Check-in from]",
            "source_type": "direct",
        },
        {
            "field": "hotel.check_out.until",
            "source_path": "aboutHotel.usefulInfoGroups[].items[Check-out until]",
            "source_type": "direct",
        },
    ]
    hotel_record = {
        "schema_version": "1.0",
        "folder_name": folder_name,
        "provider": "agoda",
        "source_url": source_url,
        "secondary_payload_url": secondary_url,
        "fetched_at_utc": fetched_at,
        "source_hotel_id": str(payload.get("hotelId") or ""),
        "name": hotel_name,
        "display_name": hotel_name,
        "slug": slugify(hotel_name),
        "accommodation_type": hotel_info.get("accommodationType"),
        "star_rating": hotel_info.get("starRating", {}).get("value"),
        "awards": {
            "text": clean_html_text(hotel_info.get("awardsAndAccolades", {}).get("text", "")),
            "gold_circle_year": hotel_info.get("awardsAndAccolades", {}).get("goldCircleAward", {}).get("year"),
            "gold_circle_award_text": clean_html_text(
                hotel_info.get("awardsAndAccolades", {}).get("goldCircleAward", {}).get("awardText", "")
            ),
        },
        "address": {
            "full": address.get("full"),
            "line_1": address.get("address"),
            "area": address.get("areaName"),
            "district": address.get("areaName"),
            "city": address.get("cityName"),
            "country": address.get("countryName"),
            "postal_code": address.get("postalCode"),
        },
        "coordinates": {
            "latitude": map_params.get("latlng", [None, None])[0] if map_params.get("latlng") else None,
            "longitude": map_params.get("latlng", [None, None])[1] if map_params.get("latlng") else None,
        },
        "review": {
            "score": review_payload.get("score") or reviews.get("score"),
            "score_text": review_payload.get("scoreText") or reviews.get("scoreText"),
            "review_count": review_payload.get("reviewCount") or reviews.get("reviewsCount"),
            "formatted_review_count": review_payload.get("formattedReviewCount"),
            "provider_list": reviews.get("providerList"),
        },
        "description": {
            "overview_html": description_html,
            "overview_text": description_text,
        },
        "check_in": {
            "from": useful_info["lookup"].get("check-in from"),
            "until": useful_info["lookup"].get("check-in until"),
        },
        "check_out": {
            "until": useful_info["lookup"].get("check-out until"),
        },
        "contact": {
            "phone": None,
            "email": None,
            "website": None,
        },
        "highlights": {
            "location_highlight": hotel_info.get("locationHighlightMessage"),
            "things_youll_love": highlight_amenities,
            "nearby_highlights": [
                item.get("title") or item.get("name")
                for item in payload.get("highLightsInfo", {}).get("highLights", [])
            ],
        },
        "general_amenities": general_amenities,
        "featured_amenities": highlight_amenities,
        "breakfast": {
            "available_cuisines": payload.get("breakfastInformation", {}).get("cuisines", []),
            "charge": breakfast_charge,
        },
        "restaurants": payload.get("restaurantOnSite", []),
        "policies": {
            "guest_policies": about_hotel.get("guestPolicies", {}),
            "child_policies": about_hotel.get("hotelPolicy", {}).get("childPolicies", []),
            "extra_bed_policies": about_hotel.get("hotelPolicy", {}).get("extrabedPolicies", []),
            "other_policies": policy_notes,
        },
        "important_notes": [clean_policy_note(note) for note in about_hotel.get("importantNotes", [])],
        "useful_info": useful_info["groups"],
        "property_facts": {
            "distance_from_city_center": useful_info["lookup"].get("distance from city center"),
            "travel_time_to_airport_minutes": useful_info["lookup"].get("travel time to airport (minutes)"),
            "airport_transfer_fee": airport_transfer_fee,
            "daily_internet_fee": parse_money_text(useful_info["lookup"].get("daily internet/wi-fi fee")),
            "year_opened": useful_info["lookup"].get("year property opened"),
            "most_recent_renovation": useful_info["lookup"].get("most recent renovation"),
            "number_of_floors": useful_info["lookup"].get("number of floors"),
            "number_of_rooms": useful_info["lookup"].get("number of rooms"),
            "number_of_restaurants": useful_info["lookup"].get("number of restaurants"),
            "number_of_bars_lounges": useful_info["lookup"].get("number of bars/lounges"),
            "room_voltage": useful_info["lookup"].get("room voltage"),
            "parking_fee": parse_money_text(useful_info["lookup"].get("daily parking fee")),
        },
        "images": [],
        "room_grid_probe": room_grid_probe,
    }
    return hotel_record, hotel_images, field_sources


def parse_rooms(
    payload: dict[str, Any],
    hotel_record: dict[str, Any],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    policy_notes = [clean_policy_note(note) for note in payload.get("aboutHotel", {}).get("otherPolicies", [])]
    breakfast_room_names = extract_breakfast_room_names(policy_notes)
    rooms_out: list[dict[str, Any]] = []
    image_jobs: list[dict[str, Any]] = []
    inferred_fields: list[dict[str, Any]] = []
    field_sources: list[dict[str, Any]] = []
    for room_payload in payload.get("datelessMasterRoomInfo", []):
        room_name = (room_payload.get("name") or "").strip()
        room_slug = slugify(room_name)
        feature_values = extract_room_feature_values(room_payload)
        all_amenities, bathroom_amenities, facility_groups = split_room_amenities(room_payload)
        bedroom_layouts = build_bedroom_layouts(room_payload.get("bedroomLayouts", []))
        source_room_id = str(room_payload.get("id") or room_payload.get("roomid") or "")
        breakfast_included = None
        for candidate in breakfast_room_names:
            if match_policy_room_name(candidate, room_name):
                breakfast_included = True
                inferred_fields.append(
                    {
                        "field": f"rooms.{room_slug}.breakfast_included",
                        "source_type": "inferred",
                        "reason": (
                            "Marked true from aboutHotel.hotelPolicy policy note that explicitly lists "
                            f"'{candidate}' among room types with Club Lounge breakfast benefits."
                        ),
                        "source_path": "aboutHotel.hotelPolicy.policyNotes",
                    }
                )
                break
        room_tags = list(feature_values["tags"])
        if breakfast_included:
            room_tags.append("Breakfast included")
        room_record = {
            "room_id": source_room_id,
            "name": room_name,
            "slug": room_slug,
            "provider": "agoda",
            "source_room_id": source_room_id,
            "price": {
                "currency": get_default_currency(hotel_record),
                "current_amount": None,
                "original_amount": None,
                "discounted_amount": None,
                "status": "unavailable_room_grid_blocked",
            },
            "bed_configuration": {
                "summary": room_payload.get("bedConfigurationSummary", {}).get("title"),
                "layouts": bedroom_layouts,
            },
            "number_of_beds": None,
            "max_capacity": None,
            "room_size": feature_values["room_size"],
            "view": feature_values["view"],
            "breakfast_included": breakfast_included,
            "cancellation_policy": None,
            "amenities": all_amenities,
            "bathroom_amenities": bathroom_amenities,
            "facility_groups": facility_groups,
            "image_count": 0,
            "images": [],
            "short_description": " | ".join(
                [
                    value
                    for value in [
                        feature_values["room_size"]["source_text"] if feature_values["room_size"] else None,
                        feature_values["view"],
                        room_payload.get("bedConfigurationSummary", {}).get("title"),
                    ]
                    if value
                ]
            )
            or None,
            "tags": dedupe_strings(room_tags),
            "raw_features": room_payload.get("features", []),
            "raw_propaganda_messages": room_payload.get("propagandaMessages", []),
        }
        field_sources.extend(
            [
                {
                    "field": f"rooms.{room_slug}.name",
                    "source_path": "datelessMasterRoomInfo[].name",
                    "source_type": "direct",
                },
                {
                    "field": f"rooms.{room_slug}.room_size",
                    "source_path": "datelessMasterRoomInfo[].features[]",
                    "source_type": "derived",
                },
                {
                    "field": f"rooms.{room_slug}.bed_configuration",
                    "source_path": "datelessMasterRoomInfo[].bedConfigurationSummary / bedroomLayouts",
                    "source_type": "direct",
                },
                {
                    "field": f"rooms.{room_slug}.amenities",
                    "source_path": "datelessMasterRoomInfo[].facilityGroups",
                    "source_type": "direct",
                },
            ]
        )
        for image_url in room_payload.get("images", []):
            normalized = normalize_scheme_url(image_url)
            if not normalized:
                continue
            image_jobs.append(
                {
                    "group": "room",
                    "room_slug": room_slug,
                    "title": room_name,
                    "source_url": normalized,
                    "source_group": "room",
                    "source_group_id": room_slug,
                    "source_image_id": None,
                }
            )
        rooms_out.append(room_record)

    inferred_room_map: dict[str, dict[str, Any]] = {room["slug"]: room for room in rooms_out}
    for image in payload.get("mosaicInitData", {}).get("images", []):
        if not image.get("isRoomImage"):
            continue
        normalized = normalize_scheme_url(image.get("location") or "")
        if not normalized:
            continue
        derived_room_name = normalize_room_image_title(image.get("title") or "")
        room_slug = None
        if derived_room_name:
            room_slug = resolve_room_slug(derived_room_name, rooms_out)
            if room_slug is None:
                room_slug = slugify(derived_room_name)
                if room_slug not in inferred_room_map:
                    inferred_room = build_inferred_room_record(derived_room_name, hotel_record)
                    rooms_out.append(inferred_room)
                    inferred_room_map[room_slug] = inferred_room
                    inferred_fields.append(
                        {
                            "field": f"rooms.{room_slug}.name",
                            "source_type": "inferred",
                            "reason": "Created inferred room from public mosaicInitData room-image title.",
                            "source_path": "mosaicInitData.images[].title",
                        }
                    )
                    field_sources.append(
                        {
                            "field": f"rooms.{room_slug}.name",
                            "source_path": "mosaicInitData.images[].title",
                            "source_type": "derived",
                        }
                    )
        image_jobs.append(
            {
                "group": "room",
                "room_slug": room_slug or "room_misc",
                "title": derived_room_name or (image.get("title") or ""),
                "source_url": normalized,
                "source_group": image.get("group"),
                "source_group_id": image.get("groupId"),
                "source_image_id": image.get("id"),
            }
        )
    return rooms_out, image_jobs, inferred_fields, field_sources


@dataclass
class ImageResult:
    source_url: str
    local_path: str
    status: str
    downloaded: bool
    duplicate_of: str | None
    bytes_written: int | None
    content_type: str | None
    group: str
    room_slug: str | None


def choose_extension(source_url: str, content_type: str | None) -> str:
    if content_type:
        ext = mimetypes.guess_extension(content_type.split(";")[0].strip())
        if ext:
            return ".jpg" if ext == ".jpe" else ext
    path = urlparse(source_url).path
    suffix = Path(path).suffix.lower()
    if suffix in {".jpg", ".jpeg", ".png", ".webp"}:
        return ".jpg" if suffix == ".jpeg" else suffix
    return ".jpg"


def download_images(
    session: requests.Session,
    output_dir: Path,
    image_jobs: list[dict[str, Any]],
    log: SessionLog,
) -> tuple[dict[str, ImageResult], list[dict[str, Any]], dict[str, int]]:
    seen: dict[str, ImageResult] = {}
    manifest_rows: list[dict[str, Any]] = []
    folder_counters: defaultdict[str, int] = defaultdict(int)
    group_counts: Counter[str] = Counter()
    for job in image_jobs:
        source_url = job["source_url"]
        group = job["group"]
        room_slug = job["room_slug"]
        if source_url in seen:
            existing = seen[source_url]
            manifest_rows.append(
                {
                    "source_url": source_url,
                    "local_path": existing.local_path,
                    "group": group,
                    "room_slug": room_slug,
                    "status": "duplicate_reference",
                    "duplicate_of": existing.local_path,
                    "bytes_written": existing.bytes_written,
                    "content_type": existing.content_type,
                }
            )
            continue
        folder_key = "hotel" if group == "hotel" else room_slug or "room"
        folder_counters[folder_key] += 1
        prefix = "hotel" if group == "hotel" else room_slug or "room"
        subdir = Path("images") / ("hotel" if group == "hotel" else (room_slug or "room"))
        ensure_dir(output_dir / subdir)
        response = session.get(source_url, headers={"Referer": source_url}, timeout=120)
        if response.ok:
            content_type = response.headers.get("Content-Type")
            extension = choose_extension(source_url, content_type)
            filename = f"{prefix}_{folder_counters[folder_key]:02d}{extension}"
            relative_path = (subdir / filename).as_posix()
            target_path = output_dir / relative_path
            target_path.write_bytes(response.content)
            digest = hashlib.sha256(response.content).hexdigest()
            result = ImageResult(
                source_url=source_url,
                local_path=relative_path,
                status="downloaded",
                downloaded=True,
                duplicate_of=None,
                bytes_written=len(response.content),
                content_type=content_type,
                group=group,
                room_slug=room_slug,
            )
            seen[source_url] = result
            manifest_rows.append(
                {
                    "source_url": source_url,
                    "local_path": relative_path,
                    "group": group,
                    "room_slug": room_slug,
                    "status": "downloaded",
                    "duplicate_of": None,
                    "bytes_written": len(response.content),
                    "content_type": content_type,
                    "sha256": digest,
                }
            )
            group_counts[folder_key] += 1
            log.write(f"Downloaded image: {relative_path}")
        else:
            result = ImageResult(
                source_url=source_url,
                local_path="",
                status=f"failed_http_{response.status_code}",
                downloaded=False,
                duplicate_of=None,
                bytes_written=None,
                content_type=response.headers.get("Content-Type"),
                group=group,
                room_slug=room_slug,
            )
            seen[source_url] = result
            manifest_rows.append(
                {
                    "source_url": source_url,
                    "local_path": None,
                    "group": group,
                    "room_slug": room_slug,
                    "status": result.status,
                    "duplicate_of": None,
                    "bytes_written": None,
                    "content_type": response.headers.get("Content-Type"),
                }
            )
            log.write(f"Failed image download: {source_url} ({response.status_code})")
    return seen, manifest_rows, dict(group_counts)


def attach_images_to_records(
    hotel_record: dict[str, Any],
    rooms: list[dict[str, Any]],
    hotel_image_jobs: list[dict[str, Any]],
    room_image_jobs: list[dict[str, Any]],
    image_results: dict[str, ImageResult],
) -> None:
    hotel_images: list[dict[str, Any]] = []
    for job in hotel_image_jobs:
        result = image_results.get(job["source_url"])
        if not result or not result.downloaded:
            continue
        hotel_images.append(
            {
                "local_path": result.local_path,
                "source_url": job["source_url"],
                "title": job.get("title"),
                "group": job.get("source_group"),
                "group_id": job.get("source_group_id"),
            }
        )
    hotel_record["images"] = hotel_images
    room_job_map: defaultdict[str, list[dict[str, Any]]] = defaultdict(list)
    for job in room_image_jobs:
        if job.get("room_slug"):
            room_job_map[job["room_slug"]].append(job)
    for room in rooms:
        room_images: list[dict[str, Any]] = []
        for job in room_job_map.get(room["slug"], []):
            result = image_results.get(job["source_url"])
            if not result or not result.downloaded:
                continue
            room_images.append(
                {
                    "local_path": result.local_path,
                    "source_url": job["source_url"],
                    "title": job.get("title"),
                }
            )
        deduped: list[dict[str, Any]] = []
        seen_paths: set[str] = set()
        for item in room_images:
            if item["local_path"] in seen_paths:
                continue
            seen_paths.add(item["local_path"])
            deduped.append(item)
        room["images"] = deduped
        room["image_count"] = len(deduped)


def build_app_room_seed(
    rooms: list[dict[str, Any]],
    hotel_record: dict[str, Any],
    imported_at_millis: int,
) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    rating_value = 0.0
    raw_rating = hotel_record.get("review", {}).get("score")
    try:
        rating_value = float(raw_rating) if raw_rating is not None else 0.0
    except (TypeError, ValueError):
        rating_value = 0.0
    review_count = hotel_record.get("review", {}).get("review_count") or 0
    try:
        review_count = int(review_count)
    except (TypeError, ValueError):
        review_count = 0
    for room in rooms:
        images = [image["source_url"] for image in room.get("images", [])]
        out.append(
            {
                "id": room["room_id"],
                "code": f"agoda-{hotel_record['slug']}-{room['slug']}",
                "type": room["name"],
                "price": 0.0,
                "rating": rating_value,
                "reviewCount": review_count,
                "status": "available",
                "capacity": 0,
                "images": images,
                "createdAt": imported_at_millis,
            }
        )
    return out


def build_source_manifest(
    hotel_record: dict[str, Any],
    raw_files: dict[str, str],
    room_grid_probe: dict[str, Any],
    field_sources: list[dict[str, Any]],
    inferred_fields: list[dict[str, Any]],
) -> dict[str, Any]:
    blocked_fields = [
        {
            "field": "rooms[*].price.current_amount",
            "reason": "Agoda room-grid endpoint returned invalid_api_key during probe.",
        },
        {
            "field": "rooms[*].price.original_amount",
            "reason": "Agoda room-grid endpoint returned invalid_api_key during probe.",
        },
        {
            "field": "rooms[*].price.discounted_amount",
            "reason": "Agoda room-grid endpoint returned invalid_api_key during probe.",
        },
        {
            "field": "rooms[*].cancellation_policy",
            "reason": "No public offer payload was accessible from the probe completed in this run.",
        },
        {
            "field": "rooms[*].max_capacity",
            "reason": "No structured capacity field was found in datelessMasterRoomInfo.",
        },
        {
            "field": "hotel.contact.phone",
            "reason": "No public phone field was found in the accessible Agoda payload.",
        },
        {
            "field": "hotel.contact.email",
            "reason": "No public email field was found in the accessible Agoda payload.",
        },
    ]
    return {
        "schema_version": "1.0",
        "provider": "agoda",
        "hotel_folder": hotel_record["folder_name"],
        "hotel_slug": hotel_record["slug"],
        "source_url": hotel_record["source_url"],
        "secondary_payload_url": hotel_record["secondary_payload_url"],
        "source_hotel_id": hotel_record["source_hotel_id"],
        "fetched_at_utc": hotel_record["fetched_at_utc"],
        "raw_files": raw_files,
        "room_grid_probe": room_grid_probe,
        "field_sources": field_sources,
        "blocked_fields": blocked_fields,
        "inferred_fields": inferred_fields,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Import Agoda hotel data into HotelList structure.")
    parser.add_argument("--url", required=True, help="Agoda hotel URL")
    parser.add_argument("--output-dir", required=True, help="Destination hotel folder")
    parser.add_argument("--hotel-folder-name", default="SaigonPrinceHotel", help="Stable hotel folder name")
    parser.add_argument("--check-in", help="Optional Agoda check-in date (YYYY-MM-DD)")
    parser.add_argument("--los", type=int, help="Optional length of stay")
    parser.add_argument("--adults", type=int, help="Optional adult count")
    parser.add_argument("--rooms", type=int, help="Optional room count")
    parser.add_argument("--children", type=int, help="Optional child count")
    parser.add_argument("--traveller-type", type=int, help="Optional Agoda travellerType")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    log = SessionLog()
    output_dir = Path(args.output_dir).resolve()
    data_dir = output_dir / "data"
    images_dir = output_dir / "images"
    raw_dir = output_dir / "raw"
    manifest_dir = output_dir / "manifest"
    ensure_dir(output_dir)
    for directory in [data_dir, images_dir, raw_dir, manifest_dir]:
        reset_dir(directory)

    session = build_session()
    fetched_at = now_utc_iso()
    imported_at_millis = int(datetime.now(timezone.utc).timestamp() * 1000)

    page_html = fetch_text(session, args.url, None, log)
    page_html_path = raw_dir / "agoda_page.html"
    page_html_path.write_text(page_html, encoding="utf-8")

    secondary_url = discover_secondary_url(page_html, args.url, args)
    secondary_payload = fetch_json(session, secondary_url, args.url, log)
    secondary_payload_path = raw_dir / "agoda_room_payload.json"
    write_json(secondary_payload_path, secondary_payload)

    hotel_id = int(secondary_payload.get("hotelId") or 0)
    room_grid_probe = probe_room_grid(session, args.url, hotel_id, log)

    hotel_record, hotel_image_jobs, hotel_field_sources = parse_hotel_record(
        secondary_payload,
        args.url,
        secondary_url,
        fetched_at,
        args.hotel_folder_name,
        room_grid_probe,
    )
    rooms, room_image_jobs, inferred_fields, room_field_sources = parse_rooms(secondary_payload, hotel_record)

    all_image_jobs = hotel_image_jobs + room_image_jobs
    image_results, image_manifest_rows, group_counts = download_images(session, output_dir, all_image_jobs, log)
    attach_images_to_records(hotel_record, rooms, hotel_image_jobs, room_image_jobs, image_results)

    hotel_json_path = data_dir / "hotel.json"
    rooms_json_path = data_dir / "rooms.json"
    app_seed_path = data_dir / "app_room_seed.json"

    write_json(hotel_json_path, hotel_record)
    write_json(
        rooms_json_path,
        {
            "schema_version": "1.0",
            "hotel_slug": hotel_record["slug"],
            "room_count": len(rooms),
            "rooms": rooms,
        },
    )
    write_json(app_seed_path, build_app_room_seed(rooms, hotel_record, imported_at_millis))

    source_manifest = build_source_manifest(
        hotel_record,
        raw_files={
            "page_html": "raw/agoda_page.html",
            "secondary_payload": "raw/agoda_room_payload.json",
            "import_log": "raw/import_log.txt",
        },
        room_grid_probe=room_grid_probe,
        field_sources=hotel_field_sources + room_field_sources,
        inferred_fields=inferred_fields,
    )
    write_json(manifest_dir / "source_manifest.json", source_manifest)
    write_json(
        manifest_dir / "image_manifest.json",
        {
            "schema_version": "1.0",
            "generated_at_utc": fetched_at,
            "hotel_slug": hotel_record["slug"],
            "counts": {
                "unique_downloaded": len([row for row in image_manifest_rows if row["status"] == "downloaded"]),
                "total_references": len(image_manifest_rows),
                "by_group": group_counts,
            },
            "images": image_manifest_rows,
        },
    )

    log.write(f"Hotel record written: {hotel_json_path}")
    log.write(f"Room record written: {rooms_json_path}")
    log.write(f"App room seed written: {app_seed_path}")
    log.write(
        "Import summary: "
        f"rooms={len(rooms)}, hotel_images={len(hotel_record['images'])}, "
        f"room_image_references={sum(room['image_count'] for room in rooms)}"
    )
    log.dump(raw_dir / "import_log.txt")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise
