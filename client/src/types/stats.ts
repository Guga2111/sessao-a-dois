export interface GenreStatDto {
  name: string
  count: number
  percentage: number
}

export interface MonthlyStatDto {
  month: number
  count: number
}

export interface StatsResponse {
  totalWatchedHours: number
  totalWatchedMinutes: number
  currentMonthWatchedHours: number
  movieCount: number
  tvCount: number
  moviePercentage: number
  tvPercentage: number
  totalTitles: number
  averageRating: number
  favoriteGenre: string | null
  topGenres: GenreStatDto[]
  monthlySeries: MonthlyStatDto[]
}
