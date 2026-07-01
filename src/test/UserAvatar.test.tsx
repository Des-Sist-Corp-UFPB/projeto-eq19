import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { DEFAULT_AVATAR_SRC, UserAvatar } from '../components/UserAvatar';

describe('UserAvatar', () => {
  it('falls back to the default avatar when the provided image fails', () => {
    const { container } = render(<UserAvatar user={{ name: 'Ana', avatarUrl: 'broken.png' }} size={48} />);

    const img = container.querySelector('img') as HTMLImageElement;
    img.dispatchEvent(new Event('error'));

    expect(img.getAttribute('src')).toContain(DEFAULT_AVATAR_SRC);
    expect(img).toHaveAttribute('alt', 'Ana');
  });

  it('renders the default avatar when no user is provided', () => {
    render(<UserAvatar size={32} />);

    expect(screen.getByRole('img', { name: /Usuário/i })).toHaveAttribute('src', DEFAULT_AVATAR_SRC);
  });
});
