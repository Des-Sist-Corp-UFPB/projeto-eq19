-- V6__create_relational_content_tables.sql
-- Non-destructive migration: create relational content tables and extend profile fields.
-- NOTE: This migration is additive and idempotent (uses IF NOT EXISTS).

-- 1) Extend 'usuarios' with profile fields if missing
ALTER TABLE usuarios
  ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(512),
  ADD COLUMN IF NOT EXISTS bio TEXT,
  ADD COLUMN IF NOT EXISTS curso VARCHAR(180),
  ADD COLUMN IF NOT EXISTS atualizado_em TIMESTAMP;

-- 2) Ensure 'jogos' has created timestamp and profile columns if missing
ALTER TABLE jogos
  ADD COLUMN IF NOT EXISTS external_id VARCHAR(80),
  ADD COLUMN IF NOT EXISTS descricao TEXT,
  ADD COLUMN IF NOT EXISTS cover_url VARCHAR(512),
  ADD COLUMN IF NOT EXISTS categoria VARCHAR(120),
  ADD COLUMN IF NOT EXISTS min_players INT,
  ADD COLUMN IF NOT EXISTS max_players INT,
  ADD COLUMN IF NOT EXISTS avg_play_time INT,
  ADD COLUMN IF NOT EXISTS complexity NUMERIC(3,1),
  ADD COLUMN IF NOT EXISTS atualizado_em TIMESTAMP;

-- Add criado_em to jogos if not present
ALTER TABLE jogos
  ADD COLUMN IF NOT EXISTS criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Indexes for jogos
CREATE UNIQUE INDEX IF NOT EXISTS ux_jogos_external_id ON jogos (external_id);
CREATE INDEX IF NOT EXISTS ix_jogos_categoria ON jogos (categoria);

-- 3) Create 'eventos'
CREATE TABLE IF NOT EXISTS eventos (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(80),
    jogo_id BIGINT REFERENCES jogos(id) ON DELETE SET NULL,
    data_hora TIMESTAMP NOT NULL,
    local VARCHAR(512),
    descricao TEXT,
    max_participantes INT,
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    organizador_id BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_eventos_jogo_id ON eventos (jogo_id);
CREATE INDEX IF NOT EXISTS ix_eventos_data_hora ON eventos (data_hora);
CREATE UNIQUE INDEX IF NOT EXISTS ux_eventos_external_id ON eventos (external_id);

-- 4) Create 'evento_participantes'
CREATE TABLE IF NOT EXISTS evento_participantes (
    evento_id BIGINT NOT NULL REFERENCES eventos(id) ON DELETE CASCADE,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo VARCHAR(20) NOT NULL,
    inscrito_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (evento_id, usuario_id)
);
CREATE INDEX IF NOT EXISTS ix_evento_participantes_usuario_id ON evento_participantes (usuario_id);

-- 5) Create 'partidas'
CREATE TABLE IF NOT EXISTS partidas (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(80),
    jogo_id BIGINT REFERENCES jogos(id) ON DELETE SET NULL,
    evento_id BIGINT REFERENCES eventos(id) ON DELETE SET NULL,
    data_hora TIMESTAMP NOT NULL,
    local VARCHAR(512),
    organizador_id BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    vencedor_id BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    duracao_minutos INT,
    notas TEXT,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_partidas_jogo_id ON partidas (jogo_id);
CREATE INDEX IF NOT EXISTS ix_partidas_vencedor_id ON partidas (vencedor_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_partidas_external_id ON partidas (external_id);

-- 6) Create 'partida_participantes'
CREATE TABLE IF NOT EXISTS partida_participantes (
    partida_id BIGINT NOT NULL REFERENCES partidas(id) ON DELETE CASCADE,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    PRIMARY KEY (partida_id, usuario_id)
);
CREATE INDEX IF NOT EXISTS ix_partida_participantes_usuario_id ON partida_participantes (usuario_id);

CREATE TABLE IF NOT EXISTS partida_fotos (
    id BIGSERIAL PRIMARY KEY,
    partida_id BIGINT NOT NULL REFERENCES partidas(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    ordem INT,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_partida_fotos_partida_id ON partida_fotos (partida_id);

-- 7) Create 'comentarios' (usuario_id nullable so comments survive user deletion)
CREATE TABLE IF NOT EXISTS comentarios (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(80),
    partida_id BIGINT NOT NULL REFERENCES partidas(id) ON DELETE CASCADE,
    usuario_id BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    conteudo TEXT NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_comentarios_partida_id ON comentarios (partida_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_comentarios_external_id ON comentarios (external_id);

-- 8) Create 'favoritos'
CREATE TABLE IF NOT EXISTS favoritos (
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    jogo_id BIGINT NOT NULL REFERENCES jogos(id) ON DELETE CASCADE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (usuario_id, jogo_id)
);
CREATE INDEX IF NOT EXISTS ix_favoritos_usuario_id ON favoritos (usuario_id);
CREATE INDEX IF NOT EXISTS ix_favoritos_jogo_id ON favoritos (jogo_id);

-- 9) Create 'logs'
CREATE TABLE IF NOT EXISTS logs (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(80),
    usuario_id BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    nome_usuario VARCHAR(180),
    acao TEXT,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_logs_criado_em ON logs (criado_em);
CREATE UNIQUE INDEX IF NOT EXISTS ux_logs_external_id ON logs (external_id);

-- 10) Ensure unique index on usuarios.external_id exists (V3 may have created it already)
CREATE UNIQUE INDEX IF NOT EXISTS ux_usuarios_external_id ON usuarios (external_id);

-- End of V6 migration (non-destructive)
