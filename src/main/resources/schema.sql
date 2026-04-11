CREATE TABLE IF NOT EXISTS processing_settings (
    id BIGINT PRIMARY KEY,
    max_video_minutes INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS folders (
    folder_id BIGINT PRIMARY KEY,
    uid BIGINT NOT NULL,
    title VARCHAR(512) NOT NULL,
    media_count INT NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS videos (
    bvid VARCHAR(32) PRIMARY KEY,
    folder_id BIGINT NOT NULL,
    title VARCHAR(512) NOT NULL,
    up_name VARCHAR(255),
    cover_url VARCHAR(1024),
    duration INT NOT NULL,
    published_at TIMESTAMP NULL,
    cid BIGINT NULL,
    subtitle_source VARCHAR(64),
    manual_tags VARCHAR(512),
    synced_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    audio_storage_provider VARCHAR(32),
    audio_object_key VARCHAR(1024),
    audio_uploaded_at TIMESTAMP NULL,
    is_invalid INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_folders_uid ON folders (uid);
CREATE INDEX IF NOT EXISTS idx_videos_folder_id ON videos (folder_id);
