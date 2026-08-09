UPDATE comentarios
SET external_id = 'c_legacy_' || id
WHERE external_id IS NULL OR btrim(external_id) = '';

INSERT INTO comentarios (external_id, partida_id, usuario_id, conteudo, criado_em)
SELECT comment->>'id', p.id, u.id, comment->>'content',
       COALESCE(NULLIF(comment->>'createdAt', '')::timestamp, CURRENT_TIMESTAMP)
FROM app_state state
CROSS JOIN LATERAL jsonb_array_elements(COALESCE(state.data->'sessions', '[]'::jsonb)) session
CROSS JOIN LATERAL jsonb_array_elements(COALESCE(session->'comments', '[]'::jsonb)) comment
JOIN partidas p ON p.external_id = session->>'id'
LEFT JOIN usuarios u ON u.external_id = comment->>'userId'
WHERE state.id = 1
  AND NULLIF(btrim(comment->>'id'), '') IS NOT NULL
  AND NULLIF(btrim(comment->>'content'), '') IS NOT NULL
  AND (comment->>'createdAt') ~ '^\d{4}-\d{2}-\d{2}([T ][0-9:.]+(Z|[+-][0-9:]+)?)?$'
ON CONFLICT (external_id) DO NOTHING;

ALTER TABLE comentarios ALTER COLUMN external_id SET NOT NULL;
ALTER TABLE comentarios ADD CONSTRAINT ck_comentarios_external_id_nonblank
    CHECK (btrim(external_id) <> '');
