import { NavLink } from "react-router-dom"

import { cn } from "@/lib/utils"

const NAV_ITEMS = [
  { to: "/", label: "Hub Principal" },
  { to: "/match", label: "Match ♥" },
]

export function Header() {
  return (
    <header className="font-auth-body sticky top-0 z-40 flex items-center justify-between gap-6 border-b border-white/[0.07] bg-[#09090a]/72 px-5 py-4 backdrop-blur-xl sm:px-8">
      <div className="flex min-w-0 flex-1 items-center gap-3">
        <div className="grid size-9.5 flex-none place-items-center rounded-xl bg-[#ffcb2b] text-lg shadow-[0_6px_20px_rgba(255,203,43,.35)]">
          ♥
        </div>
        <div className="font-display truncate text-[19px] font-bold tracking-tight text-[#f6f4ec]">
          Sessão<span className="text-[#ff9e2c]">·</span>a·Dois
        </div>
      </div>

      <nav className="flex items-center gap-1.5 rounded-[14px] border border-white/[0.06] bg-white/[0.05] p-1.5">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === "/"}
            className={({ isActive }) =>
              cn(
                "rounded-[10px] px-4 py-2 text-sm font-semibold transition-colors",
                isActive
                  ? "bg-[#ffcb2b] text-[#111] shadow-[0_4px_14px_rgba(255,203,43,.4)]"
                  : "text-[#a6a39a] hover:text-[#f6f4ec]"
              )
            }
          >
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="flex flex-1 items-center justify-end" />
    </header>
  )
}
