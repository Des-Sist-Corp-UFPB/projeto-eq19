CREATE TABLE IF NOT EXISTS auth_tokens (
    token VARCHAR(120) PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_auth_tokens_usuario_id ON auth_tokens (usuario_id);
CREATE INDEX IF NOT EXISTS ix_auth_tokens_expires_at ON auth_tokens (expires_at);
