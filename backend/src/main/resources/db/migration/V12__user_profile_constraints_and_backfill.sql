ALTER TABLE usuarios ALTER COLUMN avatar_url TYPE TEXT;

WITH legacy_profiles AS (
    SELECT legacy_user
    FROM app_state state
    CROSS JOIN LATERAL jsonb_array_elements(COALESCE(state.data->'users', '[]'::jsonb)) legacy_user
    WHERE state.id = 1
)
UPDATE usuarios u
SET curso = CASE
        WHEN (u.curso IS NULL OR btrim(u.curso) = '')
         AND jsonb_typeof(p.legacy_user->'course') = 'string'
         AND length(btrim(p.legacy_user->>'course')) BETWEEN 1 AND 180
        THEN btrim(p.legacy_user->>'course') ELSE u.curso END,
    bio = CASE
        WHEN (u.bio IS NULL OR btrim(u.bio) = '')
         AND jsonb_typeof(p.legacy_user->'bio') = 'string'
         AND length(p.legacy_user->>'bio') <= 2000
        THEN btrim(p.legacy_user->>'bio') ELSE u.bio END,
    avatar_url = CASE
        WHEN (u.avatar_url IS NULL OR btrim(u.avatar_url) = '')
         AND jsonb_typeof(p.legacy_user->'avatarUrl') = 'string'
         AND length(p.legacy_user->>'avatarUrl') <= 1000000
         AND ((p.legacy_user->>'avatarUrl') ~ '^https?://' OR (p.legacy_user->>'avatarUrl') ~ '^data:image/')
        THEN btrim(p.legacy_user->>'avatarUrl') ELSE u.avatar_url END,
    atualizado_em = CURRENT_TIMESTAMP
FROM legacy_profiles p
WHERE u.external_id = p.legacy_user->>'id';

ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_nome_nonblank CHECK (btrim(nome) <> '');
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_curso_length CHECK (curso IS NULL OR length(curso) <= 180);
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_bio_length CHECK (bio IS NULL OR length(bio) <= 2000);
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_avatar_url_length CHECK (avatar_url IS NULL OR length(avatar_url) <= 1000000);

