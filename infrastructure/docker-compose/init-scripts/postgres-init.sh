#!/bin/bash
# Creates multiple PostgreSQL databases for the LMS platform.
# Runs automatically inside the postgres container on first start.

set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE lms_auth;
    CREATE DATABASE lms_users;
    CREATE DATABASE lms_progress;
    CREATE DATABASE lms_payments;

    GRANT ALL PRIVILEGES ON DATABASE lms_auth TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON DATABASE lms_users TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON DATABASE lms_progress TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON DATABASE lms_payments TO $POSTGRES_USER;
EOSQL
echo "PostgreSQL databases created successfully"

