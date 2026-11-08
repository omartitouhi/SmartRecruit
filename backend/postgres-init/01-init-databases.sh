#!/usr/bin/env bash
set -e

for DB in auth_db user_db job_db application_db dashboard_db ai_db; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
    CREATE DATABASE $DB;
EOSQL
done
