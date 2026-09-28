#!/bin/sh
# Runs ONCE, the first time the Postgres container starts with an empty data volume.
# Creates one database and one owner account per service. It only creates empty databases
# and accounts - all tables are created later by each service's Flyway migrations.
set -e

create_service_db() {
  db_name="$1"; db_user="$2"; db_password="$3"
  echo "Creating database '$db_name' owned by '$db_user'"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
       -v db_name="$db_name" -v db_user="$db_user" -v db_password="$db_password" <<'SQL'
CREATE ROLE :"db_user" WITH LOGIN PASSWORD :'db_password';
CREATE DATABASE :"db_name" OWNER :"db_user";
-- Only the owning service may connect: services must not read each other's data
REVOKE CONNECT ON DATABASE :"db_name" FROM PUBLIC;
SQL
}

create_service_db "$AUTH_DB_NAME"         "$AUTH_DB_USER"         "$AUTH_DB_PASSWORD"
create_service_db "$PROJECT_DB_NAME"      "$PROJECT_DB_USER"      "$PROJECT_DB_PASSWORD"
create_service_db "$TASK_DB_NAME"         "$TASK_DB_USER"         "$TASK_DB_PASSWORD"
