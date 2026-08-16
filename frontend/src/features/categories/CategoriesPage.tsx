import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import {
  Button,
  Card,
  EmptyState,
  ErrorMessage,
  Field,
  Input,
  Pagination,
  Spinner,
} from '@/components/ui'
import { useAuth } from '@/features/auth/useAuth'
import type { Category } from '@/api/types'
import { useCategories, useCreateCategory, useDeleteCategory, useUpdateCategory } from './hooks'

const schema = z.object({
  name: z.string().min(1, 'Name is required').max(120),
  description: z.string().max(500).optional(),
})

type FormValues = z.infer<typeof schema>

export function CategoriesPage() {
  const { isAdmin } = useAuth()
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Category | null>(null)
  const [isCreating, setIsCreating] = useState(false)

  const { data, isPending, error } = useCategories({ page, size: 20 })
  const deleteCategory = useDeleteCategory()

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">Categories</h1>
        {isAdmin && <Button onClick={() => setIsCreating(true)}>New category</Button>}
      </div>

      <ErrorMessage error={error} />
      <ErrorMessage error={deleteCategory.error} />

      <Card className="p-0">
        {isPending ? (
          <Spinner label="Loading categories" />
        ) : data && data.content.length > 0 ? (
          <>
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">Name</th>
                  <th className="px-4 py-3">Description</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((category) => (
                  <tr key={category.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-900">{category.name}</td>
                    <td className="px-4 py-3 text-slate-600">{category.description ?? '—'}</td>
                    <td className="px-4 py-3 text-right">
                      {isAdmin && (
                        <>
                          <Button variant="ghost" onClick={() => setEditing(category)}>
                            Edit
                          </Button>
                          <Button
                            variant="ghost"
                            onClick={() => {
                              if (confirm(`Delete ${category.name}?`)) {
                                deleteCategory.mutate(category.id)
                              }
                            }}
                          >
                            Delete
                          </Button>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />
          </>
        ) : (
          <EmptyState title="No categories yet" />
        )}
      </Card>

      {(isCreating || editing) && (
        <CategoryDialog
          category={editing ?? undefined}
          onClose={() => {
            setIsCreating(false)
            setEditing(null)
          }}
        />
      )}
    </div>
  )
}

function CategoryDialog({ category, onClose }: { category?: Category; onClose: () => void }) {
  const [submitError, setSubmitError] = useState<unknown>(null)
  const createCategory = useCreateCategory()
  const updateCategory = useUpdateCategory()

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: category?.name ?? '', description: category?.description ?? '' },
  })

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null)
    const body = { name: values.name, description: values.description || null }

    try {
      if (category) {
        await updateCategory.mutateAsync({ id: category.id, body })
      } else {
        await createCategory.mutateAsync(body)
      }
      onClose()
    } catch (error) {
      setSubmitError(error)
    }
  })

  return (
    <div className="fixed inset-0 z-10 flex items-center justify-center bg-slate-900/40 p-4">
      <Card className="w-full max-w-md">
        <h2 className="mb-4 text-lg font-semibold text-slate-900">
          {category ? 'Edit category' : 'New category'}
        </h2>

        <form onSubmit={onSubmit} className="space-y-4">
          <Field label="Name" error={errors.name?.message}>
            <Input {...register('name')} />
          </Field>

          <Field label="Description" error={errors.description?.message}>
            <Input {...register('description')} />
          </Field>

          <ErrorMessage error={submitError} />

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Saving…' : 'Save'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
