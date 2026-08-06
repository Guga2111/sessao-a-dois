import { useCallback, useEffect, useState } from "react"

import type { ComparisonItem } from "@/components/ComparisonDialog"

/** Selection-mode state for the "compare 2 titles" flow, shared by SearchTab
 *  and SuggestionsTab. Each tab keeps its own instance so switching tabs
 *  (which unmounts the previous tab component entirely) resets it for free,
 *  same effect as the key-remount pattern without needing an explicit key. */
export function useCompareSelection<T>(
  buildItem: (item: T) => Promise<ComparisonItem>,
  keyOf: (item: T) => string
) {
  const [compareMode, setCompareModeState] = useState(false)
  const [selected, setSelected] = useState<T[]>([])
  const [left, setLeft] = useState<ComparisonItem | null>(null)
  const [right, setRight] = useState<ComparisonItem | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const enterCompareMode = useCallback(() => setCompareModeState(true), [])

  const exitCompareMode = useCallback(() => {
    setCompareModeState(false)
    setSelected([])
    setLeft(null)
    setRight(null)
    setLoading(false)
    setError(null)
  }, [])

  const clearSelection = useCallback(() => {
    setSelected([])
    setLeft(null)
    setRight(null)
    setLoading(false)
    setError(null)
  }, [])

  const retry = useCallback(() => {
    setSelected((prev) => [...prev])
  }, [])

  const toggle = useCallback(
    (item: T) => {
      setSelected((prev) => {
        const key = keyOf(item)
        if (prev.some((p) => keyOf(p) === key)) {
          return prev.filter((p) => keyOf(p) !== key)
        }
        if (prev.length >= 2) return prev
        return [...prev, item]
      })
    },
    [keyOf]
  )

  useEffect(() => {
    if (selected.length !== 2) return
    let cancelled = false
    const timer = setTimeout(() => {
      if (cancelled) return
      setLoading(true)
      setError(null)
      Promise.all(selected.map(buildItem))
        .then(([nextLeft, nextRight]) => {
          if (cancelled) return
          setLeft(nextLeft)
          setRight(nextRight)
          setLoading(false)
        })
        .catch(() => {
          if (cancelled) return
          setError(
            "Não foi possível carregar os detalhes para comparação. Tente novamente."
          )
          setLoading(false)
        })
    }, 0)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [selected, buildItem])

  useEffect(() => {
    if (!compareMode) return
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") exitCompareMode()
    }
    window.addEventListener("keydown", handleKey)
    return () => window.removeEventListener("keydown", handleKey)
  }, [compareMode, exitCompareMode])

  return {
    compareMode,
    enterCompareMode,
    exitCompareMode,
    clearSelection,
    retry,
    toggle,
    selected,
    left,
    right,
    error,
    dialogOpen: loading || (left !== null && right !== null),
  }
}
