ALTER TABLE jogos ALTER COLUMN cover_url TYPE TEXT;

WITH legacy_games AS (
    SELECT game
    FROM app_state state
    CROSS JOIN LATERAL jsonb_array_elements(COALESCE(state.data->'boardGames', '[]'::jsonb)) game
    WHERE state.id = 1
      AND jsonb_typeof(game) = 'object'
      AND length(btrim(game->>'id')) BETWEEN 1 AND 80
      AND length(btrim(game->>'name')) BETWEEN 1 AND 150
      AND (game->>'minPlayers') ~ '^\d+$'
      AND (game->>'maxPlayers') ~ '^\d+$'
      AND (game->>'avgPlayTime') ~ '^\d+$'
      AND (game->>'complexity') ~ '^\d+(\.\d+)?$'
      AND (game->>'minPlayers')::int >= 1
      AND (game->>'maxPlayers')::int >= (game->>'minPlayers')::int
      AND (game->>'avgPlayTime')::int >= 1
      AND (game->>'complexity')::numeric BETWEEN 1 AND 5
), updated AS (
    UPDATE jogos j SET
        external_id = COALESCE(j.external_id, btrim(g.game->>'id')),
        descricao = CASE WHEN j.descricao IS NULL OR btrim(j.descricao) = '' THEN left(COALESCE(g.game->>'description', ''), 5000) ELSE j.descricao END,
        cover_url = CASE WHEN j.cover_url IS NULL OR btrim(j.cover_url) = '' THEN left(COALESCE(g.game->>'coverUrl', '/images/tabletop-placeholder.svg'), 2048) ELSE j.cover_url END,
        categoria = CASE WHEN j.categoria IS NULL OR btrim(j.categoria) = '' THEN left(COALESCE(NULLIF(btrim(g.game->>'category'), ''), 'Geral'), 120) ELSE j.categoria END,
        min_players = COALESCE(j.min_players, (g.game->>'minPlayers')::int),
        max_players = COALESCE(j.max_players, (g.game->>'maxPlayers')::int),
        avg_play_time = COALESCE(j.avg_play_time, (g.game->>'avgPlayTime')::int),
        complexity = COALESCE(j.complexity, (g.game->>'complexity')::numeric),
        atualizado_em = CURRENT_TIMESTAMP
    FROM legacy_games g
    WHERE j.external_id = btrim(g.game->>'id') OR (j.external_id IS NULL AND j.nome = btrim(g.game->>'name'))
    RETURNING j.id
)
INSERT INTO jogos (external_id, nome, descricao, cover_url, categoria, min_players, max_players, avg_play_time, complexity, criado_em)
SELECT btrim(g.game->>'id'), btrim(g.game->>'name'), left(COALESCE(g.game->>'description', ''), 5000),
       left(COALESCE(NULLIF(btrim(g.game->>'coverUrl'), ''), '/images/tabletop-placeholder.svg'), 2048),
       left(COALESCE(NULLIF(btrim(g.game->>'category'), ''), 'Geral'), 120),
       (g.game->>'minPlayers')::int, (g.game->>'maxPlayers')::int,
       (g.game->>'avgPlayTime')::int, (g.game->>'complexity')::numeric, CURRENT_TIMESTAMP
FROM legacy_games g
WHERE NOT EXISTS (SELECT 1 FROM jogos j WHERE j.external_id = btrim(g.game->>'id') OR j.nome = btrim(g.game->>'name'));

INSERT INTO jogos (external_id, nome, descricao, cover_url, categoria, min_players, max_players, avg_play_time, complexity)
VALUES
 ('g1','Xadrez','Um clássico duelo de estratégia pura, com planejamento, cálculo de movimentos e leitura da posição do adversário.','/images/chess_cover.png','Estratégia',2,2,45,2.1),
 ('g2','Magic: The Gathering','Jogo de cartas estratégico com mana, combinação de baralhos e decisões de tempo que definem a partida.','/images/magic_cover.png','Cartas',2,4,60,3.2),
 ('g3','Pokémon TCG','Um jogo de cartas dinâmico e divertido, onde cada duelo combina estratégia, coleção e jogadas de ataque.','/images/pokemon_cover.png','Cartas',2,4,40,2.0)
ON CONFLICT DO NOTHING;

ALTER TABLE jogos ADD CONSTRAINT ck_jogos_external_id_nonblank CHECK (external_id IS NULL OR btrim(external_id) <> '');
ALTER TABLE jogos ADD CONSTRAINT ck_jogos_nome_nonblank CHECK (btrim(nome) <> '');
ALTER TABLE jogos ADD CONSTRAINT ck_jogos_descricao_length CHECK (descricao IS NULL OR length(descricao) <= 5000);
ALTER TABLE jogos ADD CONSTRAINT ck_jogos_cover_url_length CHECK (cover_url IS NULL OR length(cover_url) <= 2048);
ALTER TABLE jogos ADD CONSTRAINT ck_jogos_categoria_length CHECK (categoria IS NULL OR length(categoria) BETWEEN 1 AND 120);
ALTER TABLE jogos ADD CONSTRAINT ck_jogos_players CHECK (min_players IS NULL OR max_players IS NULL OR (min_players >= 1 AND max_players >= min_players));
ALTER TABLE jogos ADD CONSTRAINT ck_jogos_avg_play_time CHECK (avg_play_time IS NULL OR avg_play_time >= 1);
ALTER TABLE jogos ADD CONSTRAINT ck_jogos_complexity CHECK (complexity IS NULL OR complexity BETWEEN 1 AND 5);
