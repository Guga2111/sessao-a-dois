import { isAxiosError } from "axios"
import { create } from "zustand"

import { api } from "@/lib/api"
import { useMatchStore } from "@/stores/useMatchStore"
import type { ChangePasswordRequest } from "@/types/auth"
import type {
  DeleteAccountRequest,
  UpdateProfileRequest,
  UserProfileResponse,
} from "@/types/user"

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
  /** O parceiro desfez o vinculo enquanto este cliente estava fora (US-013). */
  bondDissolved: boolean
  login: (credentials: LoginCredentials) => Promise<void>
  register: (data: RegisterData) => Promise<void>
  logout: () => Promise<void>
  joinCouple: (inviteCode: string) => Promise<Couple>
  createCouple: () => Promise<Couple>
  regenerateInviteCode: () => Promise<Couple>
  updateProfile: (request: UpdateProfileRequest) => Promise<AuthUser>
  changePassword: (request: ChangePasswordRequest) => Promise<void>
  dissolveCouple: () => Promise<void>
  deleteAccount: (request: DeleteAccountRequest) => Promise<void>
  dismissBondDissolvedNotice: () => void
  clearSession: () => void
  loadCurrentUser: () => Promise<void>
}

function loadPersistedSession(): PersistedSession {
  try {
    const raw = localStorage.getItem(SESSION_STORAGE_KEY)
    if (!raw) {
      return { user: null, couple: null }
    }
    return JSON.parse(raw) as PersistedSession
  } catch {
    // localStorage is unavailable in non-browser environments (e.g. Node
    // worker threads before jsdom is applied). Returning an empty session is
    // the correct no-op for that case.
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
  bondDissolved: false,

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
    get().clearSession()
  },

  joinCouple: async (inviteCode) => {
    const { data } = await api.post<Couple>("/api/couple/join", { inviteCode })
    persistSession(get().user, data)
    set({ couple: data, bondDissolved: false })
    return data
  },

  createCouple: async () => {
    const { data } = await api.post<Couple>("/api/couple")
    persistSession(get().user, data)
    set({ couple: data, bondDissolved: false })
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

  // O 204 do PUT ja vem com os dois cookies de sessao expirados: a sessao que trocou a
  // senha cai junto com as outras (E9.8). Derrubar o estado local NAO acontece aqui — quem
  // chama precisa avisar o usuario primeiro e so entao chamar `clearSession`.
  changePassword: async (request) => {
    await api.put("/api/auth/password", request)
  },

  // O casal deixa de existir para os dois (E9.17) — a sessao continua valida. O WebSocket
  // TEM que cair junto: a `useMatchStore` esta inscrita no topico de um casal que acabou.
  dissolveCouple: async () => {
    await api.delete("/api/couple/me")
    useMatchStore.getState().disconnect()
    persistSession(get().user, null)
    set({ couple: null })
  },

  // A conta some do banco junto com avaliacoes, notificacoes e sessoes (US-007). O estado
  // local NAO cai aqui, pelo mesmo motivo do `changePassword`: quem chama precisa mostrar o
  // desfecho ao usuario antes de a tela se desmontar, e so entao chamar `clearSession`.
  // O corpo vai em `data` porque axios nao aceita body posicional no `delete`.
  deleteAccount: async (request) => {
    await api.delete("/api/user/me", { data: request })
  },

  // Encerra a sessao do lado do cliente sem falar com a API: usado pelo `logout` (depois do
  // POST) e pela troca de senha (onde a API ja derrubou tudo e um POST a mais so tomaria 401).
  dismissBondDissolvedNotice: () => set({ bondDissolved: false }),

  clearSession: () => {
    useMatchStore.getState().disconnect()
    clearPersistedSession()
    set({ user: null, couple: null, isAuthenticated: false, bondDissolved: false })
  },

  loadCurrentUser: async () => {
    // O cache de UI de antes da resposta E o gatilho do aviso da US-013 — nao ha (nem pode
    // haver) flag paralela em `localStorage`. `persistSession` logo abaixo sobrescreve o
    // cache, entao o aviso vale para esta carga da aplicacao e nao volta no reload seguinte.
    const cachedCouple = get().couple
    try {
      const { data } = await api.get<LoginResponse>("/api/auth/me")
      persistSession(data.user, data.couple)
      set({
        user: data.user,
        couple: data.couple,
        isAuthenticated: true,
        // So dispara quem TINHA parceiro e chegou sem casal: quem nunca teve casal nao tem
        // cache, e quem desfez o vinculo ja zerou o `couple` do store na US-011.
        bondDissolved: data.couple
          ? false
          : get().bondDissolved || cachedCouple?.partner != null,
      })
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 401) {
        clearPersistedSession()
        set({ user: null, couple: null, isAuthenticated: false, bondDissolved: false })
        return
      }
      throw error
    } finally {
      set({ loading: false })
    }
  },
}))
