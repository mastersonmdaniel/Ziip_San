// Extrai as tarefas do plano canônico para JSON estruturado (base da reescrita v3.0).
// Uso: node scripts/plano/extrair-tarefas.js docs/PLANO-EXECUCAO-PDCA-R0-R9.md docs/plano-v3/fonte/tarefas-v2.21.json
const fs = require('fs');
const [, , src, out] = process.argv;
const lines = fs.readFileSync(src, 'utf8').split('\n');
const ID = /^(P\d+\.\d+|GL\.\d+|LC\.\d+|ED-\d+)$/;
let section = '', wave = '';
const tasks = [];
lines.forEach((l, i) => {
  if (/^#{2,4} /.test(l)) { section = l.replace(/^#+\s*/, '').trim(); wave = ''; }
  const w = l.match(/^\*\*Onda ([ABC])\*\*/); if (w) wave = w[1];
  if (/^\*\*Fora da trilha/.test(l)) wave = 'fora';
  if (!l.startsWith('| ')) return;
  const cells = l.split('|').slice(1);
  if (cells.length && cells[cells.length - 1].trim() === '') cells.pop();
  const c = cells.map(x => x.trim());
  if (!ID.test(c[0])) return;
  let acceptance = c.slice(5).join(' | ');
  const extraCells = Math.max(0, c.length - 6);
  // separa ajustes
  const parts = acceptance.split(/\s(?=\*\*Ajuste v2\.\d+)/);
  const base = parts.shift();
  const adjustments = parts.map(p => {
    const m = p.match(/^\*\*Ajuste (v2\.\d+)[^*]*\*\*:?\s*/);
    return { version: m ? m[1] : '?', text: p.replace(/^\*\*Ajuste v2\.\d+[^*]*\*\*:?\s*/, '').trim() };
  });
  tasks.push({ id: c[0], line: i + 1, section, wave, title: c[1], origin: c[2], size: c[3], deps: c[4],
    acceptance: base, adjustments, extra_cells: extraCells });
});
fs.mkdirSync(require('path').dirname(out), { recursive: true });
fs.writeFileSync(out, JSON.stringify(tasks, null, 1) + '\n', 'utf8');
const bySec = {}; for (const t of tasks) { const k = t.section.slice(0, 40) + (t.wave ? ' / onda ' + t.wave : ''); bySec[k] = (bySec[k] || 0) + 1; }
console.log('tarefas:', tasks.length, '| com coluna extra:', tasks.filter(t => t.extra_cells).length, '| ajustes:', tasks.reduce((a, t) => a + t.adjustments.length, 0));
console.log(bySec);
const dup = tasks.map(t => t.id).filter((x, i, a) => a.indexOf(x) !== i); console.log('ids repetidos:', [...new Set(dup)]);
