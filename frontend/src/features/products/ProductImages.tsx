import { useState, type ChangeEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { uploadToPresignedUrl } from '@/api/client'
import { productsApi } from '@/api/endpoints'
import { Button, Card, ErrorMessage, Spinner } from '@/components/ui'
import { useAuth } from '@/features/auth/useAuth'
import { productKeys, useProductImages } from './hooks'

/**
 * Three-step presigned upload: ask the API for a PUT URL, send the bytes straight to S3,
 * then confirm the key so the backend persists it. Image bytes never touch our API.
 */
export function ProductImages({ productId }: { productId: number }) {
  const { isAdmin } = useAuth()
  const queryClient = useQueryClient()
  const { data: images, isPending } = useProductImages(productId)

  const [isUploading, setIsUploading] = useState(false)
  const [uploadError, setUploadError] = useState<unknown>(null)

  async function handleFileSelected(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return

    setIsUploading(true)
    setUploadError(null)

    try {
      const presigned = await productsApi.presignImage(productId, {
        filename: file.name,
        contentType: file.type,
        sizeBytes: file.size,
      })

      await uploadToPresignedUrl(presigned.uploadUrl, file)

      await productsApi.confirmImage(productId, {
        key: presigned.key,
        contentType: file.type,
        sizeBytes: file.size,
      })

      await queryClient.invalidateQueries({ queryKey: productKeys.images(productId) })
    } catch (error) {
      setUploadError(error)
    } finally {
      setIsUploading(false)
      event.target.value = ''
    }
  }

  async function handleDelete(imageId: number) {
    setUploadError(null)
    try {
      await productsApi.removeImage(productId, imageId)
      await queryClient.invalidateQueries({ queryKey: productKeys.images(productId) })
    } catch (error) {
      setUploadError(error)
    }
  }

  return (
    <Card>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-900">Images</h2>
        {isAdmin && (
          <label className="cursor-pointer text-sm font-medium text-brand-700 hover:text-brand-800">
            {isUploading ? 'Uploading…' : 'Upload image'}
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="hidden"
              disabled={isUploading}
              onChange={handleFileSelected}
            />
          </label>
        )}
      </div>

      <ErrorMessage error={uploadError} />

      {isPending ? (
        <Spinner label="Loading images" />
      ) : images && images.length > 0 ? (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {images.map((image) => (
            <li key={image.id} className="group relative">
              <img
                src={image.downloadUrl}
                alt=""
                className="aspect-square w-full rounded-md object-cover ring-1 ring-slate-200"
              />
              {isAdmin && (
                <Button
                  variant="danger"
                  className="absolute right-1 top-1 hidden px-2 py-1 text-xs group-hover:inline-flex"
                  onClick={() => handleDelete(image.id)}
                >
                  Remove
                </Button>
              )}
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-slate-500">No images yet.</p>
      )}
    </Card>
  )
}
