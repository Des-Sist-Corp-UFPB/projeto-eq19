ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS external_id VARCHAR(80);

UPDATE usuarios
SET external_id = 'u_' || id::text
WHERE external_id IS NULL;

ALTER TABLE usuarios ALTER COLUMN external_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_usuarios_external_id ON usuarios (external_id);

INSERT INTO usuarios (external_id, nome, email, senha_hash, role, email_verificado)
VALUES (
    'u_admin',
    'Administrador Tabula',
    'admin@tabula.com',
    '25440bbdc3ec84eaff2f09c15e9922031cb5173cd4d00ce736fcd96ee6858af6',
    'ADMIN',
    TRUE
)
ON CONFLICT (email) DO UPDATE
SET external_id = EXCLUDED.external_id,
    nome = EXCLUDED.nome,
    senha_hash = EXCLUDED.senha_hash,
    role = EXCLUDED.role,
    email_verificado = TRUE;
