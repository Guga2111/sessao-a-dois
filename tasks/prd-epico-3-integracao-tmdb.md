# PRD: Épico 3 — Integração TMDB (Domínio Media/BFF)

## 1. Introdução/Visão Geral

Este épico implementa o backend como um **Backend for Frontend (BFF)** para a API do TMDB (The Movie Database). O objetivo é que o frontend nunca converse diretamente com o TMDB — todas as buscas de filmes/séries e consultas de detalhes (incluindo onde assistir) passam pelo backend Spring Boot, que esconde a API Key do TMDB e padroniza a resposta em português do Brasil (pt-BR).

Isso cria a base de dados de mídia (filmes e séries) que será usada pelos Épicos 4 (Tracking) e 5 (Match), ambos dependentes de poder buscar e detalhar títulos do TMDB através do backend.

**Pacote:** `com.app.media`

## 2. Objetivos

- Ocultar completamente a API Key do TMDB do frontend e de qualquer cliente externo.
- Garantir que toda resposta do TMDB consumida pelo app esteja em pt-BR.
- Fornecer um endpoint de busca combinada (filmes + séries) para popular a busca do modal de adição de título e da tela de Match.
- Fornecer um endpoint de detalhes de título que inclua informações de "onde assistir" (watch providers).
- Garantir resiliência: falhas do TMDB não devem derrubar a aplicação nem vazar detalhes internos ao cliente.
- Ter cobertura de testes unitários (Mockito) simulando respostas de sucesso e falha do TMDB.

## 3. User Stories

### US-001: Configurar cliente HTTP para o TMDB
**Descrição:** Como desenvolvedor, preciso de um `RestClient` configurado para chamar a API do TMDB de forma centralizada, para que todas as integrações futuras reutilizem a mesma configuração (base URL, autenticação, idioma padrão).

**Critérios de Aceitação:**
- [ ] `RestClient` (Spring 3.2+) configurado como `@Bean` em uma classe de configuração dentro de `com.app.media`.
- [ ] Base URL do TMDB (`https://api.themoviedb.org/3`) e API Key lidos de variáveis de ambiente (ex.: `TMDB_API_KEY`), nunca hardcoded no código.
- [ ] Todo request enviado ao TMDB inclui `language=pt-BR` como parâmetro padrão, sem precisar ser passado manualmente em cada chamada.
- [ ] A API Key é enviada via header de autenticação do TMDB (Bearer token do TMDB v4 ou `api_key` query param, conforme a chave disponível), nunca exposta em logs.
- [ ] Aplicação falha ao subir (fail-fast) com mensagem clara se `TMDB_API_KEY` não estiver configurada.
- [ ] Typecheck/build (`mvn compile`) passa.

### US-002: Endpoint de busca combinada de filmes e séries
**Descrição:** Como usuário do app, quero pesquisar por um título (filme ou série) e ver os dois tipos misturados nos resultados, para que eu não precise saber previamente se é um filme ou uma série.

**Critérios de Aceitação:**
- [ ] Endpoint `GET /api/media/search?q={termo}` implementado em `com.app.media`.
- [ ] Internamente, chama o endpoint `/search/multi` do TMDB.
- [ ] Resposta é um DTO simplificado próprio (não o JSON bruto do TMDB), contendo por item: `tmdbId`, `mediaType` (`MOVIE` | `TV`), `title`, `year` (extraído de `release_date`/`first_air_date`), `posterUrl` (URL completa, já concatenada com o base da imagem do TMDB), `overview` (sinopse curta), `voteAverage`.
- [ ] Itens do TMDB que não sejam filme nem série (ex.: `person`) são filtrados e não aparecem na resposta.
- [ ] Se `q` estiver vazio ou ausente, retorna `400 Bad Request` com mensagem clara.
- [ ] Retorna lista vazia (`200 OK` com `[]`) quando o TMDB não encontra resultados — isso não é um erro.
- [ ] Typecheck/build passa.

### US-003: Endpoint de detalhes do título com "onde assistir"
**Descrição:** Como usuário do app, quero ver os detalhes completos de um filme/série (sinopse, gêneros, nota do TMDB, streamings disponíveis), para decidir se quero assistir e onde.

**Critérios de Aceitação:**
- [ ] Endpoint `GET /api/media/{mediaType}/{tmdbId}` (`mediaType` = `movie` ou `tv`) implementado em `com.app.media`.
- [ ] Internamente, chama `GET /{mediaType}/{tmdbId}?append_to_response=watch/providers` no TMDB, em uma única chamada HTTP.
- [ ] Resposta é um DTO simplificado contendo: `tmdbId`, `mediaType`, `title`, `year`, `posterUrl`, `overview`, `genres` (lista de nomes em pt-BR), `voteAverage`, `runtime` (apenas para filmes, em minutos — usado depois na Task 6.1), e `watchProviders` (lista com `name` e `logoUrl` dos serviços de streaming disponíveis no Brasil, região `BR`).
- [ ] Se o TMDB não tiver dados de `watch/providers` para o Brasil, `watchProviders` retorna lista vazia (não é erro).
- [ ] Se o `tmdbId` não existir no TMDB, retorna `404 Not Found` com mensagem clara.
- [ ] Se `mediaType` for um valor diferente de `movie`/`tv`, retorna `400 Bad Request`.
- [ ] Typecheck/build passa.

### US-004: Tratamento de falhas do TMDB
**Descrição:** Como usuário do app, quero receber uma mensagem de erro compreensível (não uma tela quebrada) quando o TMDB estiver indisponível, para entender que é um problema temporário e não um bug do app.

**Critérios de Aceitação:**
- [ ] Um `@ControllerAdvice` (ou equivalente) em `com.app.media` captura exceções de chamada ao TMDB (timeout, erro de conexão, resposta 4xx/5xx do TMDB).
- [ ] Quando o TMDB retorna erro ou está inacessível, o backend responde com `502 Bad Gateway`, com corpo JSON contendo uma mensagem genérica (ex.: `"Não foi possível buscar dados no momento. Tente novamente mais tarde."`), sem vazar stacktrace, URL da API do TMDB ou a API Key.
- [ ] Erros do TMDB são logados no backend (nível `ERROR`, incluindo status code e endpoint chamado) para diagnóstico, mas nunca a API Key.
- [ ] Typecheck/build passa.

### US-005: Testes unitários com Mockito para o cliente TMDB
**Descrição:** Como desenvolvedor, preciso de testes automatizados que simulem as respostas do TMDB (sucesso e falha), para garantir que a integração continue funcionando corretamente conforme o código evolui.

**Critérios de Aceitação:**
- [ ] Testes unitários (JUnit 5 + Mockito) cobrindo o service de busca (US-002): caso de sucesso com resultados mistos (filme + série), caso de lista vazia, e caso de parâmetro `q` inválido.
- [ ] Testes unitários cobrindo o service de detalhes (US-003): caso de sucesso com `watch/providers` presente, caso de sucesso sem `watch/providers` (BR vazio), e caso de `tmdbId` inexistente (404).
- [ ] Testes unitários cobrindo o tratamento de erro (US-004): simulando timeout/erro 5xx do TMDB e verificando que o backend responde `502` com a mensagem esperada, sem vazar a API Key.
- [ ] Todos os testes rodam via `mvn test` e passam.
- [ ] Chamadas reais ao TMDB são mockadas — nenhum teste faz requisição HTTP de verdade.

## 4. Requisitos Funcionais

- FR-1: O sistema deve expor um `RestClient` configurado centralmente em `com.app.media` para todas as chamadas ao TMDB, com `language=pt-BR` fixo em todo request.
- FR-2: A API Key do TMDB deve ser lida exclusivamente de variável de ambiente (`TMDB_API_KEY`), nunca do código-fonte ou de arquivos versionados.
- FR-3: O sistema deve expor `GET /api/media/search?q={termo}` usando `/search/multi` do TMDB, filtrando resultados para apenas `movie` e `tv`, e retornando um DTO simplificado.
- FR-4: O sistema deve expor `GET /api/media/{mediaType}/{tmdbId}` usando `append_to_response=watch/providers`, retornando um DTO simplificado com dados de streaming da região `BR`.
- FR-5: Toda falha de comunicação com o TMDB (timeout, 4xx, 5xx) deve ser convertida em resposta `502 Bad Gateway` com mensagem genérica ao cliente, sem vazar detalhes internos.
- FR-6: Parâmetros inválidos (`q` vazio, `mediaType` desconhecido) devem retornar `400 Bad Request` antes de qualquer chamada ao TMDB.
- FR-7: `tmdbId` inexistente no endpoint de detalhes deve retornar `404 Not Found`.
- FR-8: Toda a lógica de integração com o TMDB deve estar isolada no pacote `com.app.media` — nenhum outro pacote (`tracking`, `match`, etc.) deve chamar o TMDB diretamente; eles devem consumir os endpoints/services deste pacote.
- FR-9: Deve haver testes unitários com Mockito cobrindo os cenários de sucesso, lista vazia, dados ausentes (sem watch providers) e falha (erro/timeout) para busca e detalhes.

## 5. Não-Objetivos (Fora de Escopo)

- Cache de respostas do TMDB (decisão já registrada no backlog: "não por enquanto").
- Paginação de resultados de busca (o TMDB pagina, mas este épico não expõe página seguinte ao frontend — apenas a primeira página).
- Suporte a outros idiomas além de pt-BR.
- Persistência de dados do TMDB no banco (isso é responsabilidade do Épico 4 — `MediaTrack`/`UserReview`).
- Autenticação/autorização nos endpoints de media (assume-se que a proteção JWT geral do Épico 2 já cobre isso via filtro global).
- Endpoints de descoberta/recomendação do TMDB (`/discover`, `/trending`) — fora do escopo deste épico.

## 6. Considerações de Design

- Não há tela nova neste épico — os endpoints alimentam o campo de busca do `TitleModal.tsx` (Épico 4) e a tela `MatchScreen.tsx` (Épico 5), já mapeados no protótipo de design.
- URLs de imagem (poster, logos de streaming) devem vir prontas (URL completa) do backend, para o frontend não precisar montar a URL base do TMDB.

## 7. Considerações Técnicas

- Usar `RestClient` do Spring 3.2+ (não `RestTemplate`, que está em modo de manutenção).
- Este épico depende do Épico 1 (projeto Spring Boot inicializado) estar concluído.
- Os Épicos 4 e 5 dependem diretamente dos endpoints deste épico para funcionar — priorizar estabilidade da interface (`tmdbId`, `mediaType`, formato dos DTOs) já que outros domínios vão consumi-la.
- Region fixa `BR` para watch providers, alinhado com a decisão de idioma pt-BR fixo.
- A imagem base do TMDB (`https://image.tmdb.org/t/p/{size}`) deve ser configurável (tamanho de poster/logo) via constante ou propriedade, não hardcoded espalhada pelo código.

## 8. Métricas de Sucesso

- 100% das chamadas ao TMDB originadas do frontend passam pelo backend (nenhuma chamada direta frontend → TMDB).
- Cobertura de testes unitários no pacote `com.app.media` para services e tratamento de erro.
- Endpoints de busca e detalhes respondem corretamente em pt-BR para os cenários de teste manual (ex.: buscar "Duna", verificar que retorna filme e possíveis séries relacionadas).

## 9. Questões em Aberto

- Qual tamanho de imagem do TMDB usar por padrão (ex.: `w500` para poster, `w92` para logos de provider)? Sugestão: definir como constante configurável e ajustar depois com o frontend.
- O TMDB v4 (Bearer token) ou v3 (`api_key` query param) será usado? Depende de qual tipo de chave for gerada na conta TMDB — ajustar a configuração do `RestClient` (US-001) conforme o tipo de chave disponível no `.env`.
- Deve haver um timeout específico configurado no `RestClient` (ex.: 5s) para evitar que uma lentidão do TMDB trave requests do app? Recomenda-se definir um valor durante a implementação da US-001.
