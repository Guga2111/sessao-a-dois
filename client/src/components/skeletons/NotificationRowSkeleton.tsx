import { Skeleton } from "@/components/ui/skeleton"

export function NotificationRowSkeleton() {
  return (
    <div className="flex w-full items-start gap-3 rounded-[12px] px-3 py-3">
      <Skeleton className="size-9 flex-none rounded-full" />
      <div className="min-w-0 flex-1 space-y-1.5">
        <Skeleton className="h-3.5 w-3/4" />
        <Skeleton className="h-3 w-full" />
        <Skeleton className="h-2.5 w-16" />
      </div>
    </div>
  )
}
