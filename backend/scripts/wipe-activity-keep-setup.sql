-- ============================================================================
-- BeOffline — wipe activity, keep the setup
--
-- The lighter sibling of wipe-test-data.sql, for the common case mid-testing:
-- you want a clean slate for requests/chat/logs but you do NOT want to re-pair
-- two accounts by invite code and re-register devices every single run.
--
-- KEEPS:   Users, Devices, Pairings, Groups, GroupMembers
-- CLEARS:  unlock requests + approvals, allowances, chat, tamper events,
--          solo-unlock ladders, unconsumed invite codes, API logs
--
-- Plain SQL only — runs as-is in pgAdmin, DBeaver, DataGrip, or psql.
-- ============================================================================

BEGIN;

DO $$
DECLARE
  db      text := current_database();
  -- Named explicitly, because this script is deliberately selective — unlike
  -- the full wipe it must NOT pick up new tables automatically.
  wanted  text[] := ARRAY[
    'Allowances', 'RequestApprovals', 'UnlockRequests', 'ChatMessages',
    'TamperEvents', 'SoloUnlocks', 'InviteCodes', 'GroupInviteCodes',
    'ActivityLogs', 'ErrorLogs'
  ];
  stmt    text;
BEGIN
  IF db ~* '(prod|production|live)' THEN
    RAISE EXCEPTION 'Database "%" looks like production. Refusing to wipe.', db;
  END IF;

  -- Skip anything the database doesn't have yet: a database that predates a
  -- migration is a normal state to be in, and shouldn't make the wipe fail.
  SELECT string_agg(format('public.%I', t), ', ' ORDER BY t)
    INTO stmt
    FROM unnest(wanted) AS t
   WHERE to_regclass(format('public.%I', t)) IS NOT NULL;

  IF stmt IS NULL THEN
    RAISE NOTICE 'None of the expected tables exist in "%" — nothing to clear.', db;
    RETURN;
  END IF;

  RAISE NOTICE 'Clearing: %', stmt;
  EXECUTE 'TRUNCATE TABLE ' || stmt || ' RESTART IDENTITY CASCADE';
END $$;

-- Clearing SoloUnlocks above resets the server's half of the escalation ladder.
-- The DEVICE keeps its own copy in Room and the client takes the higher of the
-- two, so the next challenge stays at the level the phone remembers. To test
-- from level 0, clear the app's storage too (or use a rule you haven't unlocked
-- in this focus session).

-- Let everyone approve again straight away: without this, members still sitting
-- in their anti-puppet activation window would keep that timestamp and you'd be
-- waiting on a cooldown you were trying to reset.
UPDATE public."GroupMembers" SET "CanApproveAfterUtc" = now() AT TIME ZONE 'utc';
UPDATE public."Pairings"     SET "CanApproveAfterUtc" = now() AT TIME ZONE 'utc';

-- Cancel any removal cooldowns left mid-flight from the last run.
UPDATE public."GroupMembers"
SET "RemovalRequestedByUid" = NULL, "RemovalRequestedAtUtc" = NULL, "RemovalEffectiveAtUtc" = NULL
WHERE "RemovalEffectiveAtUtc" IS NOT NULL;

UPDATE public."Pairings"
SET "RemovalRequestedByUid" = NULL, "RemovalRequestedAtUtc" = NULL, "RemovalEffectiveAtUtc" = NULL
WHERE "RemovalEffectiveAtUtc" IS NOT NULL;

COMMIT;

-- ── Proof — run after the COMMIT ────────────────────────────────────────────
-- The first four should be 0; the rest are what you deliberately kept.
-- Cleared tables should read 0; Users / Devices / Pairings / Groups /
-- GroupMembers are what you deliberately kept.
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
