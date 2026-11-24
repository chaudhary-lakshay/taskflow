-- TaskFlow baseline schema (docs/architecture.md, Notion plan page 02).
-- Enums are stored as text with CHECK constraints; timestamps are UTC (timestamptz).

CREATE TABLE users (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          varchar(100) NOT NULL CHECK (btrim(name) <> ''),
    email         varchar(254) NOT NULL,
    password_hash varchar(100) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    -- Emails are normalised by the application; the database refuses anything else,
    -- so uniqueness is case-insensitive without a second index.
    CONSTRAINT ck_users_email_normalised CHECK (email = lower(btrim(email)) AND email <> '')
);

CREATE TABLE projects (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        varchar(120) NOT NULL CHECK (btrim(name) <> ''),
    description text,
    owner_id    bigint       NOT NULL REFERENCES users (id),
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE tasks (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title       varchar(120) NOT NULL CHECK (btrim(title) <> ''),
    description text,
    status      varchar(20)  NOT NULL DEFAULT 'TODO',
    priority    varchar(10)  NOT NULL DEFAULT 'MEDIUM',
    due_date    date,
    assignee_id bigint REFERENCES users (id),
    project_id  bigint       NOT NULL REFERENCES projects (id),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'))
);

-- Indexes follow the planned queries (owner-scoped project list; per-project task
-- lists by status, by due date, by assignee). Re-check with EXPLAIN in T03.
CREATE INDEX idx_projects_owner_id ON projects (owner_id, id);
CREATE INDEX idx_tasks_project_status_id ON tasks (project_id, status, id);
CREATE INDEX idx_tasks_project_due_date_id ON tasks (project_id, due_date, id);
CREATE INDEX idx_tasks_project_assignee ON tasks (project_id, assignee_id);
