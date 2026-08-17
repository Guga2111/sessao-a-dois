import "@testing-library/jest-dom/vitest"

import { afterEach, vi } from "vitest"
import { cleanup } from "@testing-library/react"

import "./storeReset"

afterEach(() => {
  cleanup()
})

// jsdom has no layout engine, so `window.matchMedia` isn't implemented at
// all — used by lib/useIsMobile.ts.
Object.defineProperty(window, "matchMedia", {
  writable: true,
  configurable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

// jsdom doesn't implement ResizeObserver either — used for positioning by the
// @base-ui/react primitives (popover/select/etc).
class MockResizeObserver implements ResizeObserver {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

vi.stubGlobal("ResizeObserver", MockResizeObserver)

// Minimal IntersectionObserver stub with a manual trigger, for
// screens/hub/LoadMoreSentinel.tsx and components/landing/DashboardPreview.tsx.
// Tests call `triggerIntersection(el, isIntersecting)` from ./intersectionObserver
// to fire the callback for every observer currently watching `el`.
class MockIntersectionObserver implements IntersectionObserver {
  readonly root: Element | Document | null = null
  readonly rootMargin: string = ""
  readonly scrollMargin: string = ""
  readonly thresholds: ReadonlyArray<number> = []

  private readonly callback: IntersectionObserverCallback
  private readonly elements = new Set<Element>()

  constructor(callback: IntersectionObserverCallback) {
    this.callback = callback
    intersectionObserverInstances.add(this)
  }

  observe(target: Element): void {
    this.elements.add(target)
  }

  unobserve(target: Element): void {
    this.elements.delete(target)
  }

  disconnect(): void {
    this.elements.clear()
    intersectionObserverInstances.delete(this)
  }

  takeRecords(): IntersectionObserverEntry[] {
    return []
  }

  fire(target: Element, isIntersecting: boolean): void {
    if (!this.elements.has(target)) {
      return
    }
    const entry: Partial<IntersectionObserverEntry> = {
      isIntersecting,
      target,
      boundingClientRect: target.getBoundingClientRect(),
      intersectionRatio: isIntersecting ? 1 : 0,
      intersectionRect: target.getBoundingClientRect(),
      rootBounds: null,
      time: Date.now(),
    }
    this.callback([entry as IntersectionObserverEntry], this)
  }
}

const intersectionObserverInstances = new Set<MockIntersectionObserver>()

/** Manually fires every active IntersectionObserver currently watching `target`. */
export function triggerIntersection(target: Element, isIntersecting: boolean): void {
  for (const instance of intersectionObserverInstances) {
    instance.fire(target, isIntersecting)
  }
}

vi.stubGlobal("IntersectionObserver", MockIntersectionObserver)
