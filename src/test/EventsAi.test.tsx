import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Events, tomorrowAsLocalIsoDate } from '../pages/Events';
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
  const assistantDraft = (draft: api.AiEventDraftResponse): api.AiEventAssistantResponse => ({ status: 'draft', draft });

  beforeEach(() => {
    vi.useRealTimers();
    localStorage.clear();
    sessionStorage.clear();
    sessionStorage.setItem('tabula_auth_token', 'session-token');
    sessionStorage.setItem('tabula_auth_session', 'u1');
    vi.mocked(api.getServerState).mockResolvedValue(getDefaultDatabaseState());
    vi.mocked(api.saveServerState).mockResolvedValue();
    vi.mocked(api.generateEventDraft).mockReset();
    vi.mocked(api.refineEventDraft).mockReset();
  });

  it('opens a new form with tomorrow, noon, no location preset and no quota warning', async () => {
    expect(tomorrowAsLocalIsoDate(new Date(2026, 7, 9, 23, 30, 0))).toBe('2026-08-10');
    const expectedTomorrow = tomorrowAsLocalIsoDate();
    const user = userEvent.setup();
    renderWithProviders(<Events />);

    await user.click(screen.getByRole('button', { name: /Agendar Novo Encontro/i }));

    expect(screen.getByDisplayValue(expectedTomorrow)).toBeInTheDocument();
    expect(screen.getByDisplayValue('12:00')).toBeInTheDocument();
    expect(screen.getByLabelText(/Local de Encontro/i)).toHaveValue('');
    expect(screen.queryByText(/preservar a cota da equipe/i)).not.toBeInTheDocument();
  });

  it('disables empty prompt, blocks duplicate calls and fills editable fields without saving', async () => {
    let resolveDraft!: (value: api.AiEventAssistantResponse) => void;
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

    resolveDraft(assistantDraft({
      gameId: 'g2', gameName: 'Magic: The Gathering', date: '2026-08-01', time: '18:00',
      location: 'Biblioteca', maxParticipants: 4, description: 'Mesa aberta.',
      warnings: ['A data foi interpretada como o próximo sábado.'],
    }));
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

  it('shows clarification without treating it as a draft or creating an event', async () => {
    vi.mocked(api.generateEventDraft).mockResolvedValue({
      status: 'needs_clarification',
      reasonCode: 'missing_required_information',
      missingFields: ['date', 'time', 'location'],
      message: 'Informe data, horário e local.',
      partialDraft: { gameId: 'g2', gameName: 'Magic: The Gathering' },
    });
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    renderWithProviders(<Events />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Agendar Novo Encontro/i }));
    await user.type(screen.getByLabelText(/Descreva o encontro/i), 'Quero criar um evento de Magic');
    await user.click(screen.getByRole('button', { name: /Preencher formulário com IA/i }));

    expect(await screen.findByText('Informe data, horário e local.')).toBeInTheDocument();
    expect(document.querySelector('input[type="date"]')).toHaveValue('');
    expect(document.querySelector('input[type="time"]')).toHaveValue('');
    expect(screen.getByLabelText(/Local de Encontro/i)).toHaveValue('');
    expect(screen.queryByRole('heading', { name: /Refinar com IA/i })).not.toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalledWith(expect.stringContaining('/events'),
      expect.objectContaining({ method: 'POST' }));
    fetchSpy.mockRestore();
  });

  it('applies a partial draft field by field and lets the user complete the missing location', async () => {
    vi.mocked(api.generateEventDraft).mockResolvedValue({
      status: 'needs_clarification',
      reasonCode: 'missing_required_information',
      missingFields: ['location'],
      message: 'Falta informar o local.',
      partialDraft: { gameId: 'g2', gameName: 'Magic: The Gathering', date: '2026-08-14', time: '15:00' },
    });
    renderWithProviders(<Events />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Agendar Novo Encontro/i }));
    const description = screen.getByPlaceholderText(/Vamos jogar Terraforming Mars/i);
    await user.type(description, 'Texto preenchido manualmente');
    await user.type(screen.getByLabelText(/Descreva o encontro/i), 'Magic sexta às 15h');
    await user.click(screen.getByRole('button', { name: /Preencher formulário com IA/i }));

    expect(await screen.findByText('Falta informar o local.')).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /Magic: The Gathering/i }).parentElement).toHaveValue('g2');
    expect(screen.getByDisplayValue('2026-08-14')).toBeInTheDocument();
    expect(screen.getByDisplayValue('15:00')).toBeInTheDocument();
    expect(screen.getByLabelText(/Local de Encontro/i)).toHaveValue('');
    expect(description).toHaveValue('Texto preenchido manualmente');

    await user.type(screen.getByLabelText(/Local de Encontro/i), 'Biblioteca');
    expect(screen.getByLabelText(/Local de Encontro/i)).toHaveValue('Biblioteca');
    await user.clear(description);
    await user.type(description, 'Descrição final editável');
    expect(description).toHaveValue('Descrição final editável');
  });

  it('rejects general questions without creating a draft or calling POST events', async () => {
    vi.mocked(api.generateEventDraft).mockResolvedValue({
      status: 'unsupported', reasonCode: 'not_event_creation_request',
    });
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    renderWithProviders(<Events />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Agendar Novo Encontro/i }));
    await user.type(screen.getByLabelText(/Local de Encontro/i), 'Local manual');
    await user.type(screen.getByLabelText(/Descreva o encontro/i), 'Qual o horário do SBT hoje?');
    await user.click(screen.getByRole('button', { name: /Preencher formulário com IA/i }));

    expect(await screen.findByText(/A IA desta tela é usada apenas para ajudar a criar eventos/i)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /Refinar com IA/i })).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Local de Encontro/i)).toHaveValue('Local manual');
    expect(fetchSpy).not.toHaveBeenCalledWith(expect.stringContaining('/events'),
      expect.objectContaining({ method: 'POST' }));
    fetchSpy.mockRestore();
  });

  it('keeps the existing draft when refinement is outside the event domain', async () => {
    vi.mocked(api.generateEventDraft).mockResolvedValue(assistantDraft({
      gameId: 'g2', gameName: 'Magic: The Gathering', date: '2026-08-01', time: '18:00',
      location: 'Biblioteca', maxParticipants: 4, description: 'Mesa aberta.', warnings: [],
    }));
    vi.mocked(api.refineEventDraft).mockResolvedValue({
      status: 'unsupported', reasonCode: 'not_event_creation_request',
    });
    renderWithProviders(<Events />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /Agendar Novo Encontro/i }));
    await user.type(screen.getByLabelText(/Descreva o encontro/i), 'Mesa de Magic sábado');
    await user.click(screen.getByRole('button', { name: /Preencher formulário com IA/i }));
    const instruction = await screen.findByLabelText(/Alteração desejada/i);
    await user.type(instruction, 'Qual o horário do SBT hoje?');
    await user.click(screen.getByRole('button', { name: /Aplicar alteração/i }));

    expect(await screen.findByText(/A IA desta tela é usada apenas para ajudar a criar eventos/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue('Biblioteca')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Mesa aberta.')).toBeInTheDocument();
    expect(instruction).toHaveValue('Qual o horário do SBT hoje?');
  });

  it('shows the same quota message on refinement and preserves every field', async () => {
    vi.mocked(api.generateEventDraft).mockResolvedValue(assistantDraft({
      gameId: 'g2', gameName: 'Magic: The Gathering', date: '2026-08-01', time: '18:00',
      location: 'Biblioteca', maxParticipants: 4, description: 'Mesa aberta.', warnings: [],
    }));
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
    vi.mocked(api.generateEventDraft).mockResolvedValue(assistantDraft({
      gameId: 'g2', gameName: 'Magic: The Gathering', date: '2026-08-01', time: '18:00',
      location: 'Biblioteca', maxParticipants: 4, description: 'Mesa aberta.', warnings: [],
    }));
    vi.mocked(api.refineEventDraft)
      .mockResolvedValueOnce(assistantDraft({
        gameId: 'g2', gameName: 'Magic: The Gathering', date: '2026-08-02', time: '15:00',
        location: 'Sala editada', maxParticipants: 4, description: 'Mesa aberta.', warnings: ['Data alterada.'],
      }))
      .mockResolvedValueOnce(assistantDraft({
        gameId: 'g2', gameName: 'Magic: The Gathering', date: '2026-08-02', time: '16:00',
        location: 'Sala editada', maxParticipants: 4, description: 'Mesa aberta.', warnings: [],
      }));
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
    vi.mocked(api.generateEventDraft).mockResolvedValue(assistantDraft({
      gameId: 'g2', gameName: 'Magic: The Gathering', date: '2026-08-01', time: '18:00',
      location: 'Biblioteca', maxParticipants: 4, description: 'Mesa aberta.', warnings: [],
    }));
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
