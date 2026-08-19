# Polish — Melhorias de Qualidade & Experiência

Ideias selecionadas para refinar a experiência do Sessão a Dois sem adicionar features de domínio novas.
Cada item descreve o problema, o raciocínio da solução e os trade-offs envolvidos.

**Princípio transversal:** todas as implementações devem ser mobile-first — layout, tamanho de toque,
hierarquia visual e fluxos de interação pensados primeiro para tela pequena e depois adaptados para desktop.

---

## 4.1 E-mail de Resumo Mensal

### Problema
Casais que usam o app de forma intermitente perdem o senso de progresso conjunto. Não há nenhum
mecanismo de re-engajamento passivo — se o usuário não abre o app, o app não existe para ele.

### Ideia
No primeiro dia de cada mês, enviar um e-mail de resumo do mês anterior para cada usuário do casal.
Conteúdo sugerido: quantos títulos assistiram juntos, matches do mês, média de notas de cada um,
gênero favorito do período e uma chamada para o título com maior discordância de notas.

### Raciocínio
- Re-engajamento passivo sem push notification (que exige permissão e é mais invasiva).
- Aproveita a infraestrutura `EmailSender` e `ResendEmailSender` já existentes — é um novo método
  na interface e um `@Scheduled` no backend, sem nova dependência.
- Mensal é menos intrusivo que semanal e mais adequado ao ritmo real de consumo do casal.
- O conteúdo vem de queries que o Dashboard já usa (`/api/tracking/stats`), então não há nova
  lógica de agregação a inventar do zero.

### Trade-offs
- Requer `@Scheduled` no backend com cron mensal — testar agendamento em integração é verboso.
- O envio precisa iterar sobre todos os usuários ativos; com ~8 usuários hoje é trivial, mas a
  query de "casais ativos no último mês" precisa de critério claro para não enviar e-mail para
  quem não usou o app no período.
- O template de e-mail é o primeiro com conteúdo dinâmico rico — vai exigir decisão sobre
  HTML vs texto simples e como tratar dados faltantes (casal que não assistiu nada no mês).
- Deve ser opt-out via preferências de notificação (ver item 4.2 abaixo); enviar sem consentimento
  é má prática mesmo para usuários cadastrados.

---

## 4.2 Preferências de Notificação

### Problema
Hoje as notificações são tudo-ou-nada. Não há como um usuário dizer "quero receber e-mail de resumo
mas não quero o aviso de troca de senha" ou "não quero notificação de pedido de avaliação". Qualquer
nova notificação adicionada (como o resumo mensal) não tem mecanismo de opt-out.

### Ideia
Adicionar uma seção "Notificações" na tela `/conta` (`AccountScreen`) com toggles para cada tipo
de notificação disponível. Começar com os tipos que já existem ou serão adicionados:

- E-mail de resumo mensal (default: ativado)
- E-mail quando parceiro dissolve o vínculo (default: ativado)
- Pedido de avaliação in-app (default: ativado)

### Raciocínio
- É o pré-requisito de consentimento para o resumo mensal (4.1) e para qualquer futura notificação.
- A tela `/conta` já tem o padrão de `AccountSection` com `reach="you"` / `reach="both"` —
  preferências de notificação são `reach="you"` por natureza.
- No backend, uma coluna `notification_prefs` em `User` (JSON ou colunas booleanas) é suficiente
  para o volume atual; não exige uma tabela separada.
- No frontend, o padrão de `updateProfile` do `useAuthStore` já cobre o PATCH — basta adicionar
  os campos novos ao `UpdateProfileRequest` e espelhar em `src/types/user.ts`.

### Trade-offs
- **Mobile-first crítico aqui:** toggles em tela pequena precisam de área de toque generosa
  (mínimo 44px de altura) e label claramente associado. O padrão de `Switch` do Shadcn/base-ui
  deve ser avaliado antes de construir do zero.
- Adicionar colunas em `User` exige migration Flyway — baixo risco, mas é um passo que precisa
  ser coordenado com deploy.
- A semântica de "desativado" precisa ser consistente: se o usuário desativa o resumo mensal,
  o backend precisa checar a preferência antes de enviar, não apenas no nível de agendamento.
- Manter a seção simples: não criar um painel de notificações complexo para 3 tipos de evento.
  Crescer conforme os tipos de notificação crescem.

---

## 4.3 E-mail quando Parceiro Dissolve o Vínculo

### Problema
Hoje o ex-parceiro descobre a dissolução do casal na próxima vez que abre o app — o aviso
`bondDissolved` aparece em `/join`, mas só após uma requisição. Se o usuário não abrir o app
por dias, fica sem saber. Isso gera confusão ("achei que era bug do app") e uma experiência
emocional abrupta.

### Ideia
Quando `DELETE /api/couple/me` for chamado, além de dissolver o casal, enviar um e-mail ao
ex-parceiro informando que o vínculo foi encerrado. O e-mail deve ser discreto — comunicar
o fato sem dramatizar, com um CTA para criar um novo casal se desejar.

### Raciocínio
- É o único evento destrutivo importante que acontece de forma unilateral e que o outro lado
  não controla. Uma notificação fora do app é especialmente valiosa aqui.
- Tecnicamente é um novo método `sendCoupleDissolved(to, partnerName)` em `EmailSender`,
  chamado dentro de `useAuthStore.dissolveCouple()` no backend — ou melhor, dentro do
  `CoupleService` que já orquestra a dissolução.
- O e-mail do ex-parceiro está disponível no momento da dissolução (ainda são membros do casal),
  então não há problema de dado faltante.
- Deve respeitar a preferência de notificação do destinatário (ver 4.2).

### Trade-offs
- **Sensibilidade do conteúdo:** o texto do e-mail importa muito aqui. Tom errado pode piorar
  uma situação já delicada. A cópia precisa de atenção especial — neutro, factual, sem julgamento.
- O remetente é o sistema, não o parceiro — deixar isso claro no e-mail evita mal-entendido
  ("foi você que mandou esse e-mail?").
- Se o usuário dissolveu por engano e recriou o casal em seguida, o e-mail já foi enviado.
  Não há cancelamento de e-mail transacional — aceitar como comportamento esperado ou adicionar
  um delay de alguns minutos antes do envio (complexidade extra, provavelmente desnecessária).
- Requer que a preferência de notificação do **destinatário** seja checada, não a de quem dissolve.

---

## 5.1 Busca dentro das Listas do Hub

### Problema
As listas do Hub (`Assistindo`, `Queremos Ver`, `Já Vimos`) crescem com o tempo e não há como
localizar um título específico sem rolar a lista inteira. Com paginação e carregamento incremental,
isso se agrava — um título da segunda ou terceira página exige múltiplos cliques em "carregar mais".

### Ideia
Adicionar um campo de busca local no topo de cada seção do Hub. A busca filtra os títulos já
carregados client-side por título. Se o usuário buscar algo que não está nos resultados carregados,
oferecer um "buscar em todos" que dispara uma query ao backend com o filtro de texto.

### Raciocínio
- A busca client-side (sobre os itens já carregados) tem latência zero e não exige API nova —
  é um `filter` sobre o array em memória.
- A busca no backend é a extensão natural quando a lista tem mais páginas do que as carregadas,
  usando o `q` param já existente em outros endpoints como referência de padrão.
- **Mobile-first crítico:** o campo de busca não deve ocupar espaço permanente na tela pequena.
  Padrão sugerido: ícone de lupa no header da seção que expande o input ao toque, com dismiss
  ao pressionar Escape ou clicar fora — mantém a lista em tela cheia por padrão.
- Não substitui os filtros do Match; é uma busca por título dentro de uma lista já curada do casal.

### Trade-offs
- A busca client-side só cobre o que já foi carregado — comunicar isso ao usuário sem criar
  confusão ("por que não aparece o filme X que eu sei que está na lista?").
- Se implementar busca no backend, o endpoint `GET /api/tracking` precisa aceitar `?q=` e
  filtrar por `title ILIKE`. Migration não necessária, mas a query existente muda.
- O `PAGE_SIZE` atual e o comportamento de "carregar mais" interagem com a busca: uma busca
  que retorna 0 resultados locais mas tem resultados no backend é um estado novo para tratar.
- Manter o escopo inicial simples: busca por título apenas, sem filtro por gênero ou nota
  dentro do Hub (isso seria feature nova, não polish).

---

## 5.2 Ordenação das Listas do Hub

### Problema
As listas do Hub são apresentadas em ordem de inserção (`created_at` descendente,
presumivelmente). Não há como ver "qual foi o filme mais bem avaliado que assistimos" ou
"o que adicionamos mais recentemente na lista Queremos Ver" diretamente no Hub — o usuário
precisa ir ao Dashboard para inferir isso.

### Ideia
Adicionar um seletor de ordenação por seção no Hub. Opções por seção:

- **Assistindo / Queremos Ver:** data de adição (padrão), título A-Z
- **Já Vimos:** data de adição (padrão), título A-Z, nota mais alta, nota mais baixa,
  data de assistido mais recente

### Raciocínio
- **Mobile-first crítico:** o seletor de ordenação não deve ser um dropdown horizontal que
  empurra o conteúdo. Sugestão: ícone de ordenação no header da seção (ao lado da lupa de
  busca de 5.1), que abre um bottom sheet ou popover com as opções — padrão familiar em
  apps mobile.
- A ordenação por nota só faz sentido em "Já Vimos" (onde há avaliações). Nas outras seções,
  limitar as opções evita confusão.
- Client-side sort (sobre o que já foi carregado) é viável para listas pequenas. Para listas
  grandes com paginação, o sort precisa ser passado ao backend como `?sort=rating_desc` para
  ser aplicado antes da paginação — caso contrário, "nota mais alta" só ordena a primeira página.
- O padrão `SelectTrigger` do `FiltersPanel` do Match é o precedente mais próximo para a UI
  do seletor — reusar o componente `Select` do design system, não reinventar.

### Trade-offs
- Sort client-side com paginação é enganoso: ordenar só o que está carregado dá resultado
  errado quando há mais páginas. Precisa de decisão explícita: ou sort é sempre server-side,
  ou comunicar claramente a limitação.
- Backend precisa expor `?sort=` no `GET /api/tracking?status=WATCHED` — mudança de contrato
  de API que exige atualização em `src/lib/api.ts` e nos tipos espelhados.
- A ordenação por nota envolve a média das reviews dos dois usuários ou a nota individual?
  Definir antes de implementar — não deixar essa decisão para o momento do código.
- O estado de ordenação selecionado deve persistir enquanto o usuário estiver na tela, mas
  não precisa sobreviver ao reload (estado local, não `localStorage`).
