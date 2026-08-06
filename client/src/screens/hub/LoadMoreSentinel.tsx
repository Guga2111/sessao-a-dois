import { useCallback, useEffect, useRef } from "react"

import type { MediaStatus } from "@/types/tracking"

interface LoadMoreSentinelProps {
  status: MediaStatus
  hasMore: boolean
  loadingMore: boolean
  onLoadMore: (status: MediaStatus) => void
}

/** Invisible sentinel appended after the last mobile carousel card; triggers
 *  `onLoadMore` via IntersectionObserver instead of a "load more" tap. Only
 *  ever mounted on mobile (see call site), so the observer never registers
 *  on desktop. Flags are read from a ref synced in an effect rather than the
 *  closure so the observer callback (created once per `status`) always sees
 *  the latest `hasMore`/`loadingMore` without needing to be recreated. */
export function LoadMoreSentinel({ status, hasMore, loadingMore, onLoadMore }: LoadMoreSentinelProps) {
  const flagsRef = useRef({ hasMore, loadingMore })
  useEffect(() => {
    flagsRef.current = { hasMore, loadingMore }
  })

  const observerRef = useRef<IntersectionObserver | null>(null)
  const sentinelRef = useCallback(
    (node: HTMLDivElement | null) => {
      observerRef.current?.disconnect()
      observerRef.current = null
      if (!node) return
      const observer = new IntersectionObserver(
        (entries) => {
          const flags = flagsRef.current
          if (entries[0]?.isIntersecting && flags.hasMore && !flags.loadingMore) {
            onLoadMore(status)
          }
        },
        { threshold: 0.5 }
      )
      observer.observe(node)
      observerRef.current = observer
    },
    [status, onLoadMore]
  )

  return <div ref={sentinelRef} aria-hidden="true" className="w-px shrink-0" />
}
