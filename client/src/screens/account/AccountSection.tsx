import type { ReactNode } from "react"

import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"

export type SectionReach = "you" | "both"

export function ReachChip({
  reach,
  className,
}: {
  reach: SectionReach
  className?: string
}) {
  const isShared = reach === "both"

  return (
    <Badge
      tone={isShared ? "coral" : "neutral"}
      className={cn(
        !isShared && "border-white/[0.08] bg-white/[0.04] text-muted-foreground",
        className
      )}
    >
      <span
        className={cn(
          "size-1.5 rounded-full",
          isShared ? "bg-coral" : "bg-primary"
        )}
      />
      {isShared ? "Afeta vocês dois" : "Só sua conta"}
    </Badge>
  )
}

export function AccountSection({
  id,
  title,
  description,
  reach,
  destructive = false,
  children,
}: {
  id: string
  title: string
  description: string
  reach: SectionReach
  destructive?: boolean
  children: ReactNode
}) {
  return (
    <section
      id={id}
      aria-labelledby={`${id}-title`}
      className={cn(
        "scroll-mt-28 rounded-2xl border p-6 sm:p-7",
        destructive
          ? "border-coral/28 bg-coral-card-wash"
          : "border-white/7 bg-card"
      )}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2
            id={`${id}-title`}
            className={cn(
              "font-display m-0 text-[19px] font-bold tracking-tight",
              destructive ? "text-coral-foreground" : "text-foreground"
            )}
          >
            {title}
          </h2>
          <p className="mt-1.5 max-w-[52ch] text-[14px] leading-relaxed text-muted-foreground">
            {description}
          </p>
        </div>
        <ReachChip reach={reach} />
      </div>

      <div className="mt-5">{children}</div>
    </section>
  )
}
