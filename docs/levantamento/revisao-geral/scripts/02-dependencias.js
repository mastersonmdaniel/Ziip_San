// Analisa as dependências extraídas por 01-extrair-tarefas.js.
// Uso: node 02-dependencias.js tarefas.json
// Verifica: IDs inexistentes; dependência de fase posterior; dependência de onda posterior na trilha;
// dependência de pacote pendente; ciclos; órfãs; dependência textual sem ID; dependência citada no
// texto ("depende da X") e ausente da coluna Dep.; divergência entre a tabela da onda e a onda do título.
"use strict";
const t = require(require("path").resolve(process.argv[2]));
const porId = Object.fromEntries(t.map(x => [x.id, x]));
const ordemFase = id =>
  id.startsWith("P0.") ? 0 : id.startsWith("P1.") ? 1 : id.startsWith("P2.") ? 2 :
  id.startsWith("P3.") ? 3 : id.startsWith("GL.") ? 4 : id.startsWith("P4.") ? 5 :
  id.startsWith("ED-") ? 5 : id.startsWith("LC.") ? 6 : 9;
const nomeFase = ["P0", "P1", "P2", "P3", "GL", "P4/trilha", "LC"];
const onda = x => (x.id.startsWith("ED-") ? (x.ondaTexto || (x.subsecao || "").replace("Onda ", "")) : null);
const PENDENTES = new Set(["ED-68", "ED-102", "ED-103"]);
const out = { inexistentes: [], faseAnterior: [], ondaPosterior: [], dependeDePendente: [], ciclos: [],
  orfas: [], depTextual: [], depNoTextoForaDaColuna: [], ondaDivergente: [], semDependentes: [] };

const dependentes = {};
for (const x of t) for (const d of x.depIds) (dependentes[d] = dependentes[d] || []).push(x.id);

for (const x of t) {
  for (const d of x.depIds) {
    if (!porId[d]) { out.inexistentes.push(`${x.id} -> ${d}`); continue; }
    const fx = ordemFase(x.id), fd = ordemFase(d);
    if (fd > fx) out.faseAnterior.push(`${x.id} (${nomeFase[fx]}) -> ${d} (${nomeFase[fd]})`);
    // fase (P0-P3, GL) dependendo de ED
    if (!x.id.startsWith("ED-") && !x.id.startsWith("P4.") && !x.id.startsWith("LC.") && d.startsWith("ED-"))
      out.faseAnterior.push(`${x.id} (fase) -> ${d} (trilha)`);
    if (x.id.startsWith("ED-") && d.startsWith("ED-")) {
      const ox = onda(x), od = onda(porId[d]);
      if (ox && od && od > ox) out.ondaPosterior.push(`${x.id} (onda ${ox}) -> ${d} (onda ${od})`);
    }
    if (PENDENTES.has(d)) out.dependeDePendente.push(`${x.id} -> ${d}`);
  }
  if (x.depTexto && !/^(—|-)?$/.test(x.depTexto)) out.depTextual.push(`${x.id}: "${x.dep}"`);
  // "depende da P1.31" etc. no texto da tarefa/aceite
  const re = /depende[mr]?\s+(?:também\s+)?d[aoe]s?\s+((?:(?:P[0-4]\.\d+|GL\.\d+|LC\.\d+|ED-\d+)(?:,\s*|\s+e\s+(?:d[ao]s?\s+)?)?)+)/g;
  let m; const texto = x.tarefa + " " + x.aceite;
  while ((m = re.exec(texto))) {
    for (const d of m[1].match(/P[0-4]\.\d+|GL\.\d+|LC\.\d+|ED-\d+/g))
      if (d !== x.id && !x.depIds.includes(d)) out.depNoTextoForaDaColuna.push(`${x.id}: texto "depende ... ${d}" sem ${d} em Dep.`);
  }
  if (x.id.startsWith("ED-") && x.ondaTexto && x.subsecao && x.subsecao !== "Onda " + x.ondaTexto)
    out.ondaDivergente.push(`${x.id}: na tabela "${x.subsecao}", título diz onda ${x.ondaTexto}`);
  if (x.depIds.length === 0 && !(dependentes[x.id] || []).length) out.orfas.push(`${x.id} (${x.tarefa.slice(0, 70)})`);
}
for (const x of t) if (!(dependentes[x.id] || []).length) out.semDependentes.push(x.id);

// ciclos (DFS)
const cor = {}; const pilha = [];
function dfs(v) {
  cor[v] = 1; pilha.push(v);
  for (const d of (porId[v] ? porId[v].depIds : [])) {
    if (!porId[d]) continue;
    if (cor[d] === 1) out.ciclos.push(pilha.slice(pilha.indexOf(d)).concat(d).join(" -> "));
    else if (!cor[d]) dfs(d);
  }
  pilha.pop(); cor[v] = 2;
}
for (const x of t) if (!cor[x.id]) dfs(x.id);

for (const [k, v] of Object.entries(out)) {
  console.log(`\n## ${k} (${v.length})`);
  v.forEach(s => console.log("- " + s));
}
