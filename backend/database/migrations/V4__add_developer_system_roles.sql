-- Update users table CHECK constraint for system_role to allow developer and manager
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_system_role_check;
ALTER TABLE users ADD CONSTRAINT users_system_role_check CHECK (system_role IN ('admin', 'member', 'developer', 'manager'));
