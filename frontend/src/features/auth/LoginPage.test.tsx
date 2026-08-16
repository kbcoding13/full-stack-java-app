import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/server'
import { renderWithProviders } from '@/test/render'
import { LoginPage } from './LoginPage'

const tokenResponse = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  tokenType: 'Bearer',
  expiresInSeconds: 900,
  user: { id: 1, email: 'admin@example.com', fullName: 'Admin', role: 'ADMIN' },
}

describe('LoginPage', () => {
  it('stores the session on a successful sign in', async () => {
    server.use(http.post('/api/v1/auth/login', () => HttpResponse.json(tokenResponse)))
    const user = userEvent.setup()

    renderWithProviders(<LoginPage />)

    await user.type(screen.getByLabelText(/email/i), 'admin@example.com')
    await user.type(screen.getByLabelText(/password/i), 'password123')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => expect(localStorage.getItem('inventory.accessToken')).toBe('access-1'))
    expect(localStorage.getItem('inventory.refreshToken')).toBe('refresh-1')
  })

  it('shows the backend message when credentials are wrong', async () => {
    server.use(
      http.post('/api/v1/auth/login', () =>
        HttpResponse.json({ status: 401, detail: 'Authentication is required' }, { status: 401 }),
      ),
    )
    const user = userEvent.setup()

    renderWithProviders(<LoginPage />)

    await user.type(screen.getByLabelText(/email/i), 'admin@example.com')
    await user.type(screen.getByLabelText(/password/i), 'wrong')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(localStorage.getItem('inventory.accessToken')).toBeNull()
  })

  it('validates the email client-side without calling the API', async () => {
    let called = false
    server.use(
      http.post('/api/v1/auth/login', () => {
        called = true
        return HttpResponse.json(tokenResponse)
      }),
    )
    const user = userEvent.setup()

    renderWithProviders(<LoginPage />)

    await user.type(screen.getByLabelText(/email/i), 'not-an-email')
    await user.type(screen.getByLabelText(/password/i), 'password123')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByText(/valid email/i)).toBeInTheDocument()
    expect(called).toBe(false)
  })

  it('requires a password', async () => {
    const user = userEvent.setup()
    renderWithProviders(<LoginPage />)

    await user.type(screen.getByLabelText(/email/i), 'admin@example.com')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByText(/password is required/i)).toBeInTheDocument()
  })
})
