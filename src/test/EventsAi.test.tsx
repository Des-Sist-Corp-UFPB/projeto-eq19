import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Events } from '../pages/Events';
import { renderWithProviders } from './renderWithProviders';
import { ApiError } from '../services/api';
import * as api from '../services/api';
import { getDefaultDatabaseState } from '../db/database';

vi.mock('../services/api', async importOriginal => {
  const original = await importOriginal<typeof import('../services/api')>();
  return {
    ...original,
    getServerState: vi.fn(),
    saveServerState: vi.fn(),
    generateEventDraft: vi.fn(),
  };
});

describe('Events AI draft assistant', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    sessionStorage.setItem('tabula_auth_token', 'session-token');
    sessionStorage.setItem('tabula_auth_session', 'u1');
    vi.mocked(api.getServerState).mockResolvedValue(getDefaultDatabaseState());
    vi.mocked(api.saveServerState).mockResolvedValue();
    vi.mocked(api.generateEventDraft).mockReset();
  });

  it('disables empty prompt, blocks duplicate calls and fills editable fields without saving', async () => {
    let resolveDraft!: (value: api.AiEventDraftResponse) => void;
    vi.mocked(api.generateEventDraft).mockReturnValue(new Promise(resolve => { resolveDraft = resolve; }));
    renderWithProviders(<Events />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Agendar Novo Encontro/i }));

    const generate = screen.getByRole('button', { name: /Preencher formulário com IA/i });
    expect(generate).toBeDisabled();
    await user.type(screen.getByLabelText(/Descreva o encontro/i), 'Mesa de Magic sábado');
    await user.click(generate);
    expect(api.generateEventDraft).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: /Gerando rascunho/i })).toBeDisabled();
    expect(api.generateEventDraft).toHaveBeenCalledTimes(1);

    resolveDraft({
      gameId: 'g2', gameName: 'Magic: The Gathering', date: '2026-08-01', time: '18:00',
      location: 'Biblioteca', maxParticipants: 4, description: 'Mesa aberta.',
      warnings: ['A data foi interpretada como o próximo sábado.'],
    });
    await waitFor(() => expect(screen.getByDisplayValue('Biblioteca')).toBeInTheDocument());
    expect(screen.getByDisplayValue('Mesa aberta.')).not.toBeDisabled();
    expect(screen.getByText(/próximo sábado/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Agendar Encontro de Jogo/i })).toBeInTheDocument();
    expect(api.saveServerState).not.toHaveBeenCalledWith(expect.objectContaining({
      events: expect.arrayContaining([expect.objectContaining({ description: 'Mesa aberta.' })]),
    }));
  });

  it('preserves prompt and fields and shows a friendly session error', async () => {
    vi.mocked(api.generateEventDraft).mockRejectedValue(
      new ApiError(401, 'internal detail', 'UNAUTHORIZED', '{"error":"internal detail"}'),
    );
    renderWithProviders(<Events />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Agendar Novo Encontro/i }));
    const prompt = screen.getByLabelText(/Descreva o encontro/i);
    const location = screen.getByLabelText(/Local de Encontro/i);
    await user.clear(location);
    await user.type(location, 'Meu local');
    await user.type(prompt, 'Mesa de Xadrez amanhã');
    await user.click(screen.getByRole('button', { name: /Preencher formulário com IA/i }));
    expect(await screen.findByText(/Sua sessão expirou/i)).toBeInTheDocument();
    expect(prompt).toHaveValue('Mesa de Xadrez amanhã');
    expect(location).toHaveValue('Meu local');
  });

  it('shows provider unavailability without exposing backend details', async () => {
    vi.mocked(api.generateEventDraft).mockRejectedValue(
      new ApiError(503, 'database host secret', 'AI_PROVIDER_UNAVAILABLE', '{"error":"database host secret"}'),
    );
    renderWithProviders(<Events />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Agendar Novo Encontro/i }));
    await user.type(screen.getByLabelText(/Descreva o encontro/i), 'Mesa de Magic sábado');
    await user.click(screen.getByRole('button', { name: /Preencher formulário com IA/i }));
    expect(await screen.findByText(/temporariamente indisponível/i)).toBeInTheDocument();
    expect(screen.queryByText(/database host secret/i)).not.toBeInTheDocument();
  });
});
