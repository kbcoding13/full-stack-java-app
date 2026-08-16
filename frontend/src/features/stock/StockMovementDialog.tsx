import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Button, Card, ErrorMessage, Field, Input, Select } from '@/components/ui'
import type { Product } from '@/api/types'
import { useRecordMovement } from './hooks'

const schema = z.object({
  type: z.enum(['IN', 'OUT', 'ADJUST']),
  quantity: z.coerce.number().int().min(1, 'Enter at least 1'),
  decrease: z.boolean().optional(),
  reason: z.string().max(255).optional(),
  reference: z.string().max(120).optional(),
})

type FormValues = z.input<typeof schema>

/**
 * The only way the UI changes stock. Quantity is always entered positive; the movement
 * type carries the direction, matching the backend's ledger contract.
 */
export function StockMovementDialog({ product, onClose }: { product: Product; onClose: () => void }) {
  const [submitError, setSubmitError] = useState<unknown>(null)
  const recordMovement = useRecordMovement()

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { type: 'IN', quantity: 1, decrease: false },
  })

  const type = watch('type')

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null)
    try {
      await recordMovement.mutateAsync({
        productId: product.id,
        type: values.type,
        quantity: Number(values.quantity),
        decrease: values.type === 'ADJUST' ? Boolean(values.decrease) : false,
        reason: values.reason || null,
        reference: values.reference || null,
      })
      onClose()
    } catch (error) {
      setSubmitError(error)
    }
  })

  return (
    <div className="fixed inset-0 z-10 flex items-center justify-center bg-slate-900/40 p-4">
      <Card className="w-full max-w-md">
        <h2 className="text-lg font-semibold text-slate-900">Record stock movement</h2>
        <p className="mt-1 mb-4 text-sm text-slate-500">
          {product.name} · currently {product.quantityOnHand} on hand
        </p>

        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <Field label="Type" error={errors.type?.message}>
            <Select {...register('type')}>
              <option value="IN">Stock in</option>
              <option value="OUT">Stock out</option>
              <option value="ADJUST">Adjustment</option>
            </Select>
          </Field>

          <Field label="Quantity" error={errors.quantity?.message}>
            <Input type="number" min={1} {...register('quantity')} />
          </Field>

          {type === 'ADJUST' && (
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" className="size-4 rounded border-slate-300" {...register('decrease')} />
              This adjustment reduces stock
            </label>
          )}

          <Field label="Reason" error={errors.reason?.message}>
            <Input placeholder="Delivery, sale, stock count…" {...register('reason')} />
          </Field>

          <Field label="Reference" error={errors.reference?.message}>
            <Input placeholder="PO number, invoice…" {...register('reference')} />
          </Field>

          <ErrorMessage error={submitError} />

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Recording…' : 'Record movement'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
