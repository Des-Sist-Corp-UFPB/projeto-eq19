INSERT INTO favoritos (usuario_id, jogo_id, criado_em)
SELECT DISTINCT u.id, j.id, CURRENT_TIMESTAMP
FROM app_state state
CROSS JOIN LATERAL jsonb_array_elements(COALESCE(state.data->'users', '[]'::jsonb)) legacy_user
CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(legacy_user->'favoriteGames', '[]'::jsonb)) favorite(game_external_id)
JOIN usuarios u ON u.external_id = legacy_user->>'id'
JOIN jogos j ON j.external_id = favorite.game_external_id
WHERE state.id = 1
ON CONFLICT (usuario_id, jogo_id) DO NOTHING;
