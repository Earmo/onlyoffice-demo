CREATE TABLE document_runtime_event
(
    event_id         VARCHAR(64) PRIMARY KEY,
    document_id      VARCHAR(128) NOT NULL,
    event_type       VARCHAR(64)  NOT NULL,
    callback_status  INTEGER,
    event_message    VARCHAR(512),
    event_time       TIMESTAMP    NOT NULL
);

CREATE INDEX idx_document_runtime_event_document_time
    ON document_runtime_event (document_id, event_time DESC);
