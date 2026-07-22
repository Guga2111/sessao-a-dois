import { Client, type StompSubscription } from "@stomp/stompjs"
import SockJS from "sockjs-client"
import { create } from "zustand"

import { getAuthToken } from "@/lib/authToken"

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
  connect: (coupleId: string) => void
  disconnect: () => void
  closeMatch: () => void
}

export const useMatchStore = create<MatchState>((set, get) => ({
  client: null,
  subscription: null,
  connected: false,
  matchOpen: false,
  matchData: null,

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
}))
