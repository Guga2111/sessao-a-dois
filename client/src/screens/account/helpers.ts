/**
 * Constantes compartilhadas da tela /conta. Mora fora dos `.tsx` porque
 * `react-refresh/only-export-components` proibe um arquivo de componente exportar
 * tambem valores comuns (mesmo padrao de `screens/match/helpers.ts`).
 */

/**
 * Aviso que sobrevive ao redirect da troca de senha (US-010): a `PasswordSection` manda
 * em `navigate("/login", { state: { notice } })` e a `LoginPage` mostra. Sem ele, a sessao
 * caindo no meio do caminho pareceria bug em vez do ponto da E9.8.
 */
export const PASSWORD_CHANGED_NOTICE =
  "Senha trocada. Todas as sessões foram encerradas — entre de novo com a senha nova."
