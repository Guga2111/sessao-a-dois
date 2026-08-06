import { useEffect, useState, type ReactNode } from "react"

import { Heart, RefreshCw, TriangleAlert, X } from "lucide-react"

import { ComparisonDialog } from "@/components/ComparisonDialog"
import { PendingDetailModal } from "@/components/PendingDetailModal"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { api } from "@/lib/api"
import { buildComparisonItem } from "@/lib/comparisonItem"
import { useCompareSelection } from "@/lib/useCompareSelection"
import { useDelayedLoading } from "@/lib/useDelayedLoading"
import { cn } from "@/lib/utils"
import { useMatchStore } from "@/stores/useMatchStore"
import type { PendingMatch } from "@/types/media"

import { CompareSelectionChip, CompareToggleButton } from "./compareUi"
import { TYPE_LABEL, keyOfCompareItem } from "./helpers"

export function SuggestionsTab() {
  const { pendingQueue, pendingLoading, pendingError, fetchPending, removePending } =
    useMatchStore()
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [lastAction, setLastAction] = useState<"like" | "reject" | null>(null)
  const [detailItem, setDetailItem] = useState<PendingMatch | null>(null)
  const showPendingSkeleton = useDelayedLoading(pendingLoading)
  const compare = useCompareSelection<PendingMatch>(
    (item) => buildComparisonItem(item.mediaType, item.tmdbId),
    keyOfCompareItem
  )

  useEffect(() => {
    fetchPending()
  }, [fetchPending])

  const current = pendingQueue[0] ?? null

  const handleReject = async () => {
    if (!current || actionLoading) return
    setActionLoading(true)
    setLastAction("reject")
    try {
      await api.post("/api/match/reject", {
        tmdbId: current.tmdbId,
        mediaType: current.mediaType,
      })
      removePending(current.tmdbId)
      setDetailItem(null)
      setActionError(null)
    } catch {
      setActionError("Nao foi possivel registrar. Tentem novamente.")
    } finally {
      setActionLoading(false)
    }
  }

  const handleLike = async () => {
    if (!current || actionLoading) return
    setActionLoading(true)
    setLastAction("like")
    try {
      await api.post("/api/match/like", {
        tmdbId: current.tmdbId,
        mediaType: current.mediaType,
        title: current.title,
        posterUrl: current.posterUrl,
        releaseYear: current.releaseYear,
      })
      removePending(current.tmdbId)
      setDetailItem(null)
      setActionError(null)
    } catch {
      setActionError("Nao foi possivel registrar. Tentem novamente.")
    } finally {
      setActionLoading(false)
    }
  }

  const handleActionRetry = () => {
    if (lastAction === "like") handleLike()
    else if (lastAction === "reject") handleReject()
  }

  if (showPendingSkeleton) {
    return (
      <div className="flex flex-col items-center">
        <div className="w-full max-w-[calc(100vw-32px)] sm:max-w-[280px]">
          <div className="overflow-hidden rounded-[22px] border border-[rgba(255,255,255,.07)] bg-[#161513]">
            <Skeleton className="aspect-[2/3] rounded-none" />
          </div>

          <div className="mt-6 flex items-center justify-center gap-6">
            <Skeleton className="size-14 rounded-full" />
            <Skeleton className="size-14 rounded-full" />
          </div>

          <div className="mt-4 flex justify-center">
            <Skeleton className="h-3.5 w-32" />
          </div>
        </div>
      </div>
    )
  }

  if (pendingError) {
    return (
      <div className="mx-auto flex max-w-[320px] flex-col items-center gap-2 rounded-2xl border border-[rgba(255,107,107,.35)] bg-[rgba(255,107,107,.08)] px-5 py-3 text-center">
        <p className="flex items-center gap-2 text-[13px] text-[#ffb3b3]">
          <TriangleAlert className="size-4" />
          Nao foi possivel carregar as sugestoes pendentes.
        </p>
        <Button
          type="button"
          onClick={() => fetchPending()}
          className="flex items-center gap-2 rounded-full border border-[rgba(255,107,107,.4)] bg-transparent px-4 py-1.5 text-[13px] font-semibold text-[#ffb3b3] hover:bg-[rgba(255,107,107,.12)]"
        >
          <RefreshCw className="size-3.5" /> Tentar novamente
        </Button>
      </div>
    )
  }

  const hue = current?.tmdbId ? current.tmdbId % 360 : 0

  const compareToggleButton: ReactNode = (
    <CompareToggleButton
      active={compare.compareMode}
      onToggle={() =>
        compare.compareMode ? compare.exitCompareMode() : compare.enterCompareMode()
      }
    />
  )

  return (
    <>
      <div className="mb-6 flex items-center justify-between gap-3">
        <p className="text-[13px] text-[#a6a39a]">
          {pendingQueue.length > 0
            ? `${pendingQueue.length} ${
                pendingQueue.length === 1 ? "sugestão pendente" : "sugestões pendentes"
              }`
            : "Nenhuma sugestão pendente"}
        </p>
        {compareToggleButton}
      </div>

      {compare.compareMode ? (
        pendingQueue.length === 0 ? (
          <div className="mx-auto max-w-[420px] rounded-2xl border border-dashed border-white/10 px-6 py-10 text-center text-sm text-[#a6a39a]">
            Nenhuma sugestão pendente para comparar.
          </div>
        ) : (
          <div className="grid grid-cols-[repeat(auto-fill,minmax(140px,1fr))] gap-4">
            {pendingQueue.map((item) => {
              const key = keyOfCompareItem(item)
              const isSelected = compare.selected.some(
                (selectedItem) => keyOfCompareItem(selectedItem) === key
              )
              const order =
                compare.selected.findIndex(
                  (selectedItem) => keyOfCompareItem(selectedItem) === key
                ) + 1 || null
              const itemHue = item.tmdbId % 360
              return (
                <div
                  key={key}
                  onClick={() => compare.toggle(item)}
                  aria-pressed={isSelected}
                  className={cn(
                    "cursor-pointer overflow-hidden rounded-[16px] border bg-[#161513] transition-colors",
                    isSelected
                      ? "border-2 border-[#ffcb2b] shadow-[0_0_24px_rgba(255,203,43,.18)]"
                      : "border-dashed border-white/20 hover:border-white/35"
                  )}
                >
                  <div
                    className="relative aspect-[2/3]"
                    style={{
                      background: `linear-gradient(160deg, hsl(${itemHue} 42% 24%), hsl(${itemHue} 46% 11%))`,
                    }}
                  >
                    {item.posterUrl ? (
                      <img
                        src={item.posterUrl}
                        alt={item.title}
                        className="absolute inset-0 h-full w-full object-cover"
                      />
                    ) : (
                      <div
                        className="absolute inset-0"
                        style={{
                          backgroundImage:
                            "repeating-linear-gradient(135deg, rgba(255,255,255,.05) 0 8px, transparent 8px 16px)",
                        }}
                      />
                    )}
                    <div className="absolute top-2 left-2 rounded-lg bg-[rgba(9,9,10,.6)] px-2 py-0.5 text-[10px] font-semibold text-[#f6f4ec] backdrop-blur-md">
                      {TYPE_LABEL[item.mediaType]}
                    </div>
                    {isSelected && (
                      <div className="absolute top-2 right-2 grid size-6 flex-none place-items-center rounded-full border-2 border-[#161513] bg-[#ffcb2b] text-[12px] font-black text-[#111]">
                        {order}
                      </div>
                    )}
                  </div>
                  <div className="truncate p-2.5 text-[12.5px] font-semibold text-[#f6f4ec]">
                    {item.title}
                  </div>
                </div>
              )
            })}
          </div>
        )
      ) : !current ? (
        <div className="mx-auto max-w-[420px] rounded-2xl border border-dashed border-white/10 px-6 py-10 text-center text-sm text-[#a6a39a]">
          Nenhuma sugestao pendente. Voltem a buscar titulos na aba "Buscar".
        </div>
      ) : (
        <div className="flex flex-col items-center">
          <div className="w-full max-w-[calc(100vw-32px)] sm:max-w-[280px]">
            <div
              className="cursor-pointer overflow-hidden rounded-[22px] border border-[rgba(255,255,255,.07)] bg-[#161513] transition-shadow hover:shadow-[0_0_0_2px_rgba(255,203,43,.25)]"
              onClick={() => setDetailItem(current)}
            >
              <div
                className="relative aspect-[2/3]"
                style={{
                  background: `linear-gradient(160deg, hsl(${hue} 42% 24%), hsl(${hue} 46% 11%))`,
                }}
              >
                {current.posterUrl ? (
                  <img
                    src={current.posterUrl}
                    alt={current.title}
                    className="absolute inset-0 h-full w-full object-cover"
                  />
                ) : (
                  <div
                    className="absolute inset-0"
                    style={{
                      backgroundImage:
                        "repeating-linear-gradient(135deg, rgba(255,255,255,.05) 0 8px, transparent 8px 16px)",
                    }}
                  />
                )}
                <div className="absolute top-3 left-3 rounded-lg bg-[rgba(9,9,10,.6)] px-2.5 py-1 text-[11px] font-semibold text-[#f6f4ec] backdrop-blur-md">
                  {TYPE_LABEL[current.mediaType]}
                </div>
                <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-[#09090a] via-[rgba(9,9,10,.85)] to-transparent px-5 pt-16 pb-5">
                  <h2 className="font-display text-[22px] font-bold leading-tight tracking-tight">
                    {current.title}
                  </h2>
                </div>
              </div>
            </div>

            <div className="mt-6 flex items-center justify-center gap-6">
              <Button
                type="button"
                variant="destructive"
                size="icon-lg"
                onClick={handleReject}
                disabled={actionLoading}
                className="size-14 rounded-full border border-[rgba(255,107,107,.3)] bg-[rgba(255,107,107,.08)] text-[#ff6b6b] hover:bg-[rgba(255,107,107,.16)]"
              >
                <X className="size-6" strokeWidth={2.5} />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="icon-lg"
                onClick={handleLike}
                disabled={actionLoading}
                className="size-14 rounded-full border border-[rgba(61,220,151,.3)] bg-[rgba(61,220,151,.08)] text-[#3ddc97] hover:bg-[rgba(61,220,151,.16)]"
              >
                <Heart className="size-6" strokeWidth={2.5} />
              </Button>
            </div>

            <p className="mt-4 text-center text-[13px] text-[#a6a39a]">
              1 de {pendingQueue.length}{" "}
              {pendingQueue.length === 1 ? "sugestao" : "sugestoes"}
            </p>

            {actionError && !detailItem && (
              <div className="mx-auto mt-4 flex max-w-[320px] flex-col items-center gap-2 rounded-2xl border border-[rgba(255,107,107,.35)] bg-[rgba(255,107,107,.08)] px-5 py-3 text-center">
                <p className="flex items-center gap-2 text-[13px] text-[#ffb3b3]">
                  <TriangleAlert className="size-4" />
                  {actionError}
                </p>
                <Button
                  type="button"
                  onClick={handleActionRetry}
                  disabled={actionLoading}
                  className="flex items-center gap-2 rounded-full border border-[rgba(255,107,107,.4)] bg-transparent px-4 py-1.5 text-[13px] font-semibold text-[#ffb3b3] hover:bg-[rgba(255,107,107,.12)]"
                >
                  <RefreshCw className="size-3.5" /> Tentar novamente
                </Button>
              </div>
            )}
          </div>
        </div>
      )}

      <PendingDetailModal
        item={detailItem}
        onClose={() => setDetailItem(null)}
        onLike={handleLike}
        onReject={handleReject}
        actionLoading={actionLoading}
        actionError={actionError}
        onRetryAction={handleActionRetry}
      />

      <ComparisonDialog
        open={compare.dialogOpen}
        onOpenChange={(open) => {
          if (!open) compare.exitCompareMode()
        }}
        left={compare.left}
        right={compare.right}
      />
      {compare.compareMode && (
        <CompareSelectionChip
          count={compare.selected.length}
          onClear={compare.clearSelection}
          error={compare.error}
          onRetry={compare.retry}
        />
      )}
    </>
  )
}
