import type { User, BoardGame, Session, Event } from '../types';

export const INITIAL_USERS: User[] = [
  {
    id: 'u_admin',
    name: 'Administrador Tabula',
    email: 'admin@tabula.com',
    role: 'admin',
    course: 'Administração do clube',
    avatar: 'AD',
    winCount: 0,
    favoriteGames: [],
    joinedAt: '2026-01-01T00:00:00Z',
    bio: 'Conta administrativa oficial do Tabula.'
  },
  {
    id: 'u1',
    name: 'Cauã Botelho',
    email: 'caua.botelho@universidade.edu.br',
    role: 'admin',
    course: 'Ciência da Computação',
    avatar: 'CB',
    winCount: 12,
    favoriteGames: ['g1', 'g2'],
    joinedAt: '2026-01-15T10:00:00Z',
    bio: 'Entusiasta de Eurogames pesados e otimizador de turnos profissional. Se o jogo tem mais de 3 páginas de regras, eu topo.'
  },
  {
    id: 'u2',
    name: 'Mariana Souza',
    email: 'mariana.souza@universidade.edu.br',
    role: 'student',
    course: 'Design Digital',
    avatar: 'MS',
    winCount: 8,
    favoriteGames: ['g3', 'g1'],
    joinedAt: '2026-02-10T14:30:00Z',
    bio: 'Amo jogos festivos e com belas artes. Dixit é minha paixão. Se o jogo for bonito, eu já quero jogar!'
  },
  {
    id: 'u3',
    name: 'Lucas Lima',
    email: 'lucas.lima@universidade.edu.br',
    role: 'student',
    course: 'Engenharia de Produção',
    avatar: 'LL',
    winCount: 15,
    favoriteGames: ['g1', 'g2'],
    joinedAt: '2026-02-22T09:15:00Z',
    bio: 'Estrategista competitivo. Minhas jogadas em Magic: The Gathering são lendárias (ou infames, dependendo de quem você perguntar).'
  },
  {
    id: 'u4',
    name: 'Beatriz Santos',
    email: 'beatriz.santos@universidade.edu.br',
    role: 'student',
    course: 'Arquitetura',
    avatar: 'BS',
    winCount: 6,
    favoriteGames: ['g1'],
    joinedAt: '2026-03-01T16:45:00Z',
    bio: 'Especialista em posicionamento e leitura de tabuleiro. Adoro explorar as melhores jogadas em Xadrez e Pokémon TCG.'
  },
  {
    id: 'u5',
    name: 'Gabriel Ramos',
    email: 'gabriel.ramos@universidade.edu.br',
    role: 'student',
    course: 'Matemática',
    avatar: 'GR',
    winCount: 10,
    favoriteGames: ['g2', 'g1'],
    joinedAt: '2026-03-12T11:00:00Z',
    bio: 'Calculo probabilidades até nas decisões de mana e nos ataques de Pokémon TCG. Nada na minha jogada é por acaso!'
  }
];

export const INITIAL_GAMES: BoardGame[] = [
  {
    id: 'g1',
    name: 'Xadrez',
    description: 'Um clássico duelo de estratégia pura, com planejamento, cálculo de movimentos e leitura da posição do adversário.',
    coverUrl: '/images/chess_cover.png',
    category: 'Estratégia',
    minPlayers: 2,
    maxPlayers: 2,
    avgPlayTime: 45,
    complexity: 2.1
  },
  {
    id: 'g2',
    name: 'Magic: The Gathering',
    description: 'Jogo de cartas estratégico com mana, combinação de baralhos e decisões de tempo que definem a partida.',
    coverUrl: '/images/magic_cover.png',
    category: 'Cartas',
    minPlayers: 2,
    maxPlayers: 4,
    avgPlayTime: 60,
    complexity: 3.2
  },
  {
    id: 'g3',
    name: 'Pokémon TCG',
    description: 'Um jogo de cartas dinâmico e divertido, onde cada duelo combina estratégia, coleção e jogadas de ataque.',
    coverUrl: '/images/pokemon_cover.png',
    category: 'Cartas',
    minPlayers: 2,
    maxPlayers: 4,
    avgPlayTime: 40,
    complexity: 2.0
  }
];

export const INITIAL_SESSIONS: Session[] = [
  {
    id: 's1',
    gameId: 'g1', // Catan
    date: '2026-05-15T18:00:00Z',
    location: 'Vivência do Bloco C',
    organizerId: 'u1',
    participantIds: ['u1', 'u3', 'u5'],
    winnerId: 'u3', // Lucas Lima
    duration: 95,
    notes: 'Partida de Xadrez com muita tensão e cálculo fino. A última sequência de movimentos do Lucas virou a partida e fechou a vitória em cima da hora.',
    photos: ['https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?w=600&auto=format&fit=crop&q=80'],
    comments: [
      {
        id: 'c1_1',
        userId: 'u5',
        userName: 'Gabriel Ramos',
        userAvatar: '📊',
        content: 'Aquela estrada do Lucas foi pura sorte com os dados! Na próxima eu calculo melhor o bloqueio.',
        createdAt: '2026-05-15T21:10:00Z'
      },
      {
        id: 'c1_2',
        userId: 'u3',
        userName: 'Lucas Lima',
        userAvatar: '⚙️',
        content: 'Chama de sorte, eu chamo de visão de tabuleiro aplicada ao Xadrez! ♟️',
        createdAt: '2026-05-15T21:30:00Z'
      }
    ]
  },
  {
    id: 's2',
    gameId: 'g3', // Pokémon TCG
    date: '2026-05-20T17:30:00Z',
    location: 'Diretório Acadêmico (D.A.) da Tecnologia',
    organizerId: 'u2',
    participantIds: ['u1', 'u2', 'u4', 'u5'],
    winnerId: 'u2', // Mariana Souza
    duration: 40,
    notes: 'Tarde de muitas risadas em uma partida de Pokémon TCG. As dicas da Mariana sobre "café frio da faculdade" confundiram quase todo mundo e garantiram a sua vitória.',
    photos: ['https://images.unsplash.com/photo-1543536448-d209d2d13a1c?w=600&auto=format&fit=crop&q=80'],
    comments: [
      {
        id: 'c2_1',
        userId: 'u4',
        userName: 'Beatriz Santos',
        userAvatar: '🏰',
        content: 'A carta do café frio foi genial! Identificação imediata de todos os alunos kkkkk',
        createdAt: '2026-05-20T19:00:00Z'
      }
    ]
  },
  {
    id: 's3',
    gameId: 'g2', // Magic: The Gathering
    date: '2026-05-28T19:00:00Z',
    location: 'Biblioteca - Sala de Estudos 04',
    organizerId: 'u4',
    participantIds: ['u2', 'u4', 'u5'],
    winnerId: 'u4', // Beatriz Santos
    duration: 50,
    notes: 'Partida intensa de Magic: The Gathering com muita presença de mana e escolhas de cartas. Beatriz fechou a vitória com uma sequência de combinações muito bem planejada.',
    photos: ['https://images.unsplash.com/photo-1629895015647-7ee96593a195?w=600&auto=format&fit=crop&q=80'],
    comments: []
  }
];

export const INITIAL_EVENTS: Event[] = [
  {
    id: 'e1',
    gameId: 'g2', // Magic: The Gathering
    date: '2026-06-12', // Evento futuro
    time: '18:00',
    location: 'Laboratório de Design (Sala 204 - Bloco D)',
    maxParticipants: 5,
    participantIds: ['u1', 'u5'], // Cauã e Gabriel
    waitingListIds: [],
    description: 'Mesa de Magic: The Gathering para quem gosta de baralhos, mana e decisões de alto impacto. Começaremos pontualmente às 18:00.',
    organizerId: 'u1', // Cauã Botelho
    status: 'active'
  },
  {
    id: 'e2',
    gameId: 'g3', // Pokémon TCG
    date: '2026-06-15',
    time: '14:00',
    location: 'Área Aberta do Bloco A',
    maxParticipants: 4,
    participantIds: ['u3', 'u2', 'u4', 'u5'], // Fully booked!
    waitingListIds: [],
    description: 'Encontro de Pokémon TCG na área externa do Bloco A. Perfeito para relaxar após as provas da semana com duelos rápidos.',
    organizerId: 'u3', // Lucas Lima
    status: 'active'
  },
  {
    id: 'e3',
    gameId: 'g1', // Catan
    date: '2026-06-18',
    time: '19:00',
    location: 'Vivência do Bloco C',
    maxParticipants: 4,
    participantIds: ['u1', 'u3', 'u4'],
    waitingListIds: [],
    description: 'Treino de Xadrez para o campeonato interno do clube. Iniciantes são bem-vindos para assistir ou jogar se sobrar vaga!',
    organizerId: 'u1',
    status: 'active'
  }
];
