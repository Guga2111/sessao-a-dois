import { useEffect, useState } from "react"

import { Header } from "@/components/Header"
import { api } from "@/lib/api"
import type { StatsResponse } from "@/types/stats"

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
          <div className="mb-5.5 grid grid-cols-[repeat(auto-fit,minmax(230px,1fr))] gap-4.5">
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
      </main>
    </div>
  )
}
