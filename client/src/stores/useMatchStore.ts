import { Client, type StompSubscription } from "@stomp/stompjs"
import SockJS from "sockjs-client"
import { create } from "zustand"

import { api } from "@/lib/api"
import { getAuthToken } from "@/lib/authToken"
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

interface MatchState {
  client: Client | null
  subscription: StompSubscription | null
  connected: boolean
  matchOpen: boolean
  matchData: MatchEvent | null
  pendingQueue: PendingMatch[]
  pendingLoading: boolean
  connect: (coupleId: string) => void
  disconnect: () => void
  closeMatch: () => void
  fetchPending: () => Promise<void>
  removePending: (tmdbId: number) => void
  celebrateMatch: (event: MatchEvent, dedupeKey: string) => void
}

// Both the /match STOMP event and the MATCH notification can announce the
// same match; this tracks which matches already opened the modal so a client
// never sees the celebration twice for one match.
const celebratedMatchKeys = new Set<string>()

export const useMatchStore = create<MatchState>((set, get) => ({
  client: null,
  subscription: null,
  connected: false,
  matchOpen: false,
  matchData: null,
  pendingQueue: [],
  pendingLoading: false,

  connect: (coupleId) => {
    if (get().client) {
      return
    }

    const token = getAuthToken()
    if (!token) {
      return
    }

    const client = new Client({
      webSocketFactory: () =>
        new SockJS(`${import.meta.env.VITE_API_URL}/ws?token=${token}`),
      reconnectDelay: 5000,
      onConnect: () => {
        const subscription = client.subscribe(
          `/topic/couple/${coupleId}/match`,
          (message) => {
            const matchData = JSON.parse(message.body) as MatchEvent
            get().celebrateMatch(matchData, `tmdb:${matchData.tmdbId}`)
          }
        )
        client.subscribe(
          `/topic/couple/${coupleId}/notifications`,
          (message) => {
            const notification = JSON.parse(message.body) as Notification
            const currentUserId = useAuthStore.getState().user?.id
            if (currentUserId && notification.recipientUserId === currentUserId) {
              useNotificationStore.getState().pushIncoming(notification)
            }
          }
        )
        set({ subscription, connected: true })
      },
      onDisconnect: () => {
        set({ connected: false })
      },
    })

    client.activate()
    set({ client })
  },

  celebrateMatch: (event, dedupeKey) => {
    if (celebratedMatchKeys.has(dedupeKey)) {
      return
    }
    celebratedMatchKeys.add(dedupeKey)
    set({ matchData: event, matchOpen: true })
  },

  disconnect: () => {
    const { client, subscription } = get()
    subscription?.unsubscribe()
    void client?.deactivate()
    set({ client: null, subscription: null, connected: false })
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
