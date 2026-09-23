-- Adds cross-company membership without touching any existing table, column,
-- or row. users.company_id / users.system_role stay exactly as they are today
-- (a user's "home" company) and every current read/write against them keeps
-- working unchanged.
--
-- company_members is the new source of truth for "which companies can this
-- user act in, and with what role there": a non-admin user may hold any
-- number of rows (many companies, many projects via project_members), while
-- an admin may hold at most one row with role = 'admin' (enforced below).
-- An admin elsewhere can still appear here with role = 'member' for a
-- different company.

CREATE TABLE company_members (
    membership_id  SERIAL PRIMARY KEY,
    user_id        INTEGER NOT NULL REFERENCES users(user_id),
    company_id     INTEGER NOT NULL REFERENCES companies(company_id),
    role           VARCHAR(50) NOT NULL CHECK (role IN ('admin', 'member')),
    joined_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, company_id)
);

CREATE INDEX idx_company_members_user_id ON company_members(user_id);
CREATE INDEX idx_company_members_company_id ON company_members(company_id);

-- At most one 'admin' row per user, company-wide. Unlimited 'member' rows are
-- unaffected, which is what lets a company admin also be a plain member of
-- other companies.
CREATE UNIQUE INDEX uq_company_members_one_admin
    ON company_members(user_id)
    WHERE role = 'admin';

-- Backfill: every existing user with a company today gets the equivalent
-- company_members row, so the new table starts in perfect agreement with the
-- old single-company model. No row in users or any other table is modified.
INSERT INTO company_members (user_id, company_id, role, joined_at)
SELECT user_id, company_id, system_role, created_at
FROM users
WHERE company_id IS NOT NULL
  AND system_role IN ('admin', 'member');
