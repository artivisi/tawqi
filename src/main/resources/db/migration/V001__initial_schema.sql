-- Tawqi initial schema

-- Signers: people who sign documents
CREATE TABLE signers (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_signers_username ON signers (username);
CREATE INDEX idx_signers_deleted_at ON signers (deleted_at);

-- Signer certificates: links signers to Vault Transit keys
CREATE TABLE signer_certificates (
    id UUID PRIMARY KEY,
    signer_id UUID NOT NULL REFERENCES signers(id),
    transit_key_name VARCHAR(255) NOT NULL,
    certificate_serial VARCHAR(255),
    issued_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(255) NOT NULL
);

CREATE INDEX idx_signer_certificates_signer_id ON signer_certificates (signer_id);
CREATE INDEX idx_signer_certificates_active ON signer_certificates (active);

-- Documents: PDFs submitted for signing
CREATE TABLE documents (
    id UUID PRIMARY KEY,
    document_code VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    original_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    signed_storage_path VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    submitted_by VARCHAR(255),
    batch_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_document_status CHECK (status IN ('DRAFT', 'PENDING', 'SIGNED', 'REJECTED'))
);

CREATE INDEX idx_documents_document_code ON documents (document_code);
CREATE INDEX idx_documents_status ON documents (status);
CREATE INDEX idx_documents_batch_id ON documents (batch_id);
CREATE INDEX idx_documents_deleted_at ON documents (deleted_at);

-- Signing workflows: defines how a document gets signed
CREATE TABLE signing_workflows (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id),
    workflow_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    CONSTRAINT chk_workflow_type CHECK (workflow_type IN ('SINGLE', 'SEQUENTIAL'))
);

CREATE INDEX idx_signing_workflows_document_id ON signing_workflows (document_id);

-- Signing requests: individual sign/reject actions per signer
CREATE TABLE signing_requests (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL REFERENCES signing_workflows(id),
    signer_id UUID NOT NULL REFERENCES signers(id),
    sequence_order INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    signed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    CONSTRAINT chk_signing_request_status CHECK (status IN ('PENDING', 'SIGNED', 'REJECTED'))
);

CREATE INDEX idx_signing_requests_workflow_id ON signing_requests (workflow_id);
CREATE INDEX idx_signing_requests_signer_id ON signing_requests (signer_id);
CREATE INDEX idx_signing_requests_status ON signing_requests (status);

-- Templates: reusable PDF templates with field mapping
CREATE TABLE templates (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    storage_path VARCHAR(1000) NOT NULL,
    field_mapping JSONB,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_templates_active ON templates (active);
CREATE INDEX idx_templates_deleted_at ON templates (deleted_at);

-- Batches: bulk signing jobs
CREATE TABLE batches (
    id UUID PRIMARY KEY,
    template_id UUID REFERENCES templates(id),
    signer_id UUID NOT NULL REFERENCES signers(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_count INT NOT NULL DEFAULT 0,
    completed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    CONSTRAINT chk_batch_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_batches_status ON batches (status);
CREATE INDEX idx_batches_signer_id ON batches (signer_id);

-- Add FK from documents to batches (deferred because batches table was created after documents)
ALTER TABLE documents ADD CONSTRAINT fk_documents_batch_id FOREIGN KEY (batch_id) REFERENCES batches(id);

-- Audit entries: immutable append-only log
CREATE TABLE audit_entries (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    details JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_entries_entity ON audit_entries (entity_type, entity_id);
CREATE INDEX idx_audit_entries_actor ON audit_entries (actor);
CREATE INDEX idx_audit_entries_created_at ON audit_entries (created_at);

-- Sequence for human-readable document codes (TQ-2026-00001)
CREATE SEQUENCE document_code_seq START WITH 1 INCREMENT BY 1;
