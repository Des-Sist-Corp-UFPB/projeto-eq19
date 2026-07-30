ALTER TABLE evento_participantes
    ADD COLUMN IF NOT EXISTS ordem_fila BIGINT;

UPDATE evento_participantes ep
SET ordem_fila = ranked.ordem
FROM (
    SELECT evento_id, usuario_id,
           ROW_NUMBER() OVER (PARTITION BY evento_id ORDER BY inscrito_em, usuario_id) AS ordem
    FROM evento_participantes
    WHERE tipo = 'WAITING'
) ranked
WHERE ep.evento_id = ranked.evento_id
  AND ep.usuario_id = ranked.usuario_id
  AND ep.ordem_fila IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_evento_participantes_fila
    ON evento_participantes (evento_id, ordem_fila)
    WHERE tipo = 'WAITING';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_eventos_max_participantes'
    ) THEN
        ALTER TABLE eventos
            ADD CONSTRAINT ck_eventos_max_participantes CHECK (max_participantes > 0) NOT VALID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_eventos_status'
    ) THEN
        ALTER TABLE eventos
            ADD CONSTRAINT ck_eventos_status
                CHECK (status IN ('active', 'completed', 'cancelled')) NOT VALID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_evento_participantes_tipo'
    ) THEN
        ALTER TABLE evento_participantes
            ADD CONSTRAINT ck_evento_participantes_tipo
                CHECK (tipo IN ('PARTICIPANT', 'WAITING')) NOT VALID;
    END IF;
END $$;
