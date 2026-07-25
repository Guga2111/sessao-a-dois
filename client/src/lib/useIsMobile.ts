import { useEffect, useState } from "react"

const MOBILE_QUERY = "(max-width: 767px)"

function getIsMobile() {
  if (typeof window === "undefined") return false
  return window.matchMedia(MOBILE_QUERY).matches
}

export function useIsMobile(): boolean {
  const [isMobile, setIsMobile] = useState(getIsMobile)

  useEffect(() => {
    const mql = window.matchMedia(MOBILE_QUERY)
    const onChange = () => setIsMobile(mql.matches)
    mql.addEventListener("change", onChange)
    return () => mql.removeEventListener("change", onChange)
  }, [])

  return isMobile
}
