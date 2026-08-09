import type { DatabaseState } from '../types';

const LOCAL_COVER_MAP: Record<string, string> = {
  'Xadrez': '/images/chess_cover.jpg',
  'Magic: The Gathering': '/images/magic_cover.webp',
  'Pokémon TCG': '/images/pokemon_cover.webp'
};

export const normalizeGameCoverUrl = (name: string, coverUrl?: string): string => {
  if (LOCAL_COVER_MAP[name]) return LOCAL_COVER_MAP[name];
  if (coverUrl && coverUrl.startsWith('/images/')) return coverUrl;
  return '/images/tabletop-placeholder.svg';
};

const normalizeBoardGame = (game: DatabaseState['boardGames'][number]): DatabaseState['boardGames'][number] => ({
  id: game.id,
  name: game.name,
  description: game.description,
  coverUrl: normalizeGameCoverUrl(game.name, game.coverUrl),
  category: game.category,
  minPlayers: game.minPlayers,
  maxPlayers: game.maxPlayers,
  avgPlayTime: game.avgPlayTime,
  complexity: game.complexity,
});

export const getDefaultDatabaseState = (): DatabaseState => {
  return { users: [], boardGames: [], sessions: [], events: [] };
};

export const sanitizeDatabaseState = (state: DatabaseState): DatabaseState => {
  if (!state || !Array.isArray(state.users) || !Array.isArray(state.boardGames)
      || !Array.isArray(state.sessions) || !Array.isArray(state.events)) {
    throw new TypeError('Invalid relational database state');
  }

  const boardGames = state.boardGames.map(normalizeBoardGame);

  return syncDatabaseCalculations({
    users: state.users,
    boardGames,
    sessions: state.sessions,
    events: state.events,
  });
};

export const syncDatabaseCalculations = (state: DatabaseState): DatabaseState => {
  const updatedUsers = state.users.map(user => {
    const wins = state.sessions.filter(s => s.winnerId === user.id).length;

    const uniqueFavorites = Array.from(new Set(user.favoriteGames || []))
      .filter(gid => state.boardGames.some(g => g.id === gid));

    return {
      ...user,
      winCount: wins,
      favoriteGames: uniqueFavorites
    };
  });

  return {
    ...state,
    users: updatedUsers
  };
};
