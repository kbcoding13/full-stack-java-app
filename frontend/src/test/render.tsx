import type { ReactElement, ReactNode } from 'react'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '@/features/auth/AuthProvider'
import type { User } from '@/api/types'

export const adminUser: User = { id: 1, email: 'admin@example.com', fullName: 'Admin', role: 'ADMIN' }
export const staffUser: User = { id: 2, email: 'staff@example.com', fullName: 'Staff', role: 'STAFF' }

/** Seeds the session the way a real login would, so role-gated UI can be tested. */
export function signIn(user: User) {
  localStorage.setItem('inventory.accessToken', 'test-access-token')
  localStorage.setItem('inventory.refreshToken', 'test-refresh-token')
  localStorage.setItem('inventory.user', JSON.stringify(user))
}

export function renderWithProviders(ui: ReactElement, { route = '/' }: { route?: string } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>
    )
  }

  return render(ui, { wrapper: Wrapper })
}
