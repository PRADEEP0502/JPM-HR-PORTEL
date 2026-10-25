-- Adds at-rest encryption flag for uploaded documents (Aadhaar copies are always encrypted).
ALTER TABLE candidate_documents ADD COLUMN encrypted BOOLEAN NOT NULL DEFAULT FALSE;
