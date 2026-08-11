import { beforeEach, describe, expect, it, vi } from "vitest"

import type { AuthUser, Couple, useAuthStore as UseAuthStore } from "@/stores/useAuthStore"

const { mockApi, mockDisconnect } = vi.hoisted(() => {
  const mockApi = {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  }
  const mockDisconnect = vi.fn()
  return { mockApi, mockDisconnect }
})

// useAuthStore is eagerly imported at module scope by src/test/storeReset.ts (loaded
// globally by setup.ts, for its afterEach reset) BEFORE this file's vi.mock calls below
// take effect, so a plain top-level `import { useAuthStore } from "..."` here would get
// back that already-cached module with the REAL `@/lib/api`/`@/stores/useMatchStore`
// baked into its closure — same gotcha as useMatchStore.test.ts (US-003). useAuthStore
// ALSO reads localStorage at module scope (`loadPersistedSession()`), so a test that
// needs a pre-seeded cache must seed localStorage BEFORE calling loadAuthStore(), never
// after — reassigning localStorage post-import has no effect on the already-captured
// `initialSession`.
vi.mock("@/lib/api", () => ({ api: mockApi }))
vi.mock("@/stores/useMatchStore", () => ({
  useMatchStore: { getState: () => ({ disconnect: mockDisconnect }) },
}))

const SESSION_STORAGE_KEY = "sessaoADois.session"

async function loadAuthStore(): Promise<typeof UseAuthStore> {
  vi.resetModules()
  const mod = await import("@/stores/useAuthStore")
  return mod.useAuthStore
}

const user: AuthUser = { id: "u1", name: "Ana", email: "ana@example.com" }
const couple: Couple = {
  id: "c1",
  inviteCode: null,
  inviteCodeExpiresAt: null,
  partner: { id: "u2", name: "Leo" },
  createdAt: "2026-01-01T00:00:00Z",
}

beforeEach(() => {
  localStorage.clear()
  mockApi.get.mockReset()
  mockApi.post.mockReset()
  mockApi.patch.mockReset()
  mockApi.put.mockReset()
  mockApi.delete.mockReset()
  mockDisconnect.mockClear()
})

describe("loadCurrentUser", () => {
  it("populates user/couple, sets isAuthenticated, persists the cache and turns off loading on 200", async () => {
    mockApi.get.mockResolvedValueOnce({ data: { user, couple } })
    const useAuthStore = await loadAuthStore()

    await useAuthStore.getState().loadCurrentUser()

    const state = useAuthStore.getState()
    expect(state.user).toEqual(user)
    expect(state.couple).toEqual(couple)
    expect(state.isAuthenticated).toBe(true)
    expect(state.loading).toBe(false)
    expect(JSON.parse(localStorage.getItem(SESSION_STORAGE_KEY)!)).toEqual({ user, couple })
  })

  it("clears state, removes the cache and turns off loading on 401, without throwing", async () => {
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ user, couple }))
    const useAuthStore = await loadAuthStore()
    const error = Object.assign(new Error("unauthorized"), {
      isAxiosError: true,
      response: { status: 401 },
    })
    mockApi.get.mockRejectedValueOnce(error)

    await expect(useAuthStore.getState().loadCurrentUser()).resolves.toBeUndefined()

    const state = useAuthStore.getState()
    expect(state.user).toBeNull()
    expect(state.couple).toBeNull()
    expect(state.isAuthenticated).toBe(false)
    expect(state.loading).toBe(false)
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it("rethrows a non-401 error but still turns off loading (finally)", async () => {
    const useAuthStore = await loadAuthStore()
    const error = Object.assign(new Error("server exploded"), {
      isAxiosError: true,
      response: { status: 500 },
    })
    mockApi.get.mockRejectedValueOnce(error)

    await expect(useAuthStore.getState().loadCurrentUser()).rejects.toThrow("server exploded")
    expect(useAuthStore.getState().loading).toBe(false)
  })

  it("turns on bondDissolved when the cache had couple.partner and the response comes without a couple", async () => {
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ user, couple }))
    const useAuthStore = await loadAuthStore()
    mockApi.get.mockResolvedValueOnce({ data: { user, couple: null } })

    await useAuthStore.getState().loadCurrentUser()

    expect(useAuthStore.getState().bondDissolved).toBe(true)
    expect(useAuthStore.getState().couple).toBeNull()
  })

  it("does not turn on bondDissolved for someone who never had a couple", async () => {
    const useAuthStore = await loadAuthStore()
    mockApi.get.mockResolvedValueOnce({ data: { user, couple: null } })

    await useAuthStore.getState().loadCurrentUser()

    expect(useAuthStore.getState().bondDissolved).toBe(false)
  })

  it("does not persist bondDissolved to localStorage", async () => {
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ user, couple }))
    const useAuthStore = await loadAuthStore()
    mockApi.get.mockResolvedValueOnce({ data: { user, couple: null } })

    await useAuthStore.getState().loadCurrentUser()

    expect(useAuthStore.getState().bondDissolved).toBe(true)
    const persisted = JSON.parse(localStorage.getItem(SESSION_STORAGE_KEY)!) as object
    expect(persisted).not.toHaveProperty("bondDissolved")
  })

  it("only persists { user, couple } to localStorage — nothing that looks like a credential", async () => {
    mockApi.get.mockResolvedValueOnce({ data: { user, couple } })
    const useAuthStore = await loadAuthStore()

    await useAuthStore.getState().loadCurrentUser()

    const persisted = JSON.parse(localStorage.getItem(SESSION_STORAGE_KEY)!) as object
    expect(Object.keys(persisted).sort()).toEqual(["couple", "user"])
  })
})

describe("bootstrap: corrupted localStorage", () => {
  it("falls back to { user: null, couple: null } without throwing when the cache is corrupted JSON", async () => {
    localStorage.setItem(SESSION_STORAGE_KEY, "{not json")

    const useAuthStore = await loadAuthStore()

    expect(useAuthStore.getState().user).toBeNull()
    expect(useAuthStore.getState().couple).toBeNull()
  })
})

describe("clearSession", () => {
  it("disconnects useMatchStore, clears the cache and zeros user/couple/isAuthenticated/bondDissolved", async () => {
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ user, couple }))
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ isAuthenticated: true, bondDissolved: true })
    expect(useAuthStore.getState().user).toEqual(user)

    useAuthStore.getState().clearSession()

    expect(mockDisconnect).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
    const state = useAuthStore.getState()
    expect(state.user).toBeNull()
    expect(state.couple).toBeNull()
    expect(state.isAuthenticated).toBe(false)
    expect(state.bondDissolved).toBe(false)
  })
})

describe("logout", () => {
  it("calls POST /api/auth/logout and clears local state on success", async () => {
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ user, couple, isAuthenticated: true })
    mockApi.post.mockResolvedValueOnce({ data: undefined })

    await useAuthStore.getState().logout()

    expect(mockApi.post).toHaveBeenCalledWith("/api/auth/logout")
    expect(mockDisconnect).toHaveBeenCalledTimes(1)
    const state = useAuthStore.getState()
    expect(state.user).toBeNull()
    expect(state.isAuthenticated).toBe(false)
  })

  it("clears local state even when the POST fails", async () => {
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ user, couple, isAuthenticated: true })
    mockApi.post.mockRejectedValueOnce(new Error("network down"))

    await expect(useAuthStore.getState().logout()).resolves.toBeUndefined()

    expect(mockDisconnect).toHaveBeenCalledTimes(1)
    const state = useAuthStore.getState()
    expect(state.user).toBeNull()
    expect(state.isAuthenticated).toBe(false)
  })
})
