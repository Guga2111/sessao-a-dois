import { Skeleton } from "@/components/ui/skeleton"

const BAR_HEIGHTS = ["45%", "70%", "55%", "90%", "35%", "60%", "80%"]

function BarsBody() {
  return (
    <div className="mt-5.5 flex h-[200px] items-end gap-2 pt-2.5 sm:gap-3 md:gap-5">
      {BAR_HEIGHTS.map((height, i) => (
        <div key={i} className="flex h-full flex-1 flex-col items-end justify-end gap-2.5">
          {/* radius-ok: D20/US-043 opcao (c) — espelha o canto arredondado da barra real (DashboardScreen.tsx), detalhe decorativo de skeleton */}
          <Skeleton className="w-full rounded-t-md rounded-b-[3px]" style={{ height }} />
        </div>
      ))}
    </div>
  )
}

function DonutBody() {
  return (
    <div className="mt-4.5 flex flex-1 items-center gap-5">
      <Skeleton className="h-[130px] w-[130px] flex-none rounded-full" />
      <div className="flex flex-col gap-3.5">
        <div className="flex flex-col gap-1.5">
          <Skeleton className="h-3.5 w-16" />
          <Skeleton className="h-3 w-20" />
        </div>
        <div className="flex flex-col gap-1.5">
          <Skeleton className="h-3.5 w-16" />
          <Skeleton className="h-3 w-20" />
        </div>
      </div>
    </div>
  )
}

function GenreBarsBody() {
  return (
    <div className="mt-5 flex flex-col gap-4">
      {[0, 1, 2, 3, 4].map((i) => (
        <div key={i}>
          <div className="mb-1.5 flex justify-between">
            <Skeleton className="h-3 w-24" />
            <Skeleton className="h-3 w-8" />
          </div>
          <Skeleton className="h-[9px] w-full rounded-2xl" />
        </div>
      ))}
    </div>
  )
}

interface ChartSkeletonProps {
  variant?: "bars" | "donut" | "genre-bars"
  className?: string
}

export function ChartSkeleton({ variant = "bars", className }: ChartSkeletonProps) {
  return (
    <div
      className={`rounded-2xl border border-white/7 bg-card p-6 ${className ?? ""}`}
    >
      <Skeleton className="h-[17px] w-40" />
      {variant === "bars" && <BarsBody />}
      {variant === "donut" && <DonutBody />}
      {variant === "genre-bars" && <GenreBarsBody />}
    </div>
  )
}
