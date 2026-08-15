import { Skeleton } from "@/components/ui/skeleton"

export function MediaCardSkeleton() {
  return (
    <div className="min-w-[72vw] max-w-[72vw] shrink-0 overflow-hidden rounded-2xl border border-white/7 bg-card md:min-w-0 md:max-w-none md:shrink">
      <Skeleton className="aspect-[3/4] rounded-none" />
      <div className="space-y-2.5 p-3.5">
        <Skeleton className="h-3.5 w-3/4" />
        <Skeleton className="h-3 w-1/2" />
      </div>
    </div>
  )
}
