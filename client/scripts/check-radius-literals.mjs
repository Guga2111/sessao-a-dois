#!/usr/bin/env node
// Extensao do portao de CI da US-002/US-039 (Epico 13) para arredondamento
// (US-044/D20): recusa `rounded-[...]` novo (e as variantes direcionais
// `rounded-t-[...]`/`rounded-b-[...]`/`rounded-l-[...]`/`rounded-r-[...]`)
// fora de src/components/ui/ (unica pasta permanentemente isenta). Mesmo
// espirito do check-color-literals.mjs: sem allowlist por arquivo, a unica
// forma de um raio arbitrario sobreviver e uma excecao pontual comentada no
// proprio codigo (`radius-ok: <motivo>`, mesma linha ou linha imediatamente
// anterior) - reservada para detalhe decorativo sem equivalente na escala
// (ver os 4 rounded-[3px]/rounded-b-[3px] de DashboardScreen.tsx/
// ChartSkeleton.tsx, US-043/D20 opcao (c)).
//
// Uso: `node scripts/check-radius-literals.mjs` (exit 1 se achar raio
// arbitrario sem excecao comentada). `--list-offenders` so lista, sem exit
// code != 0 - para auditar o estado atual.

import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const CLIENT_ROOT = join(__dirname, "..");
const SRC_ROOT = join(CLIENT_ROOT, "src");
const EXEMPT_PREFIX = join("components", "ui") + "/";
const IGNORE_MARKER = "radius-ok:";

// Casa `rounded-[...]` e as variantes direcionais/logicas
// (`rounded-t-[...]`, `rounded-b-[...]`, `rounded-l-[...]`, `rounded-r-[...]`,
// `rounded-tl-[...]`, `rounded-tr-[...]`, `rounded-bl-[...]`,
// `rounded-br-[...]`, `rounded-ss-[...]`, `rounded-se-[...]`,
// `rounded-ee-[...]`, `rounded-es-[...]`).
const ARBITRARY_RADIUS_RE =
  /\brounded(?:-(?:t|r|b|l|tl|tr|bl|br|ss|se|ee|es))?-\[([^\]]*)\]/g;

// --radius-sm/md/lg/chip/xl/2xl/3xl/4xl em client/src/index.css, todas
// calc(var(--radius) * N) sobre --radius: 0.625rem (10px) - ver comentario
// ao lado de --radius-chip (US-044/D20).
const SCALE_PX_TO_TOKEN = {
  6: "rounded-sm",
  8: "rounded-md",
  10: "rounded-lg",
  12: "rounded-chip",
  14: "rounded-xl",
  18: "rounded-2xl",
  22: "rounded-3xl",
  26: "rounded-4xl",
};

function suggestionFor(bracketValue) {
  const pxMatch = /^(\d+(?:\.\d+)?)px$/.exec(bracketValue.trim());
  if (pxMatch) {
    const px = Number(pxMatch[1]);
    const exact = SCALE_PX_TO_TOKEN[px];
    if (exact) return `${bracketValue} -> use ${exact}`;
    return `${bracketValue} -> nenhum token exato cobre este valor; use o token da escala mais proximo (--radius-sm=6px/md=8px/lg=10px/chip=12px/xl=14px/2xl=18px/3xl=22px/4xl=26px) se a diferenca for imperceptivel (D20), ou documente uma excecao pontual com um comentario "${IGNORE_MARKER} <motivo>" na linha (para detalhe decorativo sem equivalente, mesmo criterio da US-043)`;
  }
  return `${bracketValue} -> use um token da escala (--radius-sm/md/lg/chip/xl/2xl/3xl/4xl) em vez de raio arbitrario, ou documente uma excecao pontual com um comentario "${IGNORE_MARKER} <motivo>"`;
}

function isTestFile(relPath) {
  return (
    /\.(test|spec)\.tsx?$/.test(relPath) || relPath.split("/").includes("test")
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
    for (const match of line.matchAll(ARBITRARY_RADIUS_RE)) {
      offenses.push({ line: idx + 1, match: match[0], value: match[1] });
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
      "\nRaio arbitrario encontrado fora de components/ui/ sem excecao comentada:\n",
    );
    for (const { relPath, offenses } of failures) {
      console.error(`  ${relPath}`);
      for (const { line, value } of offenses.slice(0, 10)) {
        console.error(`    linha ${line}: ${suggestionFor(value)}`);
      }
      if (offenses.length > 10) {
        console.error(`    ... e mais ${offenses.length - 10} ocorrencia(s)`);
      }
    }
    console.error(
      `\nUse um token da escala de raio de client/src/index.css em vez de valor arbitrario, ou documente uma excecao pontual com um comentario "${IGNORE_MARKER} <motivo>" (mesma linha, ou linha imediatamente anterior) se for detalhe decorativo sem equivalente na escala.\n`,
    );
    process.exit(1);
  }

  console.log(
    "check-radius-literals: ok (nenhum raio arbitrario fora de components/ui/ sem excecao comentada).",
  );
}

main();
