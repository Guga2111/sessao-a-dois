import { isAxiosError } from "axios"
import { useState } from "react"
import type { FormEvent } from "react"
import { Link, useNavigate } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useAuthStore } from "@/stores/useAuthStore"

import { AuthLayout } from "./AuthLayout"

const MIN_PASSWORD_LENGTH = 8

export function RegisterPage() {
  const register = useAuthStore((state) => state.register)
  const navigate = useNavigate()

  const [name, setName] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const passwordTooShort =
    password.length > 0 && password.length < MIN_PASSWORD_LENGTH

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(`A senha precisa ter pelo menos ${MIN_PASSWORD_LENGTH} caracteres.`)
      return
    }

    setIsSubmitting(true)
    try {
      await register({ name, email, password })
      navigate("/join", { replace: true })
    } catch (submitError) {
      if (isAxiosError(submitError) && submitError.response?.status === 409) {
        setError("Esse e-mail já está cadastrado.")
      } else if (isAxiosError(submitError) && submitError.response?.status === 400) {
        setError("Confira os dados informados e tente novamente.")
      } else {
        setError("Não foi possível criar sua conta agora. Tente novamente.")
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthLayout
      eyebrow="Comecem juntos"
      title="Criar sua conta"
      subtitle="Depois de cadastrar, você já entra direto — sem digitar a senha de novo."
      footer={
        <>
          Já tem conta?{" "}
          <Link to="/login" className="font-medium text-primary">
            Entrar
          </Link>
        </>
      }
    >
      <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
        <label className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-muted-foreground">Nome</span>
          <Input
            type="text"
            autoComplete="name"
            required
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Seu nome"
          />
        </label>

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
          <span className="text-xs font-medium text-muted-foreground">Senha</span>
          <Input
            type="password"
            autoComplete="new-password"
            required
            aria-invalid={passwordTooShort}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="Mínimo de 8 caracteres"
          />
          {passwordTooShort ? (
            <span className="text-xs text-[#ff9b9b]">
              Faltam {MIN_PASSWORD_LENGTH - password.length} caracteres.
            </span>
          ) : null}
        </label>

        {error ? (
          <p
            role="alert"
            className="rounded-xl border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-[#ff9b9b]"
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
          {isSubmitting ? "Criando conta…" : "Criar conta"}
        </Button>
      </form>
    </AuthLayout>
  )
}
