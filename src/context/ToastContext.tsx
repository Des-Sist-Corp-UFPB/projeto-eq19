import React, { createContext, useContext, useState, useCallback } from 'react';

export type ToastType = 'success' | 'info' | 'error' | 'warning';

interface Toast {
  id: string;
  message: string;
  type: ToastType;
}

interface ToastContextType {
  showToast: (message: string, type?: ToastType) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const showToast = useCallback((message: string, type: ToastType = 'success') => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts(prev => [...prev, { id, message, type }]);

    // Auto dismiss after 3 seconds
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 3000);
  }, []);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      
      {/* Toast container rendered at the bottom right */}
      <div style={containerStyle} className="no-print">
        {toasts.map(toast => (
          <div
            key={toast.id}
            style={{
              ...toastStyle,
              backgroundColor: getBgColor(toast.type),
              borderLeft: `5px solid ${getBorderColor(toast.type)}`,
            }}
          >
            <span style={iconStyle}>{getIcon(toast.type)}</span>
            <span style={messageStyle}>{toast.message}</span>
            <button
              onClick={() => setToasts(prev => prev.filter(t => t.id !== toast.id))}
              style={closeBtnStyle}
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
};

// Styling helper functions and constants
const getBgColor = (type: ToastType) => {
  switch (type) {
    case 'success': return '#EEF7F2';
    case 'error': return '#FDF0F0';
    case 'warning': return '#FEF7EB';
    case 'info': return '#EEF4F8';
  }
};

const getBorderColor = (type: ToastType) => {
  switch (type) {
    case 'success': return '#2A8F5B';
    case 'error': return '#D93838';
    case 'warning': return '#F3B63F';
    case 'info': return '#3B7197';
  }
};

const getIcon = (type: ToastType) => {
  switch (type) {
    case 'success': return '✅';
    case 'error': return '❌';
    case 'warning': return '⚠️';
    case 'info': return 'ℹ️';
  }
};

// CSS styles in JS for toast overlay to keep things clean and modular
const containerStyle: React.CSSProperties = {
  position: 'fixed',
  bottom: '24px',
  right: '24px',
  zIndex: 10000,
  display: 'flex',
  flexDirection: 'column',
  gap: '12px',
  maxWidth: '350px',
  width: '100%',
};

const toastStyle: React.CSSProperties = {
  padding: '16px',
  borderRadius: '8px',
  boxShadow: '0 8px 16px rgba(0, 0, 0, 0.1)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: '12px',
  color: '#2B2D42',
  fontSize: '0.9rem',
  fontWeight: 500,
  animation: 'slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
};

const iconStyle: React.CSSProperties = {
  fontSize: '1.2rem',
  flexShrink: 0,
};

const messageStyle: React.CSSProperties = {
  flexGrow: 1,
};

const closeBtnStyle: React.CSSProperties = {
  background: 'none',
  border: 'none',
  fontSize: '1.2rem',
  cursor: 'pointer',
  color: '#8E93A6',
  padding: '0 4px',
  lineHeight: 1,
};
