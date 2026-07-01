import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { GlobalSearch } from '../components/GlobalSearch';
import { renderWithProviders } from './renderWithProviders';

describe('GlobalSearch', () => {
  it('finds matching games and events', async () => {
    const user = userEvent.setup();
    renderWithProviders(<GlobalSearch isOpen onClose={() => undefined} />);

    const input = screen.getByPlaceholderText(/Pesquisar jogos, jogadores, eventos ou sessões/i);
    await user.type(input, 'Xadrez');

    expect(screen.getByRole('heading', { name: /Jogos \(1\)/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Próximos Eventos \(1\)/i })).toBeInTheDocument();
    expect(screen.getByText('Xadrez', { selector: 'div' })).toBeInTheDocument();
  });
});
