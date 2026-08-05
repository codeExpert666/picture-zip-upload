-- Finalize the content hash constraint after the historical backfill and conflict cleanup are complete.
-- Replace medical_corpus_analysis_picture with the target business-area table.

ALTER TABLE medical_corpus_analysis_picture
    DROP INDEX idx_picture_content_sha256,
    ADD UNIQUE KEY uk_picture_sha256 (content_sha256);
