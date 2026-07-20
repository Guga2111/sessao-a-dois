import { useAuthStore } from "@/stores/useAuthStore"

export function HubPage() {
  const user = useAuthStore((state) => state.user)
  const couple = useAuthStore((state) => state.couple)
  const logout = useAuthStore((state) => state.logout)

  return (
    <div
      className="font-auth-body flex min-h-svh flex-col items-center justify-center gap-4 px-5 text-center text-[#f6f4ec]"
      style={{
        background:
          "radial-gradient(1200px 700px at 78% -8%, rgba(255,203,43,.16), transparent 55%), radial-gradient(1000px 600px at 5% 8%, rgba(255,158,44,.10), transparent 50%), #09090a",
      }}
    >
      <span
        className="grid size-12 place-items-center rounded-2xl bg-[#ffcb2b] text-2xl shadow-[0_6px_20px_rgba(255,203,43,.35)]"
        aria-hidden
      >
        ♥
      </span>
      <h1 className="font-display text-2xl font-bold">
        Olá, {user?.name ?? "vocês dois"}
      </h1>
      <p className="max-w-sm text-sm text-[#a6a39a]">
        Vinculados com {couple?.partner?.name ?? "seu par"}. O Hub principal
        chega em uma próxima história.
      </p>
      <button
        type="button"
        onClick={logout}
        className="mt-2 cursor-pointer rounded-full border border-white/10 bg-white/[0.04] px-4 py-2 text-sm font-medium hover:bg-white/[0.08]"
      >
        Sair
      </button>
    </div>
  )
}
