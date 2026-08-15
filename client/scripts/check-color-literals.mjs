#!/usr/bin/env node
// Gate de CI (Epico 13, US-002/D18, endurecido na US-039): recusa cor crua
// nova em QUALQUER arquivo fora de src/components/ui/ (unica pasta
// permanentemente isenta - sao os primitivos do design system). Nao existe
// mais allowlist por arquivo (US-039 fechou a divida da migracao) - a unica
// forma de um hex/utilitario arbitrario sobreviver ao gate e uma excecao
// pontual comentada no proprio codigo, no mesmo espirito do `--ignore` do
// `bun audit` (ver client/CLAUDE.md): um comentario `color-ok: <motivo>` na
// mesma linha da cor, ou na linha imediatamente anterior (para JSX, onde o
// comentario vira `{/* color-ok: ... */}` acima do elemento), documentando
// por que aquele valor e ilustracao/gradiente de marca e nao cor de
// interface. Isso nao e uma valvula de escape silenciosa: sem o comentario,
// a linha falha o gate igual a qualquer cor nova.
//
// Uso: `node scripts/check-color-literals.mjs` (falha com exit 1 se achar
// cor crua sem exceção comentada). `--list-offenders` so imprime as
// violacoes que ainda restam (mesma logica de excecao aplicada), sem exit
// code != 0 - usado para auditar o estado atual.

import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const CLIENT_ROOT = join(__dirname, "..");
const SRC_ROOT = join(CLIENT_ROOT, "src");
const EXEMPT_PREFIX = join("components", "ui") + "/";
const IGNORE_MARKER = "color-ok:";

const HEX_RE = /#[0-9a-fA-F]{3,8}\b/g;
// So casa `-[...]` cujo conteudo parece cor de verdade (hex, rgb/rgba/hsl/
// hsla, gradient com stops de cor, ou o `white`/`black` nomeado do
// Tailwind) - um arbitrario de tamanho/tipografia (`text-[13px]`,
// `text-[clamp(...)]`) nunca e cor e nao deve derrubar o gate.
const ARBITRARY_COLOR_RE =
  /\b(?:bg|text|border|ring|from|to|via|shadow|fill|stroke)-\[([^\]]*)\]/g;
function bracketLooksLikeColor(content) {
  return (
    /#[0-9a-fA-F]{3,8}\b/.test(content) ||
    /\b(?:rgba?|hsla?)\(/i.test(content) ||
    /gradient/i.test(content) ||
    /\bwhite\b|\bblack\b/i.test(content)
  );
}

// Sugestao de token por hex conhecido - mesma tabela de comentarios do
// bloco `.dark` de client/src/index.css (US-001 + US-039).
const TOKEN_SUGGESTIONS = {
  "#09090a": "bg-background / text-background",
  "#f6f4ec": "bg-foreground / text-foreground",
  "#161513": "bg-card",
  "#ffcb2b": "bg-primary / text-primary",
  "#a6a39a": "text-muted-foreground",
  "#ffe08a": "bg-accent / text-accent",
  "#ff6b6b": "bg-destructive / text-destructive / border-destructive",
  "#ffb3b3": "text-destructive-foreground",
  "#ff9e2c": "bg-series / text-series",
  "#ff5c47": "bg-coral / text-coral / border-coral",
  "#ffb3a5": "text-coral-foreground",
  "#ff8f7c": "text-coral-chip",
  "#3ddc97": "bg-success / text-success",
  "#ffb443": "text-rating / fill-rating",
  "#d6d2c8": "text-pill-foreground",
  "#d8d3c5": "text-label-foreground",
  "#6f6c62": "text-tertiary-foreground",
  "#ffdd7a": "text-accent-strong",
  "#ff9b9b": "text-destructive-soft",
  "#201e18": "bg-surface-secondary",
  "#111": "text-on-primary",
  "#c49dff": "text-want-to-see",
  "#d9d4e6": "text-opinion-foreground",
  "#c4bfb4": "text-synopsis-foreground",
  "#75726a": "text-caption-foreground",
  "#2b2920": "bg-scrollbar-thumb",
  "#8fe9c4": "text-success-foreground",
  "#a3560a": "text-warning-foreground",
};

function suggestionFor(match) {
  const bare = match.replace(/^HEX:/, "");
  const token = TOKEN_SUGGESTIONS[bare.toLowerCase()];
  if (token) return `${bare} -> use ${token}`;
  if (/^rgba?\(255,255,255,/i.test(bare))
    return `${bare} -> use white/<opacidade> (cor nomeada do Tailwind, sem token dedicado)`;
  if (/^rgba?\(8,7,11,/i.test(bare)) return `${bare} -> use backdrop/<opacidade>`;
  return `${bare} -> nenhum token conhecido cobre este valor; adicione um em index.css (US-001/US-039), reuse um existente, ou documente uma excecao pontual com um comentario "${IGNORE_MARKER} <motivo>" na linha (se for ilustracao/gradiente de marca)`;
}

function isTestFile(relPath) {
  return (
    /\.(test|spec)\.tsx?$/.test(relPath) ||
    relPath.split("/").includes("test")
  );
}

function walk(dir, files = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory()) {
      walk(full, files);
    } else if (/\.tsx?$/.test(entry)) {
      files.push(full);
    }
  }
  return files;
}

function findOffenses(content) {
  const offenses = [];
  const lines = content.split("\n");
  lines.forEach((line, idx) => {
    const hasIgnore =
      line.includes(IGNORE_MARKER) ||
      (idx > 0 && lines[idx - 1].includes(IGNORE_MARKER));
    if (hasIgnore) return;
    for (const match of line.matchAll(HEX_RE)) {
      offenses.push({ line: idx + 1, match: "HEX:" + match[0] });
    }
    for (const match of line.matchAll(ARBITRARY_COLOR_RE)) {
      if (bracketLooksLikeColor(match[1])) {
        offenses.push({ line: idx + 1, match: match[0] });
      }
    }
  });
  return offenses;
}

function main() {
  const listOffendersOnly = process.argv.includes("--list-offenders");

  const allFiles = walk(SRC_ROOT);
  const failures = [];

  for (const absPath of allFiles) {
    const relPath = relative(SRC_ROOT, absPath).split("\\").join("/");
    if (relPath.startsWith(EXEMPT_PREFIX)) continue;
    if (isTestFile(relPath)) continue;

    const content = readFileSync(absPath, "utf8");
    const offenses = findOffenses(content);
    if (offenses.length === 0) continue;

    failures.push({ relPath, offenses });
  }

  if (listOffendersOnly) {
    for (const { relPath, offenses } of failures) {
      for (const { line, match } of offenses) {
        console.log(`${relPath}:${line}: ${match}`);
      }
    }
    return;
  }

  if (failures.length > 0) {
    console.error(
      "\nCor crua encontrada fora de components/ui/ sem excecao comentada:\n",
    );
    for (const { relPath, offenses } of failures) {
      console.error(`  ${relPath}`);
      for (const { line, match } of offenses.slice(0, 10)) {
        console.error(`    linha ${line}: ${suggestionFor(match)}`);
      }
      if (offenses.length > 10) {
        console.error(`    ... e mais ${offenses.length - 10} ocorrencia(s)`);
      }
    }
    console.error(
      `\nUse um token semantico de client/src/index.css em vez de cor crua, ou documente uma excecao pontual com um comentario "${IGNORE_MARKER} <motivo>" (mesma linha, ou linha imediatamente anterior) se for ilustracao/gradiente de marca - nunca uma cor de interface.\n`,
    );
    process.exit(1);
  }

  console.log(
    "check-color-literals: ok (nenhuma cor crua fora de components/ui/ sem excecao comentada).",
  );
}

main();
