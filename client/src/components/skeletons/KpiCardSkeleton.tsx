import { Skeleton } from "@/components/ui/skeleton"

export function KpiCardSkeleton() {
  return (
    <div className="rounded-[18px] border border-[rgba(255,255,255,.07)] bg-[#161513] p-5.5">
      <Skeleton className="h-3.5 w-2/3" />
      <Skeleton className="mt-3 h-9 w-1/2 bg-white/[0.08]" />
      <Skeleton className="mt-2.5 h-3 w-3/4" />
    </div>
  )
}
