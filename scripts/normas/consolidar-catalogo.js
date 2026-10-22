// Consolida docs/normas/partes/* em docs/normas/catalogo-normas.json e catalogo/*.md. Reexecutável: node scripts/normas/consolidar-catalogo.js
const fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'../../docs/normas').split(path.sep).join('/')+'/';
const rd=f=>JSON.parse(fs.readFileSync(root+f,'utf8'));
const ex=f=>fs.existsSync(root+f);
const TODAY='2026-10-22';

// ---------- carga
const parts=[
  ['04','partes/04-eris.json'],
  ['02','partes/02-federal-e-setor.json'],
  ['03','partes/03-tecnicas-abnt-iso.json'],
  ['05A','partes/05-verificadas-A.json'],['05B','partes/05-verificadas-B.json'],['05C','partes/05-verificadas-C.json'],['05D','partes/05-verificadas-D.json'],
  ['06A','partes/06-reverificadas-A.json'],['06B','partes/06-reverificadas-B.json'],
  ['01','partes/01-citadas-no-projeto.json']
];
const aliases=ex('partes/entrada/aliases.json')?rd('partes/entrada/aliases.json'):{};
const used=[];const byId=new Map();const log={merged:0,aliasHits:0,keyHits:0,only01:0};
const numNorm=n=>String(n||'').replace(/\D/g,'').replace(/^0+/,'');
const yearNorm=y=>String(y||'').replace(/\D/g,'').slice(0,4);
const TYPEW=new Set(['res','resolucao','del','deliberacao','rn','portaria','in','lei','decreto','nt','ato','rf','instrucao','norma','ordem','resolucao-homologatoria']);
const agencyOf=id=>{const t=id.split('-');const r=[];for(const w of t){if(TYPEW.has(w)||/^d/.test(w))break;r.push(w);}return r.join('-')||t[0];};
const first=agencyOf;
const keyOf=x=>numNorm(x.number)+'|'+yearNorm(x.year||x.edition_year);
const byKey=new Map();
const related=(a,b)=>a===b||a.startsWith(b+'-')||b.startsWith(a+'-');
const curated=new Set(['04','02','03','05A','05B','05C']);
// partes 06 são reverificações: seus campos preenchidos SUBSTITUEM os anteriores
function overrideInto(base,add,src){
  for(const k of ['kind','number','year','edition_year','issuer','title_pt','title_en','scope_summary','norm_class','certifiable','applicability','jurisdiction','status','status_note','verified','url']){
    if(add[k]!==''&&add[k]!=null)base[k]=add[k];
  }
  for(const k of ['topics','modules_affected']) if(add[k]&&add[k].length)base[k]=add[k];
  if(add.notes)base.notes=[base.notes,add.notes].filter(Boolean).join(' | ');
  if(!base.sources.includes(src))base.sources.push(src);
}

function norm(x,src){
  const o={
    id:x.id,kind:x.kind,number:x.number??'',year:x.year??'',edition_year:x.edition_year??'',issuer:x.issuer??'',
    title_pt:x.title_pt??'',title_en:x.title_en??'',scope_summary:x.scope_summary??'',
    norm_class:x.norm_class??'',certifiable:x.certifiable??'',applicability:x.applicability??'',jurisdiction:x.jurisdiction??'',
    status:x.status??'',status_note:x.status_note??'',verified:x.verified??'',url:x.url??'',
    topics:x.topics??[],modules_affected:x.modules_affected??[],
    cited_in:x.cited_in??[],cited_count:x.cited_count??null,related_tasks:x.related_tasks??[],status_in_project:x.status_in_project??'',
    notes:x.notes??'',sources:[src],aliases:[],needs_review:false,classification_inferred:false
  };
  return o;
}
function mergeInto(base,add,src){
  for(const k of ['kind','number','year','edition_year','issuer','title_pt','title_en','scope_summary','norm_class','certifiable','applicability','jurisdiction','status','status_note','verified','url']){
    if((base[k]===''||base[k]==null)&&add[k]!==''&&add[k]!=null)base[k]=add[k];
  }
  for(const k of ['topics','modules_affected','related_tasks']) base[k]=[...new Set([...(base[k]||[]),...(add[k]||[])])];
  base.cited_in=[...new Set([...(base.cited_in||[]),...(add.cited_in||[])])].slice(0,12);
  if(add.cited_count&&!base.cited_count)base.cited_count=add.cited_count;
  if(!base.status_in_project&&add.status_in_project)base.status_in_project=add.status_in_project;
  if(add.notes&&add.notes!==base.notes)base.notes=[base.notes,add.notes].filter(Boolean).join(' | ');
  if(!base.sources.includes(src))base.sources.push(src);
  if(add.id!==base.id&&!base.aliases.includes(add.id))base.aliases.push(add.id);
}
for(const [src,f] of parts){
  if(!ex(f)){console.log('ausente:',f);continue;}
  used.push(f);const arr=rd(f);
  for(const x of arr){
    const o=norm(x,src);
    if(src.startsWith('06')){
      if(byId.has(o.id)){overrideInto(byId.get(o.id),o,src);log.reverified=(log.reverified||0)+1;}
      else console.log('reverificada sem correspondente:',o.id);
      continue;
    }
    if(src==='01'){
      let tgt=null;
      if(byId.has(o.id))tgt=byId.get(o.id);
      else if(aliases[o.id]&&byId.has(aliases[o.id])){tgt=byId.get(aliases[o.id]);log.aliasHits++;}
      else if(o.number&&byKey.has(keyOf(o))){const c2=byKey.get(keyOf(o)).filter(t=>related(agencyOf(t.id),agencyOf(o.id)));if(c2.length===1){tgt=c2[0];log.keyHits++;(log.pairs=log.pairs||[]).push(o.id+" => "+tgt.id);}}
      if(tgt){mergeInto(tgt,o,src);log.merged++;continue;}
      // só citada
      o.needs_review=true;o.verified='NOT_CONFIRMED';o.status='A_CONFIRMAR';o.classification_inferred=true;
      const k=o.kind;
      o.norm_class=(['ABNT_NBR','ISO','ISO_IEC','IEC','OGC','OTHER'].includes(k))?'REFERENCE':'REGULATORY';
      byId.set(o.id,o);log.only01++;continue;
    }
    if(byId.has(o.id)){mergeInto(byId.get(o.id),o,src);log.merged++;continue;}
    byId.set(o.id,o);
    if(o.number){const k=keyOf(o);(byKey.get(k)||byKey.set(k,[]).get(k)).push(o);}
  }
}
const items=[...byId.values()];
for(const o of items){
  // itens que não são norma em vigor (projeto de lei, relatório, minuta, quadro de síntese)
  if(o.status==='A_CONFIRMAR'&&/^N[ÃA]O [ÉE] NORMA/i.test(o.status_note||''))o.status='NAO_E_NORMA_EM_VIGOR';
  if(!o.verified)o.verified='NOT_CONFIRMED';
  if(o.verified==='NOT_CONFIRMED'||o.status==='A_CONFIRMAR')o.needs_review=true;
  if(!o.status)o.status='A_CONFIRMAR';
}

// ---------- área de exibição
const UF=new Set('AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO'.split(' '));
function area(o){
  const k=o.kind;
  if(['LAW','COMPLEMENTARY_LAW','CONSTITUTIONAL_AMENDMENT','DECREE'].includes(k))return ['01','Legislação federal: leis, leis complementares e decretos'];
  if(['ANA_NR','ANA_RESOLUTION'].includes(k)||/^ana-/.test(o.id))return ['02','Agência Nacional de Águas e Saneamento Básico (ANA): normas de referência e resoluções'];
  if(['MS_ORDINANCE','CONAMA'].includes(k)||/^(cnrh|conama|portaria-gm|mcid|mdr)/.test(o.id))return ['03','Saúde, meio ambiente, recursos hídricos e política do setor'];
  if(k==='INMETRO')return ['04','Metrologia legal (Inmetro)'];
  if(k==='MTE_NR')return ['05','Segurança e saúde no trabalho (MTE)'];
  if(['BCB','CMN','ANPD','TAX_AUTHORITY'].includes(k))return ['06','Dados pessoais, pagamentos e tributos'];
  if(k==='COURT')return ['07','Jurisprudência e súmulas'];
  if(['AGENCY_NORM','STATE_LAW','MUNICIPAL_LAW'].includes(k)){
    const j=String(o.jurisdiction||'').toUpperCase();const uf=(j.match(/^[A-Z]{2}/)||[''])[0];
    if(uf==='SC')return ['08','Entidades reguladoras e normas estaduais: Santa Catarina'];
    if(['SP','MG','RJ','ES'].includes(uf)||/PCJ/.test(j))return ['09','Entidades reguladoras e normas estaduais: Sudeste'];
    if(['RS','PR'].includes(uf))return ['10','Entidades reguladoras e normas estaduais: Sul (RS e PR)'];
    if(['AL','BA','CE','MA','PB','PE','PI','RN','SE'].includes(uf))return ['11','Entidades reguladoras e normas estaduais: Nordeste'];
    return ['12','Entidades reguladoras e normas estaduais: Norte, Centro-Oeste e outras'];
  }
  if(k==='ABNT_NBR')return ['13','Normas técnicas da ABNT (NBR)'];
  if(['ISO','ISO_IEC','IEC'].includes(k))return ['14','Normas ISO, ISO/IEC e IEC'];
  return ['15','Padrões técnicos, interoperabilidade e outros'];
}
for(const o of items){const [a,n]=area(o);o.area=a;o.area_name=n;}
items.sort((a,b)=>a.area.localeCompare(b.area)||a.id.localeCompare(b.id));

// ---------- saída
const count=(arr,f)=>arr.reduce((m,x)=>{const k=f(x)||'(vazio)';m[k]=(m[k]||0)+1;return m;},{});
const counts={total:items.length,by_area:count(items,o=>o.area+' '+o.area_name),by_kind:count(items,o=>o.kind),by_status:count(items,o=>o.status),by_verified:count(items,o=>o.verified),by_class:count(items,o=>o.norm_class),needs_review:items.filter(o=>o.needs_review).length};
const out={schema_version:1,generated_on:TODAY,note:'Catálogo consolidado das partes em docs/normas/partes. Metadados e resumos em palavras próprias; o texto das normas não é reproduzido.',source_parts:used,counts,items};
fs.writeFileSync(root+'catalogo-normas.json',JSON.stringify(out,null,1)+'\n','utf8');

const esc=s=>String(s??'').replace(/\|/g,'/').replace(/\s+/g,' ').trim();
const cut=(s,n)=>{s=esc(s);return s.length<=n?s:s.slice(0,n-1).replace(/\s+\S*$/,'')+'…';};
fs.mkdirSync(root+'catalogo',{recursive:true});
for(const f of fs.readdirSync(root+'catalogo'))fs.unlinkSync(root+'catalogo/'+f);
const areas=[...new Set(items.map(o=>o.area))];
const slug=s=>s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'').slice(0,50);
const index=[];
for(const a of areas){
  const arr=items.filter(o=>o.area===a);const name=arr[0].area_name;const file=a+'-'+slug(name)+'.md';
  let md='# '+name+'\n\nCatálogo de normas e legislação do Ziip SAN, área '+a+'. Gerado em '+TODAY+' a partir de `docs/normas/partes`; fonte de verdade em `docs/normas/catalogo-normas.json`. Verificação: **OF** = fonte oficial, **SEC** = fonte secundária, **NC** = não confirmada (precisa de revisão). Situação: VIGENTE, CONFIRMADA, EM_REVISAO, SUBSTITUIDA, CANCELADA, REVOGADA, PARCIALMENTE_REVOGADA, VIGENCIA_FUTURA ou A_CONFIRMAR.\n\n'+arr.length+' normas.\n\n';
  md+='| ID | Norma | Título | Situação | Verif. | Classe | Módulos do produto |\n|---|---|---|---|---|---|---|\n';
  for(const o of arr){
    const ver=o.verified==='OFFICIAL_SOURCE'?'OF':o.verified==='SECONDARY_SOURCE'?'SEC':'NC';
    const nm=esc([o.number,(o.year||o.edition_year)].filter(Boolean).join('/'));
    const st=o.status+(o.status_note&&/futur|revog|substitu|cancel|vigor|revis/i.test(o.status_note)?' ('+cut(o.status_note,70)+')':'');
    md+='| `'+o.id+'` | '+(o.issuer?cut(o.issuer.split(' - ')[0].split(' (')[0],24)+' ':'')+nm+' | '+cut(o.title_pt,120)+' | '+st+' | '+ver+' | '+esc(o.norm_class)+' | '+esc((o.modules_affected||[]).join(', '))+' |\n';
  }
  fs.writeFileSync(root+'catalogo/'+file,md,'utf8');
  index.push([a,name,file,arr.length,arr.filter(o=>o.needs_review).length]);
}
let readme='# Normas e legislação do Ziip SAN\n\nBiblioteca de normas e legislação do setor de saneamento e das áreas que o produto toca. É a base do item "Normas e legislação" do módulo Certificações de qualidade (ADR-0041) e a fonte da carga do catálogo global de normas (core `V9_8`, tarefa P1.34 do plano).\n\n'+
'Gerada em '+TODAY+'. Contém metadados e resumos em palavras próprias; o texto das normas não é reproduzido, e as pagas (ABNT, ISO) entram só por título, edição e situação.\n\n'+
'## Conteúdo\n\n- `catalogo-normas.json`: catálogo consolidado, uma norma por item, com origem, situação, nível de verificação, módulos afetados, tarefas do plano e documentos do projeto que a citam.\n- `catalogo/`: leitura por área.\n- `partes/`: levantamentos que originaram o catálogo (01 inventário do projeto, 02 legislação federal e do setor, 03 normas técnicas, 04 entidades reguladoras, 05 verificações) e as pendências de cada um.\n- `pendencias-consolidadas.md`: o que ainda precisa de confirmação.\n\n'+
'## Totais\n\n- **Normas:** '+counts.total+'.\n- **Por verificação:** '+Object.entries(counts.by_verified).map(e=>e.join(' ')).join(', ')+'.\n- **Por situação:** '+Object.entries(counts.by_status).map(e=>e.join(' ')).join(', ')+'.\n- **Precisam de revisão:** '+counts.needs_review+'.\n\n'+
'| Área | Arquivo | Normas | A revisar |\n|---|---|---|---|\n'+index.map(i=>'| '+i[0]+' '+i[1]+' | [`'+i[2]+'`](catalogo/'+i[2]+') | '+i[3]+' | '+i[4]+' |').join('\n')+'\n\n'+
'## Regras\n\n1. **Classes:** `REGULATORY` (obrigatória por aplicabilidade, não desativável), `VOLUNTARY` (ativável pelo tenant), `REFERENCE` (referência técnica) e `EXPORT_PROFILE` (perfil de exportação de indicadores), conforme o ADR-0041.\n2. **Verificação:** `OFFICIAL_SOURCE` quando a norma foi vista em fonte oficial; `SECONDARY_SOURCE` quando só em catálogos e portais de terceiros (caso comum das NBR); `NOT_CONFIRMED` quando só o projeto a cita. Nenhuma norma é descartada por falta de confirmação: ela entra marcada para revisão.\n3. **Situação:** a data da conferência é 22/10/2026; norma com vigência futura traz a data no campo de situação.\n4. **Sem dado inventado:** número, ano, título e situação vêm de fonte consultada; o que não foi localizado fica em A_CONFIRMAR, com o motivo em `notes`.\n5. **Atualização:** o catálogo é regenerado por `scripts/normas/consolidar-catalogo.js` (`node scripts/normas/consolidar-catalogo.js`) a partir de `partes/`; revisões entram como nova parte, sem apagar as anteriores.\n6. **Direitos autorais:** só metadados e resumos em palavras próprias.\n';
fs.writeFileSync(root+'README.md',readme,'utf8');

// pendências
const rev=items.filter(o=>o.needs_review);
let pd='# Pendências consolidadas do catálogo de normas\n\nGerado em '+TODAY+'. Itens com `needs_review`: '+rev.length+'.\n\n'+
'Pendências detalhadas de cada levantamento: `partes/02-pendencias.md`, `partes/03-pendencias.md`, `partes/04-pendencias.md` e `partes/04-mapa-de-eris.md`.\n\n## Por área\n\n';
for(const a of areas){const r=rev.filter(o=>o.area===a);if(!r.length)continue;pd+='### '+a+' '+r[0].area_name+' ('+r.length+')\n\n';for(const o of r.slice(0,400))pd+='- `'+o.id+'`: '+cut((o.title_pt||o.scope_summary||'')+(o.notes?' — '+o.notes:''),180)+'\n';pd+='\n';}
fs.writeFileSync(root+'pendencias-consolidadas.md',pd,'utf8');

if(process.env.PAIRS)console.log((log.pairs||[]).join(String.fromCharCode(10)));else console.log(JSON.stringify({log:{merged:log.merged,aliasHits:log.aliasHits,keyHits:log.keyHits,only01:log.only01},total:counts.total,needs_review:counts.needs_review}));
for(const f of ['catalogo-normas.json','README.md','pendencias-consolidadas.md',...fs.readdirSync(root+'catalogo').map(x=>'catalogo/'+x)]){const t=fs.readFileSync(root+f,'utf8');if(t.includes('\r')||t.includes('\uFFFD'))console.log('PROBLEMA em',f);}
