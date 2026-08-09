import type { DatabaseState } from '../types';
import { syncDatabaseCalculations } from '../db/database';
import { INITIAL_EVENTS, INITIAL_GAMES, INITIAL_SESSIONS, INITIAL_USERS } from '../db/initialData';

export const getTestDatabaseState = (): DatabaseState => syncDatabaseCalculations({
  users: structuredClone(INITIAL_USERS),
  boardGames: structuredClone(INITIAL_GAMES),
  sessions: structuredClone(INITIAL_SESSIONS),
  events: structuredClone(INITIAL_EVENTS),
});
