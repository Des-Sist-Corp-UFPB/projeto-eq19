import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Games } from '../pages/Games';
import { renderWithProviders } from './renderWithProviders';

describe('Games page', () => {
  beforeEach(() => {
    localStorage.setItem('tabula_auth_token', 'token');
    localStorage.setItem('tabula_auth_session', 'u1');
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

    expect(screen.getByRole('heading', { name: /Xadrez/i, level: 3 })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Adicionar Novo Jogo/i }));
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
