import React from 'react';

export const DEFAULT_AVATAR_SRC = '/images/default-avatar.svg';

interface UserAvatarProps {
  user?: { name?: string; avatarUrl?: string };
  size?: number;
  style?: React.CSSProperties;
}

export const UserAvatar: React.FC<UserAvatarProps> = ({ user, size = 40, style }) => {
  const src = user?.avatarUrl?.trim() || DEFAULT_AVATAR_SRC;

  return (
    <img
      src={src}
      alt={user?.name || 'Usuário'}
      onError={(e) => {
        e.currentTarget.src = DEFAULT_AVATAR_SRC;
      }}
      style={{
        width: size,
        height: size,
        borderRadius: '50%',
        objectFit: 'cover',
        flexShrink: 0,
        ...style,
      }}
    />
  );
};
