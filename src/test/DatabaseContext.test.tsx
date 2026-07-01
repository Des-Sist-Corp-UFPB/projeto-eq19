import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { DatabaseProvider, useDatabase } from '../context/DatabaseContext';
import { ToastProvider } from '../context/ToastContext';

function TestConsumer() {
  const { state, addUser } = useDatabase();

  return (
    <div>
      <span>{state.users.length}</span>
      <button onClick={() => addUser({ name: 'Novo Usuário', email: 'novo@tabula.com' })}>Add user</button>
    </div>
  );
}

describe('DatabaseProvider', () => {
  it('exposes initial state and allows adding a user', async () => {
    render(
      <ToastProvider>
        <DatabaseProvider>
          <TestConsumer />
        </DatabaseProvider>
      </ToastProvider>
    );

    expect(screen.getByText('6')).toBeInTheDocument();

    await vi.waitFor(() => {
      screen.getByRole('button', { name: /Add user/i }).click();
    });

    expect(screen.getByText('7')).toBeInTheDocument();
  });
});
