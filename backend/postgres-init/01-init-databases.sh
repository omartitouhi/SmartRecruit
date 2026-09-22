#!/usr/bin/env bash
set -e

for DB in auth_db user_db job_db application_db dashboard_db ai_db; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE $DB;
EOSQL
done

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname ai_db <<-EOSQL
  CREATE EXTENSION IF NOT EXISTS vector;
EOSQL
