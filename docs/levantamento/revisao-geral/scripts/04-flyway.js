// Confronta o §3 (mapa Flyway) com as tarefas.
// Uso: node 04-flyway.js tarefas.json
// 1) versões repetidas nas tabelas do §3; 2) reserva cuja tarefa não cita a versão; 3) tarefa que cita
// versão sem reserva ou com reserva atribuída a outra tarefa; 4) tarefas que citam tabelas ou migration
// sem versão (lista para leitura manual).
"use strict";
const fs = require("fs"), path = require("path");
const t = require(path.resolve(process.argv[2]));
const porId = Object.fromEntries(t.map(x => [x.id, x]));
const plano = fs.readFileSync(path.join(__dirname, "..", "..", "..", "PLANO-EXECUCAO-PDCA-R0-R9.md"), "utf8").split("\n");
const ini = plano.findIndex(l => l.startsWith("## 3. Mapa de versões Flyway"));
const fim = plano.findIndex((l, i) => i > ini && l.startsWith("## 4."));
const reservas = []; // {versao, core, tarefas, linha, tabela}
let tabela = "";
for (let i = ini; i < fim; i++) {
  const l = plano[i];
  if (/^\*\*/.test(l)) tabela = l.replace(/\*/g, "").slice(0, 40);
  if (!l.startsWith("| ") || /^\| (Versão|Faixa) /.test(l)) continue;
  const c = l.split(" | ");
  const versaoCel = c[0].replace(/^\| /, "");
  const ultima = c[c.length - 1].replace(/ \|$/, "");
  const core = /core/.test(versaoCel);
  const vers = versaoCel.match(/V\d+(?:_\d+|_x)?/g) || [];
  const tarefas = [];
  const re = /\b(P[0-4]|GL|LC)\.(\d+)(?:[–-](?:P[0-4]\.)?(\d+))?|\bED-(\d+)(?: a ED-(\d+))?/g;
  let m;
  while ((m = re.exec(ultima))) {
    if (m[1]) { const a = +m[2], b = m[3] ? +m[3] : a; for (let k = a; k <= b; k++) tarefas.push(`${m[1]}.${k}`); }
    else { const a = +m[4], b = m[5] ? +m[5] : a; for (let k = a; k <= b; k++) tarefas.push(`ED-${String(k).padStart(2, "0")}`); }
  }
  for (const v of vers) reservas.push({ versao: (core ? "core " : "") + v, tarefas, linha: i + 1, tabela });
}
// 1) repetidas
const cont = {};
for (const r of reservas) (cont[r.versao] = cont[r.versao] || []).push(r.linha + " (" + r.tabela + ")");
console.log("## versões repetidas no §3");
for (const [v, ls] of Object.entries(cont)) if (ls.length > 1) console.log(`- ${v}: linhas ${ls.join("; ")}`);
// 2) reserva cuja tarefa não cita a versão
console.log("\n## reserva atribuída a tarefa que não cita a versão");
for (const r of reservas) {
  if (/_x$/.test(r.versao)) continue;
  const v = r.versao.replace("core ", "");
  for (const id of r.tarefas) {
    const x = porId[id];
    if (!x) { console.log(`- ${r.versao} -> ${id}: tarefa inexistente`); continue; }
    const s = [x.tarefa, x.dep, x.aceite].join(" ");
    if (!new RegExp("`" + v + "`").test(s)) console.log(`- ${r.versao} (linha ${r.linha}) -> ${id}: a linha da tarefa não cita \`${v}\``);
  }
}
// 3) tarefa cita versão sem reserva
console.log("\n## tarefa que cita versão sem reserva correspondente para ela");
const reservaPorVersao = {};
for (const r of reservas) (reservaPorVersao[r.versao] = reservaPorVersao[r.versao] || new Set()).add(...r.tarefas.length ? r.tarefas : ["?"]);
for (const r of reservas) for (const id of r.tarefas) reservaPorVersao[r.versao].add(id);
for (const x of t) {
  const s = [x.tarefa, x.dep, x.aceite].join(" ");
  const re = /`(V\d+(?:_\d+)?)`/g; let m;
  const vistos = new Set();
  while ((m = re.exec(s))) {
    m[2] = m[1];
    const antes = s.slice(Math.max(0, m.index - 40), m.index);
    const ehCore = /core\s*$/.test(antes) || /core `V[\d_]+`(?:, | e | a )$/.test(antes);
    const v = (ehCore ? "core " : "") + m[2];
    if (vistos.has(v)) continue; vistos.add(v);
    if (/^V1\d\d$/.test(m[2])) continue; // sequência global
    const res = reservaPorVersao[v];
    if (!res) console.log(`- ${x.id}: cita \`${v}\`, sem reserva no §3`);
    else if (res.has("?") && res.size === 1) console.log(`- ${x.id}: cita \`${v}\`; reserva do "mapa da v1" sem tarefa nomeada (só a fase)`);
    else if (!res.has(x.id)) console.log(`- ${x.id}: cita \`${v}\`, reservada no §3 para ${[...res].join(", ")}`);
  }
}
// 4) tarefas novas (v2.13+) que criam entidade persistente sem versão citada
console.log("\n## tarefas que mencionam tabela, cadastro ou registro persistente sem citar versão Flyway (leitura manual)");
for (const x of t) {
  const s = [x.tarefa, x.aceite].join(" ");
  if (/`V\d/.test(s)) continue;
  if (/\b(tabela|cadastro|catálogo|registro de|entidade|schema|migration)\b/i.test(x.tarefa + " " + x.aceite.slice(0, 400)))
    console.log(`- ${x.id}: ${x.tarefa.slice(0, 90)}`);
}
