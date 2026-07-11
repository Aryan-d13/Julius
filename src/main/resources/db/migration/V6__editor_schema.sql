-- =============================================================================
-- V6__editor_schema.sql
-- Julius — Interactive Editing & Subtitle Schema
-- =============================================================================

CREATE TABLE edit_sessions (
    id                  VARCHAR(36)     NOT NULL,
    clip_id             VARCHAR(36)     NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    updated_at          TIMESTAMP       NOT NULL,
    deleted_at          TIMESTAMP,
    CONSTRAINT pk_edit_sessions PRIMARY KEY (id)
);

CREATE TABLE subtitle_styles (
    id                  VARCHAR(36)     NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    font_name           VARCHAR(100)    NOT NULL,
    font_size           INTEGER         NOT NULL,
    primary_color       VARCHAR(10)     NOT NULL,
    secondary_color     VARCHAR(10)     NOT NULL,
    outline_color       VARCHAR(10)     NOT NULL,
    shadow_color        VARCHAR(10)     NOT NULL,
    outline_width       DOUBLE PRECISION NOT NULL,
    shadow_depth        DOUBLE PRECISION NOT NULL,
    alignment           INTEGER         NOT NULL,
    safe_zone_vertical  INTEGER         NOT NULL,
    CONSTRAINT pk_subtitle_styles PRIMARY KEY (id)
);

CREATE TABLE render_profiles (
    id                  VARCHAR(36)     NOT NULL,
    name                VARCHAR(100)    NOT NULL,
    width               INTEGER         NOT NULL,
    height              INTEGER         NOT NULL,
    fps                 INTEGER         NOT NULL,
    video_bitrate_kbps  INTEGER         NOT NULL,
    audio_bitrate_kbps  INTEGER         NOT NULL,
    crop_strategy       VARCHAR(50)     NOT NULL,
    watermark_key       VARCHAR(255),
    CONSTRAINT pk_render_profiles PRIMARY KEY (id)
);

CREATE TABLE clip_versions (
    id                  VARCHAR(36)     NOT NULL,
    session_id          VARCHAR(36)     NOT NULL,
    version_number      INTEGER         NOT NULL,
    name                VARCHAR(255),
    timeline_state_json TEXT            NOT NULL,
    style_preset_id     VARCHAR(36)     NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_clip_versions PRIMARY KEY (id),
    CONSTRAINT fk_clip_versions_sessions FOREIGN KEY (session_id) REFERENCES edit_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_clip_versions_styles FOREIGN KEY (style_preset_id) REFERENCES subtitle_styles(id)
);

CREATE TABLE render_artifacts (
    id                  VARCHAR(36)     NOT NULL,
    version_id          VARCHAR(36)     NOT NULL,
    profile_id          VARCHAR(36)     NOT NULL,
    status              VARCHAR(50)     NOT NULL,
    render_hash         VARCHAR(64)     NOT NULL,
    storage_key         VARCHAR(255),
    url                 VARCHAR(255),
    size_bytes          BIGINT,
    duration_seconds    DOUBLE PRECISION,
    error_message       TEXT,
    created_at          TIMESTAMP       NOT NULL,
    completed_at        TIMESTAMP,
    CONSTRAINT pk_render_artifacts PRIMARY KEY (id),
    CONSTRAINT fk_render_artifacts_versions FOREIGN KEY (version_id) REFERENCES clip_versions(id) ON DELETE CASCADE,
    CONSTRAINT fk_render_artifacts_profiles FOREIGN KEY (profile_id) REFERENCES render_profiles(id)
);

CREATE INDEX idx_edit_sessions_clip_id ON edit_sessions(clip_id);
CREATE INDEX idx_clip_versions_session_id ON clip_versions(session_id);
CREATE INDEX idx_render_artifacts_version_id ON render_artifacts(version_id);
CREATE INDEX idx_render_artifacts_hash ON render_artifacts(render_hash);
