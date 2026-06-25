import type { DatabaseState, ActivityLog } from '../types';
import { INITIAL_USERS, INITIAL_GAMES, INITIAL_SESSIONS, INITIAL_EVENTS, INITIAL_LOGS } from './initialData';

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

export const getDefaultDatabaseState = (): DatabaseState => {
  const defaultState: DatabaseState = {
    users: INITIAL_USERS,
    boardGames: INITIAL_GAMES.map(game => ({
      ...game,
      coverUrl: normalizeGameCoverUrl(game.name, game.coverUrl),
    })),
    sessions: INITIAL_SESSIONS,
    events: INITIAL_EVENTS,
    logs: INITIAL_LOGS
  };

  return syncDatabaseCalculations(defaultState);
};

export const sanitizeDatabaseState = (state: DatabaseState): DatabaseState => {
  const hasLegacyCatalog = state.boardGames?.some(game => LEGACY_GAME_NAMES.includes(game.name));
  if (!state.users || !state.boardGames || !state.sessions || !state.events || !state.logs || hasLegacyCatalog) {
    return getDefaultDatabaseState();
  }

  const adminSeed = INITIAL_USERS.find(user => user.id === 'u_admin');
  const users = adminSeed && !state.users.some(user => user.id === adminSeed.id)
    ? [adminSeed, ...state.users]
    : state.users;

  const boardGames = state.boardGames.map(game => ({
    ...game,
    coverUrl: normalizeGameCoverUrl(game.name, game.coverUrl),
  }));

  return syncDatabaseCalculations({
    ...state,
    users,
    boardGames,
  });
};

export const syncDatabaseCalculations = (state: DatabaseState): DatabaseState => {
  const updatedUsers = state.users.map(user => {
    const wins = state.sessions.filter(s => s.winnerId === user.id).length;

    const gamesPlayedIds = state.sessions
      .filter(s => s.participantIds.includes(user.id))
      .map(s => s.gameId);

    const gameCounts = gamesPlayedIds.reduce((acc, gid) => {
      acc[gid] = (acc[gid] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);

    const sortedGamesByPlay = Object.entries(gameCounts)
      .sort((a, b) => b[1] - a[1])
      .map(([gid]) => gid);

    const uniqueFavorites = Array.from(
      new Set([...(user.favoriteGames || []), ...sortedGamesByPlay.slice(0, 3)])
    ).filter(gid => state.boardGames.some(g => g.id === gid));

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

export const createLog = (
  state: DatabaseState,
  userId: string,
  userName: string,
  action: string
): ActivityLog[] => {
  const newLog: ActivityLog = {
    id: `l_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`,
    userId,
    userName,
    action,
    timestamp: new Date().toISOString()
  };
  return [newLog, ...state.logs].slice(0, 50);
};
