export type UserRole = 'student' | 'admin';

export interface User {
  id: string;
  name: string;
  email: string;
  passwordHash?: string;
  role: UserRole;
  course: string;
  avatar: string; // Emoji ou nome simples de ícone/imagem
  avatarUrl?: string; // URL opcional da foto de perfil
  winCount: number;
  favoriteGames: string[]; // IDs dos jogos de tabuleiro
  joinedAt: string; // Data no formato ISO
  bio: string;
}

export interface BoardGame {
  id: string;
  name: string;
  description: string;
  coverUrl: string; // Referência para uma URL local ou pública
  category: string; // ex.: "Estratégia", "Cooperativo", "Party"
  minPlayers: number;
  maxPlayers: number;
  avgPlayTime: number; // em minutos
  complexity: number; // 1 a 5
}

export interface Comment {
  id: string;
  userId: string;
  userName: string;
  userAvatar: string;
  content: string;
  createdAt: string; // Data no formato ISO
}

export interface Session {
  id: string;
  gameId: string;
  date: string; // Data no formato ISO
  location: string;
  organizerId: string;
  participantIds: string[];
  winnerId: string | null; // ID do usuário
  duration: number; // em minutos
  notes: string;
  photos: string[]; // URLs de imagens ou marcadores de posição
  comments: Comment[];
}

export interface Event {
  id: string;
  gameId: string;
  date: string; // YYYY-MM-DD
  time: string; // HH:MM
  location: string;
  maxParticipants: number;
  participantIds: string[];
  waitingListIds: string[];
  description: string;
  organizerId: string;
  status: 'active' | 'completed' | 'cancelled';
}

export interface DatabaseState {
  users: User[];
  boardGames: BoardGame[];
  sessions: Session[];
  events: Event[];
}
