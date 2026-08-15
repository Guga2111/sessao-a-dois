import { forwardRef } from "react"
import { Bell } from "lucide-react"

import { cn } from "@/lib/utils"
import { useNotificationStore } from "@/stores/useNotificationStore"

export const NotificationBell = forwardRef<
  HTMLButtonElement,
  React.ComponentPropsWithoutRef<"button">
>(function NotificationBell({ className, ...props }, ref) {
  const unreadCount = useNotificationStore((s) => s.unreadCount)
  const hasUnread = unreadCount > 0
  const badgeLabel = unreadCount > 9 ? "9+" : String(unreadCount)

  const ariaLabel = hasUnread
    ? `Notificações, ${unreadCount} não ${unreadCount === 1 ? "lida" : "lidas"}`
    : "Notificações"

  return (
    <button
      ref={ref}
      type="button"
      aria-label={ariaLabel}
      className={cn(
        "relative grid size-9.5 flex-none place-items-center rounded-[10px] border border-white/[0.06] bg-white/[0.05] text-muted-foreground transition-colors hover:bg-white/[0.09] hover:text-foreground focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary",
        className
      )}
      {...props}
    >
      <Bell className="size-[18px]" />
      {hasUnread && (
        <>
          <span className="absolute right-[3px] top-[3px] size-2 animate-ping rounded-full bg-[#ff9e2c] motion-reduce:animate-none" />
          <span
            className="absolute -right-1 -top-1.5 flex h-[18px] min-w-[18px] items-center justify-center rounded-full border-2 border-background bg-gradient-to-b from-primary to-[#ff9e2c] px-1 text-[10px] font-bold leading-none text-[#111]"
            aria-hidden="true"
          >
            {badgeLabel}
          </span>
        </>
      )}
    </button>
  )
})
