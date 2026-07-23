import { differenceInDays, parseISO } from "date-fns"

export function daysSince(isoDate: string): number {
  return differenceInDays(new Date(), parseISO(isoDate))
}
