const PBKDF2_PREFIX = 'pbkdf2';
const ITERATIONS = 120000;

const toBase64 = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes));
const fromBase64 = (value: string) => Uint8Array.from(atob(value), c => c.charCodeAt(0));

export const hashPassword = async (password: string): Promise<string> => {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const encoder = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey('raw', encoder.encode(password), 'PBKDF2', false, ['deriveBits']);
  const hashBuffer = await crypto.subtle.deriveBits({ name: 'PBKDF2', salt, iterations: ITERATIONS, hash: 'SHA-256' }, keyMaterial, 256);
  return `${PBKDF2_PREFIX}$${ITERATIONS}$${toBase64(salt)}$${toBase64(new Uint8Array(hashBuffer))}`;
};

export const verifyPassword = async (password: string, storedHash?: string): Promise<boolean> => {
  if (!storedHash || !storedHash.startsWith(`${PBKDF2_PREFIX}$`)) return false;

  const [prefix, iterationsText, saltBase64, hashBase64] = storedHash.split('$');
  if (!prefix || !iterationsText || !saltBase64 || !hashBase64) return false;

  const salt = fromBase64(saltBase64);
  const iterations = Number(iterationsText);
  const encoder = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey('raw', encoder.encode(password), 'PBKDF2', false, ['deriveBits']);
  const hashBuffer = await crypto.subtle.deriveBits({ name: 'PBKDF2', salt, iterations, hash: 'SHA-256' }, keyMaterial, 256);
  const expected = new Uint8Array(hashBuffer);
  const actual = fromBase64(hashBase64);

  return expected.length === actual.length && expected.every((value, index) => value === actual[index]);
};

export const isStrongPassword = (password: string) => {
  return password.length >= 8 && /[A-Z]/.test(password) && /[0-9]/.test(password) && /[^A-Za-z0-9]/.test(password);
};
