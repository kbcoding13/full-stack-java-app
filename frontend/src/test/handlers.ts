import { http, HttpResponse } from 'msw'
import type { Category, Page, Product } from '@/api/types'

export function pageOf<T>(content: T[]): Page<T> {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    number: 0,
    size: 20,
    first: true,
    last: true,
  }
}

export const testProduct: Product = {
  id: 1,
  sku: 'WID-001',
  name: 'Widget',
  description: 'A test widget',
  unitPrice: '9.99',
  reorderLevel: 5,
  categoryId: 1,
  categoryName: 'Hardware',
  supplierId: null,
  supplierName: null,
  quantityOnHand: 3,
  lowStock: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

export const testCategory: Category = {
  id: 1,
  name: 'Hardware',
  description: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

export const handlers = [
  http.get('/api/v1/products', () => HttpResponse.json(pageOf([testProduct]))),
  http.get('/api/v1/products/:id', () => HttpResponse.json(testProduct)),
  http.get('/api/v1/products/:id/movements', () => HttpResponse.json(pageOf([]))),
  http.get('/api/v1/products/:id/images', () => HttpResponse.json([])),
  http.get('/api/v1/attachments', () => HttpResponse.json([])),
  http.get('/api/v1/categories', () => HttpResponse.json(pageOf([testCategory]))),
  http.get('/api/v1/suppliers', () => HttpResponse.json(pageOf([]))),
]
