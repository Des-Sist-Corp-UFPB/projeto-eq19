CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NULL REFERENCES usuarios(id) ON DELETE SET NULL,
    ator_id_externo VARCHAR(80),
    acao VARCHAR(80) NOT NULL CHECK (btrim(acao) <> ''),
    tipo_recurso VARCHAR(80),
    recurso_id VARCHAR(120),
    detalhes JSONB NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(detalhes) = 'object'),
    endereco_ip INET,
    user_agent VARCHAR(512),
    sucesso BOOLEAN NOT NULL,
    trace_id VARCHAR(32)
        CHECK (trace_id IS NULL OR trace_id ~ '^[0-9a-f]{32}$'),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_audit_logs_criado_em
    ON audit_logs (criado_em DESC, id DESC);
CREATE INDEX ix_audit_logs_usuario
    ON audit_logs (usuario_id, criado_em DESC);
CREATE INDEX ix_audit_logs_ator_externo
    ON audit_logs (ator_id_externo, criado_em DESC);
CREATE INDEX ix_audit_logs_acao
    ON audit_logs (acao, criado_em DESC);
CREATE INDEX ix_audit_logs_sucesso
    ON audit_logs (sucesso, criado_em DESC);
CREATE INDEX ix_audit_logs_recurso
    ON audit_logs (tipo_recurso, recurso_id, criado_em DESC);

CREATE FUNCTION prevent_audit_logs_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND pg_trigger_depth() > 1
       AND OLD.usuario_id IS NOT NULL
       AND NEW.usuario_id IS NULL
       AND NEW.id IS NOT DISTINCT FROM OLD.id
       AND NEW.ator_id_externo IS NOT DISTINCT FROM OLD.ator_id_externo
       AND NEW.acao IS NOT DISTINCT FROM OLD.acao
       AND NEW.tipo_recurso IS NOT DISTINCT FROM OLD.tipo_recurso
       AND NEW.recurso_id IS NOT DISTINCT FROM OLD.recurso_id
       AND NEW.detalhes IS NOT DISTINCT FROM OLD.detalhes
       AND NEW.endereco_ip IS NOT DISTINCT FROM OLD.endereco_ip
       AND NEW.user_agent IS NOT DISTINCT FROM OLD.user_agent
       AND NEW.sucesso IS NOT DISTINCT FROM OLD.sucesso
       AND NEW.trace_id IS NOT DISTINCT FROM OLD.trace_id
       AND NEW.criado_em IS NOT DISTINCT FROM OLD.criado_em THEN
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$;

CREATE TRIGGER trg_audit_logs_no_update_delete
BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION prevent_audit_logs_mutation();

CREATE FUNCTION prevent_audit_logs_truncate()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$;

CREATE TRIGGER trg_audit_logs_no_truncate
BEFORE TRUNCATE ON audit_logs
FOR EACH STATEMENT EXECUTE FUNCTION prevent_audit_logs_truncate();

COMMENT ON TABLE audit_logs IS
    'Official backend-owned append-only audit trail. Runtime role should receive only SELECT/INSERT plus sequence usage.';
COMMENT ON COLUMN audit_logs.ator_id_externo IS
    'Immutable snapshot of usuarios.external_id, retained after usuario_id is set to NULL.';
