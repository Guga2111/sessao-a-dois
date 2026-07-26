import { Client, type StompSubscription } from "@stomp/stompjs"
import SockJS from "sockjs-client"
import { create } from "zustand"

import { api } from "@/lib/api"
import { getAuthToken } from "@/lib/authToken"
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
}

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
            set({ matchData, matchOpen: true })
          }
        )
        client.subscribe(
          `/topic/couple/${coupleId}/notifications`,
          (message) => {
            const notification = JSON.parse(message.body) as Notification
            useNotificationStore.getState().pushIncoming(notification)
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
