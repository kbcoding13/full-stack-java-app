import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/server'
import { testProduct } from '@/test/handlers'
import { renderWithProviders, signIn, staffUser } from '@/test/render'
import { StockMovementDialog } from './StockMovementDialog'

/** Captures the body the UI actually posts, so the ledger contract is asserted end to end. */
function captureMovementPost() {
  const bodies: Record<string, unknown>[] = []

  server.use(
    http.post('/api/v1/stock-movements', async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>
      bodies.push(body)
      return HttpResponse.json({ ...body, id: 1 }, { status: 201 })
    }),
  )

  return bodies
}

describe('StockMovementDialog', () => {
  it('posts a positive quantity with the type carrying the direction', async () => {
    const bodies = captureMovementPost()
    signIn(staffUser)
    const user = userEvent.setup()

    renderWithProviders(<StockMovementDialog product={testProduct} onClose={vi.fn()} />)

    await user.selectOptions(screen.getByLabelText(/type/i), 'OUT')
    await user.clear(screen.getByLabelText(/quantity/i))
    await user.type(screen.getByLabelText(/quantity/i), '4')
    await user.click(screen.getByRole('button', { name: /record movement/i }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toMatchObject({ productId: 1, type: 'OUT', quantity: 4 })
  })

  it('only offers the decrease toggle for an adjustment', async () => {
    signIn(staffUser)
    const user = userEvent.setup()

    renderWithProviders(<StockMovementDialog product={testProduct} onClose={vi.fn()} />)

    expect(screen.queryByLabelText(/reduces stock/i)).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText(/type/i), 'ADJUST')
    expect(screen.getByLabelText(/reduces stock/i)).toBeInTheDocument()
  })

  it('sends decrease only when the adjustment is marked as reducing', async () => {
    const bodies = captureMovementPost()
    signIn(staffUser)
    const user = userEvent.setup()

    renderWithProviders(<StockMovementDialog product={testProduct} onClose={vi.fn()} />)

    await user.selectOptions(screen.getByLabelText(/type/i), 'ADJUST')
    await user.click(screen.getByLabelText(/reduces stock/i))
    await user.click(screen.getByRole('button', { name: /record movement/i }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toMatchObject({ type: 'ADJUST', decrease: true })
  })

  it('rejects a quantity below one before calling the API', async () => {
    const bodies = captureMovementPost()
    signIn(staffUser)
    const user = userEvent.setup()

    renderWithProviders(<StockMovementDialog product={testProduct} onClose={vi.fn()} />)

    await user.clear(screen.getByLabelText(/quantity/i))
    await user.type(screen.getByLabelText(/quantity/i), '0')
    await user.click(screen.getByRole('button', { name: /record movement/i }))

    expect(await screen.findByText(/at least 1/i)).toBeInTheDocument()
    expect(bodies).toHaveLength(0)
  })

  it('shows the backend message when a movement would oversell', async () => {
    server.use(
      http.post('/api/v1/stock-movements', () =>
        HttpResponse.json(
          { status: 422, title: 'Business rule violated', detail: 'Cannot remove 9 units: only 3 in stock' },
          { status: 422 },
        ),
      ),
    )
    signIn(staffUser)
    const user = userEvent.setup()

    renderWithProviders(<StockMovementDialog product={testProduct} onClose={vi.fn()} />)

    await user.selectOptions(screen.getByLabelText(/type/i), 'OUT')
    await user.clear(screen.getByLabelText(/quantity/i))
    await user.type(screen.getByLabelText(/quantity/i), '9')
    await user.click(screen.getByRole('button', { name: /record movement/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('only 3 in stock')
  })

  it('closes on a successful movement', async () => {
    captureMovementPost()
    const onClose = vi.fn()
    signIn(staffUser)
    const user = userEvent.setup()

    renderWithProviders(<StockMovementDialog product={testProduct} onClose={onClose} />)
    await user.click(screen.getByRole('button', { name: /record movement/i }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })
})
