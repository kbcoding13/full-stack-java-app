import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorMessage,
  Input,
  Pagination,
  Select,
  Spinner,
} from '@/components/ui'
import { useCategories } from '@/features/categories/hooks'
import { useAuth } from '@/features/auth/useAuth'
import { useDeleteProduct, useProducts } from './hooks'
import { ProductFormDialog } from './ProductFormDialog'

export function ProductsPage() {
  const { isAdmin } = useAuth()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [categoryId, setCategoryId] = useState<string>('')
  const [lowStock, setLowStock] = useState(false)
  const [isCreating, setIsCreating] = useState(false)

  const filters = {
    page,
    size: 20,
    search: search || undefined,
    categoryId: categoryId ? Number(categoryId) : undefined,
    lowStock: lowStock || undefined,
  }

  const { data, isPending, error } = useProducts(filters)
  const { data: categories } = useCategories({ size: 100 })
  const deleteProduct = useDeleteProduct()

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">Products</h1>
        {isAdmin && <Button onClick={() => setIsCreating(true)}>New product</Button>}
      </div>

      <Card className="flex flex-wrap items-end gap-3 p-4">
        <div className="min-w-56 flex-1">
          <Input
            placeholder="Search by name or SKU"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value)
              setPage(0)
            }}
          />
        </div>

        <Select
          className="w-48"
          value={categoryId}
          onChange={(event) => {
            setCategoryId(event.target.value)
            setPage(0)
          }}
        >
          <option value="">All categories</option>
          {categories?.content.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </Select>

        <label className="flex items-center gap-2 pb-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={lowStock}
            onChange={(event) => {
              setLowStock(event.target.checked)
              setPage(0)
            }}
            className="size-4 rounded border-slate-300"
          />
          Low stock only
        </label>
      </Card>

      <ErrorMessage error={error} />

      <Card className="p-0">
        {isPending ? (
          <Spinner label="Loading products" />
        ) : data && data.content.length > 0 ? (
          <>
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">SKU</th>
                  <th className="px-4 py-3">Name</th>
                  <th className="px-4 py-3">Category</th>
                  <th className="px-4 py-3 text-right">Price</th>
                  <th className="px-4 py-3 text-right">On hand</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((product) => (
                  <tr key={product.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-mono text-xs text-slate-600">{product.sku}</td>
                    <td className="px-4 py-3">
                      <Link
                        to={`/products/${product.id}`}
                        className="font-medium text-brand-700 hover:underline"
                      >
                        {product.name}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-slate-600">{product.categoryName ?? '—'}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{product.unitPrice}</td>
                    <td className="px-4 py-3 text-right">
                      <span className="tabular-nums">{product.quantityOnHand}</span>
                      {product.lowStock && (
                        <span className="ml-2">
                          <Badge tone="amber">Low</Badge>
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {isAdmin && (
                        <Button
                          variant="ghost"
                          onClick={() => {
                            if (confirm(`Delete ${product.name}?`)) {
                              deleteProduct.mutate(product.id)
                            }
                          }}
                        >
                          Delete
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />
          </>
        ) : (
          <EmptyState title="No products found" description="Adjust your filters or add a product." />
        )}
      </Card>

      {isCreating && <ProductFormDialog onClose={() => setIsCreating(false)} />}
    </div>
  )
}
