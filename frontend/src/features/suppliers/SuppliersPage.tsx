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
import type { Supplier } from '@/api/types'
import { useCreateSupplier, useDeleteSupplier, useSuppliers, useUpdateSupplier } from './hooks'

const schema = z.object({
  name: z.string().min(1, 'Name is required').max(160),
  contactEmail: z.union([z.email('Enter a valid email'), z.literal('')]).optional(),
  contactPhone: z.string().max(40).optional(),
  address: z.string().max(500).optional(),
})

type FormValues = z.infer<typeof schema>

export function SuppliersPage() {
  const { isAdmin } = useAuth()
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Supplier | null>(null)
  const [isCreating, setIsCreating] = useState(false)

  const { data, isPending, error } = useSuppliers({ page, size: 20 })
  const deleteSupplier = useDeleteSupplier()

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">Suppliers</h1>
        {isAdmin && <Button onClick={() => setIsCreating(true)}>New supplier</Button>}
      </div>

      <ErrorMessage error={error} />
      <ErrorMessage error={deleteSupplier.error} />

      <Card className="p-0">
        {isPending ? (
          <Spinner label="Loading suppliers" />
        ) : data && data.content.length > 0 ? (
          <>
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">Name</th>
                  <th className="px-4 py-3">Email</th>
                  <th className="px-4 py-3">Phone</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((supplier) => (
                  <tr key={supplier.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-900">{supplier.name}</td>
                    <td className="px-4 py-3 text-slate-600">{supplier.contactEmail ?? '—'}</td>
                    <td className="px-4 py-3 text-slate-600">{supplier.contactPhone ?? '—'}</td>
                    <td className="px-4 py-3 text-right">
                      {isAdmin && (
                        <>
                          <Button variant="ghost" onClick={() => setEditing(supplier)}>
                            Edit
                          </Button>
                          <Button
                            variant="ghost"
                            onClick={() => {
                              if (confirm(`Delete ${supplier.name}?`)) {
                                deleteSupplier.mutate(supplier.id)
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
          <EmptyState title="No suppliers yet" />
        )}
      </Card>

      {(isCreating || editing) && (
        <SupplierDialog
          supplier={editing ?? undefined}
          onClose={() => {
            setIsCreating(false)
            setEditing(null)
          }}
        />
      )}
    </div>
  )
}

function SupplierDialog({ supplier, onClose }: { supplier?: Supplier; onClose: () => void }) {
  const [submitError, setSubmitError] = useState<unknown>(null)
  const createSupplier = useCreateSupplier()
  const updateSupplier = useUpdateSupplier()

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: supplier?.name ?? '',
      contactEmail: supplier?.contactEmail ?? '',
      contactPhone: supplier?.contactPhone ?? '',
      address: supplier?.address ?? '',
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null)
    const body = {
      name: values.name,
      contactEmail: values.contactEmail || null,
      contactPhone: values.contactPhone || null,
      address: values.address || null,
    }

    try {
      if (supplier) {
        await updateSupplier.mutateAsync({ id: supplier.id, body })
      } else {
        await createSupplier.mutateAsync(body)
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
          {supplier ? 'Edit supplier' : 'New supplier'}
        </h2>

        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <Field label="Name" error={errors.name?.message}>
            <Input {...register('name')} />
          </Field>

          <Field label="Contact email" error={errors.contactEmail?.message}>
            <Input type="email" {...register('contactEmail')} />
          </Field>

          <Field label="Contact phone" error={errors.contactPhone?.message}>
            <Input {...register('contactPhone')} />
          </Field>

          <Field label="Address" error={errors.address?.message}>
            <Input {...register('address')} />
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
