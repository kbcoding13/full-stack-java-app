import type { ProblemDetail, TokenResponse } from './types'

const BASE_URL = '/api/v1'

const ACCESS_TOKEN_KEY = 'inventory.accessToken'
const REFRESH_TOKEN_KEY = 'inventory.refreshToken'

export const tokenStore = {
  get access() {
    return localStorage.getItem(ACCESS_TOKEN_KEY)
  },
  get refresh() {
    return localStorage.getItem(REFRESH_TOKEN_KEY)
  },
  set(tokens: { accessToken: string; refreshToken: string }) {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
  },
  clear() {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  },
}

/** Error carrying the backend's ProblemDetail so components can show field-level messages. */
export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${problem.status}`)
    this.name = 'ApiError'
    this.status = problem.status
    this.problem = problem
  }

  /** Validation messages keyed by field name, when the backend returned any. */
  get fieldErrors(): Record<string, string> {
    return this.problem.errors ?? {}
  }
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  /** Set for the retry after a token refresh, so a failed refresh cannot loop. */
  skipRefresh?: boolean
}

async function toProblem(response: Response): Promise<ProblemDetail> {
  try {
    const body = await response.json()
    return { status: response.status, ...body }
  } catch {
    return { status: response.status, title: response.statusText }
  }
}

let refreshInFlight: Promise<boolean> | null = null

/** Exchanges the refresh token for a new pair. Concurrent 401s share one refresh call. */
async function refreshTokens(): Promise<boolean> {
  const refreshToken = tokenStore.refresh
  if (!refreshToken) return false

  refreshInFlight ??= (async () => {
    try {
      const response = await fetch(`${BASE_URL}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
      if (!response.ok) {
        tokenStore.clear()
        return false
      }
      const tokens: TokenResponse = await response.json()
      tokenStore.set(tokens)
      return true
    } catch {
      tokenStore.clear()
      return false
    } finally {
      refreshInFlight = null
    }
  })()

  return refreshInFlight
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, skipRefresh, headers, ...rest } = options

  const requestHeaders = new Headers(headers)
  if (body !== undefined && !(body instanceof FormData)) {
    requestHeaders.set('Content-Type', 'application/json')
  }
  const accessToken = tokenStore.access
  if (accessToken) {
    requestHeaders.set('Authorization', `Bearer ${accessToken}`)
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...rest,
    headers: requestHeaders,
    body: body instanceof FormData ? body : body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (response.status === 401 && !skipRefresh && tokenStore.refresh) {
    if (await refreshTokens()) {
      return request<T>(path, { ...options, skipRefresh: true })
    }
  }

  if (!response.ok) {
    throw new ApiError(await toProblem(response))
  }

  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}

/**
 * Uploads bytes straight to S3 with a presigned PUT. Deliberately does not use `request`:
 * this call must not carry our Authorization header, and its origin is S3, not the API.
 */
export async function uploadToPresignedUrl(uploadUrl: string, file: File): Promise<void> {
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type },
    body: file,
  })

  if (!response.ok) {
    throw new Error(`Upload to storage failed with status ${response.status}`)
  }
}

/** Builds a query string, dropping empty values so the backend sees absent params. */
export function queryString(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams()

  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== '') {
      search.set(key, String(value))
    }
  }

  const result = search.toString()
  return result ? `?${result}` : ''
}
