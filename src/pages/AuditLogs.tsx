import React, { useEffect, useMemo, useState } from 'react';
import {
  getAuditLogs,
  type AuditLogEntry,
  type AuditLogFilters,
  type AuditLogPage,
} from '../services/api';
import './AuditLogs.css';

const PAGE_SIZE = 25;

const ACTION_LABELS: Record<string, string> = {
  USER_REGISTERED: 'Usuário cadastrado',
  LOGIN_SUCCEEDED: 'Login aceito',
  LOGIN_REJECTED: 'Login rejeitado',
  PASSWORD_RESET_COMPLETED: 'Senha redefinida',
  PASSWORD_CHANGED: 'Senha alterada',
  STATE_UPDATED: 'Estado atualizado',
  STATE_UPDATE_REJECTED: 'Atualização rejeitada',
  EMAIL_VERIFICATION_REQUESTED: 'Verificação solicitada',
  EMAIL_VERIFIED: 'E-mail verificado',
};

const SECTION_LABELS: Record<string, string> = {
  users: 'usuários',
  boardGames: 'jogos',
  sessions: 'partidas',
  events: 'eventos',
};

interface FilterForm {
  action: string;
  userId: string;
  resourceType: string;
  resourceId: string;
  success: string;
  startDate: string;
  endDate: string;
}

const EMPTY_FILTERS: FilterForm = {
  action: '',
  userId: '',
  resourceType: '',
  resourceId: '',
  success: '',
  startDate: '',
  endDate: '',
};

const EMPTY_PAGE: AuditLogPage = {
  items: [],
  page: 1,
  pageSize: PAGE_SIZE,
  total: 0,
};

export const AuditLogs: React.FC = () => {
  const [draftFilters, setDraftFilters] = useState<FilterForm>(EMPTY_FILTERS);
  const [appliedFilters, setAppliedFilters] = useState<FilterForm>(EMPTY_FILTERS);
  const [page, setPage] = useState(1);
  const [result, setResult] = useState<AuditLogPage>(EMPTY_PAGE);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;

    getAuditLogs(toApiFilters(appliedFilters, page))
      .then(response => {
        if (active) setResult(response);
      })
      .catch(() => {
        if (active) {
          setError('Não foi possível carregar a auditoria. Verifique sua sessão e tente novamente.');
          setResult(previous => ({ ...previous, items: [] }));
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [appliedFilters, page, reloadKey]);

  const totalPages = Math.max(1, Math.ceil(result.total / result.pageSize));
  const rangeLabel = useMemo(() => {
    if (result.total === 0) return 'Nenhum evento';
    const first = (result.page - 1) * result.pageSize + 1;
    const last = Math.min(result.total, first + result.items.length - 1);
    return `${first}–${last} de ${result.total}`;
  }, [result]);

  const updateFilter = (name: keyof FilterForm, value: string) => {
    setDraftFilters(current => ({ ...current, [name]: value }));
  };

  const applyFilters = (event: React.FormEvent) => {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setPage(1);
    setAppliedFilters({ ...draftFilters });
  };

  const clearFilters = () => {
    setLoading(true);
    setError(null);
    setDraftFilters({ ...EMPTY_FILTERS });
    setAppliedFilters({ ...EMPTY_FILTERS });
    setPage(1);
  };

  const reload = () => {
    setLoading(true);
    setError(null);
    setReloadKey(value => value + 1);
  };

  const changePage = (nextPage: number) => {
    setLoading(true);
    setError(null);
    setPage(nextPage);
  };

  return (
    <div className="container audit-page">
      <header className="audit-page__header">
        <div>
          <span className="audit-page__eyebrow">Administração</span>
          <h1>Auditoria do sistema</h1>
          <p>Eventos oficiais registrados pelo backend. Os registros locais legados não aparecem aqui.</p>
        </div>
        <button
          type="button"
          className="btn btn-outline"
          onClick={reload}
          disabled={loading}
        >
          Atualizar
        </button>
      </header>

      <form className="card audit-filters" onSubmit={applyFilters} aria-label="Filtros de auditoria">
        <label>
          <span>Ação</span>
          <select
            className="form-select"
            value={draftFilters.action}
            onChange={event => updateFilter('action', event.target.value)}
          >
            <option value="">Todas</option>
            {Object.entries(ACTION_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </label>
        <label>
          <span>Ator</span>
          <input
            className="form-input"
            value={draftFilters.userId}
            onChange={event => updateFilter('userId', event.target.value)}
            placeholder="Ex.: u_123"
          />
        </label>
        <label>
          <span>Tipo de recurso</span>
          <input
            className="form-input"
            value={draftFilters.resourceType}
            onChange={event => updateFilter('resourceType', event.target.value)}
            placeholder="Ex.: USER"
          />
        </label>
        <label>
          <span>ID do recurso</span>
          <input
            className="form-input"
            value={draftFilters.resourceId}
            onChange={event => updateFilter('resourceId', event.target.value)}
            placeholder="Ex.: 1"
          />
        </label>
        <label>
          <span>Resultado</span>
          <select
            className="form-select"
            value={draftFilters.success}
            onChange={event => updateFilter('success', event.target.value)}
          >
            <option value="">Todos</option>
            <option value="true">Sucesso</option>
            <option value="false">Falha</option>
          </select>
        </label>
        <label>
          <span>Desde</span>
          <input
            className="form-input"
            type="datetime-local"
            value={draftFilters.startDate}
            onChange={event => updateFilter('startDate', event.target.value)}
          />
        </label>
        <label>
          <span>Até</span>
          <input
            className="form-input"
            type="datetime-local"
            value={draftFilters.endDate}
            onChange={event => updateFilter('endDate', event.target.value)}
          />
        </label>
        <div className="audit-filters__actions">
          <button type="submit" className="btn btn-primary">Aplicar filtros</button>
          <button type="button" className="btn btn-text" onClick={clearFilters}>Limpar</button>
        </div>
      </form>

      <section className="card audit-results" aria-live="polite">
        <div className="audit-results__summary">
          <div>
            <h2>Eventos</h2>
            <span>{rangeLabel}</span>
          </div>
          <span className="audit-results__official">Fonte oficial</span>
        </div>

        {loading && <div className="audit-state">Carregando eventos…</div>}

        {!loading && error && (
          <div className="audit-state audit-state--error" role="alert">
            <p>{error}</p>
            <button type="button" className="btn btn-outline btn-sm" onClick={reload}>
              Tentar novamente
            </button>
          </div>
        )}

        {!loading && !error && result.items.length === 0 && (
          <div className="audit-state">
            <p>Nenhum evento corresponde aos filtros selecionados.</p>
          </div>
        )}

        {!loading && !error && result.items.length > 0 && (
          <div className="audit-table-scroll">
            <table className="audit-table">
              <thead>
                <tr>
                  <th>Data</th>
                  <th>Ação</th>
                  <th>Ator</th>
                  <th>Recurso</th>
                  <th>Detalhes</th>
                  <th>Resultado</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map(item => <AuditLogRow key={item.id} item={item} />)}
              </tbody>
            </table>
          </div>
        )}

        {!loading && !error && result.total > 0 && (
          <nav className="audit-pagination" aria-label="Paginação da auditoria">
            <button
              type="button"
              className="btn btn-outline btn-sm"
              disabled={page <= 1}
              onClick={() => changePage(Math.max(1, page - 1))}
            >
              Anterior
            </button>
            <span>Página {result.page} de {totalPages}</span>
            <button
              type="button"
              className="btn btn-outline btn-sm"
              disabled={page >= totalPages}
              onClick={() => changePage(Math.min(totalPages, page + 1))}
            >
              Próxima
            </button>
          </nav>
        )}
      </section>
    </div>
  );
};

const AuditLogRow: React.FC<{ item: AuditLogEntry }> = ({ item }) => (
  <tr>
    <td>
      <time dateTime={item.createdAt}>{formatDate(item.createdAt)}</time>
      {item.traceId && <span className="audit-table__secondary" title={item.traceId}>Trace {item.traceId.slice(0, 8)}</span>}
    </td>
    <td>
      <strong>{ACTION_LABELS[item.action] ?? item.action}</strong>
      <code>{item.action}</code>
    </td>
    <td>
      <span>{item.userId ?? 'Sistema / anônimo'}</span>
      {item.ipAddress && <span className="audit-table__secondary">IP {item.ipAddress}</span>}
    </td>
    <td>
      <span>{item.resourceType ?? '—'}</span>
      {item.resourceId && <span className="audit-table__secondary">#{item.resourceId}</span>}
    </td>
    <td>{formatDetails(item.details)}</td>
    <td>
      <span className={`badge ${item.success ? 'badge-success' : 'badge-danger'}`}>
        {item.success ? 'Sucesso' : 'Falha'}
      </span>
    </td>
  </tr>
);

function toApiFilters(filters: FilterForm, page: number): AuditLogFilters {
  return {
    page,
    pageSize: PAGE_SIZE,
    action: filters.action || undefined,
    userId: filters.userId.trim() || undefined,
    resourceType: filters.resourceType.trim() || undefined,
    resourceId: filters.resourceId.trim() || undefined,
    success: filters.success ? filters.success === 'true' : undefined,
    startDate: toIsoDate(filters.startDate),
    endDate: toIsoDate(filters.endDate),
  };
}

function toIsoDate(value: string): string | undefined {
  if (!value) return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'medium',
  }).format(date);
}

function formatDetails(details: Record<string, unknown>): React.ReactNode {
  const changedSections = Array.isArray(details.changedSections)
    ? details.changedSections.filter((section): section is string => typeof section === 'string')
    : [];
  if (changedSections.length > 0) {
    return `Seções: ${changedSections.map(section => SECTION_LABELS[section] ?? section).join(', ')}`;
  }
  const reasonCode = typeof details.reasonCode === 'string' ? details.reasonCode : null;
  if (reasonCode) {
    const reason = typeof details.reason === 'string' ? details.reason : null;
    const validationStage = typeof details.validationStage === 'string' ? details.validationStage : null;
    const model = typeof details.model === 'string' ? details.model : null;
    const section = typeof details.section === 'string' ? details.section : null;
    const resourceId = typeof details.resourceId === 'string' ? details.resourceId : null;
    const field = typeof details.field === 'string' ? details.field : null;
    const detail = typeof details.detail === 'string' ? details.detail : null;
    const reasonLabels: Record<string, string> = {
      unknown_field: 'campo não suportado',
      duplicate_id: 'identificador duplicado',
      duplicate_values: 'valores duplicados',
      expected_text_array: 'esperado array de strings',
      expected_entity_array: 'esperado array de registros',
      missing_section: 'seção obrigatória ausente',
      missing_id: 'identificador ausente',
      invalid_entity: 'registro inválido',
      blank_value: 'valor vazio não permitido',
      invalid_json: 'JSON inválido',
    };
    return (
      <span>
        <strong>{reason === 'invalid_ai_response' ? 'Resposta da IA rejeitada' : 'Payload inválido'}</strong>
        {section && <span className="audit-table__secondary">Seção: {SECTION_LABELS[section] ?? section}</span>}
        {resourceId && <span className="audit-table__secondary">Recurso: {resourceId}</span>}
        {field && <span className="audit-table__secondary">Campo: {field}</span>}
        <span className="audit-table__secondary">Motivo: {reasonLabels[reasonCode] ?? detail ?? reasonCode}</span>
        {reason && <span className="audit-table__secondary">Razão: {reason}</span>}
        <span className="audit-table__secondary">Código: {reasonCode}</span>
        {validationStage && <span className="audit-table__secondary">Etapa: {validationStage}</span>}
        {model && <span className="audit-table__secondary">Modelo: {model}</span>}
      </span>
    );
  }
  if (typeof details.reason === 'string') return `Motivo: ${details.reason}`;
  if (details.initialization === true) return 'Inicialização do estado';
  return '—';
}
