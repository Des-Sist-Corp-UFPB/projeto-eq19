import React, { createContext, useContext, useState, useEffect } from 'react';
import type { DatabaseState, BoardGame, Session, Event, Comment, User, UserRole } from '../types';
import { getInitialDatabaseState, saveDatabaseState, syncDatabaseCalculations, createLog, normalizeGameCoverUrl } from '../db/database';
import { useToast } from './ToastContext';

const generateId = (prefix: string) => {
  const randomId = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
  return `${prefix}_${randomId}`;
};

interface DatabaseContextType {
  state: DatabaseState;
  addGame: (game: Omit<BoardGame, 'id'>) => void;
  addUser: (user: Omit<User, 'id' | 'avatar' | 'winCount' | 'favoriteGames' | 'joinedAt' | 'bio'> & { passwordHash: string; avatar?: string; course?: string; bio?: string; role?: UserRole; }) => User | null;
  editGame: (game: BoardGame) => void;
  deleteGame: (gameId: string) => void;
  addSession: (session: Omit<Session, 'id' | 'comments'>, initialComment?: string) => void;
  deleteSession: (sessionId: string) => void;
  addComment: (sessionId: string, userId: string, content: string) => void;
  addEvent: (event: Omit<Event, 'id' | 'participantIds' | 'waitingListIds' | 'status' | 'organizerId'>, organizerId: string) => void;
  joinEvent: (eventId: string, userId: string) => void;
  leaveEvent: (eventId: string, userId: string) => void;
  completeEvent: (
    eventId: string,
    winnerId: string | null,
    duration: number,
    notes: string,
    initialComment?: string,
    photoUrl?: string
  ) => void;
  deleteUser: (userId: string) => void;
  promoteUser: (userId: string) => void;
  editUser: (userId: string, updates: Partial<User>) => void;
}

const DatabaseContext = createContext<DatabaseContextType | undefined>(undefined);

export const DatabaseProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [state, setState] = useState<DatabaseState>(getInitialDatabaseState);
  const { showToast } = useToast();

  // Auto-save state to localStorage whenever it changes
  useEffect(() => {
    saveDatabaseState(state);
  }, [state]);

  // Game actions
  const addGame = (game: Omit<BoardGame, 'id'>) => {
    const id = generateId('g');
    const newGame: BoardGame = { ...game, id, coverUrl: normalizeGameCoverUrl(game.name, game.coverUrl) };
    
    setState(prev => {
      const updated = {
        ...prev,
        boardGames: [...prev.boardGames, newGame],
        logs: createLog(prev, 'system', 'Administração', `adicionou o jogo "${game.name}" ao acervo`)
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
        boardGames: prev.boardGames.map(g => g.id === sanitizedGame.id ? sanitizedGame : g),
        logs: createLog(prev, 'system', 'Administração', `editou as informações do jogo "${updatedGame.name}"`)
      };
      return syncDatabaseCalculations(updated);
    });
  };

  const deleteGame = (gameId: string) => {
    const game = state.boardGames.find(g => g.id === gameId);
    const hasLinkedRecords = state.sessions.some(s => s.gameId === gameId) || state.events.some(e => e.gameId === gameId);

    if (hasLinkedRecords) {
      showToast('Não é possível remover este jogo porque ele está vinculado a sessões ou eventos.', 'warning');
      return;
    }

    setState(prev => {
      const updated = {
        ...prev,
        boardGames: prev.boardGames.filter(g => g.id !== gameId),
        logs: createLog(prev, 'system', 'Administração', `removeu o jogo "${game?.name || 'Desconhecido'}" do acervo`)
      };
      return syncDatabaseCalculations(updated);
    });
  };

  // Session actions
  const addSession = (sessionData: Omit<Session, 'id' | 'comments'>, initialComment?: string) => {
    const sessionId = generateId('s');
    const organizer = state.users.find(u => u.id === sessionData.organizerId);
    const game = state.boardGames.find(g => g.id === sessionData.gameId);
    
    const comments: Comment[] = [];
    if (initialComment && organizer) {
      comments.push({
        id: generateId('c'),
        userId: organizer.id,
        userName: organizer.name,
        userAvatar: organizer.avatar,
        content: initialComment,
        createdAt: new Date().toISOString()
      });
    }

    const newSession: Session = {
      ...sessionData,
      id: sessionId,
      comments
    };

    setState(prev => {
      const updated = {
        ...prev,
        sessions: [newSession, ...prev.sessions],
        logs: createLog(prev, sessionData.organizerId, organizer?.name || 'Usuário', `registrou uma partida de "${game?.name || 'um jogo'}" em ${sessionData.location}`)
      };
      return syncDatabaseCalculations(updated);
    });
  };

  const deleteSession = (sessionId: string) => {
    const session = state.sessions.find(s => s.id === sessionId);
    const game = state.boardGames.find(g => g.id === session?.gameId);
    setState(prev => {
      const updated = {
        ...prev,
        sessions: prev.sessions.filter(s => s.id !== sessionId),
        logs: createLog(prev, 'system', 'Administração', `removeu a sessão de "${game?.name || 'Desconhecido'}" jogada em ${session?.date ? new Date(session.date).toLocaleDateString('pt-BR') : ''}`)
      };
      return syncDatabaseCalculations(updated);
    });
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
        }),
        logs: createLog(prev, userId, user.name, `comentou na partida registrada de id #${sessionId.substring(2, 6)}`)
      };
    });
  };

  // Event actions
  const addEvent = (eventData: Omit<Event, 'id' | 'participantIds' | 'waitingListIds' | 'status' | 'organizerId'>, organizerId: string) => {
    const eventId = generateId('e');
    const game = state.boardGames.find(g => g.id === eventData.gameId);
    const organizer = state.users.find(u => u.id === organizerId);

    const newEvent: Event = {
      ...eventData,
      id: eventId,
      organizerId,
      participantIds: [organizerId], // Organizer is automatically registered
      waitingListIds: [],
      status: 'active'
    };

    setState(prev => {
      const updated = {
        ...prev,
        events: [newEvent, ...prev.events],
        logs: createLog(prev, organizerId, organizer?.name || 'Organizador', `agendou um evento de "${game?.name || 'Jogo'}" para o dia ${new Date(eventData.date).toLocaleDateString('pt-BR')}`)
      };
      return syncDatabaseCalculations(updated);
    });
  };

  const joinEvent = (eventId: string, userId: string) => {
    const user = state.users.find(u => u.id === userId);
    if (!user) return;

    setState(prev => {
      const eventIndex = prev.events.findIndex(e => e.id === eventId);
      if (eventIndex === -1) return prev;

      const event = prev.events[eventIndex];
      const isAlreadyParticipant = event.participantIds.includes(userId);
      const isAlreadyWaiting = event.waitingListIds.includes(userId);

      if (event.status !== 'active' || isAlreadyParticipant || isAlreadyWaiting) return prev;

      const updatedParticipants = [...event.participantIds];
      const updatedWaitingList = [...event.waitingListIds];

      if (event.participantIds.length < event.maxParticipants) {
        updatedParticipants.push(userId);
      } else {
        updatedWaitingList.push(userId);
      }

      const actionMsg = event.participantIds.length < event.maxParticipants
        ? `inscreveu-se no evento de "${prev.boardGames.find(g => g.id === event.gameId)?.name || 'Jogo'}"`
        : `entrou na lista de espera do evento de "${prev.boardGames.find(g => g.id === event.gameId)?.name || 'Jogo'}"`;

      const updatedEvents = [...prev.events];
      updatedEvents[eventIndex] = {
        ...event,
        participantIds: updatedParticipants,
        waitingListIds: updatedWaitingList
      };

      return {
        ...prev,
        events: updatedEvents,
        logs: createLog(prev, userId, user.name, actionMsg)
      };
    });
  };

  const leaveEvent = (eventId: string, userId: string) => {
    const user = state.users.find(u => u.id === userId);
    if (!user) return;

    const event = state.events.find(e => e.id === eventId);
    if (event?.organizerId === userId) {
      showToast('O organizador não pode sair do evento.', 'warning');
      return;
    }

    setState(prev => {
      const eventIndex = prev.events.findIndex(e => e.id === eventId);
      if (eventIndex === -1) return prev;

      const event = prev.events[eventIndex];

      if (event.organizerId === userId) {
        return prev;
      }

      const updatedParticipants = event.participantIds.filter(id => id !== userId);
      const updatedWaitingList = event.waitingListIds.filter(id => id !== userId);
      const logMsg = `cancelou sua participação no evento de "${prev.boardGames.find(g => g.id === event.gameId)?.name || 'Jogo'}"`;

      const updatedEvents = [...prev.events];

      // If a slot opened in the main participants list and someone was in the waiting list
      if (
        event.participantIds.includes(userId) &&
        updatedParticipants.length < event.maxParticipants &&
        updatedWaitingList.length > 0
      ) {
        const nextUser = updatedWaitingList[0];
        if (nextUser) {
          const promotedWaitingList = updatedWaitingList.slice(1);
          updatedParticipants.push(nextUser);
          updatedEvents[eventIndex] = {
            ...event,
            participantIds: updatedParticipants,
            waitingListIds: promotedWaitingList
          };

          const nextUserData = prev.users.find(u => u.id === nextUser);
          if (nextUserData) {
            return {
              ...prev,
              events: updatedEvents,
              logs: createLog(prev, nextUser, nextUserData.name, `saiu da lista de espera e entrou nas vagas principais do evento de "${prev.boardGames.find(g => g.id === event.gameId)?.name || 'Jogo'}"`)
            };
          }
        }
      }

      updatedEvents[eventIndex] = {
        ...event,
        participantIds: updatedParticipants,
        waitingListIds: updatedWaitingList
      };

      return {
        ...prev,
        events: updatedEvents,
        logs: createLog(prev, userId, user.name, logMsg)
      };
    });
  };

  // The critical conversion workflow: Event -> Session
  const completeEvent = (
    eventId: string,
    winnerId: string | null,
    duration: number,
    notes: string,
    initialComment?: string,
    photoUrl?: string
  ) => {
    const event = state.events.find(e => e.id === eventId);
    if (!event || event.status !== 'active') return;

    const game = state.boardGames.find(g => g.id === event.gameId);
    const organizer = state.users.find(u => u.id === event.organizerId);

    // 1. Create comments array
    const comments: Comment[] = [];
    if (initialComment && organizer) {
      comments.push({
        id: generateId('c'),
        userId: organizer.id,
        userName: organizer.name,
        userAvatar: organizer.avatar,
        content: initialComment,
        createdAt: new Date().toISOString()
      });
    }

    // 2. Create the historical session
    const sessionId = generateId('s');
    const newSession: Session = {
      id: sessionId,
      gameId: event.gameId,
      date: `${event.date}T${event.time}:00`,
      location: event.location,
      organizerId: event.organizerId,
      participantIds: event.participantIds,
      winnerId,
      duration,
      notes,
      photos: photoUrl ? [photoUrl] : [],
      comments
    };

    setState(prev => {
      // 3. Mark the event as completed
      const updatedEvents = prev.events.map(e =>
        e.id === eventId ? { ...e, status: 'completed' as const } : e
      );

      const updated = {
        ...prev,
        sessions: [newSession, ...prev.sessions],
        events: updatedEvents,
        logs: createLog(
          prev,
          event.organizerId,
          organizer?.name || 'Organizador',
          `concluiu o evento de "${game?.name || 'Jogo'}" e registrou a partida histórica correspondente`
        )
      };

      // Recalculates wins/favorites
      return syncDatabaseCalculations(updated);
    });
  };

  const addUser = (userData: Omit<User, 'id' | 'avatar' | 'winCount' | 'favoriteGames' | 'joinedAt' | 'bio'> & { passwordHash: string; avatar?: string; course?: string; bio?: string; role?: UserRole; }) => {
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
      id: generateId('u'),
      name: userData.name.trim(),
      email: normalizedEmail,
      passwordHash: userData.passwordHash,
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
      users: [...prev.users, newUser],
      logs: createLog(prev, 'system', 'Administração', `criou a conta de "${newUser.name}"`)
    }));

    return newUser;
  };

  // User Administration
  const deleteUser = (userId: string) => {
    const user = state.users.find(u => u.id === userId);
    setState(prev => {
      const updated = {
        ...prev,
        users: prev.users.filter(u => u.id !== userId),
        logs: createLog(prev, 'system', 'Administração', `removeu o perfil do estudante "${user?.name || 'Desconhecido'}"`)
      };
      return syncDatabaseCalculations(updated);
    });
  };

  const editUser = (userId: string, updates: Partial<User>) => {
    setState(prev => {
      const target = prev.users.find(u => u.id === userId);
      const updated = {
        ...prev,
        users: prev.users.map(u => u.id === userId ? { ...u, ...updates } : u),
        logs: createLog(prev, 'system', 'Administração', `atualizou o perfil de "${target?.name || 'Usuário'}"`)
      };
      return syncDatabaseCalculations(updated);
    });
  };

  const promoteUser = (userId: string) => {
    setState(prev => {
      const user = prev.users.find(u => u.id === userId);
      const newRole: UserRole = user?.role === 'admin' ? 'student' : 'admin';
      const actionName = newRole === 'admin' ? 'promoveu a Administrador' : 'rebaixou a Estudante';
      
      const updated = {
        ...prev,
        users: prev.users.map(u => {
          if (u.id !== userId) return u;
          return { ...u, role: newRole };
        }),
        logs: createLog(prev, 'system', 'Administração', `${actionName} o estudante "${user?.name || 'Desconhecido'}"`)
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
