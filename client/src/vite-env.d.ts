/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Host da API, SEM `/api` e sem barra final (ex.: `http://localhost:8080`).
   *
   * Deliberadamente OPCIONAL: em producao a SPA e a API sao same-origin (o nginx
   * faz proxy de `/api` e `/ws` no mesmo dominio), entao o valor correto la e
   * string vazia — URL relativa. O arquivo que definia isso, `client/.env.production`,
   * e ignorado pelo git e portanto NAO existe no runner do GitHub Actions: o build
   * do CD sempre roda sem ele. Marcar como `string | undefined` faz o compilador
   * exigir o fallback (`?? ""`) em todo uso, em vez de deixar `undefined` virar a
   * string "undefined" dentro de um template literal.
   */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
