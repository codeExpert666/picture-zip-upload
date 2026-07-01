CREATE TABLE IF NOT EXISTS corpus_analysis_picture (
    voice_code varchar(36) NOT NULL,
    filename varchar(100) DEFAULT NULL,
    extname varchar(10) DEFAULT NULL,
    file_URL varchar(255) DEFAULT NULL,
    import_time datetime DEFAULT NULL,
    update_time datetime DEFAULT NULL,
    file_path varchar(255) DEFAULT NULL,
    status varchar(10) DEFAULT NULL,
    content_sha256 char(64) NOT NULL,
    file_size bigint NOT NULL DEFAULT 0,
    upload_id varchar(36) DEFAULT NULL,
    original_zip_name varchar(255) DEFAULT NULL,
    PRIMARY KEY (voice_code),
    UNIQUE KEY uk_picture_sha256 (content_sha256),
    KEY idx_picture_upload_id (upload_id),
    KEY idx_picture_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
