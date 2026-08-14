import { Heart, Menu, Settings, X } from "lucide-react"
import { useState } from "react"
import { NavLink, useLocation } from "react-router-dom"

import { CoupleAvatars } from "@/components/CoupleAvatars"
import { NotificationDropdown } from "@/components/NotificationDropdown"
import { daysSince } from "@/lib/date"
import { cn } from "@/lib/utils"
import { useAuthStore } from "@/stores/useAuthStore"

const NAV_ITEMS = [
  { to: "/hub", label: "Hub Principal" },
  { to: "/match", label: "Match", icon: Heart },
  { to: "/dashboard", label: "Dashboard" },
]

export function Header() {
  const user = useAuthStore((s) => s.user)
  const couple = useAuthStore((s) => s.couple)
  const location = useLocation()

  const firstName = user?.name.split(" ")[0] ?? ""
  const partnerFirstName = couple?.partner?.name.split(" ")[0] ?? ""
  const days = couple?.createdAt ? daysSince(couple.createdAt) : 0

  return (
    <header className="font-auth-body sticky top-0 z-40 flex items-center justify-between gap-6 border-b border-white/[0.07] bg-background/72 px-5 py-4 backdrop-blur-xl sm:px-8">
      <div className="flex min-w-0 flex-1 items-center gap-3">
        <div className="grid size-9.5 flex-none place-items-center rounded-xl bg-primary shadow-[0_6px_20px_rgba(255,203,43,.35)]">
          <Heart className="size-[18px] fill-current text-[#111]" />
        </div>
        <div className="font-display truncate text-[19px] font-bold tracking-tight text-foreground">
          Sessão<span className="text-[#ff9e2c]">·</span>a·Dois
        </div>
      </div>

      <nav className="hidden items-center gap-1.5 rounded-[14px] border border-white/[0.06] bg-white/[0.05] p-1.5 md:flex">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === "/hub"}
            className={({ isActive }) =>
              cn(
                "inline-flex items-center gap-1.5 rounded-[10px] px-4 py-2 text-sm font-semibold transition-colors",
                isActive
                  ? "bg-primary text-[#111] shadow-[0_4px_14px_rgba(255,203,43,.4)]"
                  : "text-muted-foreground hover:text-foreground"
              )
            }
          >
            {item.label}
            {"icon" in item && item.icon && <item.icon className="size-3.5 fill-current" />}
          </NavLink>
        ))}
      </nav>

      <div className="flex min-w-0 flex-1 items-center justify-end gap-3">
        {couple?.partner && (
          <>
            <div className="hidden text-right leading-tight sm:block">
              <div className="text-[13px] font-semibold text-foreground">
                {firstName} & {partnerFirstName}
              </div>
              <div className="text-[11px] text-muted-foreground">
                {days} {days === 1 ? "dia" : "dias"} juntos no app
              </div>
            </div>
            <NotificationDropdown />
            <CoupleAvatars
              userInitial={firstName.charAt(0)}
              partnerInitial={partnerFirstName.charAt(0)}
            />
          </>
        )}

        <NavLink
          to="/conta"
          title="Conta"
          aria-label="Conta"
          className={({ isActive }) =>
            cn(
              "hidden size-9.5 flex-none place-items-center rounded-[10px] border transition-colors md:grid",
              isActive
                ? "border-transparent bg-primary text-[#111] shadow-[0_4px_14px_rgba(255,203,43,.4)]"
                : "border-white/[0.06] bg-white/[0.05] text-muted-foreground hover:bg-white/[0.09] hover:text-foreground"
            )
          }
        >
          <Settings size={17} />
        </NavLink>

        <MobileNav key={location.pathname} />
      </div>
    </header>
  )
}

function MobileNav() {
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <div className="relative md:hidden">
      <button
        type="button"
        onClick={() => setMenuOpen((open) => !open)}
        aria-label={menuOpen ? "Fechar menu" : "Abrir menu"}
        aria-expanded={menuOpen}
        className="grid size-9.5 flex-none place-items-center rounded-[10px] border border-white/[0.06] bg-white/[0.05] text-foreground transition-colors hover:bg-white/[0.09]"
      >
        {menuOpen ? <X size={19} /> : <Menu size={19} />}
      </button>

      {menuOpen && (
        <nav className="absolute top-[calc(100%+10px)] right-0 z-50 flex w-48 flex-col gap-1 rounded-[14px] border border-white/[0.08] bg-[#141312] p-1.5 shadow-[0_16px_40px_rgba(0,0,0,.5)]">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/hub"}
              onClick={() => setMenuOpen(false)}
              className={({ isActive }) =>
                cn(
                  "inline-flex items-center gap-1.5 rounded-[10px] px-4 py-2.5 text-sm font-semibold transition-colors",
                  isActive
                    ? "bg-primary text-[#111] shadow-[0_4px_14px_rgba(255,203,43,.4)]"
                    : "text-muted-foreground hover:text-foreground"
                )
              }
            >
              {item.label}
              {"icon" in item && item.icon && <item.icon className="size-3.5 fill-current" />}
            </NavLink>
          ))}

          <div className="my-1 h-px bg-white/[0.08]" />

          <NavLink
            to="/conta"
            onClick={() => setMenuOpen(false)}
            className={({ isActive }) =>
              cn(
                "inline-flex items-center gap-2 rounded-[10px] px-4 py-2.5 text-sm font-semibold transition-colors",
                isActive
                  ? "bg-primary text-[#111] shadow-[0_4px_14px_rgba(255,203,43,.4)]"
                  : "text-muted-foreground hover:text-foreground"
              )
            }
          >
            <Settings className="size-3.5" />
            Conta
          </NavLink>
        </nav>
      )}
    </div>
  )
}
