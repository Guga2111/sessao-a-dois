import { Client, type StompSubscription } from "@stomp/stompjs"
import SockJS from "sockjs-client"
import { create } from "zustand"

import { api } from "@/lib/api"
import { useAuthStore } from "@/stores/useAuthStore"
import { useNotificationStore } from "@/stores/useNotificationStore"
import type { PendingMatch } from "@/types/media"
import type { Notification } from "@/types/notification"

export type MediaType = "MOVIE" | "TV"

export interface MatchEvent {
  tmdbId: number
  title: string
  mediaType: MediaType
}

// @stomp/stompjs 7.3.0's Client#deactivate() only resolves once the
// underlying socket fires its "close" event and has no timeout of its own -
// a socket that never closes would hang every future reconnect forever, so
// connect() races a pending deactivate() against this timeout instead of
// awaiting it directly.
const DEACTIVATE_TIMEOUT_MS = 3000

interface MatchState {
  client: Client | null
  subscriptions: StompSubscription[]
  connecting: boolean
  pendingDisconnect: Promise<void> | null
  connected: boolean
  matchOpen: boolean
  matchData: MatchEvent | null
  pendingQueue: PendingMatch[]
  pendingLoading: boolean
  // Both the /match STOMP event and the MATCH notification can announce the
  // same match; this tracks which matches already opened the modal so a
  // client never sees the celebration twice for one match. Lives in state
  // (not module scope) so disconnect() can clear it per session.
  celebratedMatchKeys: Set<string>
  connect: (coupleId: string) => void
  disconnect: () => void
  closeMatch: () => void
  fetchPending: () => Promise<void>
  removePending: (tmdbId: number) => void
  celebrateMatch: (event: MatchEvent, dedupeKey: string) => void
}

export const useMatchStore = create<MatchState>((set, get) => ({
  client: null,
  subscriptions: [],
  connecting: false,
  pendingDisconnect: null,
  connected: false,
  matchOpen: false,
  matchData: null,
  pendingQueue: [],
  pendingLoading: false,
  celebratedMatchKeys: new Set<string>(),

  connect: (coupleId) => {
    const { client, connecting, pendingDisconnect } = get()
    if (client || connecting) {
      return
    }
    set({ connecting: true })

    const start = async () => {
      if (pendingDisconnect) {
        await Promise.race([
          pendingDisconnect,
          new Promise<void>((resolve) => setTimeout(resolve, DEACTIVATE_TIMEOUT_MS)),
        ])
      }

      // A disconnect()/connect() may have interleaved while we waited above.
      if (get().client) {
        set({ connecting: false })
        return
      }

      const stompClient = new Client({
        webSocketFactory: () => new SockJS(`${import.meta.env.VITE_API_URL}/ws`),
        reconnectDelay: 5000,
        onConnect: () => {
          const matchSubscription = stompClient.subscribe(
            `/topic/couple/${coupleId}/match`,
            (message) => {
              const matchData = JSON.parse(message.body) as MatchEvent
              get().celebrateMatch(matchData, `tmdb:${matchData.tmdbId}`)
            }
          )
          const notificationsSubscription = stompClient.subscribe(
            `/topic/couple/${coupleId}/notifications`,
            (message) => {
              const notification = JSON.parse(message.body) as Notification
              const currentUserId = useAuthStore.getState().user?.id
              if (currentUserId && notification.recipientUserId === currentUserId) {
                useNotificationStore.getState().pushIncoming(notification)
              }
            }
          )
          set({
            subscriptions: [matchSubscription, notificationsSubscription],
            connected: true,
          })
        },
        onDisconnect: () => {
          set({ connected: false })
        },
      })

      stompClient.activate()
      set({ client: stompClient, connecting: false })
    }

    void start()
  },

  celebrateMatch: (event, dedupeKey) => {
    const { celebratedMatchKeys } = get()
    if (celebratedMatchKeys.has(dedupeKey)) {
      return
    }
    celebratedMatchKeys.add(dedupeKey)
    set({ matchData: event, matchOpen: true })
  },

  disconnect: () => {
    const { client, subscriptions } = get()
    set({ celebratedMatchKeys: new Set(), connecting: false })

    if (!client) {
      return
    }

    subscriptions.forEach((subscription) => subscription.unsubscribe())

    const deactivatePromise: Promise<void> = client.deactivate().then(() => {
      set((state) =>
        state.pendingDisconnect === deactivatePromise ? { pendingDisconnect: null } : {}
      )
    })

    set({
      client: null,
      subscriptions: [],
      connected: false,
      pendingDisconnect: deactivatePromise,
    })
  },

  closeMatch: () => {
    set({ matchOpen: false, matchData: null })
  },

  fetchPending: async () => {
    set({ pendingLoading: true })
    try {
      const response = await api.get<PendingMatch[]>("/api/match/pending")
      set({ pendingQueue: response.data })
    } catch {
      set({ pendingQueue: [] })
    } finally {
      set({ pendingLoading: false })
    }
  },

  removePending: (tmdbId) => {
    set((state) => ({
      pendingQueue: state.pendingQueue.filter((p) => p.tmdbId !== tmdbId),
    }))
  },
}))
