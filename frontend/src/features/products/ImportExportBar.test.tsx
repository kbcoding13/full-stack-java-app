import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/server'
import { adminUser, renderWithProviders, signIn, staffUser } from '@/test/render'
import { ImportExportBar } from './ImportExportBar'

const csvFile = () => new File(['sku,name\nA-1,Thing\n'], 'products.csv', { type: 'text/csv' })

describe('ImportExportBar', () => {
  it('reports the import summary returned by the backend', async () => {
    server.use(
      http.post('/api/v1/imports/products', () =>
        HttpResponse.json({
          objectKey: 'imports/2026/08/x.csv',
          totalRows: 3,
          created: 2,
          updated: 1,
          skipped: 0,
          errors: [],
        }),
      ),
    )
    signIn(adminUser)
    const user = userEvent.setup()

    renderWithProviders(<ImportExportBar />)
    await user.upload(screen.getByLabelText(/import products csv/i), csvFile())

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Imported 3 row(s): 2 created, 1 updated, 0 skipped.',
    )
  })

  it('lists per-row errors so a user can fix the file', async () => {
    server.use(
      http.post('/api/v1/imports/products', () =>
        HttpResponse.json({
          objectKey: 'imports/2026/08/x.csv',
          totalRows: 2,
          created: 1,
          updated: 0,
          skipped: 1,
          errors: [{ line: 3, sku: 'BAD-1', message: "'not-a-number' is not a valid price" }],
        }),
      ),
    )
    signIn(adminUser)
    const user = userEvent.setup()

    renderWithProviders(<ImportExportBar />)
    await user.upload(screen.getByLabelText(/import products csv/i), csvFile())

    expect(await screen.findByText(/Line 3 \(BAD-1\)/)).toBeInTheDocument()
  })

  it('surfaces a rejected upload', async () => {
    server.use(
      http.post('/api/v1/imports/products', () =>
        HttpResponse.json(
          { status: 422, detail: "CSV must have at least 'sku' and 'name' columns" },
          { status: 422 },
        ),
      ),
    )
    signIn(adminUser)
    const user = userEvent.setup()

    renderWithProviders(<ImportExportBar />)
    await user.upload(screen.getByLabelText(/import products csv/i), csvFile())

    expect(await screen.findByRole('alert')).toHaveTextContent('sku')
  })

  it('opens the presigned URL when exporting', async () => {
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)
    server.use(
      http.get('/api/v1/exports/inventory', () =>
        HttpResponse.json({
          objectKey: 'exports/inventory-1.csv',
          downloadUrl: 'https://s3.example/exports/inventory-1.csv?sig=abc',
          rowCount: 12,
          expiresInSeconds: 900,
        }),
      ),
    )
    signIn(staffUser)
    const user = userEvent.setup()

    renderWithProviders(<ImportExportBar />)
    await user.click(screen.getByRole('button', { name: /export inventory csv/i }))

    await waitFor(() =>
      expect(open).toHaveBeenCalledWith(
        'https://s3.example/exports/inventory-1.csv?sig=abc',
        '_blank',
        'noopener,noreferrer',
      ),
    )
    open.mockRestore()
  })

  it('does not offer import to STAFF, but export stays available', () => {
    signIn(staffUser)
    renderWithProviders(<ImportExportBar />)

    expect(screen.queryByLabelText(/import products csv/i)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /export inventory csv/i })).toBeInTheDocument()
  })
})
