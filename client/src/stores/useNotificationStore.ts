import { create } from "zustand"

import { api } from "@/lib/api"
import { useMatchStore } from "@/stores/useMatchStore"
import type { Notification, PagedNotificationResponse } from "@/types/notification"

const PAGE_SIZE = 20

interface UnreadCountResponse {
  count: number
}

interface NotificationState {
  notifications: Notification[]
  unreadCount: number
  page: number
  hasMore: boolean
  loading: boolean
  fetchNotifications: () => Promise<void>
  fetchMore: () => Promise<void>
  fetchUnreadCount: () => Promise<void>
  markAsRead: (id: string) => Promise<void>
  markAllAsRead: () => Promise<void>
  pushIncoming: (notification: Notification) => void
}

export const useNotificationStore = create<NotificationState>((set, get) => ({
  notifications: [],
  unreadCount: 0,
  page: 0,
  hasMore: false,
  loading: true,

  fetchNotifications: async () => {
    set({ loading: true })
    try {
      const { data } = await api.get<PagedNotificationResponse>("/api/notifications", {
        params: { page: 0, size: PAGE_SIZE },
      })
      set({
        notifications: data.content,
        page: data.number,
        hasMore: data.number + 1 < data.totalPages,
      })
    } finally {
      set({ loading: false })
    }
  },

  fetchMore: async () => {
    const { page, hasMore, notifications } = get()
    if (!hasMore) {
      return
    }

    const nextPage = page + 1
    const { data } = await api.get<PagedNotificationResponse>("/api/notifications", {
      params: { page: nextPage, size: PAGE_SIZE },
    })
    set({
      notifications: [...notifications, ...data.content],
      page: data.number,
      hasMore: data.number + 1 < data.totalPages,
    })
  },

  fetchUnreadCount: async () => {
    const { data } = await api.get<UnreadCountResponse>("/api/notifications/unread-count")
    set({ unreadCount: data.count })
  },

  markAsRead: async (id) => {
    const { notifications, unreadCount } = get()
    const target = notifications.find((n) => n.id === id)
    if (!target || target.read) {
      return
    }

    set({
      notifications: notifications.map((n) => (n.id === id ? { ...n, read: true } : n)),
      unreadCount: Math.max(0, unreadCount - 1),
    })

    try {
      await api.patch(`/api/notifications/${id}/read`)
    } catch (error) {
      set({ notifications, unreadCount })
      throw error
    }
  },

  markAllAsRead: async () => {
    const { notifications, unreadCount } = get()

    set({
      notifications: notifications.map((n) => ({ ...n, read: true })),
      unreadCount: 0,
    })

    try {
      await api.patch("/api/notifications/read-all")
    } catch (error) {
      set({ notifications, unreadCount })
      throw error
    }
  },

  pushIncoming: (notification) => {
    set((state) => ({
      notifications: [notification, ...state.notifications],
      unreadCount: state.unreadCount + 1,
    }))

    if (notification.type === "MATCH") {
      useMatchStore.getState().celebrateMatch(
        {
          tmdbId: notification.tmdbId,
          title: notification.title,
          mediaType: notification.mediaType,
        },
        `tmdb:${notification.tmdbId}`
      )
    }
  },
}))
