import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Button, Card, ErrorMessage, Field, Input, Select } from '@/components/ui'
import { useCategories } from '@/features/categories/hooks'
import { useSuppliers } from '@/features/suppliers/hooks'
import type { Product } from '@/api/types'
import { useCreateProduct, useUpdateProduct } from './hooks'

/**
 * Single source of client-side validation truth, mirroring the backend's ProductRequest.
 * There is no quantity field here on purpose — stock only moves through the ledger.
 */
const schema = z.object({
  sku: z.string().min(1, 'SKU is required').max(64),
  name: z.string().min(1, 'Name is required').max(200),
  description: z.string().optional(),
  unitPrice: z
    .string()
    .min(1, 'Price is required')
    .regex(/^\d+(\.\d{1,2})?$/, 'Use a number with up to 2 decimals'),
  reorderLevel: z.coerce.number().int().min(0, 'Must be zero or more'),
  categoryId: z.string().optional(),
  supplierId: z.string().optional(),
})

type FormValues = z.input<typeof schema>

export function ProductFormDialog({ product, onClose }: { product?: Product; onClose: () => void }) {
  const isEdit = product !== undefined
  const [submitError, setSubmitError] = useState<unknown>(null)

  const { data: categories } = useCategories({ size: 100 })
  const { data: suppliers } = useSuppliers({ size: 100 })

  const createProduct = useCreateProduct()
  const updateProduct = useUpdateProduct(product?.id ?? 0)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      sku: product?.sku ?? '',
      name: product?.name ?? '',
      description: product?.description ?? '',
      unitPrice: product?.unitPrice ?? '0.00',
      reorderLevel: product?.reorderLevel ?? 0,
      categoryId: product?.categoryId ? String(product.categoryId) : '',
      supplierId: product?.supplierId ? String(product.supplierId) : '',
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null)
    const body = {
      sku: values.sku,
      name: values.name,
      description: values.description || null,
      unitPrice: values.unitPrice,
      reorderLevel: Number(values.reorderLevel),
      categoryId: values.categoryId ? Number(values.categoryId) : null,
      supplierId: values.supplierId ? Number(values.supplierId) : null,
    }

    try {
      if (isEdit) {
        await updateProduct.mutateAsync(body)
      } else {
        await createProduct.mutateAsync(body)
      }
      onClose()
    } catch (error) {
      setSubmitError(error)
    }
  })

  return (
    <div className="fixed inset-0 z-10 flex items-center justify-center bg-slate-900/40 p-4">
      <Card className="max-h-[90vh] w-full max-w-lg overflow-y-auto">
        <h2 className="mb-4 text-lg font-semibold text-slate-900">
          {isEdit ? 'Edit product' : 'New product'}
        </h2>

        <form onSubmit={onSubmit} className="space-y-4">
          <Field label="SKU" error={errors.sku?.message}>
            <Input {...register('sku')} />
          </Field>

          <Field label="Name" error={errors.name?.message}>
            <Input {...register('name')} />
          </Field>

          <Field label="Description" error={errors.description?.message}>
            <Input {...register('description')} />
          </Field>

          <div className="grid grid-cols-2 gap-4">
            <Field label="Unit price" error={errors.unitPrice?.message}>
              <Input inputMode="decimal" {...register('unitPrice')} />
            </Field>

            <Field label="Reorder level" error={errors.reorderLevel?.message}>
              <Input type="number" min={0} {...register('reorderLevel')} />
            </Field>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <Field label="Category" error={errors.categoryId?.message}>
              <Select {...register('categoryId')}>
                <option value="">None</option>
                {categories?.content.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </Select>
            </Field>

            <Field label="Supplier" error={errors.supplierId?.message}>
              <Select {...register('supplierId')}>
                <option value="">None</option>
                {suppliers?.content.map((supplier) => (
                  <option key={supplier.id} value={supplier.id}>
                    {supplier.name}
                  </option>
                ))}
              </Select>
            </Field>
          </div>

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
