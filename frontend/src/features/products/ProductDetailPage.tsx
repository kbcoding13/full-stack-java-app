import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Badge, Button, Card, EmptyState, ErrorMessage, Pagination, Spinner } from '@/components/ui'
import { useAuth } from '@/features/auth/useAuth'
import { useMovements } from '@/features/stock/hooks'
import { StockMovementDialog } from '@/features/stock/StockMovementDialog'
import { ProductFormDialog } from './ProductFormDialog'
import { ProductImages } from './ProductImages'
import { useProduct } from './hooks'

const movementTones = { IN: 'green', OUT: 'red', ADJUST: 'amber' } as const

export function ProductDetailPage() {
  const { id } = useParams<{ id: string }>()
  const productId = id ? Number(id) : undefined
  const { isAdmin } = useAuth()

  const [movementsPage, setMovementsPage] = useState(0)
  const [isEditing, setIsEditing] = useState(false)
  const [isRecording, setIsRecording] = useState(false)

  const { data: product, isPending, error } = useProduct(productId)
  const { data: movements } = useMovements(productId, { page: movementsPage, size: 10 })

  if (isPending) return <Spinner label="Loading product" />
  if (error) return <ErrorMessage error={error} />
  if (!product) return <EmptyState title="Product not found" />

  return (
    <div className="space-y-4">
      <Link to="/products" className="text-sm text-brand-700 hover:underline">
        ← Back to products
      </Link>

      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">{product.name}</h1>
          <p className="font-mono text-sm text-slate-500">{product.sku}</p>
        </div>
        <div className="flex gap-2">
          <Button onClick={() => setIsRecording(true)}>Record movement</Button>
          {isAdmin && (
            <Button variant="secondary" onClick={() => setIsEditing(true)}>
              Edit
            </Button>
          )}
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <p className="text-sm text-slate-500">On hand</p>
          <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">
            {product.quantityOnHand}
          </p>
          {product.lowStock && (
            <p className="mt-2">
              <Badge tone="amber">At or below reorder level ({product.reorderLevel})</Badge>
            </p>
          )}
        </Card>

        <Card>
          <p className="text-sm text-slate-500">Unit price</p>
          <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">{product.unitPrice}</p>
        </Card>

        <Card>
          <p className="text-sm text-slate-500">Category / Supplier</p>
          <p className="mt-1 text-sm text-slate-900">{product.categoryName ?? 'No category'}</p>
          <p className="text-sm text-slate-900">{product.supplierName ?? 'No supplier'}</p>
        </Card>
      </div>

      {product.description && (
        <Card>
          <p className="text-sm text-slate-700">{product.description}</p>
        </Card>
      )}

      <ProductImages productId={product.id} />

      <Card className="p-0">
        <h2 className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-900">
          Stock ledger
        </h2>

        {movements && movements.content.length > 0 ? (
          <>
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">When</th>
                  <th className="px-4 py-3">Type</th>
                  <th className="px-4 py-3 text-right">Change</th>
                  <th className="px-4 py-3">Reason</th>
                  <th className="px-4 py-3">Reference</th>
                  <th className="px-4 py-3">By</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {movements.content.map((movement) => (
                  <tr key={movement.id}>
                    <td className="px-4 py-3 text-slate-600">
                      {new Date(movement.occurredAt).toLocaleString()}
                    </td>
                    <td className="px-4 py-3">
                      <Badge tone={movementTones[movement.type]}>{movement.type}</Badge>
                    </td>
                    <td className="px-4 py-3 text-right font-medium tabular-nums">
                      {movement.quantityDelta > 0 ? `+${movement.quantityDelta}` : movement.quantityDelta}
                    </td>
                    <td className="px-4 py-3 text-slate-600">{movement.reason ?? '—'}</td>
                    <td className="px-4 py-3 text-slate-600">{movement.reference ?? '—'}</td>
                    <td className="px-4 py-3 text-slate-600">{movement.createdBy ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination
              page={movements.number}
              totalPages={movements.totalPages}
              onChange={setMovementsPage}
            />
          </>
        ) : (
          <EmptyState title="No movements yet" description="Stock changes will appear here." />
        )}
      </Card>

      {isEditing && <ProductFormDialog product={product} onClose={() => setIsEditing(false)} />}
      {isRecording && <StockMovementDialog product={product} onClose={() => setIsRecording(false)} />}
    </div>
  )
}
