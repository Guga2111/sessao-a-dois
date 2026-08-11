import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { InternalAxiosRequestConfig } from "axios"

// Testing the interceptor of lib/api.ts itself, not a consumer of it: vi.mock would hide
// exactly the thing under test. Instead a fake adapter is installed on axios.defaults.adapter
// BEFORE lib/api.ts is imported, so that both `api` and the internal (unexported)
// `refreshClient` — both created via axios.create() at module scope — inherit it. Every
// test does vi.resetModules() + a fresh dynamic import of "axios" and "@/lib/api" so the
// module-scoped `refreshPromise` single-flight guard never leaks between tests, and the
// adapter is re-applied to whichever axios module instance ends up backing that fresh import.

const REFRESH_URL = "/api/auth/refresh"
const BREADCRUMB_KEY = "sessaoADois.lastAuthRedirect"

interface QueuedOk {
  status: number
  data?: unknown
}

interface QueuedNetworkError {
  networkError: true
  message: string
  code?: string
}

type Queued = QueuedOk | QueuedNetworkError

const responseQueues = new Map<string, Queued[]>()

function requestKey(method: string | undefined, url: string | undefined): string {
  return `${(method ?? "get").toUpperCase()} ${url ?? ""}`
}

function queueResponse(method: string, url: string, status: number, data?: unknown): void {
  const key = requestKey(method, url)
  const arr = responseQueues.get(key) ?? []
  arr.push({ status, data })
  responseQueues.set(key, arr)
}

function queueNetworkError(method: string, url: string, message: string, code?: string): void {
  const key = requestKey(method, url)
  const arr = responseQueues.get(key) ?? []
  arr.push({ networkError: true, message, code })
  responseQueues.set(key, arr)
}

function makeAxiosError(config: InternalAxiosRequestConfig, status: number, data?: unknown) {
  return Object.assign(new Error(`Request failed with status code ${status}`), {
    isAxiosError: true,
    config,
    response: { status, data, statusText: "", headers: {}, config, request: {} },
  })
}

const fakeAdapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
  const key = requestKey(config.method, config.url)
  const arr = responseQueues.get(key)
  const next = arr?.shift()
  if (!next) {
    throw new Error(`[fakeAdapter] no queued response left for ${key}`)
  }
  if ("networkError" in next) {
    throw Object.assign(new Error(next.message), {
      isAxiosError: true,
      config,
      code: next.code,
    })
  }
  if (next.status >= 200 && next.status < 300) {
    return {
      data: next.data,
      status: next.status,
      statusText: "",
      headers: {},
      config,
      request: {},
    }
  }
  throw makeAxiosError(config, next.status, next.data)
})

function stubLocation(pathname: string): { pathname: string; href: string } {
  const location = { pathname, href: `http://localhost${pathname}` }
  Object.defineProperty(window, "location", {
    configurable: true,
    writable: true,
    value: location,
  })
  return location
}

let api: typeof import("@/lib/api").api
let consoleErrorSpy: ReturnType<typeof vi.spyOn>

beforeEach(async () => {
  responseQueues.clear()
  fakeAdapter.mockClear()
  sessionStorage.clear()
  stubLocation("/hub")
  consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {})

  vi.resetModules()
  const axiosModule = await import("axios")
  axiosModule.default.defaults.adapter = fakeAdapter
  const apiModule = await import("@/lib/api")
  api = apiModule.api
})

afterEach(() => {
  consoleErrorSpy.mockRestore()
})

function refreshCallCount(): number {
  return fakeAdapter.mock.calls.filter(
    ([config]) => requestKey(config.method, config.url) === requestKey("POST", REFRESH_URL)
  ).length
}

describe("401 comum", () => {
  it("dispara UM POST /api/auth/refresh e reexecuta a requisicao original de forma transparente", async () => {
    queueResponse("GET", "/api/foo", 401)
    queueResponse("GET", "/api/foo", 200, { ok: true })
    queueResponse("POST", REFRESH_URL, 200)

    const response = await api.get("/api/foo")

    expect(response.data).toEqual({ ok: true })
    expect(refreshCallCount()).toBe(1)
  })

  it("cinco requisicoes concorrentes recebendo 401 disparam UM UNICO refresh", async () => {
    for (let i = 0; i < 5; i++) {
      queueResponse("GET", "/api/foo", 401)
    }
    for (let i = 0; i < 5; i++) {
      queueResponse("GET", "/api/foo", 200, { ok: true })
    }
    queueResponse("POST", REFRESH_URL, 200)

    const results = await Promise.all(Array.from({ length: 5 }, () => api.get("/api/foo")))

    expect(results.every((r) => r.data.ok)).toBe(true)
    expect(refreshCallCount()).toBe(1)
  })
})

describe("refresh falha", () => {
  it("redireciona para /login e grava o breadcrumb com o motivo do refresh", async () => {
    stubLocation("/hub")
    queueResponse("GET", "/api/foo", 401)
    queueResponse("POST", REFRESH_URL, 401)

    await expect(api.get("/api/foo")).rejects.toBeTruthy()

    expect(window.location.href).toBe("/login")
    const breadcrumb = JSON.parse(sessionStorage.getItem(BREADCRUMB_KEY)!)
    expect(breadcrumb).toMatchObject({ method: "GET", url: "/api/foo" })
    expect(breadcrumb.reason).toMatch(/POST \/api\/auth\/refresh falhou \(401\)/)
    expect(breadcrumb.at).toEqual(expect.any(String))
    expect(consoleErrorSpy).toHaveBeenCalled()
  })
})

describe("segundo 401 na mesma requisicao", () => {
  it("redireciona sem tentar refresh de novo, com motivo distinto do refresh falho", async () => {
    stubLocation("/hub")
    queueResponse("GET", "/api/foo", 401)
    queueResponse("GET", "/api/foo", 401)
    queueResponse("POST", REFRESH_URL, 200)

    await expect(api.get("/api/foo")).rejects.toBeTruthy()

    expect(refreshCallCount()).toBe(1)
    expect(window.location.href).toBe("/login")
    const breadcrumb = JSON.parse(sessionStorage.getItem(BREADCRUMB_KEY)!)
    expect(breadcrumb.reason).toBe("401 tambem apos renovar a sessao")
  })
})

describe("guarda do password challenge", () => {
  it("PUT /api/auth/password com 401 e rejeitado cru, sem refresh e sem redirect", async () => {
    queueResponse("PUT", "/api/auth/password", 401)

    await expect(api.put("/api/auth/password")).rejects.toBeTruthy()

    expect(refreshCallCount()).toBe(0)
    expect(window.location.href).toBe("http://localhost/hub")
  })

  it("DELETE /api/user/me com 401 e rejeitado cru, sem refresh e sem redirect", async () => {
    queueResponse("DELETE", "/api/user/me", 401)

    await expect(api.delete("/api/user/me")).rejects.toBeTruthy()

    expect(refreshCallCount()).toBe(0)
    expect(window.location.href).toBe("http://localhost/hub")
  })
})

describe("guarda da sondagem de sessao", () => {
  it("GET /api/auth/me com 401 tenta o refresh mas nao redireciona quando ele falha", async () => {
    queueResponse("GET", "/api/auth/me", 401)
    queueResponse("POST", REFRESH_URL, 401)

    await expect(api.get("/api/auth/me")).rejects.toBeTruthy()

    expect(refreshCallCount()).toBe(1)
    expect(window.location.href).toBe("http://localhost/hub")
  })
})

describe("guarda do no-op em /login", () => {
  it("estando em /login, redirectToLogin nao atribui window.location.href", async () => {
    const location = stubLocation("/login")
    queueResponse("GET", "/api/foo", 401)
    queueResponse("POST", REFRESH_URL, 401)

    await expect(api.get("/api/foo")).rejects.toBeTruthy()

    expect(location.href).toBe("http://localhost/login")
    expect(consoleErrorSpy).toHaveBeenCalled()
    expect(sessionStorage.getItem(BREADCRUMB_KEY)).not.toBeNull()
  })
})

describe("erro que nao e 401", () => {
  it.each([403, 500])("status %i passa direto, sem refresh e sem redirect", async (status) => {
    queueResponse("GET", "/api/foo", status)

    await expect(api.get("/api/foo")).rejects.toBeTruthy()

    expect(refreshCallCount()).toBe(0)
    expect(window.location.href).toBe("http://localhost/hub")
  })

  it("timeout (sem response) passa direto, sem refresh e sem redirect", async () => {
    queueNetworkError("GET", "/api/foo", "timeout of 15000ms exceeded", "ECONNABORTED")

    await expect(api.get("/api/foo")).rejects.toBeTruthy()

    expect(refreshCallCount()).toBe(0)
    expect(window.location.href).toBe("http://localhost/hub")
  })
})
