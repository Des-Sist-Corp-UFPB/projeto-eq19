import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it} from 'vitest';
import { DatabaseProvider, useDatabase } from '../context/DatabaseContext';
import { ToastProvider } from '../context/ToastContext';

function TestConsumer() {
  const { state, addUser } = useDatabase();

  return (
    <div>
      <span>{state.users.length}</span>
      <button onClick={() => addUser({ name: 'Novo Usuário', email: 'novo@tabula.com', course: 'Sem curso informado', role: 'student', })}>Add user</button>
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

    expect(await screen.findByText('6')).toBeInTheDocument();

    fireEvent.click(await screen.findByRole('button', { name: /Add user/i }));

    expect(await screen.findByText('7')).toBeInTheDocument();
  });
});
