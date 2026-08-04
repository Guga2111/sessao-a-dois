import axios from "axios"

import { clearAuthToken, getAuthToken } from "@/lib/authToken"

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
})

api.interceptors.request.use((config) => {
  const token = getAuthToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const hadAuthHeader = Boolean(error.config?.headers?.Authorization)
    if (error.response?.status === 401 && hadAuthHeader) {
      clearAuthToken()
      window.location.href = "/login"
    }
    return Promise.reject(error)
  }
)
