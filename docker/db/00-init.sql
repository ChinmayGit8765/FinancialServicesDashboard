-- Runs ONCE on first container start as the postgres superuser
-- via /docker-entrypoint-initdb.d. The quantlens app user is NOT a superuser
-- and cannot CREATE EXTENSION, so this script must run before Flyway starts.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
