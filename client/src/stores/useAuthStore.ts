import { isAxiosError } from "axios"
import { create } from "zustand"

import { api } from "@/lib/api"
import { clearAuthToken, getAuthToken, setAuthToken } from "@/lib/authToken"
import { useMatchStore } from "@/stores/useMatchStore"

const SESSION_STORAGE_KEY = "sessaoADois.session"

export interface AuthUser {
  id: string
  name: string
  email: string
}

export interface PartnerSummary {
  id: string
  name: string
  email: string
}

export interface Couple {
  id: string
  inviteCode: string
  partner: PartnerSummary | null
  createdAt: string
}

interface PersistedSession {
  user: AuthUser | null
  couple: Couple | null
}

interface LoginResponse {
  token: string
  user: AuthUser
  couple: Couple | null
}

interface RegisterData {
  name: string
  email: string
  password: string
}

interface LoginCredentials {
  email: string
  password: string
}

interface AuthState {
  token: string | null
  user: AuthUser | null
  couple: Couple | null
  isAuthenticated: boolean
  loading: boolean
  login: (credentials: LoginCredentials) => Promise<void>
  register: (data: RegisterData) => Promise<void>
  logout: () => void
  joinCouple: (inviteCode: string) => Promise<Couple>
  createCouple: () => Promise<Couple>
  loadCurrentUser: () => Promise<void>
}

function loadPersistedSession(): PersistedSession {
  const raw = localStorage.getItem(SESSION_STORAGE_KEY)
  if (!raw) {
    return { user: null, couple: null }
  }
  try {
    return JSON.parse(raw) as PersistedSession
  } catch {
    return { user: null, couple: null }
  }
}

function persistSession(user: AuthUser | null, couple: Couple | null): void {
  localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ user, couple }))
}

function clearPersistedSession(): void {
  localStorage.removeItem(SESSION_STORAGE_KEY)
}

const initialSession = loadPersistedSession()
const initialToken = getAuthToken()

export const useAuthStore = create<AuthState>((set, get) => ({
  token: initialToken,
  user: initialSession.user,
  couple: initialSession.couple,
  isAuthenticated: Boolean(initialToken),
  loading: Boolean(initialToken),

  login: async ({ email, password }) => {
    const { data } = await api.post<LoginResponse>("/api/auth/login", {
      email,
      password,
    })
    setAuthToken(data.token)
    persistSession(data.user, data.couple)
    set({
      token: data.token,
      user: data.user,
      couple: data.couple,
      isAuthenticated: true,
    })
  },

  register: async (registerData) => {
    await api.post("/api/auth/register", registerData)
    try {
      await get().login({ email: registerData.email, password: registerData.password })
    } catch (error) {
      get().logout()
      window.location.href = "/login"
      throw error
    }
  },

  logout: () => {
    useMatchStore.getState().disconnect()
    clearAuthToken()
    clearPersistedSession()
    set({ token: null, user: null, couple: null, isAuthenticated: false })
  },

  joinCouple: async (inviteCode) => {
    const { data } = await api.post<Couple>("/api/couple/join", { inviteCode })
    persistSession(get().user, data)
    set({ couple: data })
    return data
  },

  createCouple: async () => {
    const { data } = await api.post<Couple>("/api/couple")
    persistSession(get().user, data)
    set({ couple: data })
    return data
  },

  loadCurrentUser: async () => {
    const token = getAuthToken()
    if (!token) {
      set({ token: null, isAuthenticated: false, loading: false })
      return
    }

    set({ token, isAuthenticated: true })

    try {
      const { data } = await api.get<Couple>("/api/couple/me")
      persistSession(get().user, data)
      set({ couple: data })
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 404) {
        persistSession(get().user, null)
        set({ couple: null })
        return
      }
      throw error
    } finally {
      set({ loading: false })
    }
  },
}))
