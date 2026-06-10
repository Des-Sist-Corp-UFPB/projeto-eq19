import type { DatabaseState, ActivityLog } from '../types';
import { INITIAL_USERS, INITIAL_GAMES, INITIAL_SESSIONS, INITIAL_EVENTS, INITIAL_LOGS } from './initialData';

const LOCAL_STORAGE_KEY = 'tabula_db_state';
const LEGACY_GAME_NAMES = ['Catan', 'Ticket to Ride', 'Carcassonne', 'Dixit', 'Terraforming Mars'];

const LOCAL_COVER_MAP: Record<string, string> = {
  'Xadrez': 'http://localhost:8119/images/chess_cover.jpg',
  'Magic: The Gathering': 'http://localhost:8119/images/magic_cover.webp',
  'Pokémon TCG': 'http://localhost:8119/images/pokemon_cover.webp'
};

export const normalizeGameCoverUrl = (name: string, coverUrl?: string): string => {
  if (LOCAL_COVER_MAP[name]) return LOCAL_COVER_MAP[name];
  if (coverUrl && coverUrl.startsWith('/images/')) return coverUrl;
  return '/images/tabletop-placeholder.svg';
};

// Load or initialize state
export const getInitialDatabaseState = (): DatabaseState => {
  try {
    const saved = localStorage.getItem(LOCAL_STORAGE_KEY);
    if (saved) {
      const state = JSON.parse(saved) as DatabaseState;
      const hasLegacyCatalog = state.boardGames.some(game => LEGACY_GAME_NAMES.includes(game.name));
      if (hasLegacyCatalog) {
        const defaultState = getDefaultDatabaseState();
        saveDatabaseState(defaultState);
        return defaultState;
      }
      // Ensure default users and games use the latest professional data
      state.users = state.users.map(user => {
        const initialUser = INITIAL_USERS.find(u => u.id === user.id);
        return initialUser
          ? { ...user, avatar: initialUser.avatar, course: initialUser.course, bio: initialUser.bio }
          : user;
      });

      state.boardGames = INITIAL_GAMES.map(initialGame => {
        const currentGame = state.boardGames.find(game => game.id === initialGame.id);
        return {
          ...currentGame,
          ...initialGame,
          coverUrl: normalizeGameCoverUrl(initialGame.name, initialGame.coverUrl),
          tags: [...initialGame.tags]
        };
      });

      saveDatabaseState(state);
      
      // Guarantee stats are accurate and sync'd on start
      return syncDatabaseCalculations(state);
    }
  } catch (e) {
    console.error('Failed to read from localStorage, initializing fresh database', e);
  }

  const defaultState = getDefaultDatabaseState();
  saveDatabaseState(defaultState);
  return defaultState;
};

const getDefaultDatabaseState = (): DatabaseState => {
  const defaultState: DatabaseState = {
    users: INITIAL_USERS,
    boardGames: INITIAL_GAMES,
    sessions: INITIAL_SESSIONS,
    events: INITIAL_EVENTS,
    logs: INITIAL_LOGS
  };

  return syncDatabaseCalculations(defaultState);
};

// Save state to localStorage
export const saveDatabaseState = (state: DatabaseState): void => {
  try {
    localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(state));
  } catch (e) {
    console.error('Failed to save to localStorage', e);
  }
};

// Recalculates dynamic fields to ensure data integrity
export const syncDatabaseCalculations = (state: DatabaseState): DatabaseState => {
  const updatedUsers = state.users.map(user => {
    // 1. Calculate wins
    const wins = state.sessions.filter(s => s.winnerId === user.id).length;

    // 2. Calculate favorite games (combine initial favorites + games played in sessions)
    const gamesPlayedIds = state.sessions
      .filter(s => s.participantIds.includes(user.id))
      .map(s => s.gameId);

    // Count occurrences of each game ID
    const gameCounts = gamesPlayedIds.reduce((acc, gid) => {
      acc[gid] = (acc[gid] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);

    // Sort by play count
    const sortedGamesByPlay = Object.entries(gameCounts)
      .sort((a, b) => b[1] - a[1])
      .map(([gid]) => gid);

    // Take top 3 unique played games and combine with original favorites
    const uniqueFavorites = Array.from(
      new Set([...user.favoriteGames, ...sortedGamesByPlay.slice(0, 3)])
    ).filter(gid => state.boardGames.some(g => g.id === gid)); // Filter out deleted games

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

// Helper to log system events
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
  return [newLog, ...state.logs].slice(0, 50); // Keep last 50 logs
};
