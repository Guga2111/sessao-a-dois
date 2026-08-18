import { Skeleton } from "@/components/ui/skeleton"

export function KpiCardSkeleton() {
  return (
    <div className="rounded-2xl border border-white/7 bg-card p-5.5">
      <Skeleton className="h-3.5 w-2/3" />
      <Skeleton className="mt-3 h-9 w-1/2 bg-white/[0.08]" />
      <Skeleton className="mt-2.5 h-3 w-3/4" />
    </div>
  )
}
