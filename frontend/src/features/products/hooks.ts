import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { productsApi, type ProductFilters } from '@/api/endpoints'
import type { ProductRequest } from '@/api/types'

export const productKeys = {
  all: ['products'] as const,
  list: (filters: ProductFilters) => [...productKeys.all, 'list', filters] as const,
  detail: (id: number) => [...productKeys.all, 'detail', id] as const,
  images: (id: number) => [...productKeys.all, 'images', id] as const,
}

export function useProducts(filters: ProductFilters) {
  return useQuery({
    queryKey: productKeys.list(filters),
    queryFn: () => productsApi.list(filters),
  })
}

export function useProduct(id: number | undefined) {
  return useQuery({
    queryKey: productKeys.detail(id!),
    queryFn: () => productsApi.get(id!),
    enabled: id !== undefined,
  })
}

export function useCreateProduct() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: ProductRequest) => productsApi.create(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: productKeys.all }),
  })
}

export function useUpdateProduct(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: ProductRequest) => productsApi.update(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: productKeys.all }),
  })
}

export function useDeleteProduct() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => productsApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: productKeys.all }),
  })
}

export function useProductImages(id: number | undefined) {
  return useQuery({
    queryKey: productKeys.images(id!),
    queryFn: () => productsApi.listImages(id!),
    enabled: id !== undefined,
  })
}
