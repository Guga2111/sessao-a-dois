import { Skeleton } from "@/components/ui/skeleton"

export function DetailModalSkeleton() {
  return (
    <div className="flex flex-col gap-5">
      <Skeleton className="aspect-[2/3] w-28 flex-none rounded-[14px] sm:w-32" />
      <div className="flex gap-4">
        <div className="flex flex-col gap-1.5">
          <Skeleton className="h-3 w-10" />
          <Skeleton className="h-5 w-12" />
        </div>
        <div className="flex flex-col gap-1.5">
          <Skeleton className="h-3 w-16" />
          <Skeleton className="h-5 w-20" />
        </div>
        <div className="flex flex-col gap-1.5">
          <Skeleton className="h-3 w-14" />
          <Skeleton className="h-5 w-16" />
        </div>
      </div>
      <div>
        <Skeleton className="mb-2 h-3 w-16" />
        <div className="flex gap-2">
          <Skeleton className="h-6 w-16 rounded-full" />
          <Skeleton className="h-6 w-20 rounded-full" />
        </div>
      </div>
      <div>
        <Skeleton className="mb-2 h-3 w-24" />
        <div className="space-y-1.5">
          <Skeleton className="h-3.5 w-full" />
          <Skeleton className="h-3.5 w-5/6" />
          <Skeleton className="h-3.5 w-4/6" />
        </div>
      </div>
    </div>
  )
}
