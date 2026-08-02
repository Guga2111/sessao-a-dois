import { Skeleton } from "@/components/ui/skeleton"

export function AppShellSkeleton() {
  return (
    <div
      className="font-auth-body min-h-svh text-[#f6f4ec]"
      style={{
        background:
          "radial-gradient(1200px 700px at 78% -8%, rgba(255,203,43,.16), transparent 55%), radial-gradient(1000px 600px at 5% 8%, rgba(255,158,44,.10), transparent 50%), #09090a",
      }}
    >
      <header className="sticky top-0 z-40 flex items-center justify-between gap-6 border-b border-white/[0.07] bg-[#09090a]/72 px-5 py-4 backdrop-blur-xl sm:px-8">
        <div className="flex min-w-0 flex-1 items-center gap-3">
          <Skeleton className="size-9.5 flex-none rounded-xl" />
          <Skeleton className="h-5 w-36" />
        </div>
        <div className="hidden items-center gap-1.5 rounded-[14px] border border-white/[0.06] bg-white/[0.05] p-1.5 md:flex">
          <Skeleton className="h-9 w-28 rounded-[10px]" />
          <Skeleton className="h-9 w-20 rounded-[10px]" />
          <Skeleton className="h-9 w-24 rounded-[10px]" />
        </div>
        <div className="flex flex-1 items-center justify-end gap-3">
          <Skeleton className="size-9 rounded-full" />
        </div>
      </header>
      <main className="mx-auto max-w-[1240px] px-4 pt-10 pb-32 sm:px-8 sm:pt-11">
        <div className="mb-7 flex flex-col gap-2">
          <Skeleton className="h-3 w-24" />
          <Skeleton className="h-9 w-64" />
        </div>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-24 rounded-[18px]" />
          ))}
        </div>
      </main>
    </div>
  )
}
