import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Games } from '../pages/Games';
import { renderWithProviders } from './renderWithProviders';
import { getTestDatabaseState } from './testState';
import * as api from '../services/api';

vi.mock('../services/api', async importOriginal => {
  const original = await importOriginal<typeof import('../services/api')>();
  return {
    ...original,
    getServerState: vi.fn(),
    createGame: vi.fn(async game => ({ id: 'g_created', ...game })),
    updateGame: vi.fn(async game => game),
    deleteGameRequest: vi.fn(async () => undefined),
    addFavoriteRequest: vi.fn(async () => ({ gameId: 'g1' })),
    removeFavoriteRequest: vi.fn(async () => undefined),
  };
});

describe('Games page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem('tabula_auth_token', 'token');
    localStorage.setItem('tabula_auth_session', 'u1');
    vi.mocked(api.getServerState).mockResolvedValue(getTestDatabaseState());
  });

  it('renders the favorite control for the authenticated user', async () => {
    renderWithProviders(<Games />);
    expect(await screen.findByRole('button', { name: /Favoritar Xadrez/i })).toBeInTheDocument();
  });

  it('filters the catalog by search text', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Games />);

    expect(screen.getByRole('heading', { name: /Acervo de Jogos de Mesa/i })).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText(/Pesquisar por nome ou descrição/i), 'Xadrez');

    expect(screen.getByText(/Xadrez/i)).toBeInTheDocument();
    expect(screen.queryByText(/Magic: The Gathering/i)).not.toBeInTheDocument();
  });

  it('supports add, edit, delete and details flows for an admin', async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderWithProviders(<Games />, { route: '/games?id=g1' });

    expect(await screen.findByRole('heading', { name: /Xadrez/i, level: 3 })).toBeInTheDocument();

    await user.click(await screen.findByRole('button', { name: /Adicionar Novo Jogo/i }));
    const modal = screen.getByRole('heading', { name: /Adicionar Novo Jogo ao Acervo/i }).closest('.modal-content');
    if (!modal) throw new Error('Modal not found');
    const addInputs = within(modal).getAllByRole('textbox');
    await user.type(addInputs[0], 'Catan');
    await user.type(addInputs[1], 'Jogo de estratégia');
    await user.click(within(modal).getByRole('button', { name: /Adicionar Jogo/i }));

    expect(screen.getByRole('heading', { name: /Catan/i, level: 3 })).toBeInTheDocument();

    await user.click(screen.getAllByRole('button', { name: /Editar/i })[0]);
    const editModal = screen.getByRole('heading', { name: /Editar Jogo:/i }).closest('.modal-content');
    if (!editModal) throw new Error('Edit modal not found');
    const editInputs = within(editModal).getAllByRole('textbox');
    await user.clear(editInputs[0]);
    await user.type(editInputs[0], 'Catan editado');
    await user.click(within(editModal).getByRole('button', { name: /Salvar Alterações/i }));

    expect(screen.getByRole('heading', { name: /Catan editado/i, level: 3 })).toBeInTheDocument();

    await user.click(screen.getAllByRole('button', { name: /Excluir/i })[screen.getAllByRole('button', { name: /Excluir/i }).length - 1]);
    expect(confirmSpy).toHaveBeenCalled();
  });
});
