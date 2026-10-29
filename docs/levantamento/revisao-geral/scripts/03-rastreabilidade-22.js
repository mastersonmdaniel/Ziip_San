// Confronta o destino de cada achado em levantamento/lacunas/22-INCORPORACAO-NO-PLANO.md com as tarefas
// do plano que citam o achado (CAD-, OPE-, PLT-, GES-). Também conta em quantas tarefas cada achado aparece.
// Uso: node 03-rastreabilidade-22.js tarefas.json
"use strict";
const fs = require("fs"), path = require("path");
const t = require(path.resolve(process.argv[2]));
const doc = fs.readFileSync(path.join(__dirname, "..", "..", "lacunas", "22-INCORPORACAO-NO-PLANO.md"), "utf8");
const destino = {};
for (const l of doc.split("\n")) {
  const m = l.match(/^\| ((?:CAD|OPE|PLT|GES)-\d+) \|.*\| ([^|]+) \|\s*$/);
  if (m) destino[m[1]] = new Set(m[2].match(/P[0-4]\.\d+|GL\.\d+|LC\.\d+|ED-\d+/g) || []);
}
const noPlano = {};
for (const x of t) {
  const s = [x.tarefa, x.origem, x.aceite].join(" ");
  for (const a of new Set(s.match(/\b(?:CAD|OPE|PLT|GES)-\d+\b/g) || [])) (noPlano[a] = noPlano[a] || new Set()).add(x.id);
}
let semPlano = [], soNoPlano = [], soNoDoc = [];
for (const [a, ds] of Object.entries(destino)) {
  const p = noPlano[a] || new Set();
  if (!p.size) semPlano.push(a);
  const extra = [...p].filter(i => !ds.has(i)), falta = [...ds].filter(i => !p.has(i));
  if (extra.length) soNoPlano.push(`${a}: no plano e não no 22: ${extra.join(", ")}`);
  if (falta.length) soNoDoc.push(`${a}: no 22 e não no plano: ${falta.join(", ")}`);
}
const naoNoDoc = Object.keys(noPlano).filter(a => !destino[a]);
console.log("achados no 22:", Object.keys(destino).length, "| citados no plano:", Object.keys(noPlano).length);
console.log("\n## achados do 22 sem nenhuma tarefa no plano (" + semPlano.length + ")\n" + semPlano.join(", "));
console.log("\n## citados no plano e ausentes do 22 (" + naoNoDoc.length + ")\n" + naoNoDoc.join(", "));
console.log("\n## destino divergente: tarefa no plano não listada no 22 (" + soNoPlano.length + ")\n" + soNoPlano.join("\n"));
console.log("\n## destino divergente: tarefa listada no 22 sem citação no plano (" + soNoDoc.length + ")\n" + soNoDoc.join("\n"));
const dist = {};
for (const s of Object.values(noPlano)) dist[s.size] = (dist[s.size] || 0) + 1;
console.log("\n## distribuição: nº de tarefas por achado", JSON.stringify(dist));
