ALTER TABLE pull_requests
ADD COLUMN author_association VARCHAR(30);

COMMENT ON COLUMN pull_requests.author_association IS
    'GitHub author association at PR creation time';