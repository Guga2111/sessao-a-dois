import { beforeEach, describe, expect, it, vi } from "vitest"

import type { useNotificationStore as UseNotificationStore } from "@/stores/useNotificationStore"
import type { Notification, PagedNotificationResponse } from "@/types/notification"

const { mockApi, mockCelebrateMatch } = vi.hoisted(() => {
  const mockApi = {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  }
  const mockCelebrateMatch = vi.fn()
  return { mockApi, mockCelebrateMatch }
})

// src/test/storeReset.ts (loaded globally by setup.ts) eagerly imports
// useNotificationStore at module scope, BEFORE the vi.mock calls below take effect —
// same gotcha documented for useMatchStore.test.ts (US-003) and useAuthStore.test.ts
// (US-004/US-005). A plain top-level import here would get back that already-cached
// module with the REAL `@/lib/api`/`@/stores/useMatchStore` baked into its closure.
vi.mock("@/lib/api", () => ({ api: mockApi }))
vi.mock("@/stores/useMatchStore", () => ({
  useMatchStore: { getState: () => ({ celebrateMatch: mockCelebrateMatch }) },
}))

async function loadNotificationStore(): Promise<typeof UseNotificationStore> {
  vi.resetModules()
  const mod = await import("@/stores/useNotificationStore")
  return mod.useNotificationStore
}

function makeNotification(overrides: Partial<Notification> = {}): Notification {
  return {
    id: "n1",
    type: "MATCH",
    tmdbId: 42,
    mediaType: "MOVIE",
    title: "Some Title",
    actorUserId: "u2",
    actorName: "Leo",
    read: false,
    createdAt: "2026-01-01T00:00:00Z",
    recipientUserId: "u1",
    mediaTrackId: null,
    ...overrides,
  }
}

function pagedResponse(overrides: Partial<PagedNotificationResponse> = {}): PagedNotificationResponse {
  return {
    content: [makeNotification()],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 20,
    ...overrides,
  }
}

beforeEach(() => {
  mockApi.get.mockReset()
  mockApi.post.mockReset()
  mockApi.patch.mockReset()
  mockApi.put.mockReset()
  mockApi.delete.mockReset()
  mockCelebrateMatch.mockClear()
})

describe("fetchNotifications", () => {
  it("popula notifications, page e hasMore a partir do PagedNotificationResponse", async () => {
    mockApi.get.mockResolvedValueOnce({
      data: pagedResponse({
        content: [makeNotification({ id: "n1" }), makeNotification({ id: "n2" })],
        totalElements: 3,
        totalPages: 2,
        number: 0,
      }),
    })
    const useNotificationStore = await loadNotificationStore()

    await useNotificationStore.getState().fetchNotifications()

    const state = useNotificationStore.getState()
    expect(state.notifications.map((n) => n.id)).toEqual(["n1", "n2"])
    expect(state.page).toBe(0)
    expect(state.hasMore).toBe(true)
  })

  it("desliga loading mesmo quando a chamada falha", async () => {
    mockApi.get.mockRejectedValueOnce(new Error("network down"))
    const useNotificationStore = await loadNotificationStore()

    await expect(useNotificationStore.getState().fetchNotifications()).rejects.toThrow("network down")

    expect(useNotificationStore.getState().loading).toBe(false)
  })
})

describe("fetchMore", () => {
  it("concatena a pagina seguinte a lista existente", async () => {
    const useNotificationStore = await loadNotificationStore()
    const existing = makeNotification({ id: "n1" })
    useNotificationStore.setState({ notifications: [existing], page: 0, hasMore: true })

    mockApi.get.mockResolvedValueOnce({
      data: pagedResponse({
        content: [makeNotification({ id: "n2" })],
        totalElements: 2,
        totalPages: 2,
        number: 1,
      }),
    })

    await useNotificationStore.getState().fetchMore()

    const state = useNotificationStore.getState()
    expect(state.notifications.map((n) => n.id)).toEqual(["n1", "n2"])
    expect(state.page).toBe(1)
    expect(state.hasMore).toBe(false)
  })

  it("e no-op quando hasMore e false", async () => {
    const useNotificationStore = await loadNotificationStore()
    useNotificationStore.setState({ notifications: [], page: 0, hasMore: false })

    await useNotificationStore.getState().fetchMore()

    expect(mockApi.get).not.toHaveBeenCalled()
  })
})

describe("markAsRead", () => {
  it("aplica a mudanca antes da resposta (otimista) e decrementa unreadCount sem passar de zero", async () => {
    const useNotificationStore = await loadNotificationStore()
    useNotificationStore.setState({
      notifications: [makeNotification({ id: "n1", read: false })],
      unreadCount: 0,
    })

    let resolvePatch!: () => void
    mockApi.patch.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolvePatch = () => resolve({ data: undefined })
        })
    )

    const pending = useNotificationStore.getState().markAsRead("n1")

    const midState = useNotificationStore.getState()
    expect(midState.notifications.find((n) => n.id === "n1")?.read).toBe(true)
    expect(midState.unreadCount).toBe(0)

    resolvePatch()
    await pending
  })

  it("reverte notifications e unreadCount quando o PATCH falha, e relanca o erro", async () => {
    const useNotificationStore = await loadNotificationStore()
    const original = [makeNotification({ id: "n1", read: false })]
    useNotificationStore.setState({ notifications: original, unreadCount: 1 })

    mockApi.patch.mockRejectedValueOnce(new Error("falhou"))

    await expect(useNotificationStore.getState().markAsRead("n1")).rejects.toThrow("falhou")

    const state = useNotificationStore.getState()
    expect(state.notifications).toEqual(original)
    expect(state.unreadCount).toBe(1)
  })

  it("e no-op para id inexistente", async () => {
    const useNotificationStore = await loadNotificationStore()
    useNotificationStore.setState({ notifications: [], unreadCount: 0 })

    await useNotificationStore.getState().markAsRead("nao-existe")

    expect(mockApi.patch).not.toHaveBeenCalled()
    expect(useNotificationStore.getState().unreadCount).toBe(0)
  })

  it("e no-op para notificacao ja lida (nao decrementa o contador duas vezes)", async () => {
    const useNotificationStore = await loadNotificationStore()
    useNotificationStore.setState({
      notifications: [makeNotification({ id: "n1", read: true })],
      unreadCount: 0,
    })

    await useNotificationStore.getState().markAsRead("n1")

    expect(mockApi.patch).not.toHaveBeenCalled()
    expect(useNotificationStore.getState().unreadCount).toBe(0)
  })
})

describe("markAllAsRead", () => {
  it("zera o contador e marca todas como lidas", async () => {
    const useNotificationStore = await loadNotificationStore()
    useNotificationStore.setState({
      notifications: [
        makeNotification({ id: "n1", read: false }),
        makeNotification({ id: "n2", read: false }),
      ],
      unreadCount: 2,
    })
    mockApi.patch.mockResolvedValueOnce({ data: undefined })

    await useNotificationStore.getState().markAllAsRead()

    const state = useNotificationStore.getState()
    expect(state.notifications.every((n) => n.read)).toBe(true)
    expect(state.unreadCount).toBe(0)
    expect(mockApi.patch).toHaveBeenCalledWith("/api/notifications/read-all")
  })

  it("reverte igual no erro", async () => {
    const useNotificationStore = await loadNotificationStore()
    const original = [
      makeNotification({ id: "n1", read: false }),
      makeNotification({ id: "n2", read: false }),
    ]
    useNotificationStore.setState({ notifications: original, unreadCount: 2 })
    mockApi.patch.mockRejectedValueOnce(new Error("falhou"))

    await expect(useNotificationStore.getState().markAllAsRead()).rejects.toThrow("falhou")

    const state = useNotificationStore.getState()
    expect(state.notifications).toEqual(original)
    expect(state.unreadCount).toBe(2)
  })
})

describe("pushIncoming", () => {
  it("insere no topo da lista e incrementa unreadCount", async () => {
    const useNotificationStore = await loadNotificationStore()
    const existing = makeNotification({ id: "n1", type: "NO_MATCH" })
    useNotificationStore.setState({ notifications: [existing], unreadCount: 0 })

    const incoming = makeNotification({ id: "n2", type: "NO_MATCH" })
    useNotificationStore.getState().pushIncoming(incoming)

    const state = useNotificationStore.getState()
    expect(state.notifications[0]).toEqual(incoming)
    expect(state.notifications).toHaveLength(2)
    expect(state.unreadCount).toBe(1)
  })

  it("chama celebrateMatch com a chave 'tmdb:<id>' so quando type === 'MATCH'", async () => {
    const useNotificationStore = await loadNotificationStore()
    useNotificationStore.setState({ notifications: [], unreadCount: 0 })

    const matchNotification = makeNotification({
      id: "n1",
      type: "MATCH",
      tmdbId: 99,
      title: "Match Title",
      mediaType: "TV",
    })
    useNotificationStore.getState().pushIncoming(matchNotification)

    expect(mockCelebrateMatch).toHaveBeenCalledWith(
      { tmdbId: 99, title: "Match Title", mediaType: "TV" },
      "tmdb:99"
    )

    mockCelebrateMatch.mockClear()

    const noMatchNotification = makeNotification({ id: "n2", type: "NO_MATCH" })
    useNotificationStore.getState().pushIncoming(noMatchNotification)

    expect(mockCelebrateMatch).not.toHaveBeenCalled()
  })
})
