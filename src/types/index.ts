export type UserRole = 'student' | 'admin';

export interface User {
  id: string;
  name: string;
  email: string;
  passwordHash?: string;
  role: UserRole;
  course: string;
  avatar: string; // Emoji or simple icon/image name
  avatarUrl?: string; // Optional profile photo URL
  winCount: number;
  favoriteGames: string[]; // BoardGame IDs
  joinedAt: string; // ISO date string
  bio: string;
}

export interface BoardGame {
  id: string;
  name: string;
  description: string;
  coverUrl: string; // Reference to a local or public URL
  category: string; // e.g. "Estratégia", "Cooperativo", "Party"
  minPlayers: number;
  maxPlayers: number;
  avgPlayTime: number; // in minutes
  complexity: number; // 1 to 5
}

export interface Comment {
  id: string;
  userId: string;
  userName: string;
  userAvatar: string;
  content: string;
  createdAt: string; // ISO date string
}

export interface Session {
  id: string;
  gameId: string;
  date: string; // ISO date string
  location: string;
  organizerId: string;
  participantIds: string[];
  winnerId: string | null; // User ID
  duration: number; // in minutes
  notes: string;
  photos: string[]; // Image URLs or placeholders
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

export interface ActivityLog {
  id: string;
  userId: string;
  userName: string;
  action: string;
  timestamp: string; // ISO date string
}

export interface DatabaseState {
  users: User[];
  boardGames: BoardGame[];
  sessions: Session[];
  events: Event[];
  logs: ActivityLog[];
}
