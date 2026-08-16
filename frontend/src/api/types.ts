/**
 * Typed mirrors of the backend DTOs. Keep these in step with the `*Dtos.java` records
 * under `backend/src/main/java/com/example/inventory/`.
 */

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export type Role = 'ADMIN' | 'STAFF'

export interface User {
  id: number
  email: string
  fullName: string | null
  role: Role
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInSeconds: number
  user: User
}

export interface Category {
  id: number
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface CategoryRequest {
  name: string
  description?: string | null
}

export interface Supplier {
  id: number
  name: string
  contactEmail: string | null
  contactPhone: string | null
  address: string | null
  createdAt: string
  updatedAt: string
}

export interface SupplierRequest {
  name: string
  contactEmail?: string | null
  contactPhone?: string | null
  address?: string | null
}

/** Note: quantityOnHand is derived from the ledger and is never writable. */
export interface Product {
  id: number
  sku: string
  name: string
  description: string | null
  unitPrice: string
  reorderLevel: number
  categoryId: number | null
  categoryName: string | null
  supplierId: number | null
  supplierName: string | null
  quantityOnHand: number
  lowStock: boolean
  createdAt: string
  updatedAt: string
}

export interface ProductRequest {
  sku: string
  name: string
  description?: string | null
  unitPrice: string
  reorderLevel: number
  categoryId?: number | null
  supplierId?: number | null
}

export type MovementType = 'IN' | 'OUT' | 'ADJUST'

export interface StockMovement {
  id: number
  productId: number
  productSku: string
  productName: string
  type: MovementType
  quantity: number
  quantityDelta: number
  reason: string | null
  reference: string | null
  occurredAt: string
  createdBy: string | null
}

export interface StockMovementRequest {
  productId: number
  type: MovementType
  quantity: number
  decrease?: boolean
  reason?: string | null
  reference?: string | null
  occurredAt?: string | null
}

export interface StockLevel {
  productId: number
  quantityOnHand: number
  reorderLevel: number
  lowStock: boolean
}

export interface ProductImage {
  id: number
  key: string
  downloadUrl: string
  contentType: string | null
  sizeBytes: number | null
  primary: boolean
}

export interface PresignImageResponse {
  uploadUrl: string
  key: string
  expiresInSeconds: number
}

/** RFC 7807 problem detail, as returned by the backend's @RestControllerAdvice. */
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  path?: string
  errors?: Record<string, string>
}
