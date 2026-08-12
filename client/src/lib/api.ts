import axios, { isAxiosError, type InternalAxiosRequestConfig } from "axios"

// Uma API que aceita a conexao TCP e nunca responde (ex.: container em loop de
// restart, com a porta ainda publicada pelo docker-proxy) deixaria a promise
// pendente para sempre - e o bootstrap do app, que so tira o AppShellSkeleton da
// tela quando o `GET /api/auth/me` assenta, ficaria congelado no skeleton em
// todas as rotas. Com timeout a chamada vira erro, o estado de loading fecha e a
// tela publica renderiza.
const REQUEST_TIMEOUT_MS = 15_000

const AXIOS_CONFIG = {
  // Sem VITE_API_URL (todo build do CD — ver vite-env.d.ts), `baseURL` vira string
  // vazia e o axios monta URL relativa: same-origin, que e exatamente o que producao
  // precisa. Deixar `undefined` aqui nao quebraria o axios, mas o `?? ""` mantem o
  // contrato explicito e igual ao do WebSocket em useMatchStore.
  baseURL: import.meta.env.VITE_API_URL ?? "",
  timeout: REQUEST_TIMEOUT_MS,
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
  // Obrigatorio, nao redundante: desde a 1.6.2 o axios so anexa o header de XSRF
  // sozinho quando a chamada e same-origin. Em producao a SPA e a API vivem no
  // mesmo dominio e isso acontecia de graca; em desenvolvimento a pagina esta em
  // localhost:5173 e a API em localhost:8080 — origens diferentes, header omitido,
  // e TODA requisicao mutante (POST/PUT/PATCH/DELETE) era barrada pelo CsrfFilter.
  // Pior: o CsrfFilter roda antes do JwtAuthenticationFilter, entao a recusa sai
  // como 401 (o entry point de SecurityConfig), nao 403 — o interceptor abaixo lia
  // isso como "sessao expirada" e mandava o usuario para /login em vez de mostrar
  // o erro. Nao remover achando que os dois `xsrf*` acima ja bastam.
  withXSRFToken: true,
} as const

export const api = axios.create(AXIOS_CONFIG)

// Ultimo X-Request-Id visto numa resposta (sucesso ou erro), em escopo de modulo.
// reportClientError (US-005) manda esse valor de volta ao backend para que o
// relatorio de erro do cliente carregue o mesmo correlation id da chamada que
// falhou, sem o boundary da US-006 precisar saber nada de HTTP.
let lastRequestId: string | undefined

function captureRequestId(headers: unknown): void {
  if (!headers || typeof headers !== "object") {
    return
  }
  const value = (headers as Record<string, unknown>)["x-request-id"]
  if (typeof value === "string" && value) {
    lastRequestId = value
  }
}

const REFRESH_URL = "/api/auth/refresh"
const CLIENT_ERROR_URL = "/api/client-errors"
const CLIENT_ERROR_STACK_MAX_LENGTH = 4000

// Separate instance (no response interceptor) so a failed refresh call
// never recurses back into the 401 handler below.
const refreshClient = axios.create(AXIOS_CONFIG)

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

// Module-scoped so concurrent 401s share one in-flight refresh instead of
// each firing its own POST /api/auth/refresh.
let refreshPromise: Promise<unknown> | null = null

function refreshSession(): Promise<unknown> {
  if (!refreshPromise) {
    refreshPromise = refreshClient.post(REFRESH_URL).finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

/**
 * Chave de `sessionStorage` com o motivo do ultimo redirect forcado para /login.
 * Existe porque `redirectToLogin` faz uma navegacao dura: o console e o Network
 * do DevTools sao limpos junto, e a falha que causou o redirect desaparece antes
 * de dar para ler (a menos que "Preserve log" esteja ligado, que ninguem lembra
 * de ligar ANTES de ver o problema). O breadcrumb sobrevive a navegacao e pode
 * ser lido depois com `sessionStorage.getItem("sessaoADois.lastAuthRedirect")`.
 */
const AUTH_REDIRECT_BREADCRUMB_KEY = "sessaoADois.lastAuthRedirect"

function redirectToLogin(config: RetryableRequestConfig, reason: string): void {
  const method = config.method?.toUpperCase() ?? "?"
  const url = config.url ?? "?"

  // Nao engolir o motivo (anti-pattern #4): sem isto, uma sessao caindo no meio
  // de uma acao do usuario vira "a pagina recarregou sozinha" e nada mais.
  console.error(`[api] sessao encerrada em ${method} ${url}: ${reason}`)
  try {
    sessionStorage.setItem(
      AUTH_REDIRECT_BREADCRUMB_KEY,
      JSON.stringify({ method, url, reason, at: new Date().toISOString() }),
    )
  } catch (storageError) {
    console.error("[api] nao foi possivel gravar o breadcrumb do redirect", storageError)
  }

  // `href` recarrega a pagina mesmo quando o destino e a URL atual. Sem esta guarda,
  // um 401 disparado a partir da propria /login vira um loop de reload.
  if (window.location.pathname === "/login") {
    return
  }
  window.location.href = "/login"
}

// Sondagem de sessao do bootstrap: aqui 401 significa "nao ha sessao", nao "a sessao
// expirou no meio do uso". Quem chamou (`useAuthStore.loadCurrentUser`) ja trata esse
// caso zerando o estado local, e a landing e a /login sao publicas — mandar um visitante
// deslogado para /login neste ponto o prendia num loop de reload, com o app inteiro preso
// no skeleton de bootstrap. A tentativa de refresh continua acontecendo: quem tem access
// token expirado e refresh valido recupera a sessao normalmente; o que muda e so o
// desfecho quando o refresh tambem falha.
function isSessionProbe(config: RetryableRequestConfig): boolean {
  return config.method?.toUpperCase() === "GET" && config.url === "/api/auth/me"
}

// Endpoints em que 401 significa "a senha digitada esta errada", nao "a sessao expirou".
// Renovar e repetir a chamada gastaria um refresh e, no segundo 401, expulsaria o usuario
// para /login no meio de um formulario — o erro precisa chegar cru em quem chamou.
function isPasswordChallenge(config: RetryableRequestConfig): boolean {
  const method = config.method?.toUpperCase()
  const url = config.url ?? ""
  return (
    (method === "PUT" && url === "/api/auth/password") ||
    (method === "DELETE" && url === "/api/user/me")
  )
}

// POST /api/client-errors (reportClientError, US-005) roda sem gesto do usuario, dentro de
// um componentDidCatch que ja esta lidando com uma falha - um 401 dali (nao deveria
// acontecer, o endpoint e permitAll, mas nada impede um proxy/middleware de devolver um no
// meio do caminho) nao pode disparar refresh nem redirect por cima do que o boundary da
// US-006 esta tentando mostrar. Mesmo tratamento cru do isPasswordChallenge acima.
function isClientErrorReport(config: RetryableRequestConfig): boolean {
  return config.method?.toUpperCase() === "POST" && config.url === CLIENT_ERROR_URL
}

api.interceptors.response.use(
  (response) => {
    captureRequestId(response.headers)
    return response
  },
  async (error) => {
    if (error.response) {
      captureRequestId(error.response.headers)
    }

    const config = error.config as RetryableRequestConfig | undefined
    if (error.response?.status !== 401 || !config) {
      return Promise.reject(error)
    }

    if (isPasswordChallenge(config) || isClientErrorReport(config)) {
      return Promise.reject(error)
    }

    const sessionProbe = isSessionProbe(config)

    if (config._retry) {
      if (!sessionProbe) {
        redirectToLogin(config, "401 tambem apos renovar a sessao")
      }
      return Promise.reject(error)
    }

    config._retry = true
    try {
      await refreshSession()
    } catch (refreshError) {
      if (!sessionProbe) {
        const status = isAxiosError(refreshError)
          ? (refreshError.response?.status ?? refreshError.code ?? "sem resposta")
          : "erro desconhecido"
        redirectToLogin(config, `POST ${REFRESH_URL} falhou (${status})`)
      }
      return Promise.reject(refreshError)
    }

    return api(config)
  }
)

interface ClientErrorReport {
  message: string
  stack?: string
  route?: string
}

/**
 * Manda o relatorio de erro de render do frontend ao backend (POST /api/client-errors,
 * publico, US-003/US-004). Fire-and-forget por design: quem chama (o error boundary da
 * US-006) esta lidando com uma falha de render e nao pode ficar bloqueado nem quebrar de
 * novo por causa deste envio — a promise devolvida nunca rejeita, so serve para quem
 * quiser aguardar em teste. O X-Request-Id da ultima resposta viaja no header quando
 * existe; sem ele o backend gera um novo.
 */
export function reportClientError({ message, stack, route }: ClientErrorReport): Promise<void> {
  const headers = lastRequestId ? { "X-Request-Id": lastRequestId } : undefined
  return api
    .post(
      CLIENT_ERROR_URL,
      {
        message,
        stack: stack?.slice(0, CLIENT_ERROR_STACK_MAX_LENGTH),
        route,
      },
      { headers }
    )
    .then(() => undefined)
    .catch((error: unknown) => {
      console.error("[api] falha ao reportar erro do cliente", error)
    })
}
