import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/server'
import { pageOf } from '@/test/handlers'
import { adminUser, renderWithProviders, signIn, staffUser } from '@/test/render'
import { ProductsPage } from './ProductsPage'

describe('ProductsPage', () => {
  it('lists products with their derived stock level', async () => {
    signIn(staffUser)
    renderWithProviders(<ProductsPage />)

    expect(await screen.findByText('Widget')).toBeInTheDocument()
    expect(screen.getByText('WID-001')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
  })

  it('flags a product at or below its reorder level', async () => {
    signIn(staffUser)
    renderWithProviders(<ProductsPage />)

    expect(await screen.findByText('Low')).toBeInTheDocument()
  })

  it('does not offer catalog editing to STAFF', async () => {
    signIn(staffUser)
    renderWithProviders(<ProductsPage />)

    await screen.findByText('Widget')
    expect(screen.queryByRole('button', { name: /new product/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /delete/i })).not.toBeInTheDocument()
  })

  it('offers catalog editing to ADMIN', async () => {
    signIn(adminUser)
    renderWithProviders(<ProductsPage />)

    await screen.findByText('Widget')
    expect(screen.getByRole('button', { name: /new product/i })).toBeInTheDocument()
  })

  it('shows an empty state when nothing matches', async () => {
    server.use(http.get('/api/v1/products', () => HttpResponse.json(pageOf([]))))
    signIn(staffUser)
    renderWithProviders(<ProductsPage />)

    expect(await screen.findByText('No products found')).toBeInTheDocument()
  })

  it('surfaces a server error to the user', async () => {
    server.use(
      http.get('/api/v1/products', () =>
        HttpResponse.json({ status: 500, detail: 'Something went wrong on our side' }, { status: 500 }),
      ),
    )
    signIn(staffUser)
    renderWithProviders(<ProductsPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong on our side')
  })

  it('never renders an editable quantity field', async () => {
    signIn(adminUser)
    renderWithProviders(<ProductsPage />)

    await screen.findByText('Widget')
    // Stock is derived from the ledger; the catalog UI must not expose a way to set it.
    expect(screen.queryByLabelText(/quantity/i)).not.toBeInTheDocument()
  })
})
