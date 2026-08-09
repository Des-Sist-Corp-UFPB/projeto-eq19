import React, { createContext, useCallback, useContext, useState, useEffect, useRef } from 'react';
import type { DatabaseState, BoardGame, Session, Event, User, UserRole } from '../types';
import { getDefaultDatabaseState, sanitizeDatabaseState, syncDatabaseCalculations, normalizeGameCoverUrl } from '../db/database';
import {
  completeEventRequest,
  addFavoriteRequest,
  createComment,
  createSession,
  createEvent,
  deleteSessionRequest,
  deleteCommentRequest,
  getEvents,
  getSession,
  getSessions,
  getServerState,
  joinEventRequest,
  leaveEventRequest,
  removeFavoriteRequest,
  saveServerState,
  updateProfile,
  createGame,
  updateGame,
  deleteGameRequest,
} from '../services/api';
import { useToast } from './ToastContext';

const generateId = (prefix: string) => {
  const randomId = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
  return `${prefix}_${randomId}`;
};

const serializeLegacyState = (state: DatabaseState) => JSON.stringify({
  users: state.users.map(user => ({
    id: user.id,
    email: user.email,
    passwordHash: user.passwordHash,
    role: user.role,
    winCount: user.winCount,
    joinedAt: user.joinedAt,
  })),
});

interface DatabaseContextType {
  state: DatabaseState;
  addGame: (game: Omit<BoardGame, 'id'>) => Promise<BoardGame>;
  addUser: (user: Omit<User, 'id' | 'avatar' | 'winCount' | 'favoriteGames' | 'joinedAt' | 'bio'> & { id?: string; passwordHash?: string; avatar?: string; course?: string; bio?: string; role?: UserRole; }) => User | null;
  editGame: (game: BoardGame) => Promise<BoardGame>;
  deleteGame: (gameId: string) => Promise<boolean>;
  addSession: (session: Omit<Session, 'id' | 'comments'>, initialComment?: string) => Promise<Session>;
  deleteSession: (sessionId: string) => Promise<void>;
  getSessionById: (sessionId: string) => Promise<Session>;
  addComment: (sessionId: string, content: string) => Promise<void>;
  deleteComment: (sessionId: string, commentId: string) => Promise<void>;
  addFavorite: (userId: string, gameId: string) => Promise<void>;
  removeFavorite: (userId: string, gameId: string) => Promise<void>;
  addEvent: (event: Omit<Event, 'id' | 'participantIds' | 'waitingListIds' | 'status' | 'organizerId'>, organizerId: string) => Promise<Event>;
  joinEvent: (eventId: string, userId: string) => Promise<boolean>;
  leaveEvent: (eventId: string, userId: string) => Promise<void>;
  completeEvent: (
    eventId: string,
    winnerId: string | null,
    duration: number,
    notes: string,
    initialComment?: string,
    photoUrl?: string
  ) => Promise<void>;
  deleteUser: (userId: string) => void;
  promoteUser: (userId: string) => void;
  editUser: (userId: string, updates: Pick<Partial<User>, 'name' | 'course' | 'bio' | 'avatarUrl'>) => Promise<void>;
}

const DatabaseContext = createContext<DatabaseContextType | undefined>(undefined);

export const DatabaseProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [state, setState] = useState<DatabaseState>(getDefaultDatabaseState);
  const { showToast } = useToast();
  const serverLoadedRef = useRef(false);
  const saveTimerRef = useRef<number | null>(null);
  const lastSavedJsonRef = useRef<string | null>(null);
  const fetchedSessionsRef = useRef(new Map<string, Session>());

  useEffect(() => {
    let cancelled = false;

    const loadFromServer = async () => {
      try {
        const serverState = await getServerState();
        let nextState = serverState ? sanitizeDatabaseState(serverState) : getDefaultDatabaseState();
        try {
          const [relationalEvents, relationalSessions] = await Promise.all([getEvents(), getSessions()]);
          if (Array.isArray(relationalEvents)) {
            nextState = { ...nextState, events: relationalEvents };
          }
          if (Array.isArray(relationalSessions)) {
            nextState = { ...nextState, sessions: relationalSessions };
          }
        } catch {
          // Public screens may load before authentication; GET /state already
          // carries the authoritative relational event projection.
        }
        if (cancelled) return;
        setState(() => {
          const fetchedSessions = [...fetchedSessionsRef.current.values()];
          if (fetchedSessions.length === 0) return nextState;
          return syncDatabaseCalculations({
            ...nextState,
            sessions: [
              ...fetchedSessions,
              ...nextState.sessions.filter(session => !fetchedSessionsRef.current.has(session.id)),
            ],
          });
        });
        if (!serverState) {
          await saveServerState(nextState, true);
        }
        lastSavedJsonRef.current = serializeLegacyState(nextState);
        serverLoadedRef.current = true;
      } catch (error) {
        if (!cancelled) {
          serverLoadedRef.current = true;
          showToast('Não foi possível carregar os dados do servidor. Verifique a API e o banco de dados.', 'error');
          console.error('Failed to load server state', error);
        }
      }
    };

    void loadFromServer();

    return () => {
      cancelled = true;
    };
  }, [showToast]);

  useEffect(() => {
    if (!serverLoadedRef.current) return;

    const serializedState = serializeLegacyState(state);
    if (serializedState === lastSavedJsonRef.current) return;

    if (saveTimerRef.current) window.clearTimeout(saveTimerRef.current);
    saveTimerRef.current = window.setTimeout(() => {
      void saveServerState(state)
        .then(() => {
          lastSavedJsonRef.current = serializedState;
        })
        .catch(error => {
          showToast('Não foi possível salvar no servidor agora.', 'error');
          console.error('Failed to save server state', error);
        });
    }, 350);

    return () => {
      if (saveTimerRef.current) window.clearTimeout(saveTimerRef.current);
    };
  }, [state, showToast]);

  // Game actions
  const addGame = async (game: Omit<BoardGame, 'id'>) => {
    const created = await createGame({ ...game, coverUrl: normalizeGameCoverUrl(game.name, game.coverUrl) });
    setState(prev => syncDatabaseCalculations({ ...prev, boardGames: [...prev.boardGames, created] }));
    return created;
  };

  const editGame = async (updatedGame: BoardGame) => {
    const saved = await updateGame({ ...updatedGame, coverUrl: normalizeGameCoverUrl(updatedGame.name, updatedGame.coverUrl) });
    setState(prev => syncDatabaseCalculations({ ...prev, boardGames: prev.boardGames.map(g => g.id === saved.id ? saved : g) }));
    return saved;
  };

  const deleteGame = async (gameId: string) => {
    const hasLinkedRecords = state.sessions.some(s => s.gameId === gameId) || state.events.some(e => e.gameId === gameId);

    if (hasLinkedRecords) {
      showToast('Não é possível remover este jogo porque ele está vinculado a sessões ou eventos.', 'warning');
      return false;
    }
    await deleteGameRequest(gameId);
    setState(prev => syncDatabaseCalculations({ ...prev, boardGames: prev.boardGames.filter(g => g.id !== gameId) }));
    return true;
  };

  // Session actions
  const addSession = async (sessionData: Omit<Session, 'id' | 'comments'>, initialComment?: string) => {
    void initialComment;
    const created = await createSession(sessionData);
    setState(prev => syncDatabaseCalculations({
      ...prev,
      sessions: [created, ...prev.sessions.filter(session => session.id !== created.id)],
    }));
    return created;
  };

  const deleteSession = async (sessionId: string) => {
    await deleteSessionRequest(sessionId);
    setState(prev => syncDatabaseCalculations({
      ...prev,
      sessions: prev.sessions.filter(s => s.id !== sessionId),
    }));
  };

  const getSessionById = useCallback(async (sessionId: string) => {
    const fetched = await getSession(sessionId);
    fetchedSessionsRef.current.set(fetched.id, fetched);
    setState(prev => syncDatabaseCalculations({
      ...prev,
      sessions: [fetched, ...prev.sessions.filter(session => session.id !== fetched.id)],
    }));
    return fetched;
  }, []);

  const addComment = async (sessionId: string, content: string) => {
    const newComment = await createComment(sessionId, content);
    setState(prev => {
      return {
        ...prev,
        sessions: prev.sessions.map(s => {
          if (s.id !== sessionId) return s;
          return {
            ...s,
            comments: [...s.comments, newComment]
          };
        })
      };
    });
  };

  const deleteComment = async (sessionId: string, commentId: string) => {
    await deleteCommentRequest(sessionId, commentId);
    setState(prev => ({
      ...prev,
      sessions: prev.sessions.map(session => session.id === sessionId
        ? { ...session, comments: session.comments.filter(comment => comment.id !== commentId) }
        : session),
    }));
  };

  const addFavorite = async (userId: string, gameId: string) => {
    const result = await addFavoriteRequest(gameId);
    setState(prev => ({ ...prev, users: prev.users.map(user => user.id === userId
      ? { ...user, favoriteGames: Array.from(new Set([...user.favoriteGames, result.gameId])) }
      : user) }));
  };

  const removeFavorite = async (userId: string, gameId: string) => {
    await removeFavoriteRequest(gameId);
    setState(prev => ({ ...prev, users: prev.users.map(user => user.id === userId
      ? { ...user, favoriteGames: user.favoriteGames.filter(id => id !== gameId) }
      : user) }));
  };

  // Event actions
  const addEvent = async (
    eventData: Omit<Event, 'id' | 'participantIds' | 'waitingListIds' | 'status' | 'organizerId'>,
    organizerId: string,
  ) => {
    void organizerId;
    const created = await createEvent(eventData);
    setState(prev => syncDatabaseCalculations({
      ...prev,
      events: [created, ...prev.events.filter(event => event.id !== created.id)],
    }));
    return created;
  };

  const replaceEvent = (updatedEvent: Event) => {
    setState(prev => ({
      ...prev,
      events: prev.events.map(event => event.id === updatedEvent.id ? updatedEvent : event),
    }));
  };

  const joinEvent = async (eventId: string, userId: string) => {
    void userId;
    const result = await joinEventRequest(eventId);
    replaceEvent(result.event);
    return result.waitlisted;
  };

  const leaveEvent = async (eventId: string, userId: string) => {
    const event = state.events.find(candidate => candidate.id === eventId);
    if (event?.organizerId === userId) {
      showToast('O organizador não pode sair do evento.', 'warning');
      return;
    }
    const result = await leaveEventRequest(eventId);
    replaceEvent(result.event);
  };

  // The critical conversion workflow: Event -> Session
  const completeEvent = async (
    eventId: string,
    winnerId: string | null,
    duration: number,
    notes: string,
    initialComment?: string,
    photoUrl?: string
  ) => {
    const sourceEvent = state.events.find(event => event.id === eventId);
    if (!sourceEvent) return;
    const completed = await completeEventRequest(eventId, {
      winnerId, duration, notes, initialComment, photoUrl,
    });
    replaceEvent(completed);
    const refreshedSessions = await getSessions();
    setState(prev => syncDatabaseCalculations({ ...prev, sessions: refreshedSessions }));
  };

  const addUser = (userData: Omit<User, 'id' | 'avatar' | 'winCount' | 'favoriteGames' | 'joinedAt' | 'bio'> & { id?: string; passwordHash?: string; avatar?: string; course?: string; bio?: string; role?: UserRole; }) => {
    const normalizedEmail = userData.email.trim().toLowerCase();
    if (state.users.some(u => u.email.toLowerCase() === normalizedEmail)) {
      return null;
    }

    const initials = userData.name
      .split(' ')
      .map(part => part[0])
      .join('')
      .slice(0, 2)
      .toUpperCase() || 'U';

    const newUser: User = {
      id: userData.id || generateId('u'),
      name: userData.name.trim(),
      email: normalizedEmail,
      ...(userData.passwordHash ? { passwordHash: userData.passwordHash } : {}),
      role: userData.role || 'student',
      course: userData.course || 'Sem curso informado',
      avatar: userData.avatar || initials,
      winCount: 0,
      favoriteGames: [],
      joinedAt: new Date().toISOString(),
      bio: userData.bio || 'Novo membro do Tabula.'
    };

    setState(prev => ({
      ...prev,
      users: [...prev.users, newUser]
    }));

    return newUser;
  };

  // User Administration
  const deleteUser = (userId: string) => {
    setState(prev => {
      const updated = {
        ...prev,
        users: prev.users.filter(u => u.id !== userId)
      };
      return syncDatabaseCalculations(updated);
    });
  };

  const editUser = async (userId: string, updates: Pick<Partial<User>, 'name' | 'course' | 'bio' | 'avatarUrl'>) => {
    const user = state.users.find(candidate => candidate.id === userId);
    if (!user) throw new Error('Profile not found');
    const saved = await updateProfile({
      name: updates.name ?? user.name,
      course: updates.course ?? user.course,
      bio: updates.bio ?? user.bio,
      avatarUrl: 'avatarUrl' in updates ? updates.avatarUrl : user.avatarUrl,
    });
    setState(prev => ({ ...prev, users: prev.users.map(candidate => candidate.id === saved.id
      ? { ...candidate, ...saved }
      : candidate) }));
  };

  const promoteUser = (userId: string) => {
    setState(prev => {
      const user = prev.users.find(u => u.id === userId);
      const newRole: UserRole = user?.role === 'admin' ? 'student' : 'admin';
      
      const updated = {
        ...prev,
        users: prev.users.map(u => {
          if (u.id !== userId) return u;
          return { ...u, role: newRole };
        })
      };
      return syncDatabaseCalculations(updated);
    });
  };

  return (
    <DatabaseContext.Provider
      value={{
        state,
        addGame,
        editGame,
        deleteGame,
        addSession,
        deleteSession,
        getSessionById,
        addComment,
        deleteComment,
        addFavorite,
        removeFavorite,
        addEvent,
        joinEvent,
        leaveEvent,
        completeEvent,
        addUser,
        deleteUser,
        promoteUser,
        editUser
      }}
    >
      {children}
    </DatabaseContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useDatabase = () => {
  const context = useContext(DatabaseContext);
  if (!context) {
    throw new Error('useDatabase must be used within a DatabaseProvider');
  }
  return context;
};
