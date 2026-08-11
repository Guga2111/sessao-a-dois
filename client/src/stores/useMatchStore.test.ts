import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import type { useMatchStore as UseMatchStore, MatchEvent } from "@/stores/useMatchStore"
import type { PendingMatch } from "@/types/media"

interface MockStompClientOptions {
  onConnect?: () => void
  onDisconnect?: () => void
}

interface MockSubscription {
  unsubscribe: ReturnType<typeof vi.fn>
}

const { MockClient, mockApi } = vi.hoisted(() => {
  class MockClient {
    static instances: MockClient[] = []

    onConnect?: () => void
    onDisconnect?: () => void
    activate = vi.fn()
    deactivate = vi.fn(() => Promise.resolve())
    subscribe = vi.fn(() => ({ unsubscribe: vi.fn() }))

    constructor(options: MockStompClientOptions) {
      this.onConnect = options.onConnect
      this.onDisconnect = options.onDisconnect
      MockClient.instances.push(this)
    }
  }
  const mockApi = { get: vi.fn() }
  return { MockClient, mockApi }
})

vi.mock("@stomp/stompjs", () => ({ Client: MockClient }))
vi.mock("sockjs-client", () => ({ default: vi.fn() }))
vi.mock("@/lib/api", () => ({ api: mockApi }))

function subscriptionsOf(client: InstanceType<typeof MockClient>): MockSubscription[] {
  return client.subscribe.mock.results.map((r) => r.value as MockSubscription)
}

// useMatchStore is eagerly imported at module scope by src/test/storeReset.ts
// (loaded globally by setup.ts, for its afterEach reset) BEFORE this file's
// vi.mock calls above take effect, so a plain top-level `import { useMatchStore }`
// here would get back that already-cached module with the REAL @stomp/stompjs
// Client and the REAL axios `api` baked into its closure — same class of gotcha
// as useAuthStore's module-scope localStorage read (see client/CLAUDE.md).
// vi.resetModules() + a fresh dynamic import per test forces useMatchStore.ts
// (and its internal @stomp/stompjs/sockjs-client/lib-api imports) to be
// re-evaluated against the now-registered mocks.
let useMatchStore: typeof UseMatchStore

beforeEach(async () => {
  vi.resetModules()
  MockClient.instances.length = 0
  mockApi.get.mockReset()
  ;({ useMatchStore } = await import("@/stores/useMatchStore"))
})

afterEach(() => {
  vi.useRealTimers()
})

describe("connect", () => {
  it("does not instantiate a second Client when one already exists", () => {
    useMatchStore.getState().connect("couple-1")
    expect(MockClient.instances).toHaveLength(1)

    useMatchStore.getState().connect("couple-1")
    expect(MockClient.instances).toHaveLength(1)
  })

  it("does not instantiate a second Client while a connect is still in flight", () => {
    useMatchStore.getState().connect("couple-1")
    const firstClient = MockClient.instances[0]
    firstClient.deactivate = vi.fn(() => new Promise<void>(() => {}))

    useMatchStore.getState().disconnect()
    useMatchStore.getState().connect("couple-2")
    expect(useMatchStore.getState().connecting).toBe(true)
    expect(useMatchStore.getState().client).toBeNull()

    useMatchStore.getState().connect("couple-2")
    expect(MockClient.instances).toHaveLength(1)
  })

  it("waits for pendingDisconnect before connecting again", async () => {
    useMatchStore.getState().connect("couple-1")
    const firstClient = MockClient.instances[0]

    let resolveDeactivate: () => void = () => {}
    firstClient.deactivate = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveDeactivate = resolve
        })
    )

    useMatchStore.getState().disconnect()
    expect(useMatchStore.getState().pendingDisconnect).not.toBeNull()

    useMatchStore.getState().connect("couple-1")
    expect(MockClient.instances).toHaveLength(1)
    expect(useMatchStore.getState().client).toBeNull()
    expect(useMatchStore.getState().connecting).toBe(true)

    resolveDeactivate()
    await vi.waitFor(() => {
      expect(useMatchStore.getState().client).not.toBeNull()
    })
    expect(MockClient.instances).toHaveLength(2)
  })

  it("does not block reconnecting longer than DEACTIVATE_TIMEOUT_MS when deactivate never resolves", async () => {
    vi.useFakeTimers()

    useMatchStore.getState().connect("couple-1")
    const firstClient = MockClient.instances[0]
    firstClient.deactivate = vi.fn(() => new Promise<void>(() => {}))

    useMatchStore.getState().disconnect()
    useMatchStore.getState().connect("couple-1")
    expect(useMatchStore.getState().client).toBeNull()

    await vi.advanceTimersByTimeAsync(3000)

    expect(useMatchStore.getState().client).not.toBeNull()
    expect(MockClient.instances).toHaveLength(2)
  })
})

describe("disconnect", () => {
  it("unsubscribes both subscriptions created onConnect and clears the array (regression T7.3)", () => {
    useMatchStore.getState().connect("couple-1")
    const client = MockClient.instances[0]
    client.onConnect?.()

    expect(client.subscribe).toHaveBeenCalledTimes(2)
    const [matchSubscription, notificationsSubscription] = subscriptionsOf(client)

    useMatchStore.getState().disconnect()

    expect(matchSubscription.unsubscribe).toHaveBeenCalledTimes(1)
    expect(notificationsSubscription.unsubscribe).toHaveBeenCalledTimes(1)
    expect(useMatchStore.getState().subscriptions).toHaveLength(0)
  })

  it("resets celebratedMatchKeys, allowing the same match to celebrate again", () => {
    const event: MatchEvent = { tmdbId: 1, title: "Foo", mediaType: "MOVIE" }

    useMatchStore.getState().celebrateMatch(event, "tmdb:1")
    expect(useMatchStore.getState().matchOpen).toBe(true)

    useMatchStore.getState().closeMatch()
    useMatchStore.getState().celebrateMatch(event, "tmdb:1")
    expect(useMatchStore.getState().matchOpen).toBe(false)

    useMatchStore.getState().disconnect()
    useMatchStore.getState().celebrateMatch(event, "tmdb:1")
    expect(useMatchStore.getState().matchOpen).toBe(true)
  })
})

describe("celebrateMatch", () => {
  it("opens the modal on the first call and is a no-op for the same dedupeKey", () => {
    const event: MatchEvent = { tmdbId: 2, title: "Bar", mediaType: "TV" }

    useMatchStore.getState().celebrateMatch(event, "tmdb:2")
    expect(useMatchStore.getState().matchOpen).toBe(true)
    expect(useMatchStore.getState().matchData).toEqual(event)

    useMatchStore.getState().closeMatch()
    useMatchStore.getState().celebrateMatch(event, "tmdb:2")
    expect(useMatchStore.getState().matchOpen).toBe(false)
  })
})

describe("fetchPending", () => {
  it("populates pendingQueue and clears pendingError on the happy path", async () => {
    const pending: PendingMatch[] = [
      { tmdbId: 10, mediaType: "MOVIE", title: "Foo", posterUrl: null, releaseYear: 2020 },
    ]
    mockApi.get.mockResolvedValueOnce({ data: pending })

    await useMatchStore.getState().fetchPending()

    expect(useMatchStore.getState().pendingQueue).toEqual(pending)
    expect(useMatchStore.getState().pendingError).toBe(false)
    expect(useMatchStore.getState().pendingLoading).toBe(false)
  })

  it("clears pendingQueue, sets pendingError and logs on failure", async () => {
    const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {})
    mockApi.get.mockRejectedValueOnce(new Error("network down"))

    await useMatchStore.getState().fetchPending()

    expect(useMatchStore.getState().pendingQueue).toEqual([])
    expect(useMatchStore.getState().pendingError).toBe(true)
    expect(useMatchStore.getState().pendingLoading).toBe(false)
    expect(consoleErrorSpy).toHaveBeenCalled()

    consoleErrorSpy.mockRestore()
  })
})
