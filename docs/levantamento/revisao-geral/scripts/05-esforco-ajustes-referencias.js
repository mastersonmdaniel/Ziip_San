// Análises complementares sobre tarefas.json (saída do 01-extrair-tarefas.js).
// Uso: node 05-esforco-ajustes-referencias.js tarefas.json
// 1) esforço por onda (tabela física × onda do título) e tarefas "a estimar";
// 2) ajustes por versão, ajustes v2.18 que são só lista de títulos, tarefas estimadas que receberam ajuste >= v2.14;
// 3) achados 22 (CAD/OPE/PLT/GES) citados em mais de uma tarefa;
// 4) tarefas de fase (P0-P3, GL) que citam no texto tarefa de fase posterior ou entrega ED;
// 5) tarefa citada como base no texto de outra sem estar no fecho transitivo de dependências;
// 6) fecho de dependências do GL.4 e tarefas desse fecho ausentes do §5.
"use strict";
const fs = require("fs"), path = require("path");
const t = require(path.resolve(process.argv[2]));
const P = Object.fromEntries(t.map(x => [x.id, x]));
const plano = fs.readFileSync(path.join(__dirname, "..", "..", "..", "PLANO-EXECUCAO-PDCA-R0-R9.md"), "utf8").split("\n");
const fase = id => id.startsWith("P0.") ? 0 : id.startsWith("P1.") ? 1 : id.startsWith("P2.") ? 2 :
  id.startsWith("P3.") ? 3 : id.startsWith("GL.") ? 4 : (id.startsWith("P4.") || id.startsWith("ED-")) ? 5 : 6;
const num = x => { const m = x.tam.match(/\(([\d,]+)/); return m ? parseFloat(m[1].replace(",", ".")) : null; };
const h = s => console.log("\n## " + s);

h("1. Esforço por onda (onda do título prevalece sobre a tabela)");
const ondas = {};
for (const x of t.filter(x => x.id.startsWith("ED-"))) {
  const tab = (x.subsecao || "").replace("Onda ", ""), o = x.ondaTexto || tab;
  const r = ondas[o] = ondas[o] || { soma: 0, est: 0, ae: [] };
  const v = num(x); if (v != null) { r.soma += v; r.est++; } else r.ae.push(x.id);
}
for (const [o, r] of Object.entries(ondas)) console.log(`onda ${o}: ${r.est} estimadas, soma ${r.soma.toFixed(1)}; a estimar ${r.ae.length}: ${r.ae.join(", ")}`);
console.log("fases com 'a estimar':", t.filter(x => !x.id.startsWith("ED-") && /a estimar/.test(x.tam)).map(x => x.id).join(", "));

h("2. Ajustes");
const porV = {}; t.forEach(x => x.ajustes.forEach(v => porV[v] = (porV[v] || 0) + 1));
console.log("marcadores por versão:", JSON.stringify(porV));
const txt = plano.join("\n");
console.log("v2.18 só com lista de títulos:", (txt.match(/Ajuste v2\.18:\*\* levantamentos 22a a 22d:/g) || []).length,
  "de", (txt.match(/Ajuste v2\.18/g) || []).length);
let n = 0, s = 0; const lst = [];
for (const x of t) if (x.id.startsWith("ED-") && num(x) != null && x.ajustes.some(v => v >= "v2.14")) { n++; s += num(x); lst.push(x.id); }
console.log(`ED estimadas com ajuste >= v2.14 sem reestimativa: ${n}, ${s.toFixed(1)} semanas-pessoa: ${lst.join(", ")}`);
console.log("linhas com 7 células (coluna extra fora do cabeçalho):", t.filter(x => x.nCelulas > 6).map(x => x.id).join(", "));

h("3. Achados 22 em mais de uma tarefa");
const ach = {};
for (const x of t) for (const a of new Set([x.tarefa, x.origem, x.aceite].join(" ").match(/\b(?:CAD|OPE|PLT|GES)-\d+\b/g) || [])) (ach[a] = ach[a] || []).push(x.id);
const multi = Object.entries(ach).filter(([, v]) => v.length > 1);
console.log(`${multi.length} de ${Object.keys(ach).length}; com 4 ou mais tarefas:`);
multi.filter(([, v]) => v.length >= 4).forEach(([a, v]) => console.log(`- ${a}: ${v.join(", ")}`));

h("4. Tarefas de fase que citam tarefa posterior ou ED no texto");
for (const x of t) {
  if (fase(x.id) > 4) continue;
  const texto = x.tarefa + " " + x.aceite; const re = /\b(P[0-4]\.\d+|GL\.\d+|ED-\d+)\b/g; let m;
  while ((m = re.exec(texto))) if (fase(m[1]) > fase(x.id) && !x.depIds.includes(m[1]))
    console.log(`- ${x.id} -> ${m[1]}: …${texto.slice(Math.max(0, m.index - 70), m.index + 20).replace(/\s+/g, " ")}…`);
}

h("5. Citada como base sem estar no fecho transitivo de dependências");
const memo = {};
const fecho = id => { if (memo[id]) return memo[id]; const r = new Set(); memo[id] = r;
  for (const d of (P[id] ? P[id].depIds : [])) { r.add(d); for (const e of fecho(d)) r.add(e); } return r; };
for (const k of ["P1.31", "P3.30", "P1.32", "P2.25", "ED-36", "ED-56", "ED-85", "ED-92", "ED-58"]) {
  const re = new RegExp("(^|[^\\w.])" + k.replace(".", "\\.") + "(?!\\d)");
  const falta = t.filter(x => x.id !== k && re.test(x.tarefa + " " + x.aceite) && !fecho(x.id).has(k) && fase(k) <= fase(x.id)).map(x => x.id);
  console.log(`- ${k}: ${falta.join(", ") || "(nenhuma)"}`);
}

h("6. Fecho do GL.4 e §5");
const i5 = plano.findIndex(l => l.startsWith("## 5.")), f5 = plano.findIndex(l => l.startsWith("## 6."));
const sec5 = plano.slice(i5, f5).join(" ");
const g = fecho("GL.4");
const fora = [...g].filter(i => P[i] && !new RegExp("(^|[^\\w.])" + i.replace(".", "\\.") + "(?!\\d)").test(sec5)).sort();
console.log(`fecho do GL.4: ${g.size} tarefas; ausentes do §5: ${fora.join(", ")}`);
