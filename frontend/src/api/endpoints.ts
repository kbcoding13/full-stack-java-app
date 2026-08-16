import { api, queryString, request } from './client'
import type {
  Attachment,
  AttachmentEntityType,
  Category,
  CategoryRequest,
  ExportResult,
  ImportResult,
  Page,
  PresignImageResponse,
  Product,
  ProductImage,
  ProductRequest,
  StockLevel,
  StockMovement,
  StockMovementRequest,
  Supplier,
  SupplierRequest,
  TokenResponse,
  User,
} from './types'

export interface PageParams {
  page?: number
  size?: number
  sort?: string
}

export interface ProductFilters extends PageParams {
  search?: string
  categoryId?: number | null
  supplierId?: number | null
  lowStock?: boolean
}

export const authApi = {
  register: (body: { email: string; password: string; fullName?: string }) =>
    api.post<TokenResponse>('/auth/register', body),
  login: (body: { email: string; password: string }) => api.post<TokenResponse>('/auth/login', body),
  me: () => api.get<User>('/auth/me'),
}

export const productsApi = {
  list: (filters: ProductFilters = {}) =>
    api.get<Page<Product>>(`/products${queryString({ ...filters })}`),
  get: (id: number) => api.get<Product>(`/products/${id}`),
  create: (body: ProductRequest) => api.post<Product>('/products', body),
  update: (id: number, body: ProductRequest) => api.put<Product>(`/products/${id}`, body),
  remove: (id: number) => api.delete<void>(`/products/${id}`),

  listImages: (id: number) => api.get<ProductImage[]>(`/products/${id}/images`),
  presignImage: (id: number, body: { filename: string; contentType: string; sizeBytes: number }) =>
    api.post<PresignImageResponse>(`/products/${id}/images/presign`, body),
  confirmImage: (
    id: number,
    body: { key: string; contentType?: string; sizeBytes?: number; makePrimary?: boolean },
  ) => api.post<ProductImage>(`/products/${id}/images/confirm`, body),
  removeImage: (id: number, imageId: number) => api.delete<void>(`/products/${id}/images/${imageId}`),
}

export const categoriesApi = {
  list: (params: PageParams & { search?: string } = {}) =>
    api.get<Page<Category>>(`/categories${queryString({ ...params })}`),
  get: (id: number) => api.get<Category>(`/categories/${id}`),
  create: (body: CategoryRequest) => api.post<Category>('/categories', body),
  update: (id: number, body: CategoryRequest) => api.put<Category>(`/categories/${id}`, body),
  remove: (id: number) => api.delete<void>(`/categories/${id}`),
}

export const suppliersApi = {
  list: (params: PageParams & { search?: string } = {}) =>
    api.get<Page<Supplier>>(`/suppliers${queryString({ ...params })}`),
  get: (id: number) => api.get<Supplier>(`/suppliers/${id}`),
  create: (body: SupplierRequest) => api.post<Supplier>('/suppliers', body),
  update: (id: number, body: SupplierRequest) => api.put<Supplier>(`/suppliers/${id}`, body),
  remove: (id: number) => api.delete<void>(`/suppliers/${id}`),
}

export const attachmentsApi = {
  list: (entityType: AttachmentEntityType, entityId: number) =>
    api.get<Attachment[]>(`/attachments${queryString({ entityType, entityId })}`),
  /** Proxy upload — the backend checks content type and size before storing. */
  upload: (entityType: AttachmentEntityType, entityId: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    form.append('entityType', entityType)
    form.append('entityId', String(entityId))
    return request<Attachment>('/attachments', { method: 'POST', body: form })
  },
  remove: (id: number) => api.delete<void>(`/attachments/${id}`),
}

export const importExportApi = {
  /** Proxy upload — the backend validates and parses the CSV before trusting it. */
  importProducts: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<ImportResult>('/imports/products', { method: 'POST', body: form })
  },
  /** Returns a presigned URL; the CSV bytes never travel back through the API. */
  exportInventory: () => api.get<ExportResult>('/exports/inventory'),
}

export const stockApi = {
  /** The only call that changes stock. */
  record: (body: StockMovementRequest) => api.post<StockMovement>('/stock-movements', body),
  listForProduct: (productId: number, params: PageParams = {}) =>
    api.get<Page<StockMovement>>(`/products/${productId}/movements${queryString({ ...params })}`),
  level: (productId: number) => api.get<StockLevel>(`/products/${productId}/stock`),
}
