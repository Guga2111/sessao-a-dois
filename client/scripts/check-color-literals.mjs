#!/usr/bin/env node
// Gate de CI (Epico 13, US-002/D18): recusa cor crua nova fora de
// src/components/ui/, que e a unica pasta autorizada a ter hex/utilitario
// arbitrario de cor permanentemente (sao os primitivos do design system).
// Arquivo fora dela e ou esta limpo, ou esta em color-migration-allowlist.txt
// (divida temporaria rastreada por US-003 em diante).
//
// Uso: `node scripts/check-color-literals.mjs` (falha com exit 1 se achar
// cor crua fora da allowlist). `--list-offenders` so imprime, sem allowlist
// nem exit code != 0 - usado para (re)gerar color-migration-allowlist.txt.

import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const CLIENT_ROOT = join(__dirname, "..");
const SRC_ROOT = join(CLIENT_ROOT, "src");
const ALLOWLIST_PATH = join(CLIENT_ROOT, "color-migration-allowlist.txt");
const EXEMPT_PREFIX = join("components", "ui") + "/";

const HEX_RE = /#[0-9a-fA-F]{6}\b/g;
const ARBITRARY_COLOR_RE =
  /\b(?:bg|text|border|ring|from|to|via|shadow|fill|stroke)-\[[^\]]*\]/g;

// Sugestao de token por hex conhecido - mesma tabela do comentario de
// client/src/index.css acima do bloco `.dark` (US-001).
const TOKEN_SUGGESTIONS = {
  "#09090a": "bg-background / text-background",
  "#f6f4ec": "bg-foreground / text-foreground",
  "#161513": "bg-card",
  "#ffcb2b": "bg-primary / text-primary",
  "#a6a39a": "text-muted-foreground",
  "#ffe08a": "bg-accent / text-accent",
  "#ff6b6b": "bg-destructive / text-destructive / border-destructive",
  "#ffb3b3": "text-destructive-foreground",
};

function suggestionFor(match) {
  const token = TOKEN_SUGGESTIONS[match.toLowerCase()];
  if (token) return `${match} -> use ${token}`;
  return `${match} -> nenhum token conhecido cobre este valor; adicione um em index.css (US-001) ou reuse um existente, nao invente utilitario arbitrario novo`;
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

function loadAllowlist() {
  const raw = readFileSync(ALLOWLIST_PATH, "utf8");
  return new Set(
    raw
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith("#")),
  );
}

function findOffenses(content) {
  const offenses = [];
  content.split("\n").forEach((line, idx) => {
    for (const match of line.matchAll(HEX_RE)) {
      offenses.push({ line: idx + 1, match: match[0] });
    }
    for (const match of line.matchAll(ARBITRARY_COLOR_RE)) {
      offenses.push({ line: idx + 1, match: match[0] });
    }
  });
  return offenses;
}

function main() {
  const listOffendersOnly = process.argv.includes("--list-offenders");
  const allowlist = listOffendersOnly ? new Set() : loadAllowlist();

  const allFiles = walk(SRC_ROOT);
  const failures = [];
  const offenderFiles = [];

  for (const absPath of allFiles) {
    const relPath = relative(SRC_ROOT, absPath).split("\\").join("/");
    if (relPath.startsWith(EXEMPT_PREFIX)) continue;
    if (isTestFile(relPath)) continue;

    const content = readFileSync(absPath, "utf8");
    const offenses = findOffenses(content);
    if (offenses.length === 0) continue;

    offenderFiles.push(relPath);
    if (!listOffendersOnly && !allowlist.has(relPath)) {
      failures.push({ relPath, offenses });
    }
  }

  if (listOffendersOnly) {
    offenderFiles.sort();
    for (const f of offenderFiles) console.log(f);
    return;
  }

  if (failures.length > 0) {
    console.error(
      "\nCor crua encontrada fora de components/ui/ e fora da allowlist (client/color-migration-allowlist.txt):\n",
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
      "\nUse um token semantico de client/src/index.css em vez de cor crua, ou adicione o arquivo a color-migration-allowlist.txt se for divida ja existente (nao aumentar o total).\n",
    );
    process.exit(1);
  }

  console.log(
    `check-color-literals: ok (${allowlist.size} arquivo(s) na allowlist, nenhuma cor crua nova encontrada).`,
  );
}

main();
