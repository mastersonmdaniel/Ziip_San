# ADR-0001 — Roadmap P0–P4 com marco de go-live piloto

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` §5 e §6 (roadmap R1–R9); revisão 17 §7 e §8.2
- **Ajuste de 12/10/2026 (editais), antes de qualquer implementação:** o go-live piloto e as fases P0–P3 não mudam; o
  P4 ganha a trilha de editais, mirando licitações de 2028, e a prontidão de POC passa a ser critério de aceite a
  partir do P3 (ADR-0023); a instância dedicada sai de 2028 para o P4, sob demanda (ADR-0024); o app de leitura
  offline do P4 deixa de ser PWA e passa a app Android com Capacitor (ADR-0026); o BI do P4 passa a híbrido, com cerca
  de 20 relatórios operacionais já no P3 (ADR-0027); qualidade da água, GIS web, edição, migração do cadastro técnico,
  manobras e SPC/Serasa saem de 2028 para o P4, e o restante do R9 e a trilha GRS seguem em 2028 (ADR-0028); decisão
  do dono do produto.
- **Ajuste de 12/10/2026 (editais, v2.5), antes de qualquer implementação:** a trilha de editais do P4 ganha equipe
  própria contratada a partir de agosto de 2027, com prontidão de POC em 30/06/2028 e licitações no 2º semestre de 2028
  (ADR-0029); o app de leitura do P4 segue os ADRs 0030 (autenticação) e 0031 (cálculo offline e nota fiscal em
  campo); compras, RH e orçamento do ERP passam a 2029, sob demanda (ADR-0028, ajuste v2.5); o FEBRABAN 150 legado
  pode subir para o P3 se o piloto tiver optantes de débito automático (ADR-0028, ajuste v2.5); decisão do dono do
  produto.
- **Ajuste de 12/10/2026 (v2.7), antes de qualquer implementação:** o marco "Go-live piloto" (jun–jul/2027) passa a
  "Homologação local do piloto": dois ou três ciclos de faturamento paralelo no ambiente local, com dados sintéticos ou
  anonimizados do cliente comparados com o sistema dele, e ensaio de POC, sem dado pessoal real, corte do legado nem
  emissão fiscal real; o P0 deixa de criar homologação e produção na nuvem, backup e PITR no RDS e o R1 sobre o KMS,
  que passam a versões locais atrás de interfaces; entra depois do P4 a fase final "Lançamento comercial", com a
  infraestrutura na AWS (ADR-0011), o pentest, a carga e o paralelo reais, o corte do legado e o primeiro atestado, com
  data a escolher pelo dono do produto (plano §2.4); o marco "primeiro cliente em produção" do Contexto passa a ser o
  corte no lançamento; as tarefas derivadas "critério de aceite do go-live" e "plano de faturamento paralelo e de corte
  do legado" leem-se para a homologação local e para o lançamento; decisão do dono do produto (ADR-0033).
- **Ajuste de 12/10/2026 (v2.8), antes de qualquer implementação:** a data da fase "Lançamento comercial" foi escolhida
  pelo dono do produto (opção A do plano §2.4): depois de todas as fases, inclusive a trilha de editais, a partir de
  outubro de 2028, com a primeira produção (corte do legado do piloto) por volta de fevereiro de 2029; as licitações do
  ajuste v2.5 passam do 2º semestre de 2028 para a partir do 3º trimestre de 2029 (ADR-0023, ADR-0029 e ADR-0033,
  ajustes v2.8); decisão do dono do produto.
- **Ajuste de 12/10/2026 (v2.12):** referências a equipe e capacidade sem efeito (ADR-0029 revogado).

## Contexto

O roadmap R1–R9 (`docs/PLANO-EXECUCAO-PDCA-R0-R9.md` §5) ordena o trabalho por funcionalidade. A revisão de estrutura
de 12/10/2026 (`docs/levantamento/17-REVISAO-ESTRUTURA-SAAS-2026-10-12.md`, achado X5) mostrou que ele não tem marco de
"primeiro cliente em produção", não prevê migração do sistema legado e não contém a camada que transforma o código em
serviço operado: artefato implantável e ambientes (X1), banco com alta disponibilidade e backup (X2), lote e trabalho
assíncrono (X4), observabilidade (O1) e metas não funcionais (O4).

As fundações E0–E6 terminaram em 12/10/2026, cinco semanas antes do alvo de 27/11/2026. A revisão recomenda usar essa
folga em plataforma e migração, e não em antecipar funcionalidade.

Ainda não há cliente piloto definido, e a fase de migração (P1) depende do acesso ao legado de um cliente concreto.

## Decisão

Adotar o roadmap por fases P0–P4 com marco de go-live piloto. O R1–R9 deixa de ser a sequência do projeto e passa a
ser conteúdo distribuído dentro das fases:

| Fase | Período | Conteúdo (inclui os itens R correspondentes) |
|---|---|---|
| **P0 — Plataforma operável** | out–nov/2026 | Escolha do cliente piloto; decisão de hospedagem (ADR-0011); Dockerfile, CI no GitHub Actions, homologação e produção; banco com backup e PITR testado; **R1** (cofre e chaves); observabilidade mínima; metas não funcionais; correções S1–S6 e médios de segurança; `V1_5` (parâmetros com vigência) antecipado; ADRs 0006 a 0010 |
| **P1 — Migração e satélite** | nov/2026–jan/2027 | Discovery do legado do piloto; importador v0 do cadastro com conciliação; **R2**; **R3** como módulo satélite sobre a base importada; console da plataforma v0; partição do `audit_log`; início do porte fiscal (ADR-0003) |
| **P2 — Ciclo de faturamento** | jan–mar/2027 | Fundação de lote (X4); **R4** e **R4a**; NFAg-e sobre o motor fiscal portado (ADR-0003); importação de leitura terceirizada; arquivo para gráfica; testes de carga; importador v1 com 120 meses de histórico |
| **P3 — Pronto para operar** | mar–mai/2027 | **R5a** e **R5b**; portal do cliente (2ª via, Pix, protocolo); **R7** mínimo (protocolo NR 11, OS com bloqueio de corte, aviso com prova de entrega); pentest; contrato SaaS, DPA e SLA (ADR-0002) |
| **Go-live piloto** | jun–jul/2027 | Dois ou três ciclos de faturamento paralelo, conta a conta contra o legado; corte do sistema antigo |
| **P4** | 2º sem/2027 | **R8**, **R6**, BI e prestação de contas, app de leitura offline, **R5c** |
| **2028** | — | Trilha GRS e **R9** sob demanda; ISO 27001; instância dedicada |

O marco que mede o projeto passa a ser o go-live piloto, e não a conclusão de um R. Cada fase termina com aceite
verificável (build verde, teste de aceite citando a decisão que o desbloqueou, registro no diário).

## Consequências

### Positivas

- O caminho até o primeiro cliente em produção fica explícito e inclui o que hoje falta: implantação, backup,
  observabilidade, migração e faturamento paralelo.
- A migração do legado deixa de ser item aberto (Q7 do relatório 00) e vira fase com entregáveis.
- A folga das fundações é aplicada onde está o risco de entrada em produção.

### Negativas

- Funcionalidades com data regulatória própria (tarifa social em 11/12/2026, NFAg-e em 01/12/2026 a confirmar,
  NR 11 em 20/05/2027) ficam no software só a partir das fases indicadas. Até o go-live, o cliente piloto cumpre essas
  obrigações com o sistema legado; isso precisa constar da comunicação comercial (G0 do plano).
- R6 (SINISA) e a trilha GRS ficam para P4 e 2028. Clientes que comprem por esses módulos antes disso exigem
  replanejamento.
- O plano de execução passa a ter duas leituras (R e P) até ser reescrito.

### O que muda no código

Nada diretamente. A ordem de implementação muda: lote (X4), migração e plataforma antecedem o ciclo completo.

### O que muda no roadmap

- `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` §5 e §6 passam a ser referência de conteúdo; a ordem e as datas vêm desta tabela.
- O alvo do R1 (18/12/2026) antecipa para o P0, condicionado à decisão de hospedagem (ADR-0011).
- A NFAg-e "por integrador" prevista no R4 e na revisão 17 é substituída pelo motor fiscal próprio (ADR-0003).
- A contratação de jurídico até 31/10 (X5) é substituída pelo modelo de parâmetros com aceite (ADR-0002).

## Alternativas consideradas

- **Manter o R1–R9 e acrescentar itens de plataforma ao fim de cada R.** Rejeitada: mantém a ausência de marco de
  produção e empurra implantação, backup e migração para depois do faturamento, quando o custo de corrigir é maior.
- **Roadmap por funcionalidade com um R0' de plataforma no início.** Rejeitada: resolve P0, mas não cria a fase de
  migração nem o faturamento paralelo, que são o critério real de aceite de um cliente de saneamento.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Definir o cliente piloto e obter acesso ao legado dele (critérios: porte, agência reguladora, sistema legado, disposição para faturamento paralelo) | P0 |
| Reescrever `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` §5 e §6 na estrutura P0–P4, com remissão a este ADR | P0 |
| Definir o critério de aceite de cada fase e do go-live (divergência máxima aceita no faturamento paralelo) | P0 |
| Comunicar aos clientes-alvo o que entra em cada fase e o que segue no legado até o go-live | P0 |
| Discovery do legado do piloto | P1 |
| Plano de faturamento paralelo e de corte do legado | P3 |
