import { isAxiosError } from "axios"
import { useState } from "react"
import type { FormEvent } from "react"
import { useNavigate } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useAuthStore } from "@/stores/useAuthStore"

import { AuthLayout } from "./AuthLayout"
import { InviteCodeTicket } from "./InviteCodeTicket"

type Mode = "join" | "create"

export function JoinPage() {
  const couple = useAuthStore((state) => state.couple)
  const joinCouple = useAuthStore((state) => state.joinCouple)
  const createCouple = useAuthStore((state) => state.createCouple)
  const loadCurrentUser = useAuthStore((state) => state.loadCurrentUser)
  const navigate = useNavigate()

  const [mode, setMode] = useState<Mode>("join")
  const [inviteCode, setInviteCode] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isChecking, setIsChecking] = useState(false)

  async function handleJoin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      const joined = await joinCouple(inviteCode.trim().toUpperCase())
      if (joined.partner) {
        navigate("/", { replace: true })
      }
    } catch (submitError) {
      if (!isAxiosError(submitError)) {
        setError("Não foi possível vincular agora. Tente novamente.")
      } else {
        switch (submitError.response?.status) {
          case 404:
            setError("Não encontramos esse código. Confira e tente de novo.")
            break
          case 400:
            setError("Você não pode entrar no seu próprio código.")
            break
          case 409:
            setError("Esse código já foi usado ou você já está em um casal.")
            break
          default:
            setError("Não foi possível vincular agora. Tente novamente.")
        }
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleCreate() {
    setError(null)
    setIsSubmitting(true)
    try {
      await createCouple()
    } catch (submitError) {
      if (isAxiosError(submitError) && submitError.response?.status === 409) {
        setError("Você já faz parte de um casal.")
      } else {
        setError("Não foi possível gerar seu código agora. Tente novamente.")
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleCheckLinked() {
    setIsChecking(true)
    try {
      await loadCurrentUser()
    } finally {
      setIsChecking(false)
    }
  }

  if (couple && !couple.partner) {
    return (
      <AuthLayout
        eyebrow="Quase lá"
        title="Compartilhe seu código"
        subtitle="Envie este ingresso para a pessoa com quem você quer dividir a sessão. Assim que ela entrar com o código, vocês formam o casal."
      >
        <div className="flex flex-col gap-5">
          <InviteCodeTicket code={couple.inviteCode} />
          <Button
            type="button"
            variant="outline"
            size="lg"
            onClick={handleCheckLinked}
            disabled={isChecking}
            className="w-full border-white/10 bg-white/[0.04] text-[#f6f4ec] hover:bg-white/[0.08]"
          >
            {isChecking ? "Verificando…" : "Já vinculamos, continuar"}
          </Button>
        </div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout
      eyebrow="Uma sessão, duas pessoas"
      title="Vincule seu par"
      subtitle="Entre com o código que sua pessoa te mandou, ou gere o seu para compartilhar."
    >
      <div className="mb-6 flex gap-1 rounded-2xl border border-white/[0.06] bg-white/[0.05] p-1">
        <button
          type="button"
          onClick={() => setMode("join")}
          className={`flex-1 rounded-xl px-3 py-2 text-sm font-medium transition-colors ${
            mode === "join"
              ? "bg-[#ffcb2b] text-[#09090a]"
              : "text-[#a6a39a] hover:text-[#f6f4ec]"
          }`}
        >
          Tenho um código
        </button>
        <button
          type="button"
          onClick={() => setMode("create")}
          className={`flex-1 rounded-xl px-3 py-2 text-sm font-medium transition-colors ${
            mode === "create"
              ? "bg-[#ffcb2b] text-[#09090a]"
              : "text-[#a6a39a] hover:text-[#f6f4ec]"
          }`}
        >
          Criar meu código
        </button>
      </div>

      {mode === "join" ? (
        <form className="flex flex-col gap-4" onSubmit={handleJoin} noValidate>
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-[#a6a39a]">
              Código de convite
            </span>
            <Input
              type="text"
              required
              value={inviteCode}
              onChange={(event) => setInviteCode(event.target.value)}
              placeholder="ex.: 7XK9QP"
              className="font-mono tracking-[0.2em] uppercase"
              maxLength={8}
            />
          </label>

          {error ? (
            <p
              role="alert"
              className="rounded-xl border border-[#ff6b6b]/30 bg-[#ff6b6b]/10 px-3 py-2 text-sm text-[#ff9b9b]"
            >
              {error}
            </p>
          ) : null}

          <Button
            type="submit"
            size="lg"
            disabled={isSubmitting || !inviteCode.trim()}
            className="mt-1 w-full bg-[#ffcb2b] text-[#09090a] hover:bg-[#ffe08a] disabled:opacity-60"
          >
            {isSubmitting ? "Vinculando…" : "Vincular"}
          </Button>
        </form>
      ) : (
        <div className="flex flex-col gap-4">
          <p className="text-sm leading-relaxed text-[#a6a39a]">
            Vamos gerar um código único para você compartilhar com sua pessoa.
          </p>

          {error ? (
            <p
              role="alert"
              className="rounded-xl border border-[#ff6b6b]/30 bg-[#ff6b6b]/10 px-3 py-2 text-sm text-[#ff9b9b]"
            >
              {error}
            </p>
          ) : null}

          <Button
            type="button"
            size="lg"
            onClick={handleCreate}
            disabled={isSubmitting}
            className="w-full bg-[#ffcb2b] text-[#09090a] hover:bg-[#ffe08a] disabled:opacity-60"
          >
            {isSubmitting ? "Gerando…" : "Gerar meu código"}
          </Button>
        </div>
      )}
    </AuthLayout>
  )
}
