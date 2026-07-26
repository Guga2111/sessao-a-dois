import type { MediaType } from "@/types/tracking"

export type NotificationType = "MATCH" | "NO_MATCH"

export interface Notification {
  id: string
  type: NotificationType
  tmdbId: number
  mediaType: MediaType
  title: string
  actorUserId: string
  actorName: string | null
  read: boolean
  createdAt: string
}

// Shape of GET /api/notifications?page=&size= (Spring Data `Page<T>` JSON).
export interface PagedNotificationResponse {
  content: Notification[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
