import { useState, type ChangeEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { importExportApi } from '@/api/endpoints'
import type { ImportResult } from '@/api/types'
import { Button, Card, ErrorMessage } from '@/components/ui'
import { useAuth } from '@/features/auth/useAuth'
import { productKeys } from './hooks'

/**
 * CSV import is a proxy upload (the backend must validate and parse it), while export
 * returns a presigned URL that we open directly — the bytes never come back through the API.
 */
export function ImportExportBar() {
  const { isAdmin } = useAuth()
  const queryClient = useQueryClient()

  const [isBusy, setIsBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [result, setResult] = useState<ImportResult | null>(null)

  async function handleImport(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return

    setIsBusy(true)
    setError(null)
    setResult(null)

    try {
      setResult(await importExportApi.importProducts(file))
      await queryClient.invalidateQueries({ queryKey: productKeys.all })
    } catch (importError) {
      setError(importError)
    } finally {
      setIsBusy(false)
      event.target.value = ''
    }
  }

  async function handleExport() {
    setIsBusy(true)
    setError(null)

    try {
      const { downloadUrl } = await importExportApi.exportInventory()
      window.open(downloadUrl, '_blank', 'noopener,noreferrer')
    } catch (exportError) {
      setError(exportError)
    } finally {
      setIsBusy(false)
    }
  }

  return (
    <Card className="space-y-3 p-4">
      <div className="flex flex-wrap items-center gap-3">
        <Button variant="secondary" onClick={handleExport} disabled={isBusy}>
          Export inventory CSV
        </Button>

        {isAdmin && (
          <label className="inline-flex cursor-pointer items-center rounded-md bg-white px-3 py-2 text-sm font-medium text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50">
            {isBusy ? 'Working…' : 'Import products CSV'}
            <input type="file" accept=".csv,text/csv" className="hidden" disabled={isBusy} onChange={handleImport} />
          </label>
        )}

        {isAdmin && (
          <span className="text-xs text-slate-500">
            Columns: sku, name, description, category, supplier, unitPrice, reorderLevel
          </span>
        )}
      </div>

      <ErrorMessage error={error} />

      {result && (
        <div role="status" className="rounded-md bg-slate-50 p-3 text-sm ring-1 ring-slate-200">
          <p className="font-medium text-slate-900">
            Imported {result.totalRows} row(s): {result.created} created, {result.updated} updated,{' '}
            {result.skipped} skipped.
          </p>

          {result.errors.length > 0 && (
            <ul className="mt-2 space-y-1 text-red-700">
              {result.errors.slice(0, 10).map((rowError) => (
                <li key={`${rowError.line}-${rowError.sku ?? ''}`}>
                  Line {rowError.line}
                  {rowError.sku ? ` (${rowError.sku})` : ''}: {rowError.message}
                </li>
              ))}
              {result.errors.length > 10 && <li>…and {result.errors.length - 10} more.</li>}
            </ul>
          )}
        </div>
      )}
    </Card>
  )
}
