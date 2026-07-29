import { useEffect, useRef, useState } from "react"

interface UseDelayedLoadingOptions {
  delayMs?: number
  minVisibleMs?: number
}

export function useDelayedLoading(
  loading: boolean,
  opts?: UseDelayedLoadingOptions,
): boolean {
  const { delayMs = 150, minVisibleMs = 400 } = opts ?? {}
  const [showSkeleton, setShowSkeleton] = useState(false)
  const shownAtRef = useRef<number | null>(null)

  useEffect(() => {
    if (loading) {
      const showTimer = setTimeout(() => {
        shownAtRef.current = Date.now()
        setShowSkeleton(true)
      }, delayMs)
      return () => clearTimeout(showTimer)
    }

    if (shownAtRef.current === null) return

    const elapsed = Date.now() - shownAtRef.current
    const remaining = Math.max(minVisibleMs - elapsed, 0)
    const hideTimer = setTimeout(() => {
      shownAtRef.current = null
      setShowSkeleton(false)
    }, remaining)
    return () => clearTimeout(hideTimer)
  }, [loading, delayMs, minVisibleMs])

  return showSkeleton
}
