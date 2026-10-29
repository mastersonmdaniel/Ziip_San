# Plano de execução do Ziip SAN: fases P0 a P4, homologação local do piloto, trilha de editais e lançamento comercial

**Versão 3.0, 28/10/2026.** Produto Ziip SAN; nome interno do código, SIGSAN, renomeado para `ziipsan` na P0.33. O
nome do arquivo foi mantido por causa dos links.

Este é o plano canônico do Ziip SAN. Ele registra o que será construído e como: escopo de cada fase, desenho, ordem,
dependências e critério de aceite de cada tarefa, das fundações ao lançamento comercial. Não trata de equipe nem de
capacidade (ADR-0035) e não fixa prazos: as datas de fase e de marco são datas de referência, que indicam sequência e
ordem de grandeza, e a ordem real vem das dependências declaradas e das decisões do dono do produto (ADR-0044). Datas
de norma (vigências, obrigações legais e regulatórias) são fatos e continuam registradas como tais. O tamanho das
tarefas (P, M, G e semanas-pessoa) é só indicação de esforço. Decisão nova entra por ADR, que o plano cita sem repetir;
em divergência entre o plano e um ADR, vale o ADR mais recente.

**O que mudou na v3.0** (decisões do dono do produto de 28/10/2026)

- **Consolidação.** Cada tarefa foi reescrita com os ajustes das versões 2.13 a 2.21 incorporados ao critério de
  aceite, sem etiquetas de versão; quando um ponto passou a outra tarefa, o aceite remete a ela ("ver P1.36"). O texto
  das versões anteriores fica no git e no [histórico de versões](plano/HISTORICO-DE-VERSOES.md).
- **Uma tarefa dona por achado.** Os 333 achados dos levantamentos 22a a 22d (156) e dos relatórios 23a a 23e (177)
  têm destino único: 311 com tarefa dona, que constrói e tem o critério testável, e 22 com destino documental (ADR ou
  seção do plano). As tarefas que só usam o que a dona constrói escrevem "usa X da tarefa Y".
- **Ondas corrigidas.** As 27 entregas de título "onda B" que estavam na tabela da onda C passam ao grupo B2 da onda B,
  com a ED-56 e a nova ED-105; a onda B fica com os grupos B1 (20 entregas, com a ED-88 e a ED-92 trazidas do B2 na montagem: a fossa séptica é item crítico na POC do SAMAE e a ED-64 depende da ocorrência de rede) e B2 (27) e a ED-01 no fim; a onda A tem 34
  entregas e a C, 12. A prontidão de POC (ED-01) conta a onda A, o grupo B1 e o P4; o grupo B2 entra no critério do
  lançamento (PD-01).
- **Pacotes pendentes fora das ondas.** P4.6, ED-68, ED-102, ED-103 e a nova ED-104 formam a seção de pacotes pendentes
  e sob demanda, fora das ondas, dos totais de esforço e do critério da LC.1; cada um só entra por cartão do dono ou
  por demanda registrada.
- **Tarefas novas e fusões.** Dezoito tarefas novas: P1.35 a P1.41, P2.30 a P2.34, P3.39 a P3.41, ED-104, ED-105 e
  LC.15. Saem P4.3 (absorvida pela ED-46, ED-47 e ED-49) e P4.4 (duplicata da ED-18). P1.3, P3.4, ED-04, ED-48 e
  ED-80 foram divididas; a P0.25 passa a limpar o código antes da renomeação. Total: 286 tarefas.
- **ADRs.** ADR-0043 (login gov.br para tenant público e pagamento sem conta no portal; altera o ADR-0022); ADR-0044
  (o projeto não tem prazos); ADR-0045 (motor de OS no núcleo, tipos e alvos de saneamento no pacote); ADR-0046
  (ingestão de supervisório somente leitura, proposto); nota de alinhamento ao NIST SP 800-63B-4 no ADR-0040; nova
  redação da regra 2 do índice de ADRs.
- **Seções reescritas.** Estado atual (§1); decisões vigentes por tema (§2.2); pendências do dono sem data, com
  "Antes de" (§2.4); mapa Flyway com as reservas das tarefas novas e as faixas que faltavam (§3); caminho de
  dependência e marcos como datas de referência (§5); riscos sem linguagem de prazo (§6); resumo por fase (§7);
  glossário (§8).
- **Sem reestimativa.** As tarefas sem tamanho seguem "a estimar", e os totais de esforço das ondas valem só para as
  entregas estimadas (decisão do dono 5).

**Histórico.** As mudanças da v1 à v2.21 estão em [plano/HISTORICO-DE-VERSOES.md](plano/HISTORICO-DE-VERSOES.md).

**Referências.** Revisão 17: [levantamento/17-REVISAO-ESTRUTURA-SAAS-2026-10-12.md](levantamento/17-REVISAO-ESTRUTURA-SAAS-2026-10-12.md);
ADRs: [arquitetura/adr/README.md](arquitetura/adr/README.md); parâmetros: [arquitetura/PARAMETROS-DO-TENANT.md](arquitetura/PARAMETROS-DO-TENANT.md);
RNF: [arquitetura/REQUISITOS-NAO-FUNCIONAIS.md](arquitetura/REQUISITOS-NAO-FUNCIONAIS.md); arquitetura-alvo:
[arquitetura/ARQUITETURA-ALVO.md](arquitetura/ARQUITETURA-ALVO.md); plataforma e verticais:
[arquitetura/PLATAFORMA-E-VERTICAIS.md](arquitetura/PLATAFORMA-E-VERTICAIS.md); porte fiscal:
[arquitetura/FISCAL-PORTE-JAVA.md](arquitetura/FISCAL-PORTE-JAVA.md); editais:
[levantamento/editais/18-ADERENCIA-EDITAIS-CONSOLIDADO.md](levantamento/editais/18-ADERENCIA-EDITAIS-CONSOLIDADO.md);
lacunas: [levantamento/lacunas/22-INCORPORACAO-NO-PLANO.md](levantamento/lacunas/22-INCORPORACAO-NO-PLANO.md);
revisão geral: [levantamento/revisao-geral/](levantamento/revisao-geral/) (23a a 23e); normas:
[normas/README.md](normas/README.md); estrutura da v3.0 e mapa de donos: [plano-v3/fonte/](plano-v3/fonte/);
execução: [DIARIO-DE-BORDO.md](DIARIO-DE-BORDO.md).

**Convenção.** Achados da revisão 17 levam o prefixo `17:` (`17:X1`, `17:S1`, `17:D1`), para não confundir com as
decisões D1–D13 e S1–S3 da v1; os médios são `17:M1`–`17:M15` e os baixos `17:B1`–`17:B11`, na ordem do relatório.
Achados dos levantamentos e relatórios levam o prefixo do relatório: CAD, OPE, PLT e GES (22a a 22d); COE, DAD, COD,
JOR e RAS (23a a 23e). PD-01 a PD-10 são as decisões pendentes da estrutura da v3.0, aceitas pelo dono do produto em
28/10/2026 (§2.4).

---
