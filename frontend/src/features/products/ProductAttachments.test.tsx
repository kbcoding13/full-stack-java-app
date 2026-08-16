import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/server'
import { adminUser, renderWithProviders, signIn, staffUser } from '@/test/render'
import { ProductAttachments } from './ProductAttachments'

const attachment = {
  id: 7,
  entityType: 'PRODUCT' as const,
  entityId: 1,
  originalName: 'delivery-note.pdf',
  contentType: 'application/pdf',
  sizeBytes: 2048,
  downloadUrl: 'https://s3.example/attachments/product/1/x.pdf?sig=abc',
  createdAt: '2026-02-01T00:00:00Z',
  createdBy: 'staff@example.com',
}

const pdf = () => new File(['%PDF'], 'delivery-note.pdf', { type: 'application/pdf' })

describe('ProductAttachments', () => {
  it('lists documents with a presigned download link', async () => {
    server.use(http.get('/api/v1/attachments', () => HttpResponse.json([attachment])))
    signIn(staffUser)

    renderWithProviders(<ProductAttachments productId={1} />)

    const link = await screen.findByRole('link', { name: 'delivery-note.pdf' })
    expect(link).toHaveAttribute('href', attachment.downloadUrl)
    expect(screen.getByText(/2 KB/)).toBeInTheDocument()
  })

  it('shows an empty state when nothing is attached', async () => {
    signIn(staffUser)
    renderWithProviders(<ProductAttachments productId={1} />)

    expect(await screen.findByText('No documents attached.')).toBeInTheDocument()
  })

  it('uploads a document and refreshes the list', async () => {
    let uploaded = false
    server.use(
      http.post('/api/v1/attachments', () => {
        uploaded = true
        return HttpResponse.json(attachment, { status: 201 })
      }),
      http.get('/api/v1/attachments', () => HttpResponse.json(uploaded ? [attachment] : [])),
    )
    signIn(staffUser)
    const user = userEvent.setup()

    renderWithProviders(<ProductAttachments productId={1} />)
    await screen.findByText('No documents attached.')

    await user.upload(screen.getByLabelText(/attach document/i), pdf())

    expect(await screen.findByRole('link', { name: 'delivery-note.pdf' })).toBeInTheDocument()
  })

  it('surfaces a rejected content type from the backend', async () => {
    server.use(
      http.post('/api/v1/attachments', () =>
        HttpResponse.json(
          { status: 422, detail: 'Unsupported attachment type. Allowed: application/pdf' },
          { status: 422 },
        ),
      ),
    )
    signIn(staffUser)
    const user = userEvent.setup()

    renderWithProviders(<ProductAttachments productId={1} />)
    await user.upload(screen.getByLabelText(/attach document/i), pdf())

    expect(await screen.findByRole('alert')).toHaveTextContent('Unsupported attachment type')
  })

  it('offers removal to ADMIN only', async () => {
    server.use(http.get('/api/v1/attachments', () => HttpResponse.json([attachment])))

    signIn(staffUser)
    const staffView = renderWithProviders(<ProductAttachments productId={1} />)
    await screen.findByRole('link', { name: 'delivery-note.pdf' })
    expect(screen.queryByRole('button', { name: /remove/i })).not.toBeInTheDocument()
    staffView.unmount()

    signIn(adminUser)
    renderWithProviders(<ProductAttachments productId={1} />)
    await screen.findByRole('link', { name: 'delivery-note.pdf' })
    await waitFor(() => expect(screen.getByRole('button', { name: /remove/i })).toBeInTheDocument())
  })
})
