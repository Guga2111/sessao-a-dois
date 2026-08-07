import { isAxiosError } from "axios"
import { create } from "zustand"

import { api } from "@/lib/api"
import { useMatchStore } from "@/stores/useMatchStore"
import type { UpdateProfileRequest, UserProfileResponse } from "@/types/user"

const SESSION_STORAGE_KEY = "sessaoADois.session"

export interface AuthUser {
  id: string
  name: string
  email: string
}

export interface PartnerSummary {
  id: string
  name: string
}

export interface Couple {
  id: string
  inviteCode: string | null
  inviteCodeExpiresAt: string | null
  partner: PartnerSummary | null
  createdAt: string
}

interface PersistedSession {
  user: AuthUser | null
  couple: Couple | null
}

interface LoginResponse {
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
  user: AuthUser | null
  couple: Couple | null
  isAuthenticated: boolean
  loading: boolean
  login: (credentials: LoginCredentials) => Promise<void>
  register: (data: RegisterData) => Promise<void>
  logout: () => Promise<void>
  joinCouple: (inviteCode: string) => Promise<Couple>
  createCouple: () => Promise<Couple>
  regenerateInviteCode: () => Promise<Couple>
  updateProfile: (request: UpdateProfileRequest) => Promise<AuthUser>
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

export const useAuthStore = create<AuthState>((set, get) => ({
  user: initialSession.user,
  couple: initialSession.couple,
  isAuthenticated: false,
  loading: true,

  login: async ({ email, password }) => {
    const { data } = await api.post<LoginResponse>("/api/auth/login", {
      email,
      password,
    })
    persistSession(data.user, data.couple)
    set({
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
      await get().logout()
      window.location.href = "/login"
      throw error
    }
  },

  logout: async () => {
    try {
      await api.post("/api/auth/logout")
    } catch {
      // Network/server failure can't trap the user logged in in the UI —
      // local state is cleared below regardless of the outcome.
    }
    useMatchStore.getState().disconnect()
    clearPersistedSession()
    set({ user: null, couple: null, isAuthenticated: false })
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

  regenerateInviteCode: async () => {
    const { data } = await api.post<Couple>("/api/couple/invite-code/regenerate")
    persistSession(get().user, data)
    set({ couple: data })
    return data
  },

  // A resposta do PATCH e a fonte da verdade do perfil: o Header e qualquer outra tela
  // que leia `user` refletem o nome novo sem reload, e o cache de UI fica em sincronia.
  updateProfile: async (request) => {
    const { data } = await api.patch<UserProfileResponse>("/api/user/me", request)
    const user: AuthUser = { id: data.id, name: data.name, email: data.email }
    persistSession(user, get().couple)
    set({ user })
    return user
  },

  loadCurrentUser: async () => {
    try {
      const { data } = await api.get<LoginResponse>("/api/auth/me")
      persistSession(data.user, data.couple)
      set({ user: data.user, couple: data.couple, isAuthenticated: true })
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 401) {
        clearPersistedSession()
        set({ user: null, couple: null, isAuthenticated: false })
        return
      }
      throw error
    } finally {
      set({ loading: false })
    }
  },
}))
