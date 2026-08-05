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

-- Add a non-unique lookup index before backfill. The backfill queries content_sha256 once per valid image.
-- If the columns above already exist in production, run only this ALTER TABLE statement at this stage.
ALTER TABLE medical_corpus_analysis_picture
    ADD KEY idx_picture_content_sha256 (content_sha256);

-- Stop here. Run scripts/backfill-existing-picture-records.sh and resolve all hash conflicts first.
-- Only after conflict cleanup, execute db/picture-maintenance-finalize-index.sql.
