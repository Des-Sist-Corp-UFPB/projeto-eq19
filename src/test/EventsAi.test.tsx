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
    refineEventDraft: vi.fn(),
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
    vi.mocked(api.refineEventDraft).mockReset();
  });

  it('disables empty prompt, blocks duplicate calls and fills editable fields without saving', async () => {
    let resolveDraft!: (value: api.AiEventDraftResponse) => void;
    vi.mocked(api.generateEventDraft).mockReturnValue(new Promise(resolve => { resolveDraft = resolve; }));
    renderWithProviders(<Events />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Agendar Novo Encontro/i }));

    const generate = screen.getByRole('button', { name: /Preencher formulário com IA/i });
    expect(generate).toBeDisabled();
    expect(api.generateEventDraft).not.toHaveBeenCalled();
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

  it('shows the temporary quota message for a usage limit', async () => {
    vi.mocked(api.generateEventDraft).mockRejectedValue(
      new ApiError(429, 'internal quota detail', 'AI_USAGE_LIMIT_REACHED', '{"error":"internal quota detail"}'),
    );
    renderWithProviders(<Events />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Agendar Novo Encontro/i }));
    await user.type(screen.getByLabelText(/Descreva o encontro/i), 'Mesa de Xadrez na sexta');
    await user.click(screen.getByRole('button', { name: /Preencher formulário com IA/i }));
    expect(await screen.findByText(/limite temporário de gerações com IA/i)).toBeInTheDocument();
    expect(screen.queryByText(/internal quota detail/i)).not.toBeInTheDocument();
  });

  it('shows the same quota message on refinement and preserves every field', async () => {
    vi.mocked(api.generateEventDraft).mockResolvedValue({
      gameId: 'g2', gameName: 'Magic: The Gathering', date: '2026-08-01', time: '18:00',
      location: 'Biblioteca', maxParticipants: 4, description: 'Mesa aberta.', warnings: [],
    });
    vi.mocked(api.refineEventDraft).mockRejectedValue(
      new ApiError(429, 'quota detail', 'AI_USAGE_LIMIT_REACHED', '{"error":"quota detail"}'),
    );
    renderWithProviders(<Events />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Agendar Novo Encontro/i }));
    await user.type(screen.getByLabelText(/Descreva o encontro/i), 'Mesa de Magic sábado');
    await user.click(screen.getByRole('button', { name: /Preencher formulário com IA/i }));
    await screen.findByRole('heading', { name: /Refinar com IA/i });
    await user.type(screen.getByLabelText(/Alteração desejada/i), 'Troque para domingo');
    await user.click(screen.getByRole('button', { name: /Aplicar alteração/i }));

    expect(await screen.findByText(/limite temporário de gerações com IA/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue('Biblioteca')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Mesa aberta.')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2026-08-01')).toBeInTheDocument();
    expect(screen.getByDisplayValue('18:00')).toBeInTheDocument();
    expect(screen.getByLabelText(/Alteração desejada/i)).toHaveValue('Troque para domingo');
  });

  it('applies successive refinements, sends edited fields and clears each successful instruction', async () => {
    vi.mocked(api.generateEventDraft).mockResolvedValue({
      gameId: 'g2', gameName: 'Magic: The Gathering', date: '2026-08-01', time: '18:00',
      location: 'Biblioteca', maxParticipants: 4, description: 'Mesa aberta.', warnings: [],
    });
    vi.mocked(api.refineEventDraft)
      .mockResolvedValueOnce({
        gameId: 'g2', gameName: 'Magic: The Gathering', date: '2026-08-02', time: '15:00',
        location: 'Sala editada', maxParticipants: 4, description: 'Mesa aberta.', warnings: ['Data alterada.'],
      })
      .mockResolvedValueOnce({
        gameId: 'g2', gameName: 'Magic: The Gathering', date: '2026-08-02', time: '16:00',
        location: 'Sala editada', maxParticipants: 4, description: 'Mesa aberta.', warnings: [],
      });
    renderWithProviders(<Events />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Agendar Novo Encontro/i }));
    await user.type(screen.getByLabelText(/Descreva o encontro/i), 'Mesa de Magic sábado');
    await user.click(screen.getByRole('button', { name: /Preencher formulário com IA/i }));
    const location = await screen.findByLabelText(/Local de Encontro/i);
    await user.clear(location);
    await user.type(location, 'Sala editada');

    const instruction = screen.getByLabelText(/Alteração desejada/i);
    await user.type(instruction, 'Troque para domingo às 15h');
    await user.click(screen.getByRole('button', { name: /Aplicar alteração/i }));
    await waitFor(() => expect(instruction).toHaveValue(''));
    expect(api.refineEventDraft).toHaveBeenLastCalledWith(
      'Troque para domingo às 15h',
      expect.objectContaining({ location: 'Sala editada', description: 'Mesa aberta.' }),
    );
    expect(screen.getByDisplayValue('15:00')).toBeInTheDocument();
    expect(screen.getByText('Data alterada.')).toBeInTheDocument();

    await user.type(instruction, 'Agora às 16h');
    await user.click(screen.getByRole('button', { name: /Aplicar alteração/i }));
    await waitFor(() => expect(instruction).toHaveValue(''));
    expect(api.refineEventDraft).toHaveBeenCalledTimes(2);
    expect(screen.getByDisplayValue('16:00')).toBeInTheDocument();
    expect(api.saveServerState).not.toHaveBeenCalledWith(expect.objectContaining({
      events: expect.arrayContaining([expect.objectContaining({ description: 'Mesa aberta.' })]),
    }));
  });

  it.each([
    [422, /não conseguiu aplicar essa alteração/i],
    [502, /temporariamente indisponível/i],
    [503, /temporariamente indisponível/i],
  ])('preserves refinement data for HTTP %s', async (status, expectedMessage) => {
    vi.mocked(api.generateEventDraft).mockResolvedValue({
      gameId: 'g2', gameName: 'Magic: The Gathering', date: '2026-08-01', time: '18:00',
      location: 'Biblioteca', maxParticipants: 4, description: 'Mesa aberta.', warnings: [],
    });
    vi.mocked(api.refineEventDraft).mockRejectedValue(new ApiError(status, 'internal'));
    renderWithProviders(<Events />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Agendar Novo Encontro/i }));
    await user.type(screen.getByLabelText(/Descreva o encontro/i), 'Mesa de Magic sábado');
    await user.click(screen.getByRole('button', { name: /Preencher formulário com IA/i }));
    const instruction = await screen.findByLabelText(/Alteração desejada/i);
    await user.type(instruction, 'Mude o horário');
    await user.click(screen.getByRole('button', { name: /Aplicar alteração/i }));
    expect(await screen.findByText(expectedMessage)).toBeInTheDocument();
    expect(instruction).toHaveValue('Mude o horário');
    expect(screen.getByDisplayValue('Biblioteca')).toBeInTheDocument();
  });
});
