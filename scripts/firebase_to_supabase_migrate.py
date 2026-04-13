#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import secrets
import string
import traceback
from dataclasses import dataclass
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import quote, unquote, urlparse

import psycopg
import requests
from firebase_admin import auth as firebase_auth
from firebase_admin import credentials, firestore, initialize_app
from google.cloud import storage
from google.oauth2 import service_account
from psycopg.types.json import Json

ROOT = Path(__file__).resolve().parents[1]

CONFIG_PATHS = {
    "firebase_service": ROOT / "firebase-service.json",
    "supabase_env": ROOT / "supabase.env",
    "supabase_db_url": ROOT / "supabase-db-url.txt",
    "migration_scope": ROOT / "migration_scope.json",
    "storage_mapping": ROOT / "storage_mapping.json",
    "cutover_plan": ROOT / "cutover_plan.json",
    "data_rules": ROOT / "data_rules.json",
    "auth_strategy": ROOT / "auth_strategy.txt",
}


@dataclass
class TableSpec:
    collection: str
    table: str
    columns: list[tuple[str, str, str]]
    field_map: dict[str, str]


KNOWN_SPECS: list[TableSpec] = [
    TableSpec(
        collection="users",
        table="users",
        columns=[
            ("id", "text", "text primary key"),
            ("name", "text", "text"),
            ("email", "text", "text"),
            ("phone", "text", "text"),
            ("role", "text", "text"),
            ("created_at", "timestamptz", "timestamptz"),
            ("raw", "jsonb", "jsonb not null default '{}'::jsonb"),
        ],
        field_map={
            "name": "name",
            "email": "email",
            "phone": "phone",
            "role": "role",
            "createdAt": "created_at",
        },
    ),
    TableSpec(
        collection="rooms",
        table="rooms",
        columns=[
            ("id", "text", "text primary key"),
            ("code", "text", "text"),
            ("type", "text", "text"),
            ("price", "numeric", "double precision"),
            ("rating", "numeric", "double precision"),
            ("review_count", "int", "integer"),
            ("status", "text", "text"),
            ("capacity", "int", "integer"),
            ("images", "jsonb", "jsonb"),
            ("created_at", "timestamptz", "timestamptz"),
            ("raw", "jsonb", "jsonb not null default '{}'::jsonb"),
        ],
        field_map={
            "code": "code",
            "type": "type",
            "price": "price",
            "rating": "rating",
            "reviewCount": "review_count",
            "status": "status",
            "capacity": "capacity",
            "images": "images",
            "createdAt": "created_at",
        },
    ),
    TableSpec(
        collection="bookings",
        table="bookings",
        columns=[
            ("id", "text", "text primary key"),
            ("user_id", "text", "text"),
            ("room_id", "text", "text"),
            ("check_in", "date", "date"),
            ("check_out", "date", "date"),
            ("status", "text", "text"),
            ("total", "numeric", "double precision"),
            ("add_ons", "jsonb", "jsonb"),
            ("created_at", "timestamptz", "timestamptz"),
            ("raw", "jsonb", "jsonb not null default '{}'::jsonb"),
        ],
        field_map={
            "userId": "user_id",
            "roomId": "room_id",
            "checkIn": "check_in",
            "checkOut": "check_out",
            "status": "status",
            "total": "total",
            "addOns": "add_ons",
            "createdAt": "created_at",
        },
    ),
    TableSpec(
        collection="reviews",
        table="reviews",
        columns=[
            ("id", "text", "text primary key"),
            ("room_id", "text", "text"),
            ("user_id", "text", "text"),
            ("rating", "int", "integer"),
            ("comment", "text", "text"),
            ("created_at", "timestamptz", "timestamptz"),
            ("raw", "jsonb", "jsonb not null default '{}'::jsonb"),
        ],
        field_map={
            "roomId": "room_id",
            "userId": "user_id",
            "rating": "rating",
            "comment": "comment",
            "createdAt": "created_at",
        },
    ),
    TableSpec(
        collection="issues",
        table="issues",
        columns=[
            ("id", "text", "text primary key"),
            ("user_id", "text", "text"),
            ("room_id", "text", "text"),
            ("title", "text", "text"),
            ("description", "text", "text"),
            ("status", "text", "text"),
            ("created_at", "timestamptz", "timestamptz"),
            ("raw", "jsonb", "jsonb not null default '{}'::jsonb"),
        ],
        field_map={
            "userId": "user_id",
            "roomId": "room_id",
            "title": "title",
            "description": "description",
            "status": "status",
            "createdAt": "created_at",
        },
    ),
    TableSpec(
        collection="vouchers",
        table="vouchers",
        columns=[
            ("id", "text", "text primary key"),
            ("code", "text", "text"),
            ("type", "text", "text"),
            ("value", "numeric", "double precision"),
            ("min_spend", "numeric", "double precision"),
            ("start_at", "date", "date"),
            ("end_at", "date", "date"),
            ("active", "bool", "boolean"),
            ("usage_limit", "int", "integer"),
            ("raw", "jsonb", "jsonb not null default '{}'::jsonb"),
        ],
        field_map={
            "code": "code",
            "type": "type",
            "value": "value",
            "minSpend": "min_spend",
            "startAt": "start_at",
            "endAt": "end_at",
            "active": "active",
            "usageLimit": "usage_limit",
        },
    ),
    TableSpec(
        collection="posters",
        table="posters",
        columns=[
            ("id", "text", "text primary key"),
            ("type", "text", "text"),
            ("title", "text", "text"),
            ("content", "text", "text"),
            ("image_url", "text", "text"),
            ("role", "text", "text"),
            ("created_at", "timestamptz", "timestamptz"),
            ("raw", "jsonb", "jsonb not null default '{}'::jsonb"),
        ],
        field_map={
            "type": "type",
            "title": "title",
            "content": "content",
            "imageUrl": "image_url",
            "role": "role",
            "createdAt": "created_at",
        },
    ),
    TableSpec(
        collection="addOns",
        table="add_ons",
        columns=[
            ("id", "text", "text primary key"),
            ("name", "text", "text"),
            ("price", "numeric", "double precision"),
            ("category", "text", "text"),
            ("active", "bool", "boolean"),
            ("raw", "jsonb", "jsonb not null default '{}'::jsonb"),
        ],
        field_map={
            "name": "name",
            "price": "price",
            "category": "category",
            "active": "active",
        },
    ),
    TableSpec(
        collection="notifications",
        table="notifications",
        columns=[
            ("id", "text", "text primary key"),
            ("title", "text", "text"),
            ("body", "text", "text"),
            ("target_role", "text", "text"),
            ("created_at", "timestamptz", "timestamptz"),
            ("raw", "jsonb", "jsonb not null default '{}'::jsonb"),
        ],
        field_map={
            "title": "title",
            "body": "body",
            "targetRole": "target_role",
            "createdAt": "created_at",
        },
    ),
    TableSpec(
        collection="payments",
        table="payments",
        columns=[
            ("id", "text", "text primary key"),
            ("booking_id", "text", "text"),
            ("user_id", "text", "text"),
            ("amount", "numeric", "double precision"),
            ("method", "text", "text"),
            ("status", "text", "text"),
            ("card_last4", "text", "text"),
            ("created_at", "timestamptz", "timestamptz"),
            ("raw", "jsonb", "jsonb not null default '{}'::jsonb"),
        ],
        field_map={
            "bookingId": "booking_id",
            "userId": "user_id",
            "amount": "amount",
            "method": "method",
            "status": "status",
            "cardLast4": "card_last4",
            "createdAt": "created_at",
        },
    ),
    TableSpec(
        collection="meta",
        table="meta",
        columns=[
            ("id", "text", "text primary key"),
            ("version", "bigint", "bigint"),
            ("seeded_at", "timestamptz", "timestamptz"),
            ("raw", "jsonb", "jsonb not null default '{}'::jsonb"),
        ],
        field_map={
            "version": "version",
            "seededAt": "seeded_at",
        },
    ),
]


def now_str() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def log(msg: str) -> None:
    print(f"[{now_str()}] {msg}")


def load_text(path: Path) -> str:
    return path.read_text(encoding="utf-8").strip()


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def load_env(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        raw = line.strip()
        if not raw or raw.startswith("#") or "=" not in raw:
            continue
        key, value = raw.split("=", 1)
        out[key.strip()] = value.strip()
    return out


def ensure_exists(paths: dict[str, Path]) -> None:
    missing = [str(p) for p in paths.values() if not p.exists()]
    if missing:
        raise FileNotFoundError("Missing files: " + ", ".join(missing))


def safe_identifier(name: str) -> str:
    x = re.sub(r"[^a-zA-Z0-9_]", "_", name)
    if not re.match(r"^[A-Za-z_]", x):
        x = f"t_{x}"
    return x.lower()


def normalize_supabase_db_url(raw: str, env: dict[str, str]) -> str:
    db_password = env.get("SUPABASE_DB_PASSWORD", "")
    project_ref = env.get("SUPABASE_PROJECT_REF", "")

    if raw and "[YOUR-PASSWORD]" not in raw:
        return raw

    if not db_password or not project_ref:
        raise ValueError("Cannot build DB URL: SUPABASE_DB_PASSWORD or SUPABASE_PROJECT_REF missing")

    enc_password = quote(db_password, safe="")
    return f"postgresql://postgres:{enc_password}@db.{project_ref}.supabase.co:5432/postgres?sslmode=require"


def parse_iso_datetime(value: str) -> datetime | None:
    if not value:
        return None
    x = value.strip()
    if x.endswith("Z"):
        x = x[:-1] + "+00:00"
    try:
        dt = datetime.fromisoformat(x)
    except ValueError:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt


def to_jsonable(value: Any) -> Any:
    if isinstance(value, dict):
        return {str(k): to_jsonable(v) for k, v in value.items()}
    if isinstance(value, list):
        return [to_jsonable(v) for v in value]
    if isinstance(value, datetime):
        if value.tzinfo is None:
            value = value.replace(tzinfo=timezone.utc)
        return value.isoformat()
    if isinstance(value, date):
        return value.isoformat()
    return value


def convert_value(value: Any, value_type: str, rules: dict[str, Any]) -> Any:
    if isinstance(value, str) and value == "" and rules.get("empty_string_to_null", False):
        return None

    if value is None:
        return None

    if value_type == "text":
        return str(value)

    if value_type == "int":
        try:
            return int(value)
        except Exception:
            return None

    if value_type == "bigint":
        try:
            return int(value)
        except Exception:
            return None

    if value_type == "numeric":
        try:
            return float(value)
        except Exception:
            return None

    if value_type == "bool":
        if isinstance(value, bool):
            return value
        s = str(value).strip().lower()
        return s in {"1", "true", "yes", "y", "on"}

    if value_type == "date":
        if isinstance(value, date) and not isinstance(value, datetime):
            return value
        if isinstance(value, datetime):
            return value.date()
        if isinstance(value, str):
            dt = parse_iso_datetime(value)
            if dt:
                return dt.date()
            try:
                return datetime.strptime(value, "%Y-%m-%d").date()
            except Exception:
                return None
        return None

    if value_type == "timestamptz":
        if isinstance(value, datetime):
            return value if value.tzinfo else value.replace(tzinfo=timezone.utc)
        if isinstance(value, (int, float)) and rules.get("convert_epoch_millis_to_timestamptz", True):
            return datetime.fromtimestamp(float(value) / 1000.0, tz=timezone.utc)
        if isinstance(value, str):
            return parse_iso_datetime(value)
        return None

    if value_type == "jsonb":
        return to_jsonable(value)

    return value


def create_table(conn: psycopg.Connection, schema: str, spec: TableSpec) -> None:
    col_defs = ", ".join([f'"{name}" {sql_type}' for name, _kind, sql_type in spec.columns])
    sql = f'create table if not exists "{schema}"."{spec.table}" ({col_defs});'
    conn.execute(sql)


def create_unknown_spec(collection: str) -> TableSpec:
    table = safe_identifier(f"fb_{collection}")
    return TableSpec(
        collection=collection,
        table=table,
        columns=[
            ("id", "text", "text primary key"),
            ("raw", "jsonb", "jsonb not null default '{}'::jsonb"),
        ],
        field_map={},
    )


def prepare_row(doc_id: str, data: dict[str, Any], spec: TableSpec, rules: dict[str, Any]) -> dict[str, Any]:
    out: dict[str, Any] = {}
    out["id"] = doc_id

    col_type_map = {name: kind for name, kind, _ in spec.columns}

    for fs_field, column in spec.field_map.items():
        if fs_field not in data:
            continue
        out[column] = convert_value(data.get(fs_field), col_type_map[column], rules)

    if "raw" in col_type_map:
        out["raw"] = to_jsonable(data)

    return out


def upsert_row(conn: psycopg.Connection, schema: str, spec: TableSpec, row: dict[str, Any]) -> None:
    ordered_columns = [name for name, _kind, _sql in spec.columns if name in row]
    values: list[Any] = []

    for col in ordered_columns:
        val = row[col]
        col_type = next(kind for name, kind, _ in spec.columns if name == col)
        if col_type == "jsonb" and val is not None:
            values.append(Json(val))
        else:
            values.append(val)

    cols_sql = ", ".join([f'"{c}"' for c in ordered_columns])
    placeholders = ", ".join(["%s"] * len(ordered_columns))
    update_columns = [c for c in ordered_columns if c != "id"]
    update_sql = ", ".join([f'"{c}" = excluded."{c}"' for c in update_columns])

    sql = (
        f'insert into "{schema}"."{spec.table}" ({cols_sql}) '
        f"values ({placeholders}) "
        f"on conflict (id) do update set {update_sql};"
    )
    conn.execute(sql, values)


def random_password(length: int = 24) -> str:
    alphabet = string.ascii_letters + string.digits + "!@#$%^&*()-_=+"
    return "".join(secrets.choice(alphabet) for _ in range(length))


def supabase_headers(service_role_key: str, content_json: bool = True) -> dict[str, str]:
    headers = {
        "apikey": service_role_key,
        "Authorization": f"Bearer {service_role_key}",
    }
    if content_json:
        headers["Content-Type"] = "application/json"
    return headers


def migrate_auth(firebase_app, supabase_url: str, service_role_key: str, strategy: str, report: dict[str, Any]) -> None:
    created = 0
    skipped = 0
    failed = 0
    warnings: list[str] = []

    endpoint = f"{supabase_url.rstrip('/')}/auth/v1/admin/users"

    page = firebase_auth.list_users(app=firebase_app)
    while page:
        for user in page.users:
            if not user.email and not user.phone_number:
                skipped += 1
                continue

            payload: dict[str, Any] = {
                "email_confirm": bool(user.email_verified),
                "phone_confirm": bool(user.phone_number),
                "user_metadata": {
                    "firebase_uid": user.uid,
                    "display_name": user.display_name or "",
                    "photo_url": user.photo_url or "",
                },
                "app_metadata": {
                    "migrated_from": "firebase",
                },
            }
            if user.email:
                payload["email"] = user.email
            if user.phone_number:
                payload["phone"] = user.phone_number

            if strategy == "reset_password":
                payload["password"] = random_password()
            else:
                payload["password"] = random_password()
                warnings.append("auth_strategy is not reset_password; fallback random password was used")

            res = requests.post(endpoint, headers=supabase_headers(service_role_key), json=payload, timeout=30)
            if 200 <= res.status_code < 300:
                created += 1
                continue

            body = res.text[:500]
            body_lower = body.lower()
            if (
                "already registered" in body_lower
                or "already exists" in body_lower
                or "\"email_exists\"" in body_lower
                or "\"phone_exists\"" in body_lower
            ):
                skipped += 1
            else:
                failed += 1
                warnings.append(f"Auth user migrate failed (uid={user.uid}): HTTP {res.status_code} {body}")

        page = page.get_next_page()

    report["auth"] = {
        "created": created,
        "skipped": skipped,
        "failed": failed,
        "warnings": warnings,
    }


def parse_storage_object_path(raw_value: Any, firebase_bucket: str, mapping: dict[str, Any], supabase_url: str) -> str | None:
    if not raw_value:
        return None
    value = str(raw_value).strip()
    if not value:
        return None

    if supabase_url and value.startswith(supabase_url):
        return None

    if value.startswith("gs://"):
        without = value[5:]
        parts = without.split("/", 1)
        if len(parts) == 2:
            bucket_name, obj_path = parts
            if bucket_name == firebase_bucket:
                return obj_path
            return obj_path
        return None

    if value.startswith("http://") or value.startswith("https://"):
        parsed = urlparse(value)

        if "firebasestorage.googleapis.com" in parsed.netloc:
            match = re.search(r"/v0/b/([^/]+)/o/(.+)$", parsed.path)
            if match:
                return unquote(match.group(2))

        if parsed.netloc == "storage.googleapis.com":
            parts = parsed.path.lstrip("/").split("/", 1)
            if len(parts) == 2:
                bucket_name, obj_path = parts
                if bucket_name == firebase_bucket:
                    return obj_path

        bucket_domain = f"{firebase_bucket}.storage.googleapis.com"
        if parsed.netloc == bucket_domain:
            return parsed.path.lstrip("/")

        return None

    prefixes = mapping.get("firebase", {}).get("path_prefixes", [])
    for prefix in prefixes:
        if value.startswith(prefix):
            return value

    return None


def ensure_supabase_bucket(supabase_url: str, service_role_key: str, bucket: str, public_read: bool) -> None:
    endpoint = f"{supabase_url.rstrip('/')}/storage/v1/bucket"
    payload = {
        "name": bucket,
        "public": bool(public_read),
    }
    res = requests.post(endpoint, headers=supabase_headers(service_role_key), json=payload, timeout=30)
    if res.status_code in (200, 201, 409):
        return
    if res.status_code == 400 and "already" in res.text.lower():
        return
    raise RuntimeError(f"Create bucket failed {bucket}: HTTP {res.status_code} {res.text[:400]}")


def upload_to_supabase_storage(
    supabase_url: str,
    service_role_key: str,
    bucket: str,
    object_path: str,
    content: bytes,
) -> None:
    safe_path = quote(object_path, safe="/")
    endpoint = f"{supabase_url.rstrip('/')}/storage/v1/object/{bucket}/{safe_path}"
    headers = {
        "apikey": service_role_key,
        "Authorization": f"Bearer {service_role_key}",
        "x-upsert": "true",
        "Content-Type": "application/octet-stream",
    }
    res = requests.post(endpoint, headers=headers, data=content, timeout=120)
    if 200 <= res.status_code < 300:
        return
    raise RuntimeError(f"Upload failed {bucket}/{object_path}: HTTP {res.status_code} {res.text[:400]}")


def migrate_storage(
    firestore_db,
    firebase_service_file: Path,
    storage_cfg: dict[str, Any],
    supabase_url: str,
    service_role_key: str,
    report: dict[str, Any],
) -> None:
    firebase_bucket = storage_cfg.get("firebase_bucket", "").strip()
    if not firebase_bucket:
        raise ValueError("storage_mapping.json missing firebase_bucket")

    creds = service_account.Credentials.from_service_account_file(str(firebase_service_file))
    gcs = storage.Client(project=creds.project_id, credentials=creds)
    bucket = gcs.bucket(firebase_bucket)

    mappings = storage_cfg.get("mappings", [])
    default_bucket = storage_cfg.get("supabase_bucket_default", "rooms")
    public_read = bool(storage_cfg.get("public_read", True))
    create_missing = bool(storage_cfg.get("create_missing_supabase_buckets", True))
    ignore_missing = bool(storage_cfg.get("ignore_missing_source_object", True))

    used_supabase_buckets = set()
    for m in mappings:
        if not m.get("enabled", True):
            continue
        target_bucket = m.get("supabase", {}).get("bucket", "") or default_bucket
        used_supabase_buckets.add(target_bucket)

    if create_missing:
        for b in sorted(used_supabase_buckets):
            ensure_supabase_bucket(supabase_url, service_role_key, b, public_read)

    copied = 0
    missing = 0
    failed = 0
    skipped = 0
    warnings: list[str] = []

    for mapping in mappings:
        if not mapping.get("enabled", True):
            continue

        source = mapping.get("source", {})
        source_mode = source.get("mode", "")
        target_bucket = mapping.get("supabase", {}).get("bucket", "") or default_bucket
        target_prefix = mapping.get("supabase", {}).get("target_prefix", "").strip("/")
        preserve = bool(mapping.get("supabase", {}).get("preserve_relative_path", True))

        object_paths: set[str] = set()

        if source_mode == "static_object_list":
            for obj in source.get("objects", []):
                if obj:
                    object_paths.add(str(obj).strip())
        else:
            collection = source.get("collection", "")
            field = source.get("field", "")
            field_type = source.get("type", "single_url_or_path")
            if not collection or not field:
                warnings.append(f"Storage mapping missing collection/field: {mapping.get('name', '<unnamed>')}")
                continue

            for doc in firestore_db.collection(collection).stream():
                data = doc.to_dict() or {}
                raw_val = data.get(field)
                values = raw_val if isinstance(raw_val, list) else [raw_val]
                if field_type.startswith("array") and not isinstance(raw_val, list):
                    values = []

                for v in values:
                    obj_path = parse_storage_object_path(v, firebase_bucket, mapping, supabase_url)
                    if obj_path:
                        object_paths.add(obj_path)
                    elif v:
                        skipped += 1

        for src_obj_path in sorted(object_paths):
            try:
                blob = bucket.blob(src_obj_path)
                if not blob.exists():
                    if ignore_missing:
                        missing += 1
                        continue
                    raise FileNotFoundError(f"Firebase object not found: {src_obj_path}")

                data = blob.download_as_bytes()
                if preserve:
                    dst_obj_path = src_obj_path
                else:
                    dst_obj_path = Path(src_obj_path).name

                if target_prefix:
                    dst_obj_path = f"{target_prefix}/{dst_obj_path}".replace("\\", "/")

                upload_to_supabase_storage(
                    supabase_url=supabase_url,
                    service_role_key=service_role_key,
                    bucket=target_bucket,
                    object_path=dst_obj_path,
                    content=data,
                )
                copied += 1
            except Exception as exc:
                failed += 1
                warnings.append(f"Storage copy failed {src_obj_path}: {exc}")

    report["storage"] = {
        "copied": copied,
        "missing_source": missing,
        "skipped": skipped,
        "failed": failed,
        "warnings": warnings,
    }


def migrate_firestore_to_postgres(
    firestore_db,
    conn: psycopg.Connection,
    schema: str,
    rules: dict[str, Any],
    overwrite_existing_data: bool,
    report: dict[str, Any],
) -> None:
    known_by_collection = {s.collection: s for s in KNOWN_SPECS}

    conn.execute(f'create schema if not exists "{schema}";')

    collection_refs = list(firestore_db.collections())
    collection_ref_map = {c.id: c for c in collection_refs}
    all_specs: list[TableSpec] = list(KNOWN_SPECS)

    # Always create the full known schema, even if some collections are empty/missing.
    for spec in KNOWN_SPECS:
        create_table(conn, schema, spec)

    # Create fallback tables for unknown collections.
    for col_ref in collection_refs:
        col_name = col_ref.id
        if col_name in known_by_collection:
            continue
        spec = create_unknown_spec(col_name)
        create_table(conn, schema, spec)
        all_specs.append(spec)

    if overwrite_existing_data:
        for spec in all_specs:
            conn.execute(f'truncate table "{schema}"."{spec.table}";')

    conn.commit()

    counts: dict[str, dict[str, Any]] = {}

    migrate_specs: list[tuple[str, TableSpec, Any]] = []
    for spec in KNOWN_SPECS:
        migrate_specs.append((spec.collection, spec, collection_ref_map.get(spec.collection)))
    for col_ref in collection_refs:
        if col_ref.id in known_by_collection:
            continue
        extra_spec = create_unknown_spec(col_ref.id)
        migrate_specs.append((col_ref.id, extra_spec, col_ref))

    for col_name, spec, col_ref in migrate_specs:

        total_docs = 0
        migrated = 0
        failed = 0
        warnings: list[str] = []

        if col_ref is not None:
            for doc in col_ref.stream():
                total_docs += 1
                try:
                    data = doc.to_dict() or {}
                    row = prepare_row(doc.id, data, spec, rules)
                    upsert_row(conn, schema, spec, row)
                    migrated += 1
                except Exception as exc:
                    failed += 1
                    warnings.append(f"{doc.id}: {exc}")

        conn.commit()

        table_count = conn.execute(f'select count(*) from "{schema}"."{spec.table}";').fetchone()[0]
        counts[col_name] = {
            "table": spec.table,
            "firestore_docs": total_docs,
            "migrated_docs": migrated,
            "failed_docs": failed,
            "postgres_rows": int(table_count),
            "warnings": warnings[:30],
        }

    report["firestore"] = counts


def validate_cutover(cutover_plan: dict[str, Any], report: dict[str, Any]) -> None:
    freeze_raw = str(cutover_plan.get("freeze_firebase_writes_at", "")).strip()
    if not freeze_raw:
        return
    freeze_dt = parse_iso_datetime(freeze_raw)
    if not freeze_dt:
        report.setdefault("warnings", []).append(f"Cannot parse freeze_firebase_writes_at: {freeze_raw}")
        return

    now = datetime.now(timezone.utc)
    if now < freeze_dt.astimezone(timezone.utc):
        report.setdefault("warnings", []).append(
            f"Cutover freeze time has not been reached yet ({freeze_raw}). Data may change after migration run."
        )


def main() -> int:
    report: dict[str, Any] = {
        "started_at": datetime.now(timezone.utc).isoformat(),
        "warnings": [],
        "errors": [],
    }

    try:
        ensure_exists(CONFIG_PATHS)

        scope = load_json(CONFIG_PATHS["migration_scope"])
        storage_cfg = load_json(CONFIG_PATHS["storage_mapping"])
        cutover_plan = load_json(CONFIG_PATHS["cutover_plan"])
        rules = load_json(CONFIG_PATHS["data_rules"])
        auth_strategy = load_text(CONFIG_PATHS["auth_strategy"]).strip()
        supabase_env = load_env(CONFIG_PATHS["supabase_env"])

        db_url_raw = load_text(CONFIG_PATHS["supabase_db_url"])
        db_url = normalize_supabase_db_url(db_url_raw, supabase_env)

        supabase_url = supabase_env.get("SUPABASE_URL", "").strip()
        service_role_key = supabase_env.get("SUPABASE_SERVICE_ROLE_KEY", "").strip()
        if not supabase_url or not service_role_key:
            raise ValueError("supabase.env missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY")

        schema = str(scope.get("supabase_schema", "public")).strip() or "public"
        overwrite_existing_data = bool(scope.get("overwrite_existing_data", False))

        validate_cutover(cutover_plan, report)

        log("Initializing Firebase Admin")
        cred = credentials.Certificate(str(CONFIG_PATHS["firebase_service"]))
        firebase_app = initialize_app(cred)
        firestore_db = firestore.client(app=firebase_app)

        log("Connecting to Supabase Postgres")
        conn = psycopg.connect(db_url, connect_timeout=20)

        if scope.get("migrate_firestore", True):
            log("Migrating Firestore to Supabase Postgres")
            migrate_firestore_to_postgres(
                firestore_db=firestore_db,
                conn=conn,
                schema=schema,
                rules=rules,
                overwrite_existing_data=overwrite_existing_data,
                report=report,
            )

        if scope.get("migrate_firebase_auth", False):
            log("Migrating Firebase Auth to Supabase Auth")
            migrate_auth(
                firebase_app=firebase_app,
                supabase_url=supabase_url,
                service_role_key=service_role_key,
                strategy=auth_strategy,
                report=report,
            )

        if scope.get("migrate_firebase_storage", False):
            log("Migrating Firebase Storage to Supabase Storage")
            migrate_storage(
                firestore_db=firestore_db,
                firebase_service_file=CONFIG_PATHS["firebase_service"],
                storage_cfg=storage_cfg,
                supabase_url=supabase_url,
                service_role_key=service_role_key,
                report=report,
            )

        conn.close()

    except Exception as exc:
        report["errors"].append(str(exc))
        report["errors"].append(traceback.format_exc())

    report["finished_at"] = datetime.now(timezone.utc).isoformat()
    output_path = ROOT / f"migration_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    log(f"Migration report saved to: {output_path}")

    has_error = bool(report.get("errors"))
    return 1 if has_error else 0


if __name__ == "__main__":
    raise SystemExit(main())
