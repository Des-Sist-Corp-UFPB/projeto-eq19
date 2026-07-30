CREATE UNIQUE INDEX IF NOT EXISTS ux_partidas_evento_id
    ON partidas (evento_id)
    WHERE evento_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_partidas_data_hora ON partidas (data_hora DESC);
CREATE INDEX IF NOT EXISTS ix_partidas_organizador_id ON partidas (organizador_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_partidas_duracao'
    ) THEN
        ALTER TABLE partidas
            ADD CONSTRAINT ck_partidas_duracao
            CHECK (duracao_minutos IS NULL OR duracao_minutos >= 0) NOT VALID;
    END IF;
END $$;
