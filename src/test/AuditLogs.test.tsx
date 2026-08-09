import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuditLogs } from '../pages/AuditLogs';
import { getAuditLogs, type AuditLogPage } from '../services/api';

const mockedGetAuditLogs = vi.mocked(getAuditLogs);

const firstPage: AuditLogPage = {
  page: 1,
  pageSize: 25,
  total: 26,
  items: [
    {
      id: 10,
      userId: 'u_admin',
      action: 'STATE_UPDATED',
      resourceType: 'APP_STATE',
      resourceId: '1',
      details: { changedSections: ['users', 'boardGames'] },
      ipAddress: '127.0.0.1',
      userAgent: 'test',
      success: true,
      traceId: '0123456789abcdef0123456789abcdef',
      createdAt: '2026-07-29T12:00:00Z',
    },
  ],
};

describe('AuditLogs page', () => {
  beforeEach(() => {
    mockedGetAuditLogs.mockReset();
  });

  it('loads official events, applies filters, and paginates', async () => {
    mockedGetAuditLogs
      .mockResolvedValueOnce(firstPage)
      .mockResolvedValueOnce({ ...firstPage, page: 1 })
      .mockResolvedValueOnce({
        ...firstPage,
        page: 2,
        items: [{ ...firstPage.items[0], id: 11, action: 'LOGIN_REJECTED', success: false }],
      });

    render(<AuditLogs />);

    expect(await screen.findByText('Estado atualizado')).toBeInTheDocument();
    expect(screen.getByText('Seções: usuários, jogos')).toBeInTheDocument();
    expect(screen.getByText('Fonte oficial')).toBeInTheDocument();
    expect(mockedGetAuditLogs).toHaveBeenNthCalledWith(1, expect.objectContaining({
      page: 1,
      pageSize: 25,
    }));

    fireEvent.change(screen.getByLabelText('Ator'), { target: { value: 'u_admin' } });
    fireEvent.change(screen.getByLabelText('Resultado'), { target: { value: 'true' } });
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

    await waitFor(() => expect(mockedGetAuditLogs).toHaveBeenNthCalledWith(2, expect.objectContaining({
      page: 1,
      userId: 'u_admin',
      success: true,
    })));

    fireEvent.click(screen.getByRole('button', { name: 'Próxima' }));

    await waitFor(() => expect(mockedGetAuditLogs).toHaveBeenNthCalledWith(3, expect.objectContaining({
      page: 2,
      userId: 'u_admin',
    })));
    expect(await screen.findByRole('cell', { name: /Login rejeitado/ })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Falha' })).toBeInTheDocument();
  });

  it('shows retryable error and empty states', async () => {
    mockedGetAuditLogs
      .mockRejectedValueOnce(new Error('forbidden'))
      .mockResolvedValueOnce({ items: [], page: 1, pageSize: 25, total: 0 });

    render(<AuditLogs />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar a auditoria');
    fireEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));

    expect(await screen.findByText('Nenhum evento corresponde aos filtros selecionados.')).toBeInTheDocument();
  });

  it('renders structured validation details and remains compatible with legacy reasons', async () => {
    mockedGetAuditLogs.mockResolvedValue({
      ...firstPage,
      total: 2,
      items: [
        {
          ...firstPage.items[0],
          id: 20,
          action: 'STATE_UPDATE_REJECTED',
          success: false,
          details: {
            reason: 'invalid_payload',
            reasonCode: 'expected_text_array',
            section: 'sessions',
            resourceId: 's1',
            field: 'photos',
            detail: 'expected array of strings',
          },
        },
        {
          ...firstPage.items[0],
          id: 21,
          action: 'STATE_UPDATE_REJECTED',
          success: false,
          details: { reason: 'invalid_payload' },
        },
      ],
    });

    render(<AuditLogs />);

    expect(await screen.findByText('Payload inválido')).toBeInTheDocument();
    expect(screen.getByText('Seção: partidas')).toBeInTheDocument();
    expect(screen.getByText('Recurso: s1')).toBeInTheDocument();
    expect(screen.getByText('Campo: photos')).toBeInTheDocument();
    expect(screen.getByText('Motivo: esperado array de strings')).toBeInTheDocument();
    expect(screen.getByText('Motivo: invalid_payload')).toBeInTheDocument();
  });
});
