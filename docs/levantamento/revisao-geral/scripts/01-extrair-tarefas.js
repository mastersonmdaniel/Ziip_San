// Extrai as tarefas das tabelas | # | Tarefa | Origem | Tam. | Dep. | Aceite | do plano canônico.
// Uso: node 01-extrair-tarefas.js [caminho-do-plano] > tarefas.json
// Saída: JSON com id, linha, seção (###), subseção (**Onda X**), onda declarada no texto, células,
// dependências extraídas (IDs), dependências textuais (sem ID) e contagem de linhas "Ajuste v2.xx".
"use strict";
const fs = require("fs");
const path = require("path");
const plano = process.argv[2] ||
  path.join(__dirname, "..", "..", "..", "PLANO-EXECUCAO-PDCA-R0-R9.md");
const linhas = fs.readFileSync(plano, "utf8").split("\n");

const RE_ID = /^(P[0-4]\.\d+|GL\.\d+|LC\.\d+|ED-\d+)$/;

// Expande intervalos "P3.25 a P3.27", "LC.2–LC.7", "ED-69 a ED-74".
function expandir(texto) {
  const ids = new Set();
  const reFaixa = /\b(P[0-4]\.|GL\.|LC\.|ED-)(\d+)\s*(?:a|–|-|até)\s*(?:\1)?(\d+)\b/g;
  let m;
  const consumido = [];
  while ((m = reFaixa.exec(texto))) {
    const pref = m[1], a = +m[2], b = +m[3];
    if (b > a && b - a < 40) {
      for (let i = a; i <= b; i++) ids.add(pref + i);
      consumido.push([m.index, m.index + m[0].length]);
    }
  }
  const reId = /\b(P[0-4]\.\d+|GL\.\d+|LC\.\d+|ED-\d+)\b/g;
  while ((m = reId.exec(texto))) ids.add(m[1]);
  return [...ids];
}

function celulas(linha) {
  // remove pipe inicial/final e divide; protege pipes dentro de crases
  let s = linha.trim().replace(/^\|/, "").replace(/\|$/, "");
  const out = [];
  let atual = "", crase = false;
  for (const ch of s) {
    if (ch === "`") crase = !crase;
    if (ch === "|" && !crase) { out.push(atual.trim()); atual = ""; continue; }
    atual += ch;
  }
  out.push(atual.trim());
  return out;
}

const tarefas = [];
let secao = "", subsecao = "", emTabelaTarefa = false;
linhas.forEach((l, i) => {
  if (/^#{2,3} /.test(l)) { secao = l.replace(/^#+ /, "").trim(); subsecao = ""; }
  const sub = l.match(/^\*\*(Onda [ABC])\*\*\s*$/);
  if (sub) subsecao = sub[1];
  if (/^\| *# *\| *Tarefa *\|/.test(l)) { emTabelaTarefa = true; return; }
  if (!l.startsWith("|")) { emTabelaTarefa = false; return; }
  if (!emTabelaTarefa) return;
  const c = celulas(l);
  if (!RE_ID.test(c[0])) return;
  const [id, tarefa, origem, tam, dep, ...resto] = c;
  const aceite = resto.join(" | ");
  const ondaTexto = (tarefa.match(/onda ([ABC])\b/i) || [])[1] || null;
  const ajustes = (l.match(/\*\*Ajuste v2\.\d+[^*]*\*\*/g) || []).map(a => a.replace(/\*/g, ""));
  const ajustesV = (l.match(/Ajuste v2\.(\d+)/g) || []).map(a => a.replace("Ajuste ", ""));
  tarefas.push({
    id, linha: i + 1, secao, subsecao: subsecao || null, ondaTexto,
    tarefa, origem, tam, dep, aceite,
    nCelulas: c.length,
    depIds: expandir(dep).filter(x => x !== id),
    depTexto: dep.replace(/\b(P[0-4]\.\d+|GL\.\d+|LC\.\d+|ED-\d+)\b/g, "").replace(/[;,.\s—–-]+/g, " ").trim(),
    ajustes: ajustesV,
    tamLinha: l.length,
  });
});
process.stdout.write(JSON.stringify(tarefas, null, 1));
