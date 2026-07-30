import React, { createContext, useContext, useState, useEffect, useRef } from 'react';
import type { DatabaseState, BoardGame, Session, Event, Comment, User, UserRole } from '../types';
import { getDefaultDatabaseState, sanitizeDatabaseState, syncDatabaseCalculations, normalizeGameCoverUrl } from '../db/database';
import {
  completeEventRequest,
  createSession,
  createEvent,
  deleteSessionRequest,
  getEvents,
  getSessions,
  getServerState,
  joinEventRequest,
  leaveEventRequest,
  saveServerState,
} from '../services/api';
import { useToast } from './ToastContext';

const generateId = (prefix: string) => {
  const randomId = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
  return `${prefix}_${randomId}`;
};

const serializeLegacyState = (state: DatabaseState) => JSON.stringify({
  users: state.users,
  boardGames: state.boardGames,
});

interface DatabaseContextType {
  state: DatabaseState;
  addGame: (game: Omit<BoardGame, 'id'>) => void;
  addUser: (user: Omit<User, 'id' | 'avatar' | 'winCount' | 'favoriteGames' | 'joinedAt' | 'bio'> & { id?: string; passwordHash?: string; avatar?: string; course?: string; bio?: string; role?: UserRole; }) => User | null;
  editGame: (game: BoardGame) => void;
  deleteGame: (gameId: string) => void;
  addSession: (session: Omit<Session, 'id' | 'comments'>, initialComment?: string) => Promise<Session>;
  deleteSession: (sessionId: string) => Promise<void>;
  addComment: (sessionId: string, userId: string, content: string) => void;
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
  editUser: (userId: string, updates: Partial<User>) => void;
}

const DatabaseContext = createContext<DatabaseContextType | undefined>(undefined);

export const DatabaseProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [state, setState] = useState<DatabaseState>(getDefaultDatabaseState);
  const { showToast } = useToast();
  const serverLoadedRef = useRef(false);
  const saveTimerRef = useRef<number | null>(null);
  const lastSavedJsonRef = useRef<string | null>(null);

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
        setState(nextState);
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
  const addGame = (game: Omit<BoardGame, 'id'>) => {
    const id = generateId('g');
    const newGame: BoardGame = { ...game, id, coverUrl: normalizeGameCoverUrl(game.name, game.coverUrl) };
    
    setState(prev => {
      const updated = {
        ...prev,
        boardGames: [...prev.boardGames, newGame]
      };
      return syncDatabaseCalculations(updated);
    });
  };

  const editGame = (updatedGame: BoardGame) => {
    setState(prev => {
      const sanitizedGame = {
        ...updatedGame,
        coverUrl: normalizeGameCoverUrl(updatedGame.name, updatedGame.coverUrl)
      };

      const updated = {
        ...prev,
        boardGames: prev.boardGames.map(g => g.id === sanitizedGame.id ? sanitizedGame : g)
      };
      return syncDatabaseCalculations(updated);
    });
  };

  const deleteGame = (gameId: string) => {
    const hasLinkedRecords = state.sessions.some(s => s.gameId === gameId) || state.events.some(e => e.gameId === gameId);

    if (hasLinkedRecords) {
      showToast('Não é possível remover este jogo porque ele está vinculado a sessões ou eventos.', 'warning');
      return;
    }

    setState(prev => {
      const updated = {
        ...prev,
        boardGames: prev.boardGames.filter(g => g.id !== gameId)
      };
      return syncDatabaseCalculations(updated);
    });
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

  const addComment = (sessionId: string, userId: string, content: string) => {
    const user = state.users.find(u => u.id === userId);
    if (!user) return;

    const newComment: Comment = {
      id: generateId('c'),
      userId,
      userName: user.name,
      userAvatar: user.avatar,
      content,
      createdAt: new Date().toISOString()
    };

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

  const editUser = (userId: string, updates: Partial<User>) => {
    setState(prev => {
      const updated = {
        ...prev,
        users: prev.users.map(u => u.id === userId ? { ...u, ...updates } : u)
      };
      return syncDatabaseCalculations(updated);
    });
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
        addComment,
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
