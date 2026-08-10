import type { ReactNode } from "react"

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
    <span
      className={cn(
        "inline-flex flex-none items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-semibold tracking-[.02em]",
        isShared
          ? "border-[rgba(255,92,71,.3)] bg-[rgba(255,92,71,.08)] text-[#ff8f7c]"
          : "border-white/[0.08] bg-white/[0.04] text-[#a6a39a]",
        className
      )}
    >
      <span
        className={cn(
          "size-1.5 rounded-full",
          isShared ? "bg-[#ff5c47]" : "bg-[#ffcb2b]"
        )}
      />
      {isShared ? "Afeta vocês dois" : "Só sua conta"}
    </span>
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
        "scroll-mt-28 rounded-[18px] border p-6 sm:p-7",
        destructive
          ? "border-[rgba(255,92,71,.28)] bg-[linear-gradient(180deg,rgba(255,92,71,.07),rgba(255,92,71,0)_120px),#161513]"
          : "border-[rgba(255,255,255,.07)] bg-[#161513]"
      )}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2
            id={`${id}-title`}
            className={cn(
              "font-display m-0 text-[19px] font-bold tracking-tight",
              destructive ? "text-[#ffb3a5]" : "text-[#f6f4ec]"
            )}
          >
            {title}
          </h2>
          <p className="mt-1.5 max-w-[52ch] text-[14px] leading-relaxed text-[#a6a39a]">
            {description}
          </p>
        </div>
        <ReachChip reach={reach} />
      </div>

      <div className="mt-5">{children}</div>
    </section>
  )
}
