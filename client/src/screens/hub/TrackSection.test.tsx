import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"

import { triggerIntersection } from "@/test/setup"
import type { MediaTrackResponse } from "@/types/tracking"
import { TrackSection } from "./TrackSection"
import type { Section, SectionState } from "./helpers"

const section: Section = {
  status: "WATCHING",
  title: "Assistindo Atualmente",
  dotColor: "#ff9e2c",
  emptyMessage: "Nada em andamento agora. Que tal começar algo hoje à noite?",
}

function makeTrack(overrides: Partial<MediaTrackResponse> = {}): MediaTrackResponse {
  return {
    id: "t1",
    tmdbId: 100,
    mediaType: "MOVIE",
    status: "WATCHING",
    watchedDate: null,
    runtime: 120,
    createdAt: "2026-01-01T00:00:00Z",
    reviews: [],
    title: "Filme Teste",
    posterUrl: null,
    releaseYear: 2024,
    ...overrides,
  }
}

function makeState(overrides: Partial<SectionState> = {}): SectionState {
  return {
    items: [],
    page: 0,
    total: 0,
    loading: false,
    loadingMore: false,
    ...overrides,
  }
}

function renderSection(overrides: {
  state?: SectionState
  showSkeleton?: boolean
  showLoadMoreSkeleton?: boolean
  isMobile?: boolean
  onLoadMore?: (status: MediaTrackResponse["status"]) => void
} = {}) {
  const onLoadMore = overrides.onLoadMore ?? vi.fn()
  const result = render(
    <TrackSection
      section={section}
      state={overrides.state ?? makeState()}
      isOpen
      onOpenChange={vi.fn()}
      showSkeleton={overrides.showSkeleton ?? false}
      showLoadMoreSkeleton={overrides.showLoadMoreSkeleton ?? false}
      isMobile={overrides.isMobile ?? false}
      myUserId="user-1"
      onStatusChange={vi.fn()}
      onStartWatching={vi.fn()}
      onReview={vi.fn()}
      onRated={vi.fn()}
      onClick={vi.fn()}
      onDelete={vi.fn()}
      onLoadMore={onLoadMore}
      compareMode={false}
      compareSelected={[]}
      onCompareToggle={vi.fn()}
    />
  )
  return { onLoadMore, container: result.container }
}

describe("TrackSection", () => {
  it("renderiza o estado de carregando (skeleton) quando showSkeleton e true", () => {
    const { container } = render(
      <TrackSection
        section={section}
        state={makeState({ total: 5, loading: true })}
        isOpen
        onOpenChange={vi.fn()}
        showSkeleton
        showLoadMoreSkeleton={false}
        isMobile={false}
        myUserId="user-1"
        onStatusChange={vi.fn()}
        onStartWatching={vi.fn()}
        onReview={vi.fn()}
        onRated={vi.fn()}
        onClick={vi.fn()}
        onDelete={vi.fn()}
        onLoadMore={vi.fn()}
        compareMode={false}
        compareSelected={[]}
        onCompareToggle={vi.fn()}
      />
    )

    // o contador do cabecalho tambem vira skeleton em vez do numero
    expect(screen.queryByText("5")).not.toBeInTheDocument()
    expect(screen.queryByText(section.emptyMessage)).not.toBeInTheDocument()
    expect(container.querySelectorAll('[data-slot="skeleton"]').length).toBeGreaterThan(0)
  })

  it("renderiza o estado vazio com a emptyMessage da secao", () => {
    renderSection({ state: makeState({ items: [], total: 0 }) })

    expect(screen.getByText(section.emptyMessage)).toBeInTheDocument()
  })

  it("renderiza o grid quando ha itens", () => {
    const track = makeTrack({ id: "t1", title: "Duna" })
    renderSection({ state: makeState({ items: [track], total: 1 }) })

    expect(screen.getByText("Duna")).toBeInTheDocument()
    expect(screen.queryByText(section.emptyMessage)).not.toBeInTheDocument()
  })

  it("nao mostra botao nem sentinela de carregar mais quando nao ha proxima pagina", () => {
    const track = makeTrack()
    renderSection({ state: makeState({ items: [track], total: 1 }), isMobile: true })

    expect(screen.queryByRole("button", { name: "Carregar mais" })).not.toBeInTheDocument()
  })

  it("mostra o botao Carregar mais quando ha proxima pagina e ele dispara onLoadMore", async () => {
    const user = userEvent.setup()
    const track = makeTrack()
    const { onLoadMore } = renderSection({
      state: makeState({ items: [track], total: 5 }),
      isMobile: false,
    })

    const button = screen.getByRole("button", { name: "Carregar mais" })
    await user.click(button)

    expect(onLoadMore).toHaveBeenCalledTimes(1)
    expect(onLoadMore).toHaveBeenCalledWith(section.status)
  })

  it("no mobile, a sentinela de intersecao dispara onLoadMore uma unica vez por intersecao", () => {
    const track = makeTrack()
    const { onLoadMore, container } = renderSection({
      state: makeState({ items: [track], total: 5 }),
      isMobile: true,
    })

    const sentinel = container.querySelector('div[aria-hidden="true"]')
    expect(sentinel).not.toBeNull()

    triggerIntersection(sentinel as Element, true)

    expect(onLoadMore).toHaveBeenCalledTimes(1)
    expect(onLoadMore).toHaveBeenCalledWith(section.status)
  })

  it("no desktop (isMobile false), a sentinela de intersecao nao e montada mesmo havendo proxima pagina", () => {
    const track = makeTrack()
    const { container } = renderSection({
      state: makeState({ items: [track], total: 5 }),
      isMobile: false,
    })

    expect(container.querySelector('div[aria-hidden="true"]')).toBeNull()
  })
})
