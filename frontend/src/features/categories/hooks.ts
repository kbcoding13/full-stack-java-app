import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { categoriesApi, type PageParams } from '@/api/endpoints'
import type { CategoryRequest } from '@/api/types'

export const categoryKeys = {
  all: ['categories'] as const,
  list: (params: PageParams & { search?: string }) => [...categoryKeys.all, 'list', params] as const,
}

export function useCategories(params: PageParams & { search?: string } = {}) {
  return useQuery({
    queryKey: categoryKeys.list(params),
    queryFn: () => categoriesApi.list(params),
  })
}

export function useCreateCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CategoryRequest) => categoriesApi.create(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: categoryKeys.all }),
  })
}

export function useUpdateCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: CategoryRequest }) => categoriesApi.update(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: categoryKeys.all }),
  })
}

export function useDeleteCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => categoriesApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: categoryKeys.all }),
  })
}
