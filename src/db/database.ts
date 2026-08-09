import type { DatabaseState } from '../types';
import { INITIAL_USERS, INITIAL_GAMES, INITIAL_SESSIONS, INITIAL_EVENTS } from './initialData';

const LEGACY_GAME_NAMES = ['Catan', 'Ticket to Ride', 'Carcassonne', 'Dixit', 'Terraforming Mars'];

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
  const defaultState: DatabaseState = {
    users: INITIAL_USERS,
    boardGames: INITIAL_GAMES.map(normalizeBoardGame),
    sessions: INITIAL_SESSIONS,
    events: INITIAL_EVENTS
  };

  return syncDatabaseCalculations(defaultState);
};

export const sanitizeDatabaseState = (state: DatabaseState): DatabaseState => {
  const hasLegacyCatalog = state.boardGames?.some(game => LEGACY_GAME_NAMES.includes(game.name));
  if (!state.users || !state.boardGames || !state.sessions || !state.events || hasLegacyCatalog) {
    return getDefaultDatabaseState();
  }

  const adminSeed = INITIAL_USERS.find(user => user.id === 'u_admin');
  const users = adminSeed && !state.users.some(user => user.id === adminSeed.id)
    ? [adminSeed, ...state.users]
    : state.users;

  const boardGames = state.boardGames.map(normalizeBoardGame);

  return syncDatabaseCalculations({
    users,
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
