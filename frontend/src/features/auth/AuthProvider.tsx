import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { tokenStore } from '@/api/client'
import { authApi } from '@/api/endpoints'
import type { User } from '@/api/types'
import { AuthContext, USER_KEY, type AuthContextValue } from './context'

function readStoredUser(): User | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}

/**
 * Holds only identity state. All server data lives in TanStack Query — this context
 * exists so route guards and role checks do not have to refetch the current user.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(readStoredUser)

  const applySession = useCallback((tokens: { accessToken: string; refreshToken: string; user: User }) => {
    tokenStore.set(tokens)
    localStorage.setItem(USER_KEY, JSON.stringify(tokens.user))
    setUser(tokens.user)
  }, [])

  const login = useCallback(
    async (email: string, password: string) => {
      applySession(await authApi.login({ email, password }))
    },
    [applySession],
  )

  const register = useCallback(
    async (email: string, password: string, fullName?: string) => {
      applySession(await authApi.register({ email, password, fullName }))
    },
    [applySession],
  )

  const logout = useCallback(() => {
    tokenStore.clear()
    localStorage.removeItem(USER_KEY)
    setUser(null)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isAdmin: user?.role === 'ADMIN',
      login,
      register,
      logout,
    }),
    [user, login, register, logout],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
