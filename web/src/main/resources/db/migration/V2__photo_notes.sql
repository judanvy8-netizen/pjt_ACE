-- WEB7: 사진 단위·버전별 비고, 입력 ID에 종속(검수 여부와 독립)
CREATE TABLE photo_note (
    input_id BIGINT NOT NULL,
    scope VARCHAR(16) NOT NULL,
    note_text VARCHAR(1000) NOT NULL DEFAULT '',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY(input_id, scope),
    CONSTRAINT fk_photo_note_media FOREIGN KEY(input_id) REFERENCES input_media(id) ON DELETE CASCADE
);
