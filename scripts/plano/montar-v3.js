// Monta o plano v3.0 a partir de docs/plano-v3 (estrutura, redação das tarefas e seções).
// Uso: node scripts/plano/montar-v3.js            (gera docs/plano-v3/PLANO-v3.0.md e relatório de checagem)
const fs = require('fs'), path = require('path');
const base = path.resolve(__dirname, '../../docs/plano-v3') + '/';
const rd = f => fs.readFileSync(base + f, 'utf8');
const E = JSON.parse(rd('fonte/estrutura-v3.json'));
const T = new Map();
for (const f of fs.readdirSync(base + 'redacao').filter(x => /^tarefas-.*\.json$/.test(x)))
  for (const t of JSON.parse(rd('redacao/' + f))) { if (T.has(t.id)) throw new Error('duplicada ' + t.id); T.set(t.id, t); }

// ---------- correções da montagem (registradas em CORRECOES da saída)
const LOG = [];
const fixText = (re, to, why) => { let n = 0; for (const t of T.values()) { const a = t.acceptance; t.acceptance = a.replace(re, () => (n++, to)); } LOG.push(why + ' (' + n + ' ocorrências)'); };
fixText(/ADR proposto d[ao] (?:decisão )?PD-04/g, 'ADR-0046 (proposto)', 'ADR do supervisório citado pelo número');
fixText(/`registry\.bill_name_roles`(?:,? com padrão `USER`|,? padrão `USER`)?/g, '`bill.holder_name_display` (padrão `USER_HOLDER_ONLY`)', 'parâmetro do nome na conta unificado no aprovado pelo dono');
const addDep = (id, ...deps) => { const t = T.get(id); for (const d of deps) if (!t.deps.includes(d)) t.deps.push(d); LOG.push('dependência declarada: ' + id + ' -> ' + deps.join(', ')); };
const append = (id, txt, why) => { const t = T.get(id); t.acceptance = t.acceptance.replace(/\s*$/, '') + ' ' + txt; LOG.push(why + ': ' + id); };
addDep('P3.39', 'P3.31'); addDep('P3.40', 'P2.28', 'P1.37');
addDep('ED-56', 'ED-17', 'ED-58'); addDep('ED-63', 'ED-17'); addDep('ED-91', 'ED-13');
addDep('ED-77', 'ED-96'); addDep('ED-64', 'ED-92');
for (const id of ['ED-69', 'ED-70', 'ED-71', 'ED-72', 'ED-73', 'ED-74']) addDep(id, 'ED-76');
append('ED-90', 'A leitura da frota da ED-59 (onda C) é opcional: sem ela, o consumo da frota entra por lançamento manual.', 'dependência posterior tornada opcional');
append('ED-17', 'O registro manual de paradas e o diário de operação de estações pertencem à ED-87; aqui fica a ingestão e o registro vindo do supervisório.', 'fronteira com a ED-87');
append('LC.2', 'Fecha a alínea "a" da VISAN (hospedagem e certificações do provedor), antes atribuída à ED-03.', 'alínea VISAN "a" com dona');
// ---------- versões Flyway alinhadas ao mapa do §3 da v3.0
const sub = (id, a, b) => { const t = T.get(id); if (!t.acceptance.includes(a)) { errs0.push("migration: trecho não encontrado em " + id + ": " + a); return; } t.acceptance = t.acceptance.split(a).join(b); LOG.push("migration alinhada ao §3: " + id); };
const errs0 = [];
sub("P0.47", "core `V9_7`", "core `V6_1`");
sub("ED-18", "core `V11_1`", "core `V11`");
sub("ED-39", "core `V11`", "core `V11_1`");
sub("ED-04", "core `V11` na parte que registra o modo de isolamento e o ambiente do tenant (ADR-0024)", "o registro do modo de isolamento e do ambiente do tenant no catálogo usa o core `V13` da LC.15 (ADR-0024)");
sub("P0.13", "em versão Flyway de platform reservada no §3", "na `V1_9` (platform)");
sub("P3.40", "na faixa reservada no §3", "na `V23_1`");
sub("P3.41", "com reserva no §3", "na `V15_2`");
sub("P1.41", "com reserva no §3", "na `V3_8`");
sub("ED-85", "com faixa Flyway própria reservada no §3", "na faixa `V22_x` (losses)");
sub("P1.33", "em migration com reserva no §3", "na `V1_10`");
sub("P3.34", "versão Flyway própria reservada no §3", "versão Flyway `V1_14`");
for (const [id, v] of [["P1.35","`V27_1`"],["P1.40","`V14_4`"],["P2.29","`V4_9`"],["P2.30","`V1_12`"],["P2.31","`V1_13` e migration repetível `R__` dos perfis"],["P2.32","`V4_10` e migration repetível `R__` do conteúdo mínimo"],["P2.33","`V4_11`"],["P2.34","`V4_12`"],["LC.15","core `V13`"]]) append(id, "Migration: " + v + " (mapa do §3).", "migration citada");
// ---------- reordenação de ondas (dependência antes da dependente)
const ED = E.phases.find(p => p.id === 'ED');
const grp = g => ED.waves.find(w => w.wave === 'B').groups.find(x => x.group === g);
const move = (arr, id, beforeId) => { arr.splice(arr.indexOf(id), 1); arr.splice(arr.indexOf(beforeId), 0, id); };
const B1 = grp('B1').tasks, B2 = grp('B2').tasks;
// ED-88 (fossa, SG-154 crítico na POC do SAMAE) e ED-92 (ocorrência de rede, base da ED-64) sobem para o B1
for (const id of ['ED-92', 'ED-88']) { B2.splice(B2.indexOf(id), 1); B1.splice(B1.indexOf('ED-64'), 0, id); }
LOG.push('ED-88 e ED-92 movidas para o grupo B1 (SG-154 crítico na POC; ED-64 depende da ED-92)');
move(B2, 'ED-76', 'ED-69'); LOG.push('ED-76 (curadoria) antes dos pacotes ED-69 a ED-74');
move(B2, 'ED-96', 'ED-77'); LOG.push('ED-96 antes da ED-77');

// ---------- ordem final e checagem
const order = [];
for (const p of E.phases) {
  if (p.id === 'ED') for (const w of p.waves) { if (w.groups) for (const g of w.groups) order.push(...g.tasks); else order.push(...w.tasks); }
  else order.push(...p.tasks);
}
const pos = new Map(order.map((id, i) => [id, i]));
const errs = [...errs0];
for (const id of order) { if (!T.has(id)) errs.push('sem redação: ' + id); }
for (const id of T.keys()) if (!pos.has(id)) errs.push('redigida mas fora da estrutura: ' + id);
for (const [id, t] of T) for (const d of t.deps) { if (!pos.has(d)) errs.push(id + ' depende de inexistente ' + d); else if (pos.get(d) > pos.get(id)) errs.push(id + ' depende de posterior ' + d); }
for (const t of T.values()) { if (/[|\n\r]/.test(t.acceptance)) errs.push('caractere proibido no aceite: ' + t.id); }
const map = JSON.parse(rd('fonte/mapa-de-donos.json'));
const owned = new Set([...T.values()].flatMap(t => t.findings_owned || []));
const semDono = map.filter(m => m.owner && /^(P\d|GL|LC|ED-)/.test(m.owner) && !owned.has(m.finding_id)).map(m => m.finding_id);
if (semDono.length) errs.push('achados sem tarefa dona na redação: ' + semDono.join(', '));

// ---------- seções
const sec = f => fs.existsSync(base + 'redacao/secoes/' + f) ? rd('redacao/secoes/' + f).replace(/\s*$/, '\n') : (errs.push('seção ausente: ' + f), '');
const intros = sec('04-intros.md').split('\n');
const introOf = re => { const i = intros.findIndex(l => re.test(l)); if (i < 0) { errs.push('intro ausente: ' + re); return ''; }
  let j = i + 1; while (j < intros.length && !/^#{2,4} /.test(intros[j])) j++; return intros.slice(i, j).join('\n').replace(/\s*$/, '\n'); };
const preface = intros.slice(0, intros.findIndex(l => /^### P0/.test(l))).join('\n').replace(/\s*$/, '\n');
const row = id => { const t = T.get(id); const c = s => String(s ?? '').replace(/\|/g, '/').replace(/\s+/g, ' ').trim();
  return '| ' + id + ' | ' + c(t.title) + ' | ' + c(t.origin) + ' | ' + c(t.size || 'a estimar') + ' | ' + (t.deps.length ? t.deps.join(', ') : '—') + ' | ' + c(t.acceptance) + ' |'; };
const table = ids => '\n| # | Tarefa | Origem | Tam. | Dep. | Aceite |\n|---|---|---|---|---|---|\n' + ids.map(row).join('\n') + '\n';
const P = id => E.phases.find(p => p.id === id).tasks;
let out = sec('00-cabecalho.md') + '\n' + sec('01-estado-atual.md') + '\n' + sec('02-decisoes.md') + '\n' + sec('03-mapa-flyway.md') + '\n' + preface + '\n';
for (const [re, id] of [[/^### P0/, 'P0'], [/^### P1/, 'P1'], [/^### P2/, 'P2'], [/^### P3/, 'P3'], [/^### Homologa/, 'GL'], [/^### P4/, 'P4']]) out += introOf(re) + table(P(id)) + '\n';
out += introOf(/^### Trilha/) + '\n';
const wA = ED.waves.find(w => w.wave === 'A'), wB = ED.waves.find(w => w.wave === 'B'), wC = ED.waves.find(w => w.wave === 'C');
out += introOf(/^#### Onda A/) + table(wA.tasks) + '\n' + introOf(/^#### Onda B/);
for (const g of wB.groups) out += '\n**Grupo ' + g.group + ': ' + g.title.replace(/^Onda B (original: )?/, '') + '**\n' + table(g.tasks);
out += '\n' + introOf(/^#### Onda C/) + table(wC.tasks) + '\n';
out += introOf(/^### Pacotes pendentes/) + table(P('PEND')) + '\n' + introOf(/^### Lan/) + table(P('LC')) + '\n';
out += sec('05-sequencia-e-marcos.md') + '\n' + sec('06-riscos.md') + '\n' + sec('07-resumo.md') + '\n' + sec('08-governanca.md');
fs.writeFileSync(base + 'PLANO-v3.0.md', out, 'utf8');
const rep = '# Checagem da montagem do plano v3.0\n\nTarefas: ' + order.length + '. Redigidas: ' + T.size + '.\n\n## Correções da montagem\n\n' + LOG.map(x => '- ' + x).join('\n') + '\n\n## Erros\n\n' + (errs.length ? errs.map(x => '- ' + x).join('\n') : 'Nenhum.') + '\n';
fs.writeFileSync(base + 'CHECAGEM-MONTAGEM.md', rep, 'utf8');
console.log('tarefas', order.length, 'erros', errs.length); if (errs.length) console.log(errs.slice(0, 40).join('\n'));
console.log('CR', out.split('\r').length - 1, 'FFFD', (out.match(/�/g) || []).length, 'linhas', out.split('\n').length);
