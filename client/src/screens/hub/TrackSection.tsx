import { ChevronDown } from "lucide-react"

import { Button } from "@/components/ui/button"
import { MediaCard } from "@/components/MediaCard"
import { MediaCardSkeleton } from "@/components/skeletons/MediaCardSkeleton"
import { Skeleton } from "@/components/ui/skeleton"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import type { MediaStatus, MediaTrackResponse } from "@/types/tracking"
import { LoadMoreSentinel } from "./LoadMoreSentinel"
import type { Section, SectionState } from "./helpers"

interface TrackSectionProps {
  section: Section
  state: SectionState
  isOpen: boolean
  onOpenChange: (open: boolean) => void
  showSkeleton: boolean
  showLoadMoreSkeleton: boolean
  isMobile: boolean
  myUserId: string
  onStatusChange: (track: MediaTrackResponse) => void
  onStartWatching: () => void
  onReview: (track: MediaTrackResponse) => void
  onRated: (track: MediaTrackResponse) => void
  onClick: (track: MediaTrackResponse) => void
  onDelete: (track: MediaTrackResponse) => void
  onLoadMore: (status: MediaStatus) => void
  compareMode: boolean
  compareSelected: MediaTrackResponse[]
  onCompareToggle: (track: MediaTrackResponse) => void
}

export function TrackSection({
  section,
  state,
  isOpen,
  onOpenChange,
  showSkeleton,
  showLoadMoreSkeleton,
  isMobile,
  myUserId,
  onStatusChange,
  onStartWatching,
  onReview,
  onRated,
  onClick,
  onDelete,
  onLoadMore,
  compareMode,
  compareSelected,
  onCompareToggle,
}: TrackSectionProps) {
  const { items, loading, loadingMore, total } = state
  const hasMore = !loading && items.length < total

  return (
    <Collapsible open={isOpen} onOpenChange={onOpenChange} className="mb-11">
      <CollapsibleTrigger className="mb-4.5 flex w-full cursor-pointer items-center gap-3">
        <span
          className="size-2.5 rounded-full"
          style={{
            background: section.dotColor,
            boxShadow: `0 0 12px ${section.dotColor}`,
          }}
        />
        <h2 className="font-display text-xl tracking-tight">{section.title}</h2>
        <span className="flex items-center rounded-full bg-white/[0.05] px-2.5 py-0.5 text-[13px] text-[#a6a39a]">
          {showSkeleton ? <Skeleton className="h-3 w-4" /> : total}
        </span>
        <ChevronDown
          className="ml-auto size-4 text-[#a6a39a] transition-transform duration-200"
          style={{ transform: isOpen ? "rotate(0deg)" : "rotate(-90deg)" }}
        />
      </CollapsibleTrigger>

      <CollapsibleContent>
        {showSkeleton ? (
          <div className="no-scrollbar flex gap-5.5 overflow-x-auto pr-[20vw] [overscroll-behavior-x:contain] [scroll-snap-type:x_mandatory] md:grid md:grid-cols-[repeat(auto-fill,minmax(250px,1fr))] md:overflow-visible md:pr-0 md:[overscroll-behavior-x:auto] md:[scroll-snap-type:none]">
            <MediaCardSkeleton />
            <MediaCardSkeleton />
            <MediaCardSkeleton />
            <MediaCardSkeleton />
          </div>
        ) : items.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-white/10 px-5 py-7 text-sm text-[#a6a39a]">
            {section.emptyMessage}
          </div>
        ) : (
          <>
            <div className="no-scrollbar flex gap-5.5 overflow-x-auto pr-[20vw] [overscroll-behavior-x:contain] [scroll-snap-type:x_mandatory] md:grid md:grid-cols-[repeat(auto-fill,minmax(250px,1fr))] md:overflow-visible md:pr-0 md:[overscroll-behavior-x:auto] md:[scroll-snap-type:none]">
              {items.map((track) => (
                <div
                  key={track.id}
                  className="min-w-[72vw] max-w-[72vw] shrink-0 [scroll-snap-align:start] md:min-w-0 md:max-w-none md:shrink md:[scroll-snap-align:none]"
                >
                  <MediaCard
                    track={track}
                    myUserId={myUserId}
                    onStatusChange={onStatusChange}
                    onStartWatching={onStartWatching}
                    onReview={onReview}
                    onRated={onRated}
                    onClick={onClick}
                    onDelete={onDelete}
                    compareMode={compareMode}
                    compareSelected={compareSelected.some((t) => t.id === track.id)}
                    compareOrder={
                      compareSelected.findIndex((t) => t.id === track.id) + 1 || null
                    }
                    onCompareToggle={onCompareToggle}
                  />
                </div>
              ))}
              {showLoadMoreSkeleton && <MediaCardSkeleton />}
              {isMobile && hasMore && (
                <LoadMoreSentinel
                  status={section.status}
                  hasMore={hasMore}
                  loadingMore={loadingMore}
                  onLoadMore={onLoadMore}
                />
              )}
            </div>
            {hasMore && (
              <div className="mt-6 hidden justify-center md:flex">
                {showLoadMoreSkeleton ? (
                  <div className="w-[220px]">
                    <MediaCardSkeleton />
                  </div>
                ) : (
                  <Button
                    type="button"
                    onClick={() => onLoadMore(section.status)}
                    disabled={loadingMore}
                    className="h-auto cursor-pointer rounded-full border border-white/10 bg-white/[0.04] px-6 py-2.5 text-[13px] font-semibold text-[#f6f4ec] transition-colors hover:border-[rgba(255,203,43,.4)] hover:bg-white/[0.07] disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    Carregar mais
                  </Button>
                )}
              </div>
            )}
          </>
        )}
      </CollapsibleContent>
    </Collapsible>
  )
}
