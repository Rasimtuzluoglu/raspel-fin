-- Notlar tablosu
CREATE TABLE note (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    category VARCHAR(50),
    color VARCHAR(20) DEFAULT '#FFD54F',
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_note_created_by ON note(created_by);
CREATE INDEX idx_note_category ON note(category);
CREATE INDEX idx_note_pinned ON note(pinned);
