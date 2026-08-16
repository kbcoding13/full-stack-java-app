import { useState, type ChangeEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { attachmentsApi } from '@/api/endpoints'
import { Button, Card, ErrorMessage, Spinner } from '@/components/ui'
import { useAuth } from '@/features/auth/useAuth'

const attachmentKeys = {
  forProduct: (productId: number) => ['attachments', 'PRODUCT', productId] as const,
}

function formatSize(bytes: number | null) {
  if (bytes === null) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/**
 * Documents proxy through the API rather than going straight to S3, because the backend has
 * to check content type and size before storing them.
 */
export function ProductAttachments({ productId }: { productId: number }) {
  const { isAdmin } = useAuth()
  const queryClient = useQueryClient()
  const [error, setError] = useState<unknown>(null)

  const { data: attachments, isPending } = useQuery({
    queryKey: attachmentKeys.forProduct(productId),
    queryFn: () => attachmentsApi.list('PRODUCT', productId),
  })

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: attachmentKeys.forProduct(productId) })

  const upload = useMutation({
    mutationFn: (file: File) => attachmentsApi.upload('PRODUCT', productId, file),
    onSuccess: invalidate,
  })

  const remove = useMutation({
    mutationFn: (id: number) => attachmentsApi.remove(id),
    onSuccess: invalidate,
  })

  async function handleFileSelected(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return

    setError(null)
    try {
      await upload.mutateAsync(file)
    } catch (uploadError) {
      setError(uploadError)
    } finally {
      event.target.value = ''
    }
  }

  return (
    <Card>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-900">Documents</h2>

        <label className="cursor-pointer text-sm font-medium text-brand-700 hover:text-brand-800">
          {upload.isPending ? 'Uploading…' : 'Attach document'}
          <input
            type="file"
            accept="application/pdf,image/jpeg,image/png,text/csv,text/plain"
            className="hidden"
            disabled={upload.isPending}
            onChange={handleFileSelected}
          />
        </label>
      </div>

      <ErrorMessage error={error} />
      <ErrorMessage error={remove.error} />

      {isPending ? (
        <Spinner label="Loading documents" />
      ) : attachments && attachments.length > 0 ? (
        <ul className="divide-y divide-slate-100">
          {attachments.map((attachment) => (
            <li key={attachment.id} className="flex items-center justify-between py-2">
              <div className="min-w-0">
                <a
                  href={attachment.downloadUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="truncate text-sm font-medium text-brand-700 hover:underline"
                >
                  {attachment.originalName ?? 'Untitled'}
                </a>
                <p className="text-xs text-slate-500">
                  {formatSize(attachment.sizeBytes)}
                  {attachment.createdBy ? ` · ${attachment.createdBy}` : ''}
                </p>
              </div>

              {isAdmin && (
                <Button variant="ghost" onClick={() => remove.mutate(attachment.id)}>
                  Remove
                </Button>
              )}
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-slate-500">No documents attached.</p>
      )}
    </Card>
  )
}
