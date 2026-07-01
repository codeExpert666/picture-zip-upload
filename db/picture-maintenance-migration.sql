-- Existing table migration for direct picture directory maintenance.
-- Replace medical_corpus_analysis_picture with the target business-area table.
-- Keep new metadata columns nullable for the first deployment so historical rows can be backfilled gradually.

ALTER TABLE medical_corpus_analysis_picture
    MODIFY file_URL varchar(1024) DEFAULT NULL,
    MODIFY file_path varchar(1024) DEFAULT NULL,
    ADD COLUMN content_sha256 char(64) DEFAULT NULL,
    ADD COLUMN file_size bigint DEFAULT NULL,
    ADD COLUMN upload_id varchar(36) DEFAULT NULL,
    ADD COLUMN original_zip_name varchar(255) DEFAULT NULL,
    ADD COLUMN operator varchar(50) DEFAULT NULL;

-- Run scripts/backfill-existing-picture-records.sh and resolve conflicts before adding this index.
ALTER TABLE medical_corpus_analysis_picture
    ADD UNIQUE KEY uk_picture_sha256 (content_sha256);
