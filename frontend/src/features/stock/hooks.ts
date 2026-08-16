import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { stockApi, type PageParams } from '@/api/endpoints'
import type { StockMovementRequest } from '@/api/types'
import { productKeys } from '@/features/products/hooks'

export const stockKeys = {
  all: ['stock'] as const,
  movements: (productId: number, params: PageParams) =>
    [...stockKeys.all, 'movements', productId, params] as const,
  level: (productId: number) => [...stockKeys.all, 'level', productId] as const,
}

export function useMovements(productId: number | undefined, params: PageParams = {}) {
  return useQuery({
    queryKey: stockKeys.movements(productId!, params),
    queryFn: () => stockApi.listForProduct(productId!, params),
    enabled: productId !== undefined,
  })
}

/**
 * Recording a movement changes derived stock, so this invalidates product queries too —
 * quantityOnHand on any product list is now stale.
 */
export function useRecordMovement() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: StockMovementRequest) => stockApi.record(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: stockKeys.all })
      queryClient.invalidateQueries({ queryKey: productKeys.all })
    },
  })
}
