CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    solved_count  INT          NOT NULL DEFAULT 0,
    submit_count  INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE problems (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(100) NOT NULL,
    difficulty      VARCHAR(16)  NOT NULL,
    tags            VARCHAR(255) NOT NULL,
    description     TEXT         NOT NULL,
    input_format    TEXT         NOT NULL,
    output_format   TEXT         NOT NULL,
    samples         TEXT         NOT NULL,
    hint            TEXT         NOT NULL,
    editorial       MEDIUMTEXT   NOT NULL,
    time_limit_ms   INT          NOT NULL,
    memory_limit_mb INT          NOT NULL,
    visible         BOOLEAN      NOT NULL,
    submit_count    INT          NOT NULL DEFAULT 0,
    accepted_count  INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL
);

CREATE TABLE test_cases (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    problem_id  BIGINT   NOT NULL,
    seq         INT      NOT NULL,
    input_data  LONGTEXT NOT NULL,
    output_data LONGTEXT NOT NULL,
    CONSTRAINT fk_test_cases_problem FOREIGN KEY (problem_id) REFERENCES problems (id) ON DELETE CASCADE
);
CREATE INDEX idx_test_cases_problem ON test_cases (problem_id, seq);

CREATE TABLE submissions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    problem_id      BIGINT      NOT NULL,
    language        VARCHAR(16) NOT NULL,
    code            MEDIUMTEXT  NOT NULL,
    status          VARCHAR(16) NOT NULL,
    verdict         VARCHAR(8),
    time_ms         INT,
    memory_kb       INT,
    passed_count    INT         NOT NULL DEFAULT 0,
    total_count     INT         NOT NULL DEFAULT 0,
    compile_message TEXT,
    case_results    MEDIUMTEXT,
    created_at      DATETIME(6) NOT NULL,
    judged_at       DATETIME(6),
    CONSTRAINT fk_submissions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_submissions_problem FOREIGN KEY (problem_id) REFERENCES problems (id) ON DELETE CASCADE
);
CREATE INDEX idx_submissions_user ON submissions (user_id, id);
CREATE INDEX idx_submissions_problem ON submissions (problem_id, id);
CREATE INDEX idx_submissions_user_problem ON submissions (user_id, problem_id, verdict);
CREATE INDEX idx_submissions_status ON submissions (status);
