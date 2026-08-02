import { Skeleton } from "@/components/ui/skeleton"

interface SearchResultSkeletonProps {
  rows?: number
}

export function SearchResultSkeleton({ rows = 3 }: SearchResultSkeletonProps) {
  return (
    <div className="py-1.5">
      {Array.from({ length: rows }).map((_, i) => (
        <div
          key={i}
          className="flex w-full items-center justify-between gap-3 px-3.5 py-2.5"
        >
          <Skeleton className="h-3.5 w-2/5" />
          <Skeleton className="h-3 w-16 flex-none" />
        </div>
      ))}
    </div>
  )
}
