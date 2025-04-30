import logging
import os
from typing import Optional
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import StreamingResponse, Response
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import aiofiles
import psycopg2
import sqlite3
from apscheduler.schedulers.background import BackgroundScheduler
import time
from datetime import datetime, timezone
import requests
from psycopg2.extras import execute_values, RealDictCursor

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger("combined-script")

# Configuration
STORAGE_DIR = os.environ.get("STORAGE_DIR", "./storage")
BACKUP_DIR = os.environ.get("BACKUP_DIR", "./backup")
PODCAST_DB_FILE = "kotlinapp_data.db"
SESSION_DB_FILE = "kotlinapp_sessions.db"
CHUNK_SIZE = 1024 * 1024  # 1MB chunks for streaming
PG_HOST = os.environ.get("PG_HOST", "db")
PG_PORT = int(os.environ.get("PG_PORT", "5432"))
PG_DBNAME = os.environ.get("PG_DBNAME", "kotlinconfg")
PG_USER = os.environ.get("PG_USER", "postgres")
PG_PASSWORD = os.environ.get("PG_PASSWORD", "postgres")
BACKEND_URL = os.environ.get("BACKEND_URL", "http://0.0.0.0:8080")
POLL_INTERVAL = 30  # Seconds between backend health checks

# Create directories
os.makedirs(STORAGE_DIR, exist_ok=True)
os.makedirs(BACKUP_DIR, exist_ok=True)

# Initialize FastAPI app
app = FastAPI(title="KotlinApp Combined Server", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Models
class VersionInfo(BaseModel):
    version: str
    required: bool
    url: str
    size: int
    hash: Optional[str] = None
    description: Optional[str] = None

# Table mappings and column mappings (unchanged)
PODCAST_TABLES = [
    {"sqlite_table": "PodcastChannelCategories", "pg_table": "podcast_channel_categories", "id_column": "id", "has_dependencies": False},
    {"sqlite_table": "PodcastEpisodeCategories", "pg_table": "podcast_episode_categories", "id_column": "id", "has_dependencies": False},
    {"sqlite_table": "PodcastChannels", "pg_table": "podcast_channels", "id_column": "id", "has_dependencies": False},
    {"sqlite_table": "PodcastEpisodes", "pg_table": "podcast_episodes", "id_column": "id", "has_dependencies": True, "depends_on": ["PodcastChannels"]},
    {"sqlite_table": "ChannelCategoryMap", "pg_table": "channel_category_map", "id_column": None, "has_dependencies": True, "depends_on": ["PodcastChannels", "PodcastChannelCategories"]},
    {"sqlite_table": "EpisodeCategoryMap", "pg_table": "episode_category_map", "id_column": None, "has_dependencies": True, "depends_on": ["PodcastEpisodes", "PodcastEpisodeCategories"]},
]

SESSION_TABLES = [
    {"sqlite_table": "ConferenceRoomsTable", "pg_table": "conference_rooms", "id_column": "id", "has_dependencies": False},
    {"sqlite_table": "ConferenceCategoriesTable", "pg_table": "conference_categories", "id_column": "id", "has_dependencies": False},
    {"sqlite_table": "ConferenceSpeakersTable", "pg_table": "conference_speakers", "id_column": "id", "has_dependencies": False},
    {"sqlite_table": "SessionTable", "pg_table": "conference_sessions", "id_column": "id", "has_dependencies": True, "depends_on": ["ConferenceRoomsTable"]},
    {"sqlite_table": "SessionSpeakersTable", "pg_table": "session_speakers", "id_column": None, "has_dependencies": True, "depends_on": ["SessionTable", "ConferenceSpeakersTable"]},
    {"sqlite_table": "SessionCategoriesTable", "pg_table": "session_categories", "id_column": None, "has_dependencies": True, "depends_on": ["SessionTable", "ConferenceCategoriesTable"]},
]

PODCAST_COLUMN_MAPPING = {
    "PodcastChannels": {"id": "id", "title": "title", "link": "link", "description": "description", "copyright": "copyright", "language": "language", "author": "author", "ownerEmail": "owner_email", "ownerName": "owner_name", "imageUrl": "image_url", "lastBuildDate": "last_build_date"},
    "PodcastEpisodes": {"id": "id", "channelId": "channel_id", "guid": "guid", "title": "title", "description": "description", "link": "link", "pubDate": "pub_date", "duration": "duration", "explicit": "explicit", "imageUrl": "image_url", "mediaUrl": "media_url", "mediaType": "media_type", "mediaLength": "media_length"},
    "PodcastChannelCategories": {"id": "id", "name": "name"},
    "PodcastEpisodeCategories": {"id": "id", "name": "name"},
    "ChannelCategoryMap": {"channelId": "channel_id", "categoryId": "category_id"},
    "EpisodeCategoryMap": {"episodeId": "episode_id", "categoryId": "category_id"}
}

SESSION_COLUMN_MAPPING = {
    "SessionTable": {"id": "id", "title": "title", "description": "description", "roomId": "room_id", "startsAt": "starts_at", "endsAt": "ends_at", "isServiceSession": "is_service_session", "isPlenumSession": "is_plenum_session", "status": "status", "isPending": None},
    "ConferenceSpeakersTable": {"id": "id", "firstName": "first_name", "lastName": "last_name", "bio": "bio", "tagLine": "tag_line", "profilePicture": "profile_picture", "isTopSpeaker": "is_top_speaker"},
    "ConferenceRoomsTable": {"id": "id", "name": "name", "sort": "sort"},
    "ConferenceCategoriesTable": {"id": "id", "title": "title", "sort": "sort", "type": "type"},
    "SessionSpeakersTable": {"sessionId": "session_id", "speakerId": "speaker_id"},
    "SessionCategoriesTable": {"sessionId": "session_id", "categoryId": "category_item_id"}
}

TIMESTAMP_COLUMNS = ["last_build_date", "pub_date", "starts_at", "ends_at"]

# SQLite schemas and mappings for export (unchanged)
SQLITE_SCHEMAS = {
    "PodcastChannels": """
        CREATE TABLE IF NOT EXISTS PodcastChannels (
            id INTEGER NOT NULL PRIMARY KEY,
            title TEXT NOT NULL,
            link TEXT NOT NULL,
            description TEXT NOT NULL,
            copyright TEXT,
            language TEXT NOT NULL,
            author TEXT NOT NULL,
            ownerEmail TEXT NOT NULL,
            ownerName TEXT NOT NULL,
            imageUrl TEXT NOT NULL,
            lastBuildDate INTEGER NOT NULL
        )
    """,
    "PodcastEpisodes": """
        CREATE TABLE IF NOT EXISTS PodcastEpisodes (
            id INTEGER NOT NULL PRIMARY KEY,
            channelId INTEGER NOT NULL,
            guid TEXT NOT NULL,
            title TEXT NOT NULL,
            description TEXT NOT NULL,
            link TEXT NOT NULL,
            pubDate INTEGER NOT NULL,
            duration INTEGER NOT NULL,
            explicit INTEGER NOT NULL,
            imageUrl TEXT,
            mediaUrl TEXT NOT NULL,
            mediaType TEXT NOT NULL,
            mediaLength INTEGER NOT NULL
        )
    """,
    "PodcastChannelCategories": """
        CREATE TABLE IF NOT EXISTS PodcastChannelCategories (
            id INTEGER NOT NULL PRIMARY KEY,
            name TEXT NOT NULL UNIQUE
        )
    """,
    "PodcastEpisodeCategories": """
        CREATE TABLE IF NOT EXISTS PodcastEpisodeCategories (
            id INTEGER NOT NULL PRIMARY KEY,
            name TEXT NOT NULL UNIQUE
        )
    """,
    "ChannelCategoryMap": """
        CREATE TABLE IF NOT EXISTS ChannelCategoryMap (
            channelId INTEGER NOT NULL,
            categoryId INTEGER NOT NULL,
            PRIMARY KEY (channelId, categoryId),
            FOREIGN KEY (channelId) REFERENCES PodcastChannels(id),
            FOREIGN KEY (categoryId) REFERENCES PodcastChannelCategories(id)
        )
    """,
    "EpisodeCategoryMap": """
        CREATE TABLE IF NOT EXISTS EpisodeCategoryMap (
            episodeId INTEGER NOT NULL,
            categoryId INTEGER NOT NULL,
            PRIMARY KEY (episodeId, categoryId),
            FOREIGN KEY (episodeId) REFERENCES PodcastEpisodes(id),
            FOREIGN KEY (categoryId) REFERENCES PodcastEpisodeCategories(id)
        )
    """
}

TABLE_MAPPING = {
    "podcast_channels": "PodcastChannels",
    "podcast_episodes": "PodcastEpisodes",
    "podcast_channel_categories": "PodcastChannelCategories",
    "podcast_episode_categories": "PodcastEpisodeCategories",
    "channel_category_map": "ChannelCategoryMap",
    "episode_category_map": "EpisodeCategoryMap"
}

EXCLUDED_COLUMNS = {
    "podcast_channels": ["created_at", "updated_at"],
    "podcast_episodes": ["created_at", "updated_at"],
    "podcast_channel_categories": ["created_at", "updated_at"],
    "podcast_episode_categories": ["created_at", "updated_at"],
    "channel_category_map": ["created_at", "updated_at"],
    "episode_category_map": ["created_at", "updated_at"]
}

COLUMN_MAPPING = {
    "podcast_channels": {"id": "id", "title": "title", "link": "link", "description": "description", "copyright": "copyright", "language": "language", "author": "author", "owner_email": "ownerEmail", "owner_name": "ownerName", "image_url": "imageUrl", "last_build_date": "lastBuildDate"},
    "podcast_episodes": {"id": "id", "channel_id": "channelId", "guid": "guid", "title": "title", "description": "description", "link": "link", "pub_date": "pubDate", "duration": "duration", "explicit": "explicit", "image_url": "imageUrl", "media_url": "mediaUrl", "media_type": "mediaType", "media_length": "mediaLength"},
    "podcast_channel_categories": {"id": "id", "name": "name"},
    "podcast_episode_categories": {"id": "id", "name": "name"},
    "channel_category_map": {"channel_id": "channelId", "category_id": "categoryId"},
    "episode_category_map": {"episode_id": "episodeId", "category_id": "categoryId"}
}

# Helper functions for import
def check_backend_availability(backend_url):
    urls_to_try = [backend_url, "http://backend:8080", "http://localhost:8080"]
    for url in urls_to_try:
        try:
            logger.info(f"Checking backend at: {url}/healthz")
            response = requests.get(f"{url}/healthz", timeout=5)
            if response.status_code == 200:
                logger.info(f"Backend at {url} responded with 200 OK")
                return True
        except requests.RequestException as e:
            logger.warning(f"Could not connect to {url}: {e}")
    return False

def check_file_exists(filepath):
    return os.path.exists(filepath)

def get_sqlite_data(sqlite_path, table_name):
    conn = sqlite3.connect(sqlite_path)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    cursor.execute(f"PRAGMA table_info({table_name})")
    columns = [row[1] for row in cursor.fetchall()]
    cursor.execute(f"SELECT * FROM {table_name}")
    rows = cursor.fetchall()
    result = [{columns[i]: row[i] for i in range(len(columns))} for row in rows]
    conn.close()
    return result

def check_sqlite_table_exists(sqlite_path, table_name):
    conn = sqlite3.connect(sqlite_path)
    cursor = conn.cursor()
    cursor.execute(f"SELECT name FROM sqlite_master WHERE type='table' AND name='{table_name}'")
    exists = cursor.fetchone() is not None
    conn.close()
    return exists

def get_postgres_column_types(pg_conn, table_name):
    cursor = pg_conn.cursor()
    cursor.execute("SELECT column_name, data_type FROM information_schema.columns WHERE table_name = %s", (table_name,))
    return {row[0]: row[1] for row in cursor.fetchall()}

def convert_timestamp_to_postgresql(value):
    if value is None:
        return None
    timestamp_in_seconds = value / 1000
    dt = datetime.fromtimestamp(timestamp_in_seconds, tz=timezone.utc)
    return dt.strftime('%Y-%m-%d %H:%M:%S')

def convert_boolean_for_postgresql(value):
    if value is None:
        return None
    return value == 1

def insert_data_to_postgres(pg_conn, table_data, table_info, column_mapping, dry_run=False):
    pg_table = table_info["pg_table"]
    sqlite_table = table_info["sqlite_table"]
    if not table_data:
        return 0
    column_types = get_postgres_column_types(pg_conn, pg_table)
    mapping = column_mapping.get(sqlite_table, {})
    mapping = {k: v for k, v in mapping.items() if v is not None}
    valid_sqlite_columns = [col for col in mapping.keys() if col in table_data[0]]
    valid_pg_columns = [mapping[col] for col in valid_sqlite_columns]
    columns_str = ', '.join([f'"{col}"' for col in valid_pg_columns])
    values = []
    for row in table_data:
        row_values = []
        for col in valid_sqlite_columns:
            pg_col = mapping[col]
            value = row.get(col)
            if pg_col in TIMESTAMP_COLUMNS and value is not None:
                value = convert_timestamp_to_postgresql(value)
            elif column_types.get(pg_col) == 'boolean' and value is not None:
                value = convert_boolean_for_postgresql(value)
            row_values.append(value)
        values.append(tuple(row_values))
    if dry_run:
        return len(values)
    cursor = pg_conn.cursor()
    insert_query = f'INSERT INTO {pg_table} ({columns_str}) VALUES %s ON CONFLICT DO NOTHING'
    execute_values(cursor, insert_query, values)
    pg_conn.commit()
    logger.info(f"Inserted {len(values)} rows into {pg_table}")
    return len(values)

def import_data_to_postgres(sqlite_path, pg_conn, table_mappings, column_mappings):
    total_rows = 0
    existing_tables = []
    for table_info in table_mappings:
        sqlite_table = table_info["sqlite_table"]
        if check_sqlite_table_exists(sqlite_path, sqlite_table):
            table_data = get_sqlite_data(sqlite_path, sqlite_table)
            total_rows += len(table_data)
            existing_tables.append(table_info)
    if not existing_tables:
        return 0
    imported_tables = set()
    rows_imported = 0
    for table_info in existing_tables:
        if not table_info["has_dependencies"]:
            sqlite_table = table_info["sqlite_table"]
            table_data = get_sqlite_data(sqlite_path, sqlite_table)
            imported_rows = insert_data_to_postgres(pg_conn, table_data, table_info, column_mappings)
            rows_imported += imported_rows
            imported_tables.add(sqlite_table)
    remaining_tables = [t for t in existing_tables if t["has_dependencies"]]
    progress_made = True
    while remaining_tables and progress_made:
        progress_made = False
        tables_to_remove = []
        for table_info in remaining_tables:
            dependencies = table_info.get("depends_on", [])
            if all(dep in imported_tables for dep in dependencies):
                sqlite_table = table_info["sqlite_table"]
                table_data = get_sqlite_data(sqlite_path, sqlite_table)
                imported_rows = insert_data_to_postgres(pg_conn, table_data, table_info, column_mappings)
                rows_imported += imported_rows
                imported_tables.add(sqlite_table)
                tables_to_remove.append(table_info)
                progress_made = True
        for table_info in tables_to_remove:
            remaining_tables.remove(table_info)
    return rows_imported

def perform_import(pg_conn, podcast_db_path, session_db_path):
    podcast_exists = check_file_exists(podcast_db_path)
    session_exists = check_file_exists(session_db_path)
    if not podcast_exists and not session_exists:
        logger.error("Neither podcast nor session SQLite files found")
        return False
    if podcast_exists:
        logger.info(f"Importing podcast data from {podcast_db_path}")
        import_data_to_postgres(podcast_db_path, pg_conn, PODCAST_TABLES, PODCAST_COLUMN_MAPPING)
    if session_exists:
        logger.info(f"Importing session data from {session_db_path}")
        import_data_to_postgres(session_db_path, pg_conn, SESSION_TABLES, SESSION_COLUMN_MAPPING)
    return True

def check_if_tables_populated(pg_conn):
    all_tables = [t["pg_table"] for t in PODCAST_TABLES] + [t["pg_table"] for t in SESSION_TABLES]
    for table in all_tables:
        cursor = pg_conn.cursor()
        cursor.execute(f"SELECT COUNT(*) FROM {table}")
        count = cursor.fetchone()[0]
        cursor.close()
        if count == 0:
            return False
    return True

# Helper functions for export
def setup_sqlite_tables(conn: sqlite3.Connection):
    cursor = conn.cursor()
    for schema in SQLITE_SCHEMAS.values():
        cursor.execute(schema)
    conn.commit()
    cursor.close()

def get_postgres_tables(pg_conn, schema):
    cursor = pg_conn.cursor()
    query = "SELECT table_name FROM information_schema.tables WHERE table_schema = %s AND table_type = 'BASE TABLE' AND table_name IN %s"
    cursor.execute(query, (schema, tuple(TABLE_MAPPING.keys())))
    tables = [row[0] for row in cursor.fetchall()]
    cursor.close()
    return tables

def get_table_columns(pg_conn, schema, table):
    cursor = pg_conn.cursor()
    query = "SELECT column_name FROM information_schema.columns WHERE table_schema = %s AND table_name = %s ORDER BY ordinal_position"
    cursor.execute(query, (schema, table))
    columns = [row[0] for row in cursor.fetchall()]
    cursor.close()
    return columns

def get_sqlite_table_columns(conn, table):
    cursor = conn.cursor()
    cursor.execute(f"PRAGMA table_info({table})")
    columns = {row[1] for row in cursor.fetchall()}
    cursor.close()
    return columns

def timestamp_to_epoch(dt):
    if dt is None:
        return None
    if isinstance(dt, str):
        dt = datetime.fromisoformat(dt.replace('Z', '+00:00'))
    return int(dt.timestamp() * 1000)

def export_table_data(pg_conn, sqlite_conn, schema, pg_table, batch_size=1000):
    sqlite_table = TABLE_MAPPING.get(pg_table)
    if not sqlite_table:
        return 0
    sqlite_columns = get_sqlite_table_columns(sqlite_conn, sqlite_table)
    pg_columns = get_table_columns(pg_conn, schema, pg_table)
    excluded = set(EXCLUDED_COLUMNS.get(pg_table, []))
    column_map = {pg_col: COLUMN_MAPPING.get(pg_table, {}).get(pg_col, pg_col) for pg_col in pg_columns if pg_col not in excluded and COLUMN_MAPPING.get(pg_table, {}).get(pg_col, pg_col) in sqlite_columns}
    if not column_map:
        return 0
    sqlite_cols = list(column_map.values())
    placeholders = ",".join(["?" for _ in sqlite_cols])
    sqlite_insert = f"INSERT OR REPLACE INTO {sqlite_table} ({','.join(sqlite_cols)}) VALUES ({placeholders})"
    pg_cols = list(column_map.keys())
    pg_cols_str = ", ".join([f'"{col}"' for col in pg_cols])
    pg_cursor = pg_conn.cursor(cursor_factory=RealDictCursor)
    pg_query = f"SELECT {pg_cols_str} FROM {pg_table}"
    total_rows = 0
    pg_cursor.execute(pg_query)
    sqlite_cursor = sqlite_conn.cursor()
    batch = []
    for row in pg_cursor:
        sqlite_row = [
            timestamp_to_epoch(row[pg_col]) if "date" in pg_col.lower() and row[pg_col] is not None
            else (1 if row[pg_col] else 0) if isinstance(row[pg_col], bool)
            else row[pg_col]
            for pg_col in column_map.keys()
        ]
        batch.append(sqlite_row)
        if len(batch) >= batch_size:
            sqlite_cursor.executemany(sqlite_insert, batch)
            total_rows += len(batch)
            batch = []
    if batch:
        sqlite_cursor.executemany(sqlite_insert, batch)
        total_rows += len(batch)
    sqlite_conn.commit()
    return total_rows

def export_postgres_to_sqlite():
    logger.info("Starting PostgreSQL to SQLite export")
    temp_db_path = os.path.join(STORAGE_DIR, f"{PODCAST_DB_FILE}.tmp")
    final_db_path = os.path.join(STORAGE_DIR, PODCAST_DB_FILE)
    backup_path = os.path.join(BACKUP_DIR, PODCAST_DB_FILE)  # Fixed name, no timestamp
    try:
        pg_conn = psycopg2.connect(host=PG_HOST, port=PG_PORT, dbname=PG_DBNAME, user=PG_USER, password=PG_PASSWORD)
        sqlite_conn = sqlite3.connect(temp_db_path)
        sqlite_conn.row_factory = sqlite3.Row
        setup_sqlite_tables(sqlite_conn)
        pg_tables = get_postgres_tables(pg_conn, "public")
        for pg_table in pg_tables:
            export_table_data(pg_conn, sqlite_conn, "public", pg_table)
        sqlite_conn.execute("VACUUM")
        sqlite_conn.execute("ANALYZE")
        sqlite_conn.close()
        pg_conn.close()
        if os.path.exists(final_db_path):
            os.replace(final_db_path, backup_path)  # Overwrite existing backup
            logger.info(f"Backed up existing file to {backup_path}")
        os.rename(temp_db_path, final_db_path)
        logger.info("Export completed successfully")
    except Exception as e:
        logger.error(f"Export failed: {e}")
        if os.path.exists(temp_db_path):
            os.remove(temp_db_path)
        raise

# Scheduler setup
scheduler = BackgroundScheduler()
scheduler.add_job(export_postgres_to_sqlite, "interval", hours=24)

# File streaming
async def file_streamer(filepath: str, start_byte: int = 0, end_byte: Optional[int] = None):
    async with aiofiles.open(filepath, "rb") as f:
        if start_byte:
            await f.seek(start_byte)
        remaining = end_byte - start_byte + 1 if end_byte else None
        while True:
            chunk_size = min(CHUNK_SIZE, remaining) if remaining else CHUNK_SIZE
            chunk = await f.read(chunk_size)
            if not chunk:
                break
            yield chunk
            if remaining:
                remaining -= len(chunk)
                if remaining <= 0:
                    break

# Endpoints
@app.get("/")
async def root():
    return {"message": "KotlinApp Combined Server is running"}

@app.get("/download_latest_file")
async def download_file(request: Request):
    filepath = os.path.join(BACKUP_DIR, PODCAST_DB_FILE)
    if not os.path.exists(filepath):
        raise HTTPException(status_code=404, detail="Database file not found")
    file_size = os.path.getsize(filepath)
    headers = {}
    start_byte = 0
    end_byte = file_size - 1
    range_header = request.headers.get("Range")
    if range_header:
        try:
            range_spec = range_header.replace("bytes=", "").split("-")
            start_byte = int(range_spec[0]) if range_spec[0] else 0
            end_byte = int(range_spec[1]) if len(range_spec) > 1 and range_spec[1] else file_size - 1
            if start_byte >= file_size:
                raise HTTPException(status_code=416, detail="Range not satisfiable")
            if end_byte >= file_size:
                end_byte = file_size - 1
            headers["Content-Range"] = f"bytes {start_byte}-{end_byte}/{file_size}"
            status_code = 206
        except ValueError:
            status_code = 200
    else:
        status_code = 200
    headers["Content-Length"] = str(end_byte - start_byte + 1)
    headers["Accept-Ranges"] = "bytes"
    headers["Content-Disposition"] = f"attachment; filename={PODCAST_DB_FILE}"
    return StreamingResponse(
        file_streamer(filepath, start_byte, end_byte),
        media_type="application/octet-stream",
        headers=headers,
        status_code=status_code,
    )

@app.head("/download_latest_file")
async def head_download_file(request: Request):
    filepath = os.path.join(BACKUP_DIR, PODCAST_DB_FILE)  # Changed to BACKUP_DIR for consistency
    if not os.path.exists(filepath):
        raise HTTPException(status_code=404, detail="Database file not found")
    file_size = os.path.getsize(filepath)
    headers = {"Content-Length": str(file_size), "Accept-Ranges": "bytes"}
    return Response(content=b"", status_code=200, headers=headers)

@app.get("/database/latest")
async def get_latest_database():
    db_path = os.path.join(BACKUP_DIR, PODCAST_DB_FILE)  # Changed to BACKUP_DIR for consistency
    if not os.path.exists(db_path):
        raise HTTPException(status_code=404, detail="Database file not found")
    file_size = os.path.getsize(db_path)
    return VersionInfo(
        version="1.0.0",
        required=True,
        url=f"/download_latest_file",
        size=file_size,
        hash=None,
        description="Latest podcast database export"
    )

# Startup and shutdown events
@app.on_event("startup")
async def startup_event():
    logger.info("Starting up...")
    podcast_db_path = os.path.join(STORAGE_DIR, PODCAST_DB_FILE)
    session_db_path = os.path.join(STORAGE_DIR, SESSION_DB_FILE)
    while True:
        if check_backend_availability(BACKEND_URL):
            try:
                pg_conn = psycopg2.connect(host=PG_HOST, port=PG_PORT, dbname=PG_DBNAME, user=PG_USER, password=PG_PASSWORD)
                if not check_if_tables_populated(pg_conn):
                    logger.info("Tables are not populated, performing import")
                    perform_import(pg_conn, podcast_db_path, session_db_path)
                else:
                    logger.info("Tables are already populated, skipping import")
                export_postgres_to_sqlite()
                scheduler.start()
                pg_conn.close()
                break
            except psycopg2.Error as e:
                logger.error(f"PostgreSQL connection failed: {e}")
                time.sleep(POLL_INTERVAL)
        else:
            logger.info(f"Waiting for backend, retrying in {POLL_INTERVAL} seconds")
            time.sleep(POLL_INTERVAL)

@app.on_event("shutdown")
def shutdown_event():
    scheduler.shutdown()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)