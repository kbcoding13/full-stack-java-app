import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { suppliersApi, type PageParams } from '@/api/endpoints'
import type { SupplierRequest } from '@/api/types'

export const supplierKeys = {
  all: ['suppliers'] as const,
  list: (params: PageParams & { search?: string }) => [...supplierKeys.all, 'list', params] as const,
}

export function useSuppliers(params: PageParams & { search?: string } = {}) {
  return useQuery({
    queryKey: supplierKeys.list(params),
    queryFn: () => suppliersApi.list(params),
  })
}

export function useCreateSupplier() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: SupplierRequest) => suppliersApi.create(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: supplierKeys.all }),
  })
}

export function useUpdateSupplier() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: SupplierRequest }) => suppliersApi.update(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: supplierKeys.all }),
  })
}

export function useDeleteSupplier() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => suppliersApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: supplierKeys.all }),
  })
}
