const TOKEN_KEY = 'oratoria.auth-token'
const USER_KEY = 'oratoria.auth-user'

export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  },
}

/** Último perfil confirmado por el servidor: permite abrir la app instalada sin conexión sin cerrar la sesión. */
export const cachedUserStorage = {
  get<T>(): T | null {
    try {
      const value = localStorage.getItem(USER_KEY)
      return value ? JSON.parse(value) as T : null
    } catch { return null }
  },
  set(user: unknown) {
    try { localStorage.setItem(USER_KEY, JSON.stringify(user)) } catch { /* sin almacenamiento: solo se pierde el modo sin conexión */ }
  },
}
