CREATE TABLE IF NOT EXISTS processing_settings (
    id BIGINT PRIMARY KEY,
    max_video_minutes INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS app_state (
    state_key VARCHAR(128) PRIMARY KEY,
    state_value LONGTEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS folders (
    folder_id BIGINT PRIMARY KEY,
    uid BIGINT NOT NULL,
    title VARCHAR(512) NOT NULL,
    media_count INT NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_folders_uid (uid)
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
    is_invalid INT NOT NULL DEFAULT 0,
    INDEX idx_videos_folder_id (folder_id)
);

CREATE TABLE IF NOT EXISTS transcripts (
    bvid VARCHAR(32) PRIMARY KEY,
    source_model VARCHAR(128),
    segment_count INT NOT NULL DEFAULT 0,
    transcript_text LONGTEXT,
    segments_json LONGTEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS video_summaries (
    bvid VARCHAR(32) PRIMARY KEY,
    transcript_hash VARCHAR(128),
    summary_text LONGTEXT,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS video_pipeline (
    bvid VARCHAR(32) PRIMARY KEY,
    overall_status VARCHAR(32) NOT NULL,
    state_json LONGTEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS ingestion_tasks (
    task_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bvid VARCHAR(32) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_msg TEXT,
    heartbeat_at TIMESTAMP NULL,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_ingestion_tasks_bvid_status (bvid, status),
    INDEX idx_ingestion_tasks_status_created_at (status, created_at)
);

CREATE TABLE IF NOT EXISTS chat_conversations (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    conversation_type VARCHAR(32) NOT NULL,
    folder_id BIGINT NULL,
    video_bvid VARCHAR(32) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_chat_conversations_updated_at (updated_at)
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    sources_json LONGTEXT,
    citation_segments_json LONGTEXT,
    answer_mode VARCHAR(32),
    route_mode VARCHAR(32),
    reasoning_text LONGTEXT,
    agent_status VARCHAR(255),
    skill_events_json LONGTEXT,
    tool_events_json LONGTEXT,
    active_skills_json LONGTEXT,
    approval_json LONGTEXT,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_chat_messages_conversation_id_created_at (conversation_id, created_at)
);


CREATE TABLE IF NOT EXISTS chat_conversation_memory (
    conversation_id VARCHAR(64) PRIMARY KEY,
    memory_text LONGTEXT,
    source_message_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_conversation_context_stats (
    conversation_id VARCHAR(64) PRIMARY KEY,
    total_messages INT NOT NULL DEFAULT 0,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS tool_workspaces (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    workspace_key VARCHAR(128) NOT NULL,
    workspace_path VARCHAR(512) NOT NULL,
    description VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_tool_workspaces_workspace_key (workspace_key)
);

CREATE TABLE IF NOT EXISTS tool_calls (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workspace_id BIGINT NULL,
    tool_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_json LONGTEXT,
    response_json LONGTEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_tool_calls_workspace_id_created_at (workspace_id, created_at)
);

CREATE TABLE IF NOT EXISTS skill_activations (
    skill_name VARCHAR(128) PRIMARY KEY,
    is_active TINYINT(1) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
