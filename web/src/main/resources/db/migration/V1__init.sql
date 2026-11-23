CREATE TABLE dataset (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE TABLE analysis_round (
    id CHAR(36) PRIMARY KEY,
    dataset_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);
CREATE TABLE input_media (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id CHAR(36) NOT NULL UNIQUE,
    dataset_id CHAR(36),
    kind VARCHAR(12) NOT NULL DEFAULT 'photo',
    filename VARCHAR(255) NOT NULL,
    source_path VARCHAR(512) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);
CREATE TABLE analysis_run (
    run_id CHAR(36) PRIMARY KEY,
    input_id BIGINT NOT NULL,
    round_id CHAR(36),
    variant VARCHAR(12) NOT NULL DEFAULT 'main-2',
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(500),
    cat_count INT,
    dog_count INT,
    result_path VARCHAR(512),
    engine VARCHAR(64),
    elapsed_ms BIGINT,
    image_width INT,
    image_height INT,
    metadata_json JSON,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_round_photo_variant (round_id, input_id, variant),
    FOREIGN KEY (round_id) REFERENCES analysis_round(id),
    FOREIGN KEY (input_id) REFERENCES input_media(id)
);
CREATE TABLE detected_pet (
    run_id CHAR(36) NOT NULL,
    object_no INT NOT NULL,
    species VARCHAR(8) NOT NULL,
    x1 DOUBLE NOT NULL,
    y1 DOUBLE NOT NULL,
    x2 DOUBLE NOT NULL,
    y2 DOUBLE NOT NULL,
    det_score DOUBLE NOT NULL,
    breed VARCHAR(200) NOT NULL,
    breed_score DOUBLE,
    class_status VARCHAR(16) NOT NULL,
    PRIMARY KEY (run_id, object_no),
    FOREIGN KEY (run_id) REFERENCES analysis_run(run_id)
);
CREATE TABLE ground_truth (
    input_id BIGINT NOT NULL,
    version INT NOT NULL,
    cat_count INT NOT NULL,
    dog_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (input_id, version),
    FOREIGN KEY (input_id) REFERENCES input_media(id)
);
CREATE TABLE ground_truth_pet (
    input_id BIGINT NOT NULL,
    version INT NOT NULL,
    object_no INT NOT NULL,
    species VARCHAR(8) NOT NULL,
    breed VARCHAR(200) NOT NULL,
    position_note VARCHAR(255) NOT NULL,
    PRIMARY KEY (input_id, version, object_no),
    FOREIGN KEY (input_id, version) REFERENCES ground_truth(input_id, version)
);
CREATE TABLE evaluation (
    run_id CHAR(36) NOT NULL,
    input_id BIGINT NOT NULL,
    version INT NOT NULL,
    cat_error INT NOT NULL,
    dog_error INT NOT NULL,
    cat_diff INT NOT NULL,
    dog_diff INT NOT NULL,
    is_match BOOLEAN NOT NULL,
    breed_correct INT NOT NULL,
    breed_total INT NOT NULL,
    breed_unsupported INT NOT NULL DEFAULT 0,
    PRIMARY KEY (run_id, version),
    FOREIGN KEY (run_id) REFERENCES analysis_run(run_id),
    FOREIGN KEY (input_id, version) REFERENCES ground_truth(input_id, version)
);
