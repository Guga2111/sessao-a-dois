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
const couple2: Couple = {
  id: "c2",
  inviteCode: "XYZ999",
  inviteCodeExpiresAt: "2026-03-01T00:00:00Z",
  partner: null,
  createdAt: "2026-02-01T00:00:00Z",
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

describe("updateProfile", () => {
  it("sends PATCH /api/user/me and writes the new AuthUser to the store AND localStorage in the same action", async () => {
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ user, couple, isAuthenticated: true })
    const updated = { id: user.id, name: "Ana Nova", email: user.email }
    mockApi.patch.mockResolvedValueOnce({ data: updated })

    const result = await useAuthStore.getState().updateProfile({ name: "Ana Nova" })

    expect(mockApi.patch).toHaveBeenCalledWith("/api/user/me", { name: "Ana Nova" })
    expect(result).toEqual(updated)
    expect(useAuthStore.getState().user).toEqual(updated)
    expect(JSON.parse(localStorage.getItem(SESSION_STORAGE_KEY)!)).toEqual({
      user: updated,
      couple,
    })
  })

  it("propagates the error and leaves the user unchanged", async () => {
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ user, couple, isAuthenticated: true })
    mockApi.patch.mockRejectedValueOnce(new Error("email already taken"))

    await expect(
      useAuthStore.getState().updateProfile({ email: "taken@example.com" })
    ).rejects.toThrow("email already taken")

    expect(useAuthStore.getState().user).toEqual(user)
  })
})

describe("joinCouple", () => {
  it("persists the couple, writes it to the store and clears bondDissolved", async () => {
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ user, couple: null, isAuthenticated: true, bondDissolved: true })
    mockApi.post.mockResolvedValueOnce({ data: couple })

    const result = await useAuthStore.getState().joinCouple("INVITE1")

    expect(mockApi.post).toHaveBeenCalledWith("/api/couple/join", { inviteCode: "INVITE1" })
    expect(result).toEqual(couple)
    const state = useAuthStore.getState()
    expect(state.couple).toEqual(couple)
    expect(state.bondDissolved).toBe(false)
    expect(JSON.parse(localStorage.getItem(SESSION_STORAGE_KEY)!)).toEqual({ user, couple })
  })

  it("propagates the error and leaves couple/bondDissolved unchanged", async () => {
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ user, couple: null, isAuthenticated: true, bondDissolved: true })
    mockApi.post.mockRejectedValueOnce(new Error("invalid invite code"))

    await expect(useAuthStore.getState().joinCouple("BAD")).rejects.toThrow(
      "invalid invite code"
    )

    const state = useAuthStore.getState()
    expect(state.couple).toBeNull()
    expect(state.bondDissolved).toBe(true)
  })
})

describe("createCouple", () => {
  it("persists the couple, writes it to the store and clears bondDissolved", async () => {
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ user, couple: null, isAuthenticated: true, bondDissolved: true })
    mockApi.post.mockResolvedValueOnce({ data: couple2 })

    const result = await useAuthStore.getState().createCouple()

    expect(mockApi.post).toHaveBeenCalledWith("/api/couple")
    expect(result).toEqual(couple2)
    const state = useAuthStore.getState()
    expect(state.couple).toEqual(couple2)
    expect(state.bondDissolved).toBe(false)
    expect(JSON.parse(localStorage.getItem(SESSION_STORAGE_KEY)!)).toEqual({
      user,
      couple: couple2,
    })
  })
})

describe("regenerateInviteCode", () => {
  it("updates the couple in the store and in the cache", async () => {
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ user, couple, isAuthenticated: true })
    mockApi.post.mockResolvedValueOnce({ data: couple2 })

    const result = await useAuthStore.getState().regenerateInviteCode()

    expect(mockApi.post).toHaveBeenCalledWith("/api/couple/invite-code/regenerate")
    expect(result).toEqual(couple2)
    expect(useAuthStore.getState().couple).toEqual(couple2)
    expect(JSON.parse(localStorage.getItem(SESSION_STORAGE_KEY)!)).toEqual({
      user,
      couple: couple2,
    })
  })
})

describe("dissolveCouple", () => {
  it("sends DELETE /api/couple/me, disconnects useMatchStore and zeros only couple", async () => {
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ user, couple, isAuthenticated: true })
    mockApi.delete.mockResolvedValueOnce({ data: undefined })

    await useAuthStore.getState().dissolveCouple()

    expect(mockApi.delete).toHaveBeenCalledWith("/api/couple/me")
    expect(mockDisconnect).toHaveBeenCalledTimes(1)
    const state = useAuthStore.getState()
    expect(state.couple).toBeNull()
    expect(state.user).toEqual(user)
    expect(state.isAuthenticated).toBe(true)
    expect(JSON.parse(localStorage.getItem(SESSION_STORAGE_KEY)!)).toEqual({
      user,
      couple: null,
    })
  })

  it("propagates the error without disconnecting or touching the couple", async () => {
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ user, couple, isAuthenticated: true })
    mockApi.delete.mockRejectedValueOnce(new Error("rate limited"))

    await expect(useAuthStore.getState().dissolveCouple()).rejects.toThrow("rate limited")

    expect(mockDisconnect).not.toHaveBeenCalled()
    expect(useAuthStore.getState().couple).toEqual(couple)
  })
})

describe("changePassword", () => {
  it("sends PUT /api/auth/password and does not touch local state", async () => {
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ user, couple, isAuthenticated: true })
    mockApi.put.mockResolvedValueOnce({ data: undefined })
    const request = { currentPassword: "old-pass", newPassword: "new-pass" }

    await useAuthStore.getState().changePassword(request)

    expect(mockApi.put).toHaveBeenCalledWith("/api/auth/password", request)
    const state = useAuthStore.getState()
    expect(state.user).toEqual(user)
    expect(state.couple).toEqual(couple)
    expect(state.isAuthenticated).toBe(true)
  })

  it("propagates the error and does not touch local state", async () => {
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ user, couple, isAuthenticated: true })
    mockApi.put.mockRejectedValueOnce(new Error("wrong current password"))

    await expect(
      useAuthStore.getState().changePassword({ currentPassword: "x", newPassword: "y" })
    ).rejects.toThrow("wrong current password")

    const state = useAuthStore.getState()
    expect(state.user).toEqual(user)
    expect(state.isAuthenticated).toBe(true)
  })
})

describe("deleteAccount", () => {
  it("sends DELETE /api/user/me with the body in { data } and does not touch local state", async () => {
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ user, couple, isAuthenticated: true })
    mockApi.delete.mockResolvedValueOnce({ data: undefined })

    await useAuthStore.getState().deleteAccount({ password: "secret" })

    expect(mockApi.delete).toHaveBeenCalledWith("/api/user/me", { data: { password: "secret" } })
    const state = useAuthStore.getState()
    expect(state.user).toEqual(user)
    expect(state.couple).toEqual(couple)
    expect(state.isAuthenticated).toBe(true)
  })

  it("propagates the error and does not touch local state", async () => {
    const useAuthStore = await loadAuthStore()
    useAuthStore.setState({ user, couple, isAuthenticated: true })
    mockApi.delete.mockRejectedValueOnce(new Error("wrong password"))

    await expect(
      useAuthStore.getState().deleteAccount({ password: "wrong" })
    ).rejects.toThrow("wrong password")

    const state = useAuthStore.getState()
    expect(state.user).toEqual(user)
    expect(state.isAuthenticated).toBe(true)
  })
})
