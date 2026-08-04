import axios, { type InternalAxiosRequestConfig } from "axios"

const AXIOS_CONFIG = {
  baseURL: import.meta.env.VITE_API_URL,
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
} as const

export const api = axios.create(AXIOS_CONFIG)

const REFRESH_URL = "/api/auth/refresh"

// Separate instance (no response interceptor) so a failed refresh call
// never recurses back into the 401 handler below.
const refreshClient = axios.create(AXIOS_CONFIG)

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

// Module-scoped so concurrent 401s share one in-flight refresh instead of
// each firing its own POST /api/auth/refresh.
let refreshPromise: Promise<unknown> | null = null

function refreshSession(): Promise<unknown> {
  if (!refreshPromise) {
    refreshPromise = refreshClient.post(REFRESH_URL).finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

function redirectToLogin(): void {
  window.location.href = "/login"
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config as RetryableRequestConfig | undefined
    if (error.response?.status !== 401 || !config) {
      return Promise.reject(error)
    }

    if (config._retry) {
      redirectToLogin()
      return Promise.reject(error)
    }

    config._retry = true
    try {
      await refreshSession()
    } catch (refreshError) {
      redirectToLogin()
      return Promise.reject(refreshError)
    }

    return api(config)
  }
)
