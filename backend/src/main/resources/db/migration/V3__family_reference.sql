-- Family reference (e.g. father / mother / spouse) in addition to the professional reference.
ALTER TABLE candidates ADD COLUMN family_ref_name VARCHAR(150);
ALTER TABLE candidates ADD COLUMN family_ref_relation VARCHAR(100);
ALTER TABLE candidates ADD COLUMN family_ref_contact VARCHAR(200);
