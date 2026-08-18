import { isAxiosError } from "axios"
import { useState } from "react"
import type { FormEvent } from "react"
import { Link, useLocation, useNavigate } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useAuthStore } from "@/stores/useAuthStore"

import { AuthLayout } from "./AuthLayout"

/** Estado que outra tela pode mandar junto no `navigate("/login", { state })`. */
type LoginNavigationState = { notice?: string } | null

export function LoginPage() {
  const login = useAuthStore((state) => state.login)
  const navigate = useNavigate()
  // Quem derruba a sessao de proposito (troca de senha, US-010) explica aqui por que.
  const notice = (useLocation().state as LoginNavigationState)?.notice ?? null

  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      await login({ email, password })
      navigate("/hub", { replace: true })
    } catch (submitError) {
      if (isAxiosError(submitError) && submitError.response?.status === 401) {
        setError("E-mail ou senha incorretos.")
      } else {
        setError("Não foi possível entrar agora. Tente novamente.")
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthLayout
      eyebrow="Bem-vindo de volta"
      title="Entrar na sessão"
      subtitle="Use seu e-mail e senha para continuar de onde vocês pararam."
      footer={
        <>
          Ainda não tem conta?{" "}
          <Link to="/register" className="font-medium text-primary">
            Criar conta
          </Link>
        </>
      }
    >
      {notice ? (
        <p
          role="status"
          className="mb-4 rounded-xl border border-primary/25 bg-primary/[0.08] px-3.5 py-2.5 text-sm text-foreground"
        >
          {notice}
        </p>
      ) : null}

      <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
        <label className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-muted-foreground">E-mail</span>
          <Input
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="voce@exemplo.com"
          />
        </label>

        <label className="flex flex-col gap-1.5">
          <div className="flex items-baseline justify-between gap-2">
            <span className="text-xs font-medium text-muted-foreground">Senha</span>
            <Link
              to="/esqueci-senha"
              className="text-xs font-medium text-primary hover:text-accent"
            >
              Esqueci minha senha
            </Link>
          </div>
          <Input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="••••••••"
          />
        </label>

        {error ? (
          <p
            role="alert"
            className="rounded-xl border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive-soft"
          >
            {error}
          </p>
        ) : null}

        <Button
          type="submit"
          size="lg"
          disabled={isSubmitting}
          className="mt-1 w-full bg-primary text-background hover:bg-accent disabled:opacity-60"
        >
          {isSubmitting ? "Entrando…" : "Entrar"}
        </Button>
      </form>
    </AuthLayout>
  )
}
