# Sessao a Dois

## Regra Obrigatoria

Antes de responder a QUALQUER prompt, voce DEVE ler todos os arquivos dentro da pasta `docs/` e suas subpastas. Isso e obrigatorio e nao pode ser ignorado, independentemente do tipo de tarefa solicitada.

Arquivos a ler:
- docs/ARCHITECTURE.md
- docs/design/claude-design-project/Sessao a Dois.dc.html
- docs/design/claude-design-project/support.js

Se novos arquivos forem adicionados a pasta `docs/`, eles tambem devem ser lidos antes de qualquer resposta.

## Frontend (client/)

Qualquer implementacao, escrita ou modificacao de codigo dentro da pasta `client/` DEVE usar a skill `frontend-design` antes de proceder. Isso garante que o design visual seja intencional e consistente.

**Excecao:** refatoracao pura, sem nenhuma mudanca visual, esta dispensada da skill.
O criterio e objetivo: se o resultado final deve ser pixel-identico ao anterior
(extrair um hook, quebrar um arquivo grande em modulos, renomear, corrigir vazamento de
estado), a skill nao agrega — ela existe para decisoes de design, e refatoracao nao toma
nenhuma. Se qualquer pixel muda, a regra acima volta a valer integralmente.
