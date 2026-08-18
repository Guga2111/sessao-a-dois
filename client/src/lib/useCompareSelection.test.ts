import { act, renderHook, waitFor } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import type { ComparisonItem } from "@/components/ComparisonDialog"

import { useCompareSelection } from "./useCompareSelection"

type Item = { id: string; label: string }

const keyOf = (item: Item) => item.id

function makeItem(id: string): ComparisonItem {
  return {
    tmdbId: Number(id) || 0,
    mediaType: "MOVIE",
    title: id,
    year: null,
    posterUrl: null,
    overview: null,
    voteAverage: null,
    coupleRating: null,
    genres: [],
    watchProviders: [],
  }
}

const itemA: Item = { id: "a", label: "A" }
const itemB: Item = { id: "b", label: "B" }
const itemC: Item = { id: "c", label: "C" }

describe("useCompareSelection", () => {
  it("toggle adds an item, removes it by keyOf when already selected, and stops at 2 (third ignored, not replacing)", () => {
    const buildItem = vi.fn(() => new Promise<ComparisonItem>(() => {}))
    const { result } = renderHook(() => useCompareSelection<Item>(buildItem, keyOf))

    act(() => result.current.toggle(itemA))
    expect(result.current.selected.map(keyOf)).toEqual(["a"])

    act(() => result.current.toggle(itemA))
    expect(result.current.selected).toEqual([])

    act(() => result.current.toggle(itemA))
    act(() => result.current.toggle(itemB))
    expect(result.current.selected.map(keyOf)).toEqual(["a", "b"])

    act(() => result.current.toggle(itemC))
    expect(result.current.selected.map(keyOf)).toEqual(["a", "b"])
  })

  it("fetches both items via buildItem once 2 are selected, filling left/right and opening the dialog", async () => {
    const buildItem = vi.fn(async (item: Item) => makeItem(item.id))
    const { result } = renderHook(() => useCompareSelection<Item>(buildItem, keyOf))

    act(() => result.current.toggle(itemA))
    act(() => result.current.toggle(itemB))

    await waitFor(() => expect(result.current.dialogOpen).toBe(true))

    expect(result.current.left?.title).toBe("a")
    expect(result.current.right?.title).toBe("b")
    expect(buildItem).toHaveBeenCalledTimes(2)
    expect(result.current.error).toBeNull()
  })

  it("leaves dialogOpen false, left/right null and an error message on buildItem failure, preserving the selection for retry", async () => {
    const buildItem = vi.fn(async () => {
      throw new Error("boom")
    })
    const { result } = renderHook(() => useCompareSelection<Item>(buildItem, keyOf))

    act(() => result.current.toggle(itemA))
    act(() => result.current.toggle(itemB))

    await waitFor(() => expect(result.current.error).not.toBeNull())

    expect(result.current.dialogOpen).toBe(false)
    expect(result.current.left).toBeNull()
    expect(result.current.right).toBeNull()
    expect(result.current.selected.map(keyOf)).toEqual(["a", "b"])
  })

  it("retry replays the fetch by giving `selected` a new array identity, without duplicating the fetch logic", async () => {
    let shouldFail = true
    const buildItem = vi.fn(async (item: Item) => {
      if (shouldFail) throw new Error("boom")
      return makeItem(item.id)
    })
    const { result } = renderHook(() => useCompareSelection<Item>(buildItem, keyOf))

    act(() => result.current.toggle(itemA))
    act(() => result.current.toggle(itemB))
    await waitFor(() => expect(result.current.error).not.toBeNull())

    const selectedBeforeRetry = result.current.selected
    shouldFail = false
    act(() => result.current.retry())

    expect(result.current.selected).not.toBe(selectedBeforeRetry)
    expect(result.current.selected.map(keyOf)).toEqual(["a", "b"])

    await waitFor(() => expect(result.current.dialogOpen).toBe(true))
    expect(result.current.left?.title).toBe("a")
    expect(result.current.right?.title).toBe("b")
    expect(result.current.error).toBeNull()
    expect(buildItem).toHaveBeenCalledTimes(4)
  })

  it("exitCompareMode zeroes selection, left, right, loading and error, and turns compareMode off", async () => {
    const buildItem = vi.fn(async (item: Item) => makeItem(item.id))
    const { result } = renderHook(() => useCompareSelection<Item>(buildItem, keyOf))

    act(() => result.current.enterCompareMode())
    act(() => result.current.toggle(itemA))
    act(() => result.current.toggle(itemB))
    await waitFor(() => expect(result.current.dialogOpen).toBe(true))

    act(() => result.current.exitCompareMode())

    expect(result.current.compareMode).toBe(false)
    expect(result.current.selected).toEqual([])
    expect(result.current.left).toBeNull()
    expect(result.current.right).toBeNull()
    expect(result.current.error).toBeNull()
    expect(result.current.dialogOpen).toBe(false)
  })

  it("clearSelection zeroes selection, left, right, loading and error without touching compareMode", async () => {
    const buildItem = vi.fn(async (item: Item) => makeItem(item.id))
    const { result } = renderHook(() => useCompareSelection<Item>(buildItem, keyOf))

    act(() => result.current.enterCompareMode())
    act(() => result.current.toggle(itemA))
    act(() => result.current.toggle(itemB))
    await waitFor(() => expect(result.current.dialogOpen).toBe(true))

    act(() => result.current.clearSelection())

    expect(result.current.selected).toEqual([])
    expect(result.current.left).toBeNull()
    expect(result.current.right).toBeNull()
    expect(result.current.error).toBeNull()
    expect(result.current.dialogOpen).toBe(false)
    expect(result.current.compareMode).toBe(true)
  })

  it("Escape exits compare mode only while compareMode is on", () => {
    const buildItem = vi.fn(() => new Promise<ComparisonItem>(() => {}))
    const { result } = renderHook(() => useCompareSelection<Item>(buildItem, keyOf))

    act(() => {
      window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }))
    })
    expect(result.current.compareMode).toBe(false)

    act(() => result.current.enterCompareMode())
    expect(result.current.compareMode).toBe(true)

    act(() => {
      window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }))
    })
    expect(result.current.compareMode).toBe(false)
  })

  it("removes the keydown listener when exiting compare mode and on unmount", () => {
    const addSpy = vi.spyOn(window, "addEventListener")
    const removeSpy = vi.spyOn(window, "removeEventListener")
    const buildItem = vi.fn(() => new Promise<ComparisonItem>(() => {}))
    const { result, unmount } = renderHook(() => useCompareSelection<Item>(buildItem, keyOf))

    act(() => result.current.enterCompareMode())
    expect(addSpy).toHaveBeenCalledWith("keydown", expect.any(Function))

    act(() => result.current.exitCompareMode())
    expect(removeSpy).toHaveBeenCalledWith("keydown", expect.any(Function))

    removeSpy.mockClear()
    act(() => result.current.enterCompareMode())
    unmount()
    expect(removeSpy).toHaveBeenCalledWith("keydown", expect.any(Function))

    addSpy.mockRestore()
    removeSpy.mockRestore()
  })
})
