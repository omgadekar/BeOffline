-- ============================================================================
-- BeOffline — wipe test data
--
-- Empties every application table and leaves the SCHEMA intact. EF Core's
-- __EFMigrationsHistory is deliberately untouched: dropping it would make the
-- API think the database is unmigrated and try to apply every migration again
-- on top of tables that already exist.
--
-- The table list is discovered from the database rather than hard-coded, so
-- this keeps working when the model gains a table (or when you run it against
-- a database that hasn't caught up to the latest migration yet).
--
-- Plain SQL only — no psql backslash commands — so this runs as-is in pgAdmin,
-- DBeaver, DataGrip, or psql. Select all and execute.
--
-- This is for development and staging. Do not point it at production.
-- ============================================================================

BEGIN;

DO $$
DECLARE
  db    text := current_database();
  stmt  text;
BEGIN
  -- Guard, inside the transaction on purpose: if it raises, the truncate below
  -- never runs and nothing is lost. Rename your dev database if this fires
  -- wrongly; don't delete the guard.
  IF db ~* '(prod|production|live)' THEN
    RAISE EXCEPTION 'Database "%" looks like production. Refusing to wipe.', db;
  END IF;

  SELECT string_agg(format('%I.%I', schemaname, tablename), ', ' ORDER BY tablename)
    INTO stmt
    FROM pg_tables
   WHERE schemaname = 'public'
     AND tablename <> '__EFMigrationsHistory';

  IF stmt IS NULL THEN
    RAISE NOTICE 'No application tables in "%" — nothing to wipe.', db;
    RETURN;
  END IF;

  RAISE NOTICE 'Wiping "%" as "%"', db, current_user;
  RAISE NOTICE 'Tables: %', stmt;

  -- One TRUNCATE for all of them. Truncating together lets Postgres satisfy the
  -- foreign keys between them (GroupMembers→Groups, UnlockRequests→Pairings/
  -- Groups) in a single pass, so there is no delete ordering to get wrong.
  -- RESTART IDENTITY resets the bigint sequences on the log tables so ids are
  -- comparable between test runs.
  EXECUTE 'TRUNCATE TABLE ' || stmt || ' RESTART IDENTITY CASCADE';
END $$;

COMMIT;

-- ── Proof — run this after the COMMIT ───────────────────────────────────────
-- Exact counts for every table, discovered the same way. All should be 0.
--
-- Counted rather than read from pg_stat_user_tables, whose numbers come from
-- the autovacuum daemon and are routinely stale straight after a truncate —
-- they would happily report your old row counts and look like a failed wipe.
SELECT c.relname AS table_name,
       (xpath(
          '/row/c/text()',
          query_to_xml(format('SELECT count(*) AS c FROM public.%I', c.relname),
                       false, true, '')
       ))[1]::text::bigint AS row_count
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind = 'r'
  AND c.relname <> '__EFMigrationsHistory'
ORDER BY c.relname;

-- The schema is intact. The API recreates the user row on the next
-- authenticated call, and the app re-registers its device on the next sign-in
-- or heartbeat. Firebase accounts are NOT touched by this script.
