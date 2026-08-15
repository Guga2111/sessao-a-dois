import { formatDistanceToNowStrict } from "date-fns"
import { ptBR } from "date-fns/locale"
import { BellOff, Heart, Star, X } from "lucide-react"
import { useState } from "react"

import { Button } from "@/components/ui/button"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"
import { ScrollArea } from "@/components/ui/scroll-area"
import { NotificationBell } from "@/components/NotificationBell"
import { RatingRequestDialog } from "@/components/RatingRequestDialog"
import { NotificationRowSkeleton } from "@/components/skeletons/NotificationRowSkeleton"
import { cn } from "@/lib/utils"
import { useDelayedLoading } from "@/lib/useDelayedLoading"
import { useAuthStore } from "@/stores/useAuthStore"
import { useNotificationStore } from "@/stores/useNotificationStore"
import type { Notification } from "@/types/notification"

function relativeTime(isoDate: string): string {
  return formatDistanceToNowStrict(new Date(isoDate), {
    addSuffix: true,
    locale: ptBR,
  })
}

function notificationText(notification: Notification, currentUserId?: string): string {
  if (notification.type === "MATCH") {
    return `Vocês dois curtiram "${notification.title}" — foi para Queremos Ver.`
  }

  if (notification.type === "RATING_REQUEST") {
    const actorName = notification.actorName ?? "Seu par"
    return `${actorName} avaliou "${notification.title}" — dê sua nota também.`
  }

  const isActor = notification.actorUserId === currentUserId
  if (isActor) {
    return `Sem match desta vez em "${notification.title}".`
  }

  const actorName = notification.actorName ?? "Seu par"
  return `${actorName} passou "${notification.title}" — sem match desta vez.`
}

function NotificationIcon({ type }: { type: Notification["type"] }) {
  if (type === "MATCH") {
    return (
      <div className="grid size-9 flex-none place-items-center rounded-full bg-gradient-to-b from-primary to-series text-on-primary shadow-[var(--shadow-glow-primary-8)]">
        <Heart className="size-4 fill-current" />
      </div>
    )
  }

  if (type === "RATING_REQUEST") {
    return (
      <div className="grid size-9 flex-none place-items-center rounded-full border border-primary/40 bg-primary/10 text-primary">
        <Star className="size-4 fill-current" />
      </div>
    )
  }

  return (
    <div className="grid size-9 flex-none place-items-center rounded-full border border-white/[0.1] bg-white/[0.05] text-muted-foreground">
      <X className="size-4" />
    </div>
  )
}

function NotificationItem({
  notification,
  currentUserId,
  onSelect,
}: {
  notification: Notification
  currentUserId?: string
  onSelect: (id: string) => void
}) {
  return (
    <Button
      type="button"
      variant="ghost"
      onClick={() => onSelect(notification.id)}
      className={cn(
        "h-auto w-full items-start justify-start gap-3 rounded-[12px] px-3 py-3 text-left transition-colors hover:bg-white/[0.05]",
        !notification.read && "bg-primary/6"
      )}
    >
      <NotificationIcon type={notification.type} />
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <div
            className={cn(
              "text-[13.5px] font-semibold leading-snug",
              notification.read ? "text-muted-foreground" : "text-foreground"
            )}
          >
            {notification.title}
          </div>
          {!notification.read && (
            <span
              className="mt-1.5 size-2 flex-none rounded-full bg-primary"
              aria-hidden="true"
            />
          )}
        </div>
        <p
          className={cn(
            "mt-1 text-[12.5px] leading-snug",
            notification.read ? "text-caption-foreground" : "text-label-foreground"
          )}
        >
          {notificationText(notification, currentUserId)}
        </p>
        <div className="mt-1.5 text-[11px] text-caption-foreground">
          {relativeTime(notification.createdAt)}
        </div>
      </div>
    </Button>
  )
}

export function NotificationDropdown() {
  const notifications = useNotificationStore((s) => s.notifications)
  const unreadCount = useNotificationStore((s) => s.unreadCount)
  const hasMore = useNotificationStore((s) => s.hasMore)
  const loading = useNotificationStore((s) => s.loading)
  const fetchMore = useNotificationStore((s) => s.fetchMore)
  const markAsRead = useNotificationStore((s) => s.markAsRead)
  const markAllAsRead = useNotificationStore((s) => s.markAllAsRead)
  const currentUserId = useAuthStore((s) => s.user?.id)
  const [ratingRequest, setRatingRequest] = useState<Notification | null>(null)
  const showSkeleton = useDelayedLoading(loading)

  const handleSelect = (id: string) => {
    const notification = notifications.find((n) => n.id === id)
    if (notification?.type === "RATING_REQUEST" && notification.mediaTrackId) {
      setRatingRequest(notification)
      return
    }
    void markAsRead(id)
  }

  return (
    <>
      <Popover>
        <PopoverTrigger render={<NotificationBell />} />
        <PopoverContent
          align="end"
          sideOffset={10}
          className="flex w-[min(380px,calc(100vw-32px))] flex-col gap-0 rounded-[18px] border border-white/[0.1] bg-card p-0 shadow-[var(--shadow-elevation-6)]"
        >
          <div className="flex items-center justify-between gap-3 border-b border-white/[0.07] px-4 py-3.5">
            <div className="font-display text-[15px] font-bold tracking-tight text-foreground">
              Notificações
            </div>
            {unreadCount > 0 && (
              <Button
                type="button"
                variant="link"
                onClick={() => void markAllAsRead()}
                className="h-auto p-0 text-[12px] font-semibold text-primary transition-colors hover:text-accent hover:no-underline"
              >
                Marcar todas como lidas
              </Button>
            )}
          </div>

          <ScrollArea viewportClassName="max-h-[420px]">
            <div className="p-2">
              {showSkeleton ? (
                <div className="flex flex-col gap-1">
                  {Array.from({ length: 4 }).map((_, index) => (
                    <NotificationRowSkeleton key={index} />
                  ))}
                </div>
              ) : notifications.length === 0 ? (
                <div className="flex flex-col items-center gap-3 px-4 py-10 text-center">
                  <div className="grid size-11 place-items-center rounded-full border border-white/[0.08] bg-white/[0.04] text-muted-foreground">
                    <BellOff className="size-5" />
                  </div>
                  <p className="text-[13px] leading-snug text-muted-foreground">
                    Nenhuma notificação por aqui ainda.
                  </p>
                </div>
              ) : (
                <div className="flex flex-col gap-1">
                  {notifications.map((notification) => (
                    <NotificationItem
                      key={notification.id}
                      notification={notification}
                      currentUserId={currentUserId}
                      onSelect={handleSelect}
                    />
                  ))}
                </div>
              )}

              {hasMore && (
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => void fetchMore()}
                  className="mt-1 h-auto w-full rounded-[10px] py-2.5 text-center text-[13px] font-semibold text-muted-foreground transition-colors hover:bg-white/[0.05] hover:text-foreground"
                >
                  Ver mais
                </Button>
              )}
            </div>
          </ScrollArea>
        </PopoverContent>
      </Popover>

      <RatingRequestDialog
        mediaTrackId={ratingRequest?.mediaTrackId ?? null}
        title={ratingRequest?.title ?? ""}
        onClose={() => setRatingRequest(null)}
        onSuccess={() => {
          if (ratingRequest) {
            void markAsRead(ratingRequest.id)
          }
          setRatingRequest(null)
        }}
      />
    </>
  )
}
