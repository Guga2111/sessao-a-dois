import { useEffect, useState } from "react"

import { Header } from "@/components/Header"
import { api } from "@/lib/api"
import type { MonthlyStatDto, StatsResponse } from "@/types/stats"

const MONTH_LABELS = [
  "Jan",
  "Fev",
  "Mar",
  "Abr",
  "Mai",
  "Jun",
  "Jul",
  "Ago",
  "Set",
  "Out",
  "Nov",
  "Dez",
]

function ChartCard({
  title,
  className,
  children,
}: {
  title: string
  className?: string
  children: React.ReactNode
}) {
  return (
    <div
      className={`rounded-[18px] border border-[rgba(255,255,255,.07)] bg-[#161513] p-6 ${className ?? ""}`}
    >
      <h3 className="font-display m-0 text-[17px]">{title}</h3>
      {children}
    </div>
  )
}

function MonthlyBarsChart({ monthlySeries }: { monthlySeries: MonthlyStatDto[] }) {
  const maxCount = Math.max(...monthlySeries.map((m) => m.count), 1)
  const hasData = monthlySeries.some((m) => m.count > 0)

  return (
    <ChartCard title="Títulos por mês" className="min-w-0">
      <div className="mb-5.5 mt-0 flex items-center justify-between">
        <span className="sr-only">Títulos por mês</span>
        <span className="ml-auto text-[12px] text-[#a6a39a]">
          {new Date().getFullYear()}
        </span>
      </div>
      {hasData ? (
        <div className="flex h-[200px] items-end gap-2 pt-2.5 sm:gap-3 md:gap-5">
          {monthlySeries.map((m) => (
            <div
              key={m.month}
              className="flex h-full flex-1 flex-col items-center justify-end gap-2.5"
            >
              <span className="text-[12px] text-[#a6a39a]">{m.count}</span>
              <div
                className="w-full rounded-t-[8px] rounded-b-[3px] bg-[#ffcb2b]"
                style={{ height: `${Math.max((m.count / maxCount) * 100, 2)}%` }}
              />
              <span className="text-[12px] text-[#a6a39a]">
                {MONTH_LABELS[m.month - 1]}
              </span>
            </div>
          ))}
        </div>
      ) : (
        <div className="flex h-[200px] items-center justify-center text-sm text-[#a6a39a]">
          Nenhum título assistido este ano ainda.
        </div>
      )}
    </ChartCard>
  )
}

function MovieTvDonutChart({
  movieCount,
  tvCount,
  moviePercentage,
  tvPercentage,
  totalTitles,
}: {
  movieCount: number
  tvCount: number
  moviePercentage: number
  tvPercentage: number
  totalTitles: number
}) {
  return (
    <ChartCard title="Filmes vs Séries" className="flex flex-col">
      <div className="mt-4.5 flex flex-1 items-center gap-5">
        <div
          className="relative h-[130px] w-[130px] flex-none rounded-full"
          style={{
            background: `conic-gradient(#ffcb2b 0 ${moviePercentage}%, #ff9e2c ${moviePercentage}% 100%)`,
          }}
        >
          <div className="absolute inset-4 grid place-items-center rounded-full bg-[#161513] text-center">
            <div>
              <div className="font-display text-[22px] font-bold">
                {totalTitles}
              </div>
              <div className="text-[10px] text-[#a6a39a]">títulos</div>
            </div>
          </div>
        </div>
        <div className="flex flex-col gap-3.5">
          <div>
            <div className="flex items-center gap-2">
              <span className="h-[11px] w-[11px] rounded-[3px] bg-[#ffcb2b]" />
              <span className="text-[13px] font-semibold">Filmes</span>
            </div>
            <div className="ml-[19px] text-[12px] text-[#a6a39a]">
              {movieCount} · {Math.round(moviePercentage)}%
            </div>
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="h-[11px] w-[11px] rounded-[3px] bg-[#ff9e2c]" />
              <span className="text-[13px] font-semibold">Séries</span>
            </div>
            <div className="ml-[19px] text-[12px] text-[#a6a39a]">
              {tvCount} · {Math.round(tvPercentage)}%
            </div>
          </div>
        </div>
      </div>
    </ChartCard>
  )
}

function GenreBarsChart({
  topGenres,
}: {
  topGenres: StatsResponse["topGenres"]
}) {
  return (
    <ChartCard title="Gêneros mais assistidos">
      {topGenres.length > 0 ? (
        <div className="mt-5 flex flex-col gap-4">
          {topGenres.slice(0, 5).map((genre) => (
            <div key={genre.name}>
              <div className="mb-1.5 flex justify-between text-[13px]">
                <span className="font-semibold">{genre.name}</span>
                <span className="text-[#a6a39a]">
                  {Math.round(genre.percentage)}%
                </span>
              </div>
              <div className="h-[9px] rounded-[20px] bg-white/[0.06]">
                <div
                  className="h-full rounded-[20px] bg-[#ffcb2b]"
                  style={{ width: `${genre.percentage}%` }}
                />
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="mt-5 text-sm text-[#a6a39a]">
          Sem dados de gênero suficientes ainda.
        </div>
      )}
    </ChartCard>
  )
}

function KpiCard({
  icon,
  iconColor,
  label,
  children,
}: {
  icon: string
  iconColor: string
  label: string
  children: React.ReactNode
}) {
  return (
    <div className="rounded-[18px] border border-[rgba(255,255,255,.07)] bg-[#161513] p-5.5">
      <div className="flex items-center gap-2 text-[13px] font-semibold text-[#a6a39a]">
        <span style={{ color: iconColor }}>{icon}</span> {label}
      </div>
      {children}
    </div>
  )
}

function KpiSkeleton() {
  return (
    <div className="rounded-[18px] border border-[rgba(255,255,255,.07)] bg-[#161513] p-5.5">
      <div className="h-3.5 w-2/3 animate-pulse rounded bg-white/[0.06]" />
      <div className="mt-3 h-9 w-1/2 animate-pulse rounded bg-white/[0.08]" />
      <div className="mt-2.5 h-3 w-3/4 animate-pulse rounded bg-white/[0.06]" />
    </div>
  )
}

export function DashboardScreen() {
  const [stats, setStats] = useState<StatsResponse | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api
      .get<StatsResponse>("/api/tracking/stats")
      .then((response) => {
        setStats(response.data)
      })
      .finally(() => {
        setLoading(false)
      })
  }, [])

  const isEmpty = !loading && (stats == null || stats.totalTitles === 0)
  const secondFavoriteGenre = stats?.topGenres[1]?.name

  return (
    <div
      className="font-auth-body min-h-svh text-[#f6f4ec]"
      style={{
        background:
          "radial-gradient(1200px 700px at 78% -8%, rgba(255,203,43,.16), transparent 55%), radial-gradient(1000px 600px at 5% 8%, rgba(255,158,44,.10), transparent 50%), #09090a",
      }}
    >
      <Header />
      <main className="mx-auto max-w-[1240px] px-5 pt-10 pb-32 sm:px-8 sm:pt-11">
        <div className="mb-7">
          <div className="mb-2 text-[13px] font-semibold tracking-[.14em] text-[#ff9e2c] uppercase">
            Estatísticas do Casal
          </div>
          <h1 className="font-display text-[clamp(28px,4vw,40px)] font-bold tracking-tight">
            O ano de vocês em telas
          </h1>
          <p className="mt-2 text-[15px] text-[#a6a39a]">
            Um retrato de tudo que vocês assistiram juntos.
          </p>
        </div>

        {isEmpty ? (
          <div className="rounded-2xl border border-dashed border-white/10 px-5 py-7 text-sm text-[#a6a39a]">
            Ainda não há títulos assistidos para gerar estatísticas. Marquem
            algo como visto para ver o painel ganhar vida.
          </div>
        ) : (
          <div className="mb-5.5 grid grid-cols-1 gap-4.5 md:grid-cols-2 lg:grid-cols-4">
            {loading || !stats ? (
              <>
                <KpiSkeleton />
                <KpiSkeleton />
                <KpiSkeleton />
                <KpiSkeleton />
              </>
            ) : (
              <>
                <KpiCard icon="◷" iconColor="#ffcb2b" label="Tempo juntos">
                  <div className="font-display mt-3 text-[36px] font-bold tracking-tight">
                    {stats.totalWatchedHours}h{" "}
                    <span className="text-[20px] text-[#a6a39a]">
                      {stats.totalWatchedMinutes}m
                    </span>
                  </div>
                  <div className="mt-1.5 text-[12.5px] text-[#3ddc97]">
                    {stats.currentMonthWatchedHours > 0
                      ? `↑ ${stats.currentMonthWatchedHours}h neste mês`
                      : "Nada assistido neste mês ainda"}
                  </div>
                </KpiCard>

                <KpiCard icon="◲" iconColor="#ff9e2c" label="Filmes vs Séries">
                  <div className="font-display mt-3 text-[36px] font-bold tracking-tight">
                    {Math.round(stats.moviePercentage)}
                    <span className="text-[18px] text-[#a6a39a]"> % filmes</span>
                  </div>
                  <div className="mt-3 flex h-2 overflow-hidden rounded-[20px] bg-white/[0.06]">
                    <div
                      className="bg-[#ffcb2b]"
                      style={{ width: `${stats.moviePercentage}%` }}
                    />
                    <div
                      className="bg-[#ff9e2c]"
                      style={{ width: `${stats.tvPercentage}%` }}
                    />
                  </div>
                  <div className="mt-1.5 flex justify-between text-[11.5px] text-[#a6a39a]">
                    <span>{stats.movieCount} filmes</span>
                    <span>{stats.tvCount} séries</span>
                  </div>
                </KpiCard>

                <KpiCard icon="♥" iconColor="#ffb443" label="Gênero favorito">
                  <div className="font-display mt-3 text-[36px] font-bold tracking-tight">
                    {stats.favoriteGenre ?? "—"}
                  </div>
                  <div className="mt-1.5 text-[12.5px] text-[#a6a39a]">
                    {stats.topGenres[0]
                      ? `${stats.topGenres[0].count} títulos${secondFavoriteGenre ? ` · seguido de ${secondFavoriteGenre}` : ""}`
                      : "Sem dados suficientes ainda"}
                  </div>
                </KpiCard>

                <KpiCard icon="✓" iconColor="#3ddc97" label="Total assistido">
                  <div className="font-display mt-3 text-[36px] font-bold tracking-tight">
                    {stats.totalTitles}{" "}
                    <span className="text-[18px] text-[#a6a39a]">títulos</span>
                  </div>
                  <div className="mt-1.5 text-[12.5px] text-[#a6a39a]">
                    Nota média do casal: {stats.averageRating.toFixed(1).replace(".", ",")} ★
                  </div>
                </KpiCard>
              </>
            )}
          </div>
        )}

        {!isEmpty && !loading && stats && (
          <>
            <div className="mb-4.5 grid grid-cols-1 gap-4.5 lg:grid-cols-[1.6fr_1fr]">
              <MonthlyBarsChart monthlySeries={stats.monthlySeries} />
              <MovieTvDonutChart
                movieCount={stats.movieCount}
                tvCount={stats.tvCount}
                moviePercentage={stats.moviePercentage}
                tvPercentage={stats.tvPercentage}
                totalTitles={stats.totalTitles}
              />
            </div>

            <GenreBarsChart topGenres={stats.topGenres} />
          </>
        )}
      </main>
    </div>
  )
}
