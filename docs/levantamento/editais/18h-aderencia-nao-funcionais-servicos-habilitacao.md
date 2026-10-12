# 18h — Aderência dos editais ao Ziip SAN: não funcionais, serviços, prova de conceito e habilitação

Análise de 12/10/2026. Compara os requisitos não funcionais, de serviço, de prova de conceito (POC) e de habilitação e
condições comerciais de dois editais de saneamento com o estado do Ziip SAN (código interno SIGSAN, renomeado
`ziipsan` na P0.33) e com o plano de execução v2.2.

**Fontes dos requisitos.** `18b-videira-edital-etp-precos.md` (IDs ED, ETP, PP), `18a-videira-termo-de-referencia.md`
(IDs TR) e `18c-sigcos-pe-2-2026.md` (IDs SG). As condições de habilitação e as regras da POC do 18c que não têm ID
(seções 2 e 3 daquele documento) entram como linhas próprias, citadas pela seção.

**Fontes do estado do produto.** Revisão 17 (`../17-REVISAO-ESTRUTURA-SAAS-2026-10-12.md`), plano
`../../PLANO-EXECUCAO-PDCA-R0-R9.md` v2.2, `../../arquitetura/REQUISITOS-NAO-FUNCIONAIS.md` (RNF),
`ARQUITETURA-ALVO.md`, `AJUDA-SUPORTE-SOBRE.md`, `CENTRAL-ZIIP.md`, ADRs 0002, 0005, 0007, 0010, 0011, 0012, 0019,
0020 e 0021, e conferência pontual no código (realm do Keycloak, `audit_log`, frontend, módulo LGPD).

**Convenções.**
- Situação hoje: **Atende**, **Parcial** ou **Não atende**, com evidência. Documento da empresa que o repositório não
  comprova (certidões, balanço) é classificado como Não atende com a nota "não comprovado".
- Situação no plano: tarefa do plano v2.2 (P0.x a P4.x, GL.x), RNF ou ADR; "Não planejado" quando nada cobre.
- Esforço, na escala do plano §4: **P** até 2 dias, **M** até 1 semana, **G** mais de 1 semana, para uma pessoa com
  agentes. Em itens de serviço, o esforço é de preparação, não de execução contratual.
- Os dois certames já ocorreram (Videira em 24/03/2026, São Ludgero em 24/06/2026). Servem de referência para os
  próximos editais do mesmo perfil.

---

## 1. Resumo

### 1.1 Requisitos selecionados por documento

| Documento | Linhas no levantamento | Selecionadas para esta área | Fora da área (vão para as matrizes funcionais) |
|---|---|---|---|
| 18b — Edital Videira (ED) | 91 | 83 | ED-057 a ED-060 (apps, Zebra, omnichannel, BI na POC), ED-076 a ED-079 (módulos, e-Sfinge, contabilidade, SINISA) |
| 18b — ETP Videira (ETP) | 22 | 21 | ETP-001 (resíduos sólidos) |
| 18b — Pesquisa de preços (PP) | 24 | 23 | PP-019 (escopo funcional excluído da cotação Jtech) |
| 18a — TR Videira (TR) | 1.114 | 177 | TR-129 a TR-1065 (itens 5 a 24 do TR, exceto 24.3.18–24.3.19) |
| 18c — SAMAE São Ludgero (SG) | 1.415 | 232 | Seções funcionais 2.8 a 2.29, salvo segurança, auditoria, LGPD, backup e migração cartográfica nelas contidos |
| **Total com ID** | 2.666 | **536** | |

Além dos IDs, entram as condições sem ID do 18c (habilitação §2.1–2.3, nas linhas M05, M06, M07, M09 e M10; regras da
POC §3, na M24) e o roteiro POC-01 a POC-81 do 18a §3.2, citado na M22. A matriz consolida os requisitos em 101
linhas (M01 a M101), juntando equivalentes. As contagens por ID foram conferidas por script: cada ID selecionado
aparece em exatamente uma linha.

### 1.2 Situação hoje

| Situação | Linhas da matriz | Requisitos com ID | ED | ETP | PP | TR | SG |
|---|---|---|---|---|---|---|---|
| Atende | 12 | 57 | 9 | 9 | 0 | 18 | 21 |
| Parcial | 33 | 182 | 26 | 5 | 23 | 39 | 89 |
| Não atende | 56 | 297 | 48 | 7 | 0 | 120 | 122 |

Dos requisitos em Não atende, 12 são documentos da empresa apenas não comprovados no repositório (M05 e M07).

### 1.3 Situação no plano

| Situação no plano | Linhas da matriz |
|---|---|
| Coberto por tarefa do plano v2.2 (total ou parcialmente) | 60 |
| Planejado com meta que conflita com o edital (M56 disponibilidade; M77 consulta pós-contrato) | 2 |
| Não planejado | 34 |
| Sem ação necessária | 5 |
| **Total** | **101** |

Mesmo nas 60 linhas cobertas, a cobertura costuma ser parcial: a tarefa existe, mas sem o número ou o detalhe que o
edital pede (por exemplo, M96, M76, M28).

### 1.4 Conclusões

1. **O que já atende** é a base técnica: 100% web, três camadas, Java 21, PostgreSQL, base única, login individual,
   senha forte, bloqueio por tentativas e trilha de auditoria por campo. Quase todo o resto depende de produção, que
   ainda não existe (17:X1, 17:X2).
2. **O plano cobre a camada técnica** (hospedagem, backup, observabilidade, desempenho, SLA de suporte, exportação de
   dados), mas com metas que divergem dos editais em pontos verificáveis: 99,5% contra 99,9% (SG), prioridades P1–P4
   sem prazo numérico contra cinco severidades com solução em 2 h, eliminação em até 60 dias contra consulta por
   120 dias, exportação em 15 dias contra 10 dias úteis, nenhuma meta para relatórios (2 e 5 min nos editais).
3. **O plano não cobre os serviços** que os editais compram junto com o software: implantação em 60 ou 90 dias com
   equipe presencial, migração de qualquer legado, migração cartográfica DWG, treinamento presencial, telefone de
   suporte, customização por banco de horas.
4. **A POC é eliminatória nos dois editais** e está fora do escopo pelo diário de bordo (§1). Sem prontidão de POC,
   nenhum dos dois certames é disputável, qualquer que seja o preço.
5. **A habilitação técnica é a barreira imediata**: Videira exige dois atestados com 8.500 ligações e e-Sfinge; o SG,
   um atestado sem quantitativo. O piloto do plano gera, no melhor caso, um atestado no fim de 2027.
6. **Duas decisões já tomadas pesam diretamente**: a hospedagem (ADR-0011) precisa de um provedor com ISO 9001,
   27001, 27017, SOC 1 e SOC 3, o que exclui VPS para editais do tipo Videira; e o modelo SaaS puro não responde às
   cláusulas de hospedagem local (TR 4.4.12) e de licença instalável (SG 2.2.3.4).

---

## 2. Matriz de aderência

Colunas: IDs; requisito; situação hoje com evidência; situação no plano; lacuna ou ação; esforço.

### 2.1 Objeto, lote e propriedade intelectual

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M01 | ED-001; ETP-004; TR-001; TR-002 | Cessão de uso por prazo determinado (Videira) ou SaaS (SG) de software de autoria própria, 100% web, com serviços agregados | Atende: software próprio; SaaS cobrado por contrato (ADR-0019) | ADR-0019; P2.12 | Cobertura de módulos nas matrizes funcionais | P |
| M02 | ED-002; ED-003; ED-089; ETP-016; TR-003; TR-1112; SG-1366 | Lote único, proposta para todos os itens, adjudicação a um só licitante, menor preço | Não atende: omnichannel, BI, SIG e laboratório (SG) não existem; `portal`, `dunning`, `field` e outros sem POM (revisão 17 §2) | BI na P4.3; SIG no R9 (2028); omnichannel e laboratório: Não planejado | Sem esses módulos, próprios ou licenciados como componente do produto, a proposta é desclassificada | G |
| M03 | ED-090; ETP-005 | Vedação a software livre e registro no INPI de todos os módulos (ETP); edital omisso sobre art. 93, código-fonte e escrow; titularidade das customizações | Não atende: programa sem registro; sem cláusula de licença das customizações | Não planejado | Registrar o programa no INPI; na proposta e no contrato, customização incorporada ao produto e licenciada, sem cessão de direitos (risco do art. 93, A-16) | P |
| M04 | ED-024; ETP-009; TR-1110; SG-1375; SG-1376 | Subcontratação vedada (TR Videira) ou só de parcelas acessórias e de infraestrutura (SG) | Parcial: núcleo próprio; hospedagem, mapas e mensageria de terceiros. Em Videira a vedação total convive com a exigência de nuvem de terceiro (A-05) | Não planejado | Pedir esclarecimento antes da sessão; tratar provedores como insumo licenciado | P |

### 2.2 Habilitação

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M05 | ED-010; ED-012; ED-013; ED-014; ED-022; TR-1100; TR-1101; TR-1102; 18c §2.2 a–l, n–s | Habilitação jurídica, fiscal, social e trabalhista; declarações; dados bancários | Não atende (não comprovado): depende da pessoa jurídica que licita, não identificada no repositório | Não planejado | Definir a empresa licitante, conferir CNAE e manter kit de certidões renovado | P |
| M06 | ED-008; ETP-010; TR-1111; 18c §2.1 | ME/EPP sem cota exclusiva, com empate ficto; MEI vedado (SG) | Atende: nada a cumprir; benefício só se a empresa for ME/EPP | Sem ação | Confirmar enquadramento | P |
| M07 | ED-015; ED-016; ETP-007; TR-1103; 18c §2.2 l–m | Certidão de falência; balanço, DRE e demonstrações dos 2 últimos exercícios, sem índices | Não atende (não comprovado). Empresa criada no exercício usa balanço de abertura (art. 65, §1º, Lei 14.133); o SG aceita o último exercício para empresa com menos de 2 anos | Não planejado | Escrituração por contador antes da primeira licitação | P |
| M08 | ED-017 a ED-021; ETP-008; TR-1104 a TR-1109 | Videira: ≥ 2 atestados em prestadora de água, com implantação, suporte e treinamento, relatórios de envio ao e-Sfinge do TCE/SC e ≥ 8.500 ligações somadas | Não atende: nenhum cliente | Não planejado: o piloto (P0.30 → GL.4) gera no máximo 1 atestado após jul/2027, sem e-Sfinge | Seção 4 | G |
| M09 | 18c §2.3 (Edital 8.2 t; TR 6.1.1 a) | SG: ≥ 1 atestado de fornecimento "sem restrição" de solução compatível, sem quantitativo nem parcela relevante | Não atende | Piloto (P0.30 → GL.4) pode gerar o atestado no 2º semestre de 2027 | Prever no termo de intenção do piloto a emissão do atestado após os ciclos paralelos | M |
| M10 | ED-009; 18c §2.1 | Consórcio: admitido em Videira com somatório de quantitativos e valores; não tratado no SG | Parcial: faculdade disponível; nenhum parceiro identificado | Não planejado | Seção 4 | M |
| M11 | ED-023; TR-1099 | Visita técnica facultativa ou declaração de pleno conhecimento | Atende | Sem ação | Declaração no kit (A-23 do 18b) | P |
| M12 | ED-011; ED-025; SG-1398 | Habilitação em até 2 h após o julgamento; contrato assinado em 3 dias úteis com ICP-Brasil ou gov.br (Videira) ou em 5 dias (SG) | Parcial: o ADR-0019 prevê certificado próprio do Ziip, ainda inexistente; kit não existe | P2.13 (contexto fiscal e certificado da plataforma) | e-CNPJ e kit prontos antes da sessão | P |
| M13 | ED-070; TR-103; TR-105 | Documento personalizado do provedor de nuvem confirmando o atendimento e o vínculo com a proponente | Não atende: sem provedor | Não planejado (a P0.26 só escolhe o provedor) | Hiperescalador raramente emite carta personalizada: reunir contrato da conta, faturas, programa de parceiros e relatórios públicos de conformidade | P |

### 2.3 Proposta, preço e contrato

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M14 | ED-005; ED-006; ED-007; ED-037; SG-1372; SG-1373; SG-1374 | Proposta válida por 60/90 dias, marca própria, todos os custos incluídos, ajuste em 2 h; preço máximo; inexequibilidade abaixo de 50% | Parcial: procedimento simples, mas não há modelo de custo (hospedagem, equipe, mensagens) | Tabela de custo da hospedagem no ADR-0011 (P0) | Planilha de custos por porte com hospedagem, plantão, deslocamento e mensageria | M |
| M15 | ED-004; ETP-011; ETP-014; PP-001 a PP-018; PP-020 a PP-024; SG-1190; SG-1367 a SG-1371; SG-1399 | Referências: Videira R$ 366.365,13 (R$ 18.866,65/mês e R$ 77.900 de implantação); SG R$ 204.984,78 (R$ 7.636,74/mês com Google Maps, banco, BI e hospedagem; omnichannel e SMS por uso; BI para 2 usuários) | Parcial: não há tabela de preços do Ziip SAN | Não planejado | Preço por porte compatível com os tetos; o teto do SG só fecha com infraestrutura compartilhada entre tenants | M |
| M16 | ED-026; TR-004; SG-1385; SG-1386 | Vigência de 12 meses, prorrogável (art. 107; SG até 15 anos pelo art. 114); custos não renováveis reduzidos na prorrogação | Atende: cláusula aceitável; contrato com vigência e aditivos no ADR-0019 | P2.12 | Separar implantação de mensalidade no preço | P |
| M17 | ED-027; ED-028; SG-1387 | Reajuste por IPCA (Videira) ou INPC (SG) após 12 meses da data do orçamento; reequilíbrio | Atende: cláusula aceitável; reajuste como proposta até o apostilamento (ADR-0019, item 6) | P2.12 | — | P |
| M18 | ED-031 a ED-034; TR-1113; TR-1114; SG-1383; SG-1388 a SG-1394 | Recebimento provisório e definitivo (5 e 30 dias úteis), pagamento em até 10 dias úteis após a liquidação, NF com certidões, XML por e-mail, retenção de IR, INSS e ISS, mensalidade só após a implantação (SG) | Parcial: o ADR-0019 cobre fatura, pagamento informado e NFS-e pelo motor portado; retenções na NFS-e da venda e anexação de certidões não especificadas | P2.12, P2.13 | Retenções na NFS-e da venda; prever 60 a 90 dias entre o serviço e o caixa | M |
| M19 | ED-040; SG-1396; SG-1407; SG-1408 | Sem garantia de execução; correção de vícios em 48 h; garantia de 12 meses das customizações (SG) | Parcial: nada a depositar; sem política de correção em 48 h nem de garantia de customização | Não planejado | Política comercial (E10) | P |
| M20 | ED-030; ED-036; ED-038; ED-039; SG-1395; SG-1400; SG-1409 a SG-1415 | Extinção por atraso na implantação; multas de 0,5% a 30%; mora de 0,5%/dia (Videira) ou 10%/dia por até 10 dias (SG); impedimento; acréscimos de 25%; manter a habilitação | Parcial: aceitáveis só com capacidade de entrega comprovada; sem matriz de risco | Não planejado | Precificar o risco; não disputar edital cujo prazo de implantação não caiba no método | P |
| M21 | ED-035; TR-024; TR-025; TR-1091; TR-1094; TR-1095; TR-1096; SG-1397 | Preposto aceito pelo órgão, equipe própria como única empregadora, comunicação imediata de anormalidades, normas do órgão | Parcial: não há preposto nem equipe; comunicação de incidente prevista | P3.10 (resposta a incidente), P3.18 (página de status) | Preposto nomeado por contrato | P |

### 2.4 Prova de conceito

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M22 | ED-055; ED-056; TR-038 a TR-045 (roteiro POC-01 a POC-81 do 18a §3.2) | Videira: POC da vencedora em nuvem própria, 81 itens com nota 2/1/0, aprovação com ≥ 90% (146 de 162 pontos), feita em 5 dias úteis após a adjudicação; a comissão pode pedir outras funcionalidades | Não atende: dos 20 grupos do roteiro, só cadastro (c) e controle de acesso (b) têm código, em parte; sem ambiente em nuvem | Não planejado: "POC e editais" fora do escopo (diário §1) | Entrega E3 | G |
| M23 | ED-074; TR-112 | ≥ 90% das funcionalidades 5.1–23.1 nativas; ≤ 10% customizadas e entregues na implantação | Não atende | Não planejado | Depende das matrizes funcionais | G |
| M24 | 18c §3 (Edital 7.1–7.13; TR 13) | SG: POC eliminatória, convocação com 7 dias úteis, presencial na sede, sistema completo em nuvem, base ≥ 10.000 unidades consumidoras fictícias ou anonimizadas (LGPD), celulares e impressora da licitante, 1 a 2 especialistas, 80 itens binários sem parcial, 90% dos essenciais, sessão gravada | Não atende | Não planejado; a massa sintética da carga (P0.23 com 20 mil ligações; RNF-70 com 300 mil) é reaproveitável | Entrega E3 | G |
| M25 | SG-001 | Itens críticos: falha em qualquer um desclassifica, sem correção posterior. Incluem 1.1.2 (todo o TR), "Item 0" (base única nativa), banco, desempenho, controle de acesso, módulos comerciais, omnichannel, laboratório, SIG, app e suporte (Item 3) | Não atende | Não planejado | Roteiro interno com a lista crítica como portão de prontidão | G |

### 2.5 Arquitetura e plataforma

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M26 | ED-061; TR-046; TR-056; SG-002; SG-022 | 100% web, sem instalação nem plug-in | Atende: SPA React no navegador (`frontend/`), API REST | ARQUITETURA-ALVO §1 | — | P |
| M27 | TR-057; SG-023 | Edge 88+, Chrome 100+, Firefox 116+ (Videira); últimas versões (SG) | Parcial: build com alvo ES2020 (`frontend/tsconfig.json`), sem matriz de teste | RNF-64 (duas últimas versões, P2) | Incluir as versões mínimas de Videira na matriz E2E; o RNF-64 não cobre navegadores de 2021 a 2023 | P |
| M28 | TR-055; TR-058; TR-059; TR-060; SG-021; SG-024; SG-025; SG-026 | Mensagens em português, sinal de espera, paginação com escolha do número de linhas e navegação a qualquer página | Parcial: textos e problem+json em português; `Spinner` (`frontend/src/components/ui/Alert.tsx:40`); página de tamanho fixo 25 (`CatalogCrud.tsx:322`), sem salto para página arbitrária | P0.22 adota keyset acima de 10 mil linhas (RNF-04), o que impede ir direto a uma página | Seletor de tamanho e campo "ir para página"; offset com teto ou keyset com salto aproximado | P |
| M29 | TR-048; TR-049; TR-050; SG-008; SG-009; SG-010 | Três camadas; sem regra de negócio na apresentação | Atende: SPA, API Spring Boot e PostgreSQL; regras nos motores e serviços (revisão 17 §3) | ARQUITETURA-ALVO §1 | — | P |
| M30 | ED-062; TR-051; SG-011 | Ambientes de desenvolvimento, homologação e produção com ferramenta automatizada de promoção | Não atende: só ambiente local (17:X1) | P0.3, P0.5, P0.27 para o código; promoção de parametrização entre ambientes: Não planejado | Exportar e importar parâmetros aceitos entre homologação e produção | M |
| M31 | TR-052; SG-013; SG-1378 | Todos os módulos integrados em base única, nativos entre si (crítico no SG); web responsivo; BI e SIG ligados à base | Parcial: monólito modular sobre um PostgreSQL; BI, SIG, omnichannel e laboratório inexistentes | ARQUITETURA-ALVO §1; BI em réplica de leitura (P4.3) | BI de terceiro com base própria pode reprovar o "Item 0": usar a mesma base ou a réplica | G |
| M32 | TR-053; SG-005; SG-006; SG-015 a SG-018 | Linguagem orientada a objetos, "interpretada", multithread, com alta disponibilidade (SG aceita J2EE 17/21 ou equivalente) | Atende: Java 21 e Spring Boot 4.1.1; "interpretada" é discutível (bytecode da JVM) | P3.14 (linha com suporte) | Argumentar pelo bytecode e pela neutralidade tecnológica (SG-005) | P |
| M33 | SG-012 | Sem triggers com lógica de negócio | Parcial: gatilhos só de auditoria (`tenant/V1_3__harden_audit_function.sql`); integridade por CHECK e EXCLUDE | P2.3 (auditoria por lote nas tabelas de fato) | Documentar que os gatilhos não contêm regra de negócio | P |
| M34 | SG-014; SG-037 a SG-041 | Balanceamento de carga nativo, uso de várias CPUs, paralelismo em cargas, componentes distribuídos | Parcial: API sem sessão, pronta para réplicas (RNF-23); JVM multithread; sem réplicas nem lote | P0.14, P0.27, P2.1, P2.2 | — | M |
| M35 | SG-019 | Execução em Windows Server 2022+ e Linux kernel 5+ | Parcial: imagem Linux (P0.5); Windows não testado | Não planejado (Windows) | Declarar Linux; Windows via máquina virtual; pedir esclarecimento | P |
| M36 | ED-067; TR-065; TR-067; SG-004; SG-020 | Hospedagem local a pedido do órgão, com percentual de dedução previsto na proposta (Videira); licenças instaláveis em qualquer servidor sem custo adicional e requisitos de hardware na proposta (SG) | Não atende: sem artefato implantável (17:X1) | Não planejado: o ADR-0011 trata só da hospedagem do Ziip; imagem portável na P0.5 | Entrega E4; decisão 3 | G |
| M37 | TR-047 | Referências legais anexadas às parametrizações; parâmetros estratificados interpretados pelos módulos | Parcial: modelo de parâmetros com vigência, norma e aceite decidido (ADR-0002, ADR-0015), ainda sem código | P0.19, P0.20 | Anexar o ato normativo (PDF) ao aceite do parâmetro | P |

### 2.6 Hospedagem e provedor de nuvem

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M38 | ED-063; TR-061; SG-003; SG-1406 | Toda a infraestrutura em datacenter no Brasil, sem armazenamento ou espelhamento fora, com comprovação; custo de hospedagem e migração com a licitante; vedada transferência internacional de dados | Não atende: sem hospedagem; o Terraform arquivado apontava para `us-east-1` (17:O2) | RNF-44; ADR-0011, requisito 1; P0.26, P0.27 | No Azure, a geo-redundância do backup pareia fora do Brasil (ARQUITETURA-ALVO §7): usar só redundância local ou por zona | M |
| M39 | ED-064; TR-062; TR-068 | Aplicação, banco e softwares em nuvem de empresa especializada | Não atende | P0.26, P0.27 (VPS ainda é opção no ADR-0011) | — | M |
| M40 | ED-065; TR-069 a TR-079 | Provedor com ISO 9001, ISO/IEC 27001, 27017, SOC 1/ISAE 3402 e SOC 3, em nome do provedor, vigentes na assinatura; sem eles, eliminação | Não atende | Não planejado: o ADR-0011 não lista certificações entre os requisitos | Incluir como requisito do ADR-0011 (seção 3.3) | P |
| M41 | ED-066; TR-085 a TR-090; TR-095; TR-098; TR-101 | Serviços do provedor: autoescala por console, CLI ou SDK; logs; instâncias Linux e Windows; cache em memória; banco relacional gerenciado; SSD; imagem padrão protegida; 24x7 com licenças e atualizações de SO, SGBD e segurança | Não atende | P0.26 a P0.29; imagem assinada e imutável (P0.5) | O edital exige que o provedor ofereça os serviços, não que o produto use todos (o cache segue congelado pelo ADR-0010). Hiperescalador atende pelo catálogo; VPS não | M |
| M42 | TR-064; TR-080 a TR-084; TR-100; SG-033; SG-667 | Redundância; backup gerenciado com console, API e CLI, na nuvem e local, cifrado, com logs e práticas PCI DSS; banco gerenciado com restauração em ponto no tempo; backup, restauração e replicação; política de recuperação | Não atende: nó único, sem backup nem PITR (17:X2; RNF-14 a RNF-16) | P0.28; GL.3; P4.7 | "Backup local" (TR-080) pode significar cópia no órgão: esclarecer | G |
| M43 | TR-091; TR-092; TR-096 | Monitoramento contínuo de segurança; avaliação automatizada de vulnerabilidades com relatório; inventário e histórico de configuração | Não atende | Não planejado (só Trivy no CI, RNF-35, e pentest, P3.8) | Ativar os serviços nativos do provedor (detecção de ameaças, inspeção de vulnerabilidades, registro de configuração) | P |
| M44 | ED-071; TR-097; TR-099; TR-104 | Painel de usuários conectados, painel de todos os logs de produção e acesso administrativo da TI do órgão ao ambiente | Não atende | Não planejado: a Central Ziip é só do operador (ADR-0018) | Em multi-tenant, mostrar ao tenant as sessões ativas e a trilha do próprio tenant; acesso administrativo só em instância dedicada (E4) | M |
| M45 | TR-063; TR-066 | Ajustar a infraestrutura para desempenho compatível com outras implantações; responsabilidade solidária pelo dimensionamento | Parcial: dimensionamento inicial em ARQUITETURA-ALVO §6, sem medição | P0.23, P2.9 | Não há outras implantações para comparar | M |

### 2.7 Banco de dados

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M46 | SG-027 a SG-032; SG-034 | PostgreSQL ou equivalente, fornecido e administrado pela contratada (tuning, índices, particionamento, recursos, patches); banco e dados de propriedade do contratante | Parcial: PostgreSQL 15 com PostGIS; sem operação nem partição | P0.4 (PG 17), P0.22, P1.7, P2.3, P0.28 | "Banco de propriedade do contratante" é ambíguo (licença, instância ou dados): responder "dados" | M |
| M47 | SG-035 | Arquivo morto: dados obsoletos fora das tabelas ativas, ainda consultáveis | Não atende | P1.7 e P2.3 particionam `audit_log` e tabelas de fato; histórico no portal (P3.20) | Declarar a partição por competência como arquivo morto, com consulta | M |

### 2.8 Desempenho, monitoramento e disponibilidade

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M48 | ED-072; TR-106; TR-107; SG-036; SG-042 | Busca, cadastro, exclusão e edição em até 10 s com 20 usuários simultâneos, em base de 50 mil (Videira) ou 10 mil (SG) | Não atende: busca por nome sem índice (17:D5), N+1 nas listas (17:D4); não medido | P0.22, P0.23; RNF-01 a RNF-04, mais rigorosos | — | M |
| M49 | TR-108; SG-043 | Processamento de arquivos e relatórios em até 2 min (Videira) ou 5 min (SG) | Não atende: sem relatórios nem lote | Não planejado: nenhum RNF trata relatório ou arquivo | Criar RNF de relatório e de arquivo (retorno bancário, leitura importada) | M |
| M50 | ED-073; TR-109; SG-044 | Programar eventos de negócio que alertam usuários | Não atende | Não planejado para o tenant (alertas da Central são do operador) | Entrega E6 | M |
| M51 | TR-110; SG-045 | Relatórios nativos de utilização, capacidade e desempenho dos serviços | Não atende | P1.17 e P3.15 (Central, só para o operador) | Painel do tenant (E6) | M |
| M52 | TR-111; SG-046 | Alerta proativo, por e-mail ou SMS, de falha em lote e de indisponibilidade de serviço | Não atende | P2.11, P3.11 (ao operador) | Notificar também o responsável do tenant | P |
| M53 | SG-1312 | Monitoramento 24/7 proativo e automatizado | Não atende: só `health` no actuator (RNF-50) | P0.8, P3.11; RNF-53, RNF-54 | Plantão 24/7 não existe (plano §6, capacidade) | M |
| M54 | ED-068; TR-093; TR-094 | Disponibilidade da nuvem ≥ 95% ao mês; desconto de 5% a 30%; 100% abaixo de 60% | Não atende: sem produção | RNF-10 (99,5%, P3) supera | — | P |
| M55 | ED-086; TR-1089 | 99% em horário comercial, com monitoramento proativo; 5% por faixa de 10% abaixo, até 30% | Não atende: sem produção | RNF-10 e RNF-53 (P2–P3) superam | — | P |
| M56 | SG-668; SG-1313 | 99,9% de disponibilidade geral; 5% por faixa de até 10% abaixo de 99,9%, cumulativa; omnichannel com alta disponibilidade | Não atende | Conflito: RNF-10 fixa 99,5% para o backoffice; 99,9% só no portal (RNF-11) | Decisão 6 | M |

### 2.9 Segurança, controle de acesso e auditoria

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M57 | TR-113; TR-114; TR-118; SG-047; SG-048; SG-052 | Acesso só com login e senha; identificação única por usuário | Atende: Keycloak 26.8 com PKCE; tenant tirado do JWT (revisão 17 §3) | P0.14 (BFF), P0.15 (MFA) | — | P |
| M58 | ED-075; TR-115; SG-049 | Administrador cria o operador e lhe atribui permissões | Parcial: `ADMIN` é do realm inteiro e `tenant_id` é editável (17:S3, `sigsan-realm.json:359`) | P0.13 | — | G |
| M59 | TR-116; TR-117; SG-050; SG-051 | Bloqueio parametrizável por tentativas indevidas (POC de Videira: 3) e expiração de senha | Parcial: `bruteForceProtected` com `failureFactor` 5 e espera de até 900 s (`infra/keycloak/realms/sigsan-realm.json:39–44`), valor único para todos os tenants; sem expiração de senha | Não planejado | Política por tenant em fluxo condicional, como no ADR-0007, ou valor do realm aceito por todos; habilitar expiração | P |
| M60 | TR-119; SG-053 | Logoff por inatividade, parametrizável | Atende: `ssoSessionIdleTimeout` 1.800 s (`sigsan-realm.json:28`), ajustável no realm | Sem ação | Por tenant: mesma observação da M59 | P |
| M61 | TR-121; TR-122; TR-123; SG-055; SG-056; SG-057 | Senha forte, armazenada cifrada e nunca exibida | Atende: `passwordPolicy` com 12 caracteres, maiúscula, minúscula, dígito e histórico de 5 (`sigsan-realm.json:48`); hash do Keycloak | Sem ação | — | P |
| M62 | TR-120; SG-054; SG-1253 | Integração LDAP (Videira); autenticação via MS Active Directory (SG); BI com LDAP/AD e SSO | Não atende: nenhuma federação configurada | Não planejado (a P0.38 usa Organizations só para a marca) | Federação LDAP/AD ou IdP por organização no Keycloak | M |
| M63 | TR-124; TR-125; TR-126; SG-058; SG-059; SG-060; SG-1250; SG-1251 | Perfis criados pelo administrador com funcionalidades e direitos; perfil só de consulta; vários perfis por usuário; segurança por grupo no BI | Parcial: papéis fixos `ADMIN`, `MANAGER`, `OPERATOR`, `VIEWER` (plano, D12), `VIEWER` só consulta; interface sem autorização por papel (17:F1) | P1.12 (ações por papel); perfis definidos pelo cliente: Não planejado | Perfis compostos de permissões, por tenant | M |
| M64 | SG-1255 | Comunicação segura SSL | Parcial: Keycloak em HTTP no ambiente local (17:O3) | P0.16, P0.29 (RNF-33) | — | P |
| M65 | ED-047; TR-021; TR-022; TR-1092 | Sigilo absoluto, inclusive após o contrato; responsabilidade por perda ou adulteração de dados | Parcial: cifra AES-GCM por campo, RLS FORCE e auditoria só de inserção (revisão 17 §3); sem termo de sigilo nem DPA | P3.9 | — | P |
| M66 | SG-630; SG-669 | Princípios de segurança no omnichannel; comprovação, a qualquer tempo, dos mecanismos de segurança e de LGPD | Parcial: testes de RLS e de isolamento no PostgreSQL real (RNF-38) servem de evidência | P3.8 (pentest), P3.15 (kit de evidências) | — | M |
| M67 | SG-699; SG-700; SG-701 | Detecção de comportamento anômalo, mascaramento de dado sensível e auditoria das conversas na IA | Não atende | Rate limit (P0.16) e alerta de volume (P0.11) cobrem parte; IA: Não planejado | Depende do módulo de IA | M |
| M68 | TR-127; SG-061 | Trilha das alterações em dados estratégicos com versão, usuário, data e hora, campo e valores anterior e posterior | Parcial: `audit_log` com `old_values`, `new_values`, `changed_fields`, usuário e data (`core/V1__create_core_schema.sql:43`), alimentado por gatilho; `@Version` nas entidades; sem tela de consulta | P1.7; a P2.3 troca a auditoria por linha por auditoria por lote em `bills`, `meter_readings` e `payments` | Manter a trilha por campo nas alterações manuais de fatura, consumo e arrecadação; lote por `run_id` só no processamento em massa | M |
| M69 | TR-128; SG-062; SG-064 | Registro das tarefas de cada usuário ou grupo, com abertura automática de OS, e consulta simples para fiscalização | Não atende | Não planejado | Consulta da trilha por usuário; vínculo com OS depende do R7 (P3.4) | M |
| M70 | SG-655 a SG-661 | Logs do omnichannel (acessos, atendimentos, automações, transferências, configuração), com integridade | Não atende: omnichannel inexistente | Não planejado | — | M |
| M71 | SG-1252 | Auditoria própria do BI (conexões, tentativas, acessos a conteúdo) | Não atende | P4.3, sem esse requisito | — | P |

### 2.10 LGPD e dados do órgão

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M72 | SG-063 | Registro histórico de toda consulta a dado pessoal | Parcial: só a revelação de documento é registrada (RNF-41) | P0.11 (`V1_6`, `personal_data_access_log`) | — | M |
| M73 | ED-087; ETP-003; TR-1093; SG-065; SG-662 a SG-666; SG-1377; SG-1380 | LGPD com consentimento, log de acesso a dado sensível e anonimização nativos; atendimento a titulares; retenção e descarte; LAI | Parcial: módulo `compliance/sigsan-lgpd` com `ConsentRecord`, `AnonymizationEngine`, `DataSubjectRequest` e `RetentionSchedule`, sem API | RNF-40 (P1–P2), P1.15, P3.10, P3.16 | API e tela de titulares e de consentimento | M |
| M74 | SG-1401 a SG-1405 | Cláusulas de operador: confidencialidade, direitos dos titulares, cooperação entre as partes | Parcial: papéis de operador e controlador definidos | P3.9 (DPA) | — | P |
| M75 | ED-069; TR-102; TR-1090 | Cópia de todos os dados a qualquer momento; bases de propriedade exclusiva do órgão | Não atende | P1.6 (exportação no console) | Exportação sob demanda durante o contrato, não só no fim | M |
| M76 | ED-051; TR-029 | Dados ao fim do contrato em até 10 dias úteis, em CSV, JSON ou XML, com dicionário e estrutura relacional | Não atende | RNF-42 (15 dias, formato aberto, manifesto e hash), P1.6, P3.16 | Prazo de 10 dias úteis e dicionário na exportação | P |
| M77 | ED-052; TR-030; TR-1098 | Consulta à base por 120 dias após o término, com um usuário, sem suporte | Não atende | Conflito: o RNF-42 conclui a eliminação em até 60 dias do fim do contrato | Acesso somente leitura por prazo contratual antes da carência (decisão 6) | P |
| M78 | ED-048; TR-023 | Catálogo e dicionário de dados em XLSX, CSV ou PDF | Não atende | Não planejado | Gerar do schema (comentários de tabela e coluna) no CI | P |
| M79 | ED-049; TR-026 | Governança de dados: integridade, versionamento, linhagem e qualidade; apoio a políticas de correção | Parcial: integridade no banco e `@Version` (revisão 17 §3) | P1.2, P2.10 (conciliação do importador) | Linhagem: origem de cada registro migrado | M |

### 2.11 Integrações genéricas

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M80 | ED-050; TR-028; TR-054; SG-007 | APIs REST com JSON compatíveis com a e-PING, de consulta e inserção pelas regras do sistema; padrões abertos | Parcial: só a API do cadastro, sem versão nem OpenAPI (17:M12) | P0.10 (`/api/v1`, OpenAPI), P1.11 | APIs dos demais módulos à medida que nascem | M |
| M81 | TR-027 | Integração com os demais sistemas do órgão durante a implantação (integrador, base externa ou webservice) | Não atende | Não planejado | Serviço por cliente, orçado na implantação | G |

### 2.12 Mobile e acessibilidade

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M82 | SG-782 | App funcional com as opções de acessibilidade do dispositivo | Parcial: base WCAG 2.1 AA com axe no CI (RNF-60); sem app | RNF-60, RNF-63 (P3); app de leitura como PWA (P4.4) | Os editais pedem apps Android e iOS e impressão térmica (matrizes funcionais) | M |

### 2.13 Serviços de implantação e migração

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M83 | ED-043; TR-007; TR-008; SG-1326 | Plano de implantação com fases, ferramentas, testes de validação e plano de treinamento | Não atende | Assistente de implantação (P1.6) e plano de paralelo do piloto (P3.13) | Metodologia padrão, reutilizável por cliente | M |
| M84 | ED-029; TR-014; SG-1332; SG-1382; SG-1384 | Implantação completa em 90 dias da ordem de serviço (Videira) ou 60 dias (SG); customização em 30 dias da OS | Não atende | O piloto vai da P1.1 (30/11/2026) ao GL.4 (30/07/2027), cerca de 8 meses | Entrega E8; sem ela, a extinção por atraso (M20) é provável | G |
| M85 | ED-044; TR-009; SG-1327; SG-1381 | Dois especialistas (Videira) ou um ou mais (SG) presenciais na sede durante toda a implantação | Não atende | Não planejado; plano §6: capacidade de "uma pessoa com IA" | Decisão 5 | G |
| M86 | ED-046; TR-019; TR-020; SG-1333 | Um técnico (Videira) ou dois (SG) por 30 dias após a implantação; substituição a critério do órgão | Não atende | Não planejado | Decisão 5 | M |
| M87 | ED-045; ETP-002; TR-010 a TR-013; TR-015 a TR-018; SG-1328 a SG-1331 | Migração de todo dado do sistema atual, adaptação de bases e fórmulas, digitação e refino pela contratada, teste com validação quantitativa homologada | Não atende | P1.1, P1.2, P2.10, P2.19, GL.1, só para o piloto e sobre leiaute genérico | Importador por legado; o de Videira seria o GSAN, se confirmado (inferência do 18b) | G |
| M88 | ED-080; TR-1066; TR-1067; SG-884 a SG-886; SG-1334 a SG-1365 | SIG a partir do cadastro técnico em DWG, migrado para PostgreSQL/PostGIS, com consulta e edição pelo QGIS (Videira); importação DWG 2007+ em qualquer datum, diagnóstico topológico, SIRGAS 2000/UTM 22S, camadas da rede, edição por WFS, Google Maps licenciado (SG) | Não atende | Não planejado: SIG no R9 (2028) | Conversão DWG→DXF e carga no PostGIS com diagnóstico; QGIS por role e visões restritas ao tenant | G |

### 2.14 Treinamento e documentação

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M89 | ED-053; TR-031 a TR-034; SG-1319; SG-1321; SG-1322; SG-1323 | Treinamento presencial (≥ 40 h em Videira), plano por turma, material e instrutores por conta da contratada, substituição de instrutor | Não atende | Não planejado | Decisão 5 | M |
| M90 | TR-035; SG-1324 | Capacitação avançada de TI e das áreas (administração, BI, relatórios, omnichannel) | Não atende | Não planejado | — | M |
| M91 | ED-054; TR-036; TR-037; SG-1318; SG-1320; SG-1325 | Material em plataforma web por tempo indeterminado, com registro de dúvidas; manual online em português; ambiente web de pré-treinamento (SG) | Não atende | P1.21 (ajuda), P1.23 (chamados); ambiente de treinamento por tenant: Não planejado | Tenant de treinamento com massa sintética | M |
| M92 | TR-1072; SG-1280; SG-1314 a SG-1317 | Manuais técnicos e de operação a cada versão; documentação de administradores, usuários, treinamento e customizações | Não atende | P0.41 (changelog), P1.21, P1.24 | Documentação de customizações não prevista | M |

### 2.15 Suporte, manutenção e níveis de serviço

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M93 | ED-081; ETP-013; TR-1068; TR-1077; TR-1078; TR-1080; SG-1285; SG-1286; SG-1288 | Suporte em português por portal de chamados, atendente apto, chamados ilimitados; órgão sem TI própria | Não atende | P1.23, P2.15 (ADR-0020) | — | M |
| M94 | TR-1076; TR-1079; SG-1284; SG-1287 | Telefone no Brasil para urgências; canal direto se a ferramenta cair | Não atende | Não planejado: ADR-0020 sem telefone; e-mail só na P4.8 | Número fixo ou 0800 e e-mail de contingência | P |
| M95 | ED-088; TR-1081; TR-1097; SG-1289 | Atendimento das 07h30 às 17h30 (Videira) ou das 8h às 18h (SG); paradas e atualizações fora do expediente, agendadas | Não atende | P2.15 (calendário útil), RNF-12 (domingo, 0h–4h), P3.18 | Calendário por contrato | P |
| M96 | ED-084; ETP-006; TR-1082 a TR-1087; SG-1290 a SG-1305 | Cinco severidades por impacto × criticidade; solução em 2/4/6/16/32 h úteis (Videira) ou 2/4/16/72/160 h (SG) | Não atende | P2.15, mas o ADR-0020 prevê prioridades P1–P4 sem prazo numérico | Cinco níveis na `sla_policy`, prazo de solução e equipe para 2 h | M |
| M97 | ED-085; TR-1088; SG-1306 a SG-1311 | Multa de 5% (Videira) ou de 10% e 5% (SG) da fatura global por severidade descumprida, cumulativa; metas de aderência de 95% a 70% (SG) | Não atende | Relatório mensal de SLA (P2.15) | Medir aderência por severidade e precificar o risco | P |
| M98 | ED-042; ETP-015; TR-005; TR-006; TR-1069; TR-1070; TR-1071; TR-1073; SG-1278; SG-1279; SG-1281; SG-1379 | Manutenção corretiva; atualizações assim que disponíveis, sem prejuízo de desempenho; atualização tecnológica e da infraestrutura (servidor HTTP, aplicação, banco) | Parcial: código mantido, com CI local; sem ambiente | P0.3, P0.39, P0.41, P3.14; RNF-13, RNF-70 | — | M |
| M99 | ED-082; TR-1074; SG-1282 | Mudanças por exigência legal, judicial, do Tribunal de Contas ou do MP sem custo adicional | Parcial: parâmetros com vigência (ADR-0002) e changelog obrigatório para legislação (ADR-0021) | P0.19, P0.41 | Embutir o custo na mensalidade | P |
| M100 | ED-083; TR-1075; SG-1283 | Evolutivas e customizações por orçamento, com horas, homem-hora por perfil e cronograma; equipe multifuncional | Não atende | Não planejado | Tabela de perfis e regra de customização sem bifurcar o código (flag por tenant, plano §8) | M |

### 2.16 Informativos

| # | IDs | Requisito | Situação hoje | Situação no plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| M101 | ED-041; ED-091; ETP-012; ETP-017 a ETP-022 | Natureza comum do objeto; fiscais nomeados; alternativas do ETP; resultados pretendidos; PCA; sem impacto ambiental; sem análise de riscos | Atende: sem obrigação | Sem ação | — | P |

---

## 3. Metas dos editais × metas do RNF e decisões tomadas

### 3.1 Metas numéricas

| Tema | Videira (18a/18b) | São Ludgero (18c) | Ziip SAN (RNF, ADR, plano) | Leitura |
|---|---|---|---|---|
| Disponibilidade da nuvem | ≥ 95% ao mês; desconto de 5% a 30%; 100% abaixo de 60% (TR-093–094) | — | RNF-10: 99,5% backoffice | Atende com folga |
| Disponibilidade do sistema | 99% em horário comercial (07h–11h30, 13h–17h30); 5% por faixa de 10%, até 30% (TR-1089) | 99,9% geral; 5% por faixa de até 10% abaixo de 99,9%, cumulativa (SG-1313) | RNF-10: 99,5% backoffice; RNF-11: 99,9% portal; janela de domingo 0h–4h fora do cálculo (RNF-12); ADR-0012 rejeitou 99,95% | Videira atendida. No SG, 99,5% fica abaixo da meta: se a faixa for lida em pontos percentuais, a multa de 5% incide todo mês em que a disponibilidade ficar entre 89,9% e 99,9%. Mudar a meta exige novo ADR (ADR-0012, item 4) |
| Monitoramento | Proativo e automatizado (TR-1089) | 24/7 proativo (SG-1312) | P0.8, P3.11, RNF-53 (burn rate), RNF-54 (plantão) | Ferramenta planejada; plantão 24/7 sem gente |
| Severidade 1 | Solução em 2 h úteis | Solução em 2 h úteis; aderência 95%; multa 10% | ADR-0020: P1 para faturamento ou portal parado, prazo pela política do contrato, sem número | Lacuna: nenhum prazo de solução definido; 2 h exige plantão técnico |
| Severidades 2 a 5 | 4 / 6 / 16 / 32 h úteis; multa de 5% cada | 4 / 16 / 72 / 160 h; aderência 95 / 90 / 80 / 70%; multa 10 / 5 / 5 / 5% | Quatro prioridades (P1–P4) | Faltam um nível e a matriz impacto × criticidade |
| Janela de suporte | 07h30–17h30; prazos contados das 08h às 17h | 8h–18h | Calendário útil por contrato com feriado municipal (P2.15) | Compatível depois da P2.15 |
| Canais | Portal, telefone no Brasil, canal alternativo | Idem | Chamados no produto (P1.23); e-mail na P4.8; sem telefone | Lacuna: telefone e canal de contingência |
| Desempenho on-line | ≤ 10 s, 50 mil usuários, 20 simultâneos | ≤ 10 s, 10 mil consumidores, 20 simultâneos | p95 < 300 ms leitura, < 800 ms gravação, busca < 1 s em 300 mil ligações (RNF-01 a RNF-03) | O RNF é mais rigoroso; cumprido o RNF, cumpre os editais |
| Arquivos e relatórios | ≤ 2 min | ≤ 5 min | Só faturamento: 100 mil contas em < 2 h (RNF-05) | Lacuna: criar RNF de relatório e de arquivo |
| Backup e recuperação | Backup gerenciado, PITR, logs PCI DSS | Backup, restauração, replicação | RPO 15 min, RTO 4 h, restauração trimestral (RNF-14 a RNF-16) | Os editais não fixam RPO/RTO; o RNF os supre |
| Implantação | 90 dias da OS, com migração, GIS, customizações e treinamento | 60 dias, com GIS | Piloto: cerca de 8 meses (P1.1 → GL.4) | Lacuna central de serviço (E8) |
| Customização | ≤ 10% do escopo, na implantação; 200 h a R$ 246,66 | 30 dias por OS; 200 h a R$ 171,69; garantia de 12 meses | Não definido | Política comercial (E10) |
| Correção de vícios | — | 48 h | Não definido | E10 |
| Treinamento | Presencial, ≥ 40 h | Presencial, com pré-treinamento web | Ajuda online (P1.21) | Lacuna de serviço |
| Exportação no fim | 10 dias úteis, CSV/JSON/XML, dicionário | Vedada retenção após o fim | 15 dias, formato aberto, manifesto e hash (RNF-42) | Ajustar prazo e incluir dicionário |
| Pós-contrato | Consulta por 120 dias, 1 usuário | — | Carência de 30 dias e eliminação até 60 dias (RNF-42) | Conflito; decisão 6 |
| Navegadores | Edge 88+, Chrome 100+, Firefox 116+ | Últimas versões | Duas últimas versões (RNF-64) | Incluir as mínimas de Videira na matriz |
| Acessibilidade | — | App com acessibilidade do dispositivo | WCAG 2.1 AA e eMAG 3.1 (RNF-60) | O RNF supera |
| Porte | ≈ 17 mil ligações | Base de POC de 10 mil | Pequeno até 20 mil ligações (ADR-0005) | Ambos no porte pequeno, infraestrutura compartilhada |
| POC | 5 dias úteis após a adjudicação | Convocação com 7 dias úteis | Fora do escopo (diário §1) | Ver 3.5 |

### 3.2 Decisões já tomadas que os editais tocam

| Decisão | Efeito dos editais |
|---|---|
| ADR-0011 (hospedagem a decidir) | Videira elimina provedor sem ISO 9001, 27001, 27017, SOC 1 e SOC 3 e descreve serviços de catálogo de hiperescalador. O SG não exige certificação, mas exige 99,9%, monitoramento 24/7 e administração do banco pela contratada. Ver 3.3 |
| ADR-0012 (metas) | 99,5% no backoffice basta para Videira e não basta para o SG; relatórios e arquivos não têm meta |
| ADR-0010 (infra congelada) | Compatível: os editais pedem que o provedor ofereça cache, filas e afins, não que o produto os use. O Kubernetes arquivado não faz falta, mas a versão instalável (3.4) precisará de um empacotamento próprio |
| ADR-0019 (cobrança por contrato) | Alinhado: vigência, aditivos, empenho, reajuste por apostilamento, pagamento após liquidação, nenhum bloqueio automático. Falta incluir retenções na NFS-e da venda |
| ADR-0020 (suporte com protocolo e SLA) | Base correta (protocolo, prazos por contrato, pausa, escalonamento), mas com quatro prioridades, sem telefone e sem prazo de solução numérico |
| ADR-0018 (Central só do operador) | Os editais pedem que o órgão veja usuários conectados, logs e indicadores de uso; hoje nada disso é exposto ao tenant |
| ADR-0015 / RNF-42 (eliminação) | Conflita com a consulta pós-contrato de 120 dias de Videira |
| ADR-0005 (porte) | Os dois órgãos são de porte pequeno, adequados ao schema por tenant em cluster compartilhado |
| Diário §1 (POC e editais fora do escopo) | Ver 3.5 |

### 3.3 Hospedagem: quais opções atendem às certificações exigidas

| Opção do ADR-0011 | ISO 9001, 27001, 27017, SOC 1, SOC 3 | Serviços do TR 4.4.20–4.12 | Residência no Brasil | Situação para editais do tipo Videira |
|---|---|---|---|---|
| AWS `sa-east-1` gerenciado | Publicados pelo provedor, com escopo que inclui a região de São Paulo (conferir no portal de conformidade na data da proposta) | Backup gerenciado, banco com PITR, autoescala, cache, detecção de ameaças, inspeção de vulnerabilidades e registro de configuração no catálogo | Uma região no país; alta disponibilidade entre zonas da mesma região; nenhuma réplica precisa sair do Brasil | Atende |
| Azure Brasil | Publicados pelo provedor (conferir o escopo da região) | Equivalentes no catálogo | Duas regiões no país, mas o backup geo-redundante pareia fora do Brasil (ARQUITETURA-ALVO §7); exige redundância local ou por zona | Atende, com a restrição de backup |
| VPS ou servidor próprio | Em geral sem SOC 1/ISAE 3402 e SOC 3; a VPS do ecossistema Ziip Cloud não serve de comprovação | Backup, PITR e monitoramento operados pela equipe; sem console gerenciado de backup | Depende do datacenter | Eliminado em Videira (TR-078). Possível no SG, que não exige certificação, ao custo de operar HA, backup e plantão |

Google Cloud e OCI também têm região no Brasil, mas não estão no ADR-0011; antes de considerá-los, conferir item a
item, em especial a ISO 9001. As certificações valem para o provedor; nenhum dos editais exige ISO 27001 da empresa
licitante, que o plano deixa para 2028.

Custo: o dimensionamento inicial da ARQUITETURA-ALVO §6 (api 3 × 2 vCPU, worker 2 × 2 vCPU, Keycloak 2 × 1 vCPU,
PostgreSQL 4 vCPU/16 GB com standby e réplica) fica, em estimativa grosseira deste levantamento, na ordem de
US$ 2 mil por mês num hiperescalador, valor a substituir pela tabela de custo do ADR-0011. É mais que a mensalidade
inteira de referência do SG (R$ 7.636,74): o modelo só fecha com vários tenants no mesmo cluster ou com um
dimensionamento menor para os primeiros clientes.

### 3.4 Versão instalável (on-premise) × SaaS puro

| Edital | Cláusula | O que exige |
|---|---|---|
| Videira | TR 4.4.12 (TR-067, ED-067) | A qualquer tempo, por desempenho insatisfatório, a VISAN assume a infraestrutura; a contratada detalha requisitos, prepara equipamentos e migra; a proposta traz o percentual de dedução da mensalidade |
| Videira | TR 4.15 (TR-104) | Acesso administrativo da TI ao ambiente de nuvem, "sempre que possível" |
| SG | 2.2.3.4 (SG-020) | Licenças instaláveis em qualquer servidor físico ou virtual, sem variação de custo |
| SG | 2.1.3 e 2.2.3.3 (SG-004, SG-019) | Requisitos de hardware na proposta; Windows Server 2022+ e Linux kernel 5+ |

O plano é neutro quanto a provedor (ADR-0011) e prevê imagem única de container (P0.5), o que torna possível uma
distribuição para um só tenant: imagem da aplicação, PostgreSQL 17 com PostGIS, Keycloak e um cofre próprio (a coluna
"VPS ou servidor próprio" da ARQUITETURA-ALVO §7 já descreve os componentes). O que falta é empacotar, documentar
requisitos, definir quem atualiza e como o SLA fica quando a infraestrutura é do órgão. Instalar fora da nuvem do
Ziip tira a atualização contínua e a medição de disponibilidade das mãos do Ziip; por isso a recomendação (decisão 3)
é oferecer instância dedicada gerenciada e manter a distribuição instalável como cláusula de reversão, não como
produto de prateleira.

### 3.5 Impacto de "POC e editais fora do escopo" (diário §1)

- Nos dois editais a POC é eliminatória e acontece depois dos lances: perder a POC entrega o contrato ao segundo
  colocado, e o SG desclassifica na hora quem falhar em item crítico, sem chance de corrigir na implantação.
- O roteiro de Videira (81 itens) e a lista crítica do SG cobrem quase todo o produto, inclusive apps Android e iOS,
  impressão térmica, omnichannel com IA, BI e SIG. Pelo plano v2.2, ao fim do P3 (maio de 2027) o Ziip SAN terá
  cadastro, ciclo, NFAg-e, arrecadação, portal e R7 mínimo, o que cobre parte dos grupos c a o do roteiro de Videira;
  ficam fora os grupos p (app de OS), q (app do usuário), s (omnichannel) e t (BI), e o SIG.
- Sem um ambiente de demonstração permanente, com massa sintética de pelo menos 10 mil unidades, o prazo de 5 ou 7
  dias úteis não basta para montar a POC.
- Manter POC e editais fora do escopo é coerente com o caminho crítico do piloto, mas significa que o Ziip SAN não
  disputa licitações deste perfil antes de 2028. A decisão 1 propõe trazer a prontidão de POC para o plano sem mexer
  no caminho crítico.

---

## 4. Barreiras de habilitação e caminhos

### 4.1 Barreiras

| # | Barreira | Videira | São Ludgero | Peso |
|---|---|---|---|---|
| H1 | Atestado de capacidade técnica | 2 atestados, prestadora de água, ≥ 8.500 ligações somadas, implantação, suporte, treinamento e relatórios do e-Sfinge | 1 atestado compatível, sem quantitativo | Impeditiva nos dois |
| H2 | POC eliminatória com produto completo | 81 itens, ≥ 90% | 80 itens, binário, críticos eliminatórios | Impeditiva até o produto existir |
| H3 | Provedor certificado | ISO 9001, 27001, 27017, SOC 1, SOC 3 | Não exigido | Resolvida pela escolha da hospedagem |
| H4 | Lote completo | Omnichannel, BI e SIG obrigatórios | Idem, mais laboratório e CTI | Impeditiva sem os módulos |
| H5 | Balanço de 2 exercícios | Exigido | Exigido, com exceção para empresa nova | Depende da empresa |
| H6 | Implantação e equipe presencial | 90 dias, 2 especialistas, técnico por 30 dias | 60 dias, especialistas, 2 técnicos por 30 dias | Não impede a habilitação, mas leva a sanção |
| H7 | Registro no INPI e vedação a código aberto | No ETP, não no edital | — | Baixa; resolve-se com registro |

### 4.2 Caminhos

| Caminho | Como funciona | Serve para | Limites |
|---|---|---|---|
| Consórcio | Ziip SAN e empresa com atestado; Videira soma quantitativos e valores (3.10); o SG não proíbe | Habilitar já, em editais que admitem consórcio | O parceiro com atestado costuma ser concorrente e ter produto próprio; Videira exige software de autoria própria e veda subcontratação, então o parceiro entra pela habilitação, implantação e suporte, e o software é o do Ziip; responsabilidade solidária; liderança a negociar |
| Piloto que gere atestado | O piloto do plano (P0.30 → GL.4) contratado formalmente, com atestado emitido após os ciclos em produção | Editais do tipo SG (1 atestado, sem quantitativo) a partir do fim de 2027 | Gera um atestado só; para Videira faltariam o segundo, as 8.500 ligações e o e-Sfinge (o piloto teria de ser autarquia de SC e o e-Sfinge tributário teria de existir) |
| Contratação do piloto | Dispensa por valor (art. 75, II, Lei 14.133, limite atualizado anualmente por decreto) ou Contrato Público de Solução Inovadora (LC 182/2021), que admite teste de solução em desenvolvimento | Dar base legal ao piloto e ao atestado | A dispensa limita o valor anual; o CPSI exige edital próprio do órgão |
| Cliente privado | Concessionária ou prestadora privada; os dois editais aceitam atestado de pessoa jurídica de direito privado | Editais do tipo SG | Não traz e-Sfinge; o mercado privado de pequeno porte é menor |
| Impugnação | Contestar o e-Sfinge no atestado e o piso de 50% (Videira) como restritivos | Abrir editais futuros do mesmo órgão | Não habilita ninguém; só reduz a exigência, e o resultado é incerto |
| Incorporação de empresa com acervo | Atestado acompanha a pessoa jurídica | Habilitar sem esperar | Custo e risco societário; fora do perfil do projeto |

### 4.3 Recomendação

1. **Primeiro contrato por piloto público em Santa Catarina**, autarquia de 5 a 20 mil ligações, contratada por
   dispensa ou CPSI, com cláusula de emissão do atestado após os ciclos paralelos. O critério deve entrar na escolha do
   piloto (P0.30, até 13/11/2026). Uma autarquia catarinense também abre o caminho do e-Sfinge.
2. **Segundo atestado com cliente privado ou segundo piloto** em 2028, para chegar aos dois atestados e ao somatório
   de ligações de editais como o de Videira.
3. **Consórcio só oportunista**, quando um edital próximo admitir somatório e houver parceiro de implantação que não
   concorra com produto próprio. Não planejar o roadmap em torno dele.
4. **Em paralelo e já**: registro no INPI, kit de habilitação, escrituração contábil e escolha de hospedagem
   certificada, que custam pouco e removem H3, H5 e H7.
5. **Não disputar editais do tipo Videira ou SG antes da prontidão de POC** (E3); o custo de perder a POC inclui
   sanção por não manter a proposta.

---

## 5. Requisitos não planejados agrupados em entregas

| # | Entrega | Linhas da matriz | Fase sugerida | Esf. | Dependências |
|---|---|---|---|---|---|
| E1 | **Kit de habilitação e propriedade intelectual**: empresa licitante definida, certidões, balanço, declarações, e-CNPJ, registro do programa no INPI, cláusula-padrão de licença das customizações, pacote de prova do vínculo com o provedor | M03, M05, M07, M12, M13 | P0, em paralelo, fora do caminho crítico | P | Jurídico e contador (revisão 17, 17:X5); hospedagem (P0.26) para M13 |
| E2 | **Hospedagem certificada e segurança do provedor**: certificações como requisito do ADR-0011; serviços nativos de detecção de ameaças, inspeção de vulnerabilidades e registro de configuração; backup sem cópia fora do Brasil | M40, M43 (e reforço de M38, M41, M42) | P0 (P0.26 a P0.28) | M | ADR-0011 |
| E3 | **Prontidão de POC**: tenant de demonstração permanente com massa sintética ≥ 10 mil unidades (reaproveita P0.23 e RNF-70); roteiro interno a partir do Anexo II de Videira e da lista crítica do SG; ensaio cronometrado ao fim de cada fase; kit de campo (Android e impressora térmica) | M22, M23, M24, M25 | Ensaios parciais no fim de P2 e P3; prontidão plena em P4 | G | Módulos funcionais; ambiente de homologação (P0.27) |
| E4 | **Instância dedicada e distribuição instalável**: pacote com imagens, compose ou k3s, PostgreSQL com PostGIS, Keycloak e cofre; requisitos de hardware; runbook de migração da nuvem do Ziip para o órgão; percentual de dedução; acesso administrativo só na instância dedicada | M35, M36, M44 | P3, depois da P0.27 | G | P0.5, P0.27, R1 neutro (ADR-0011) |
| E5 | **Controle de acesso de edital**: política de bloqueio e expiração por tenant; federação LDAP/AD por organização; perfis compostos definidos pelo administrador; sessões ativas e trilha visíveis ao tenant; consulta da trilha por usuário | M59, M62, M63, M68 (consulta), M69 | P1, depois da P0.13 | G | P0.13, P0.15, P0.43, P1.7 |
| E6 | **Monitoramento e indicadores para o cliente**: eventos de negócio com alerta ao usuário; painel de uso e desempenho do tenant; alerta de lote ao responsável do tenant; RNF de relatórios e arquivos | M49, M50, M51, M52 | P2–P3 | M | P1.9, P1.17, P2.1, P3.5 |
| E7 | **SLA de suporte compatível com editais**: cinco severidades por impacto × criticidade; prazo de solução; calendário por contrato; telefone e canal de contingência; aderência por severidade e cálculo de multa no relatório; escala de plantão | M94, M96, M97 (complementa M93, M95) | P2 (ampliar a P2.15) e P3 (operação) | M | P1.23; adendo ao ADR-0020; decisão 5 |
| E8 | **Serviços de implantação padronizados**: metodologia e plano-modelo; importadores por legado (GSAN primeiro, se confirmado); promoção de parâmetros entre ambientes; integração com sistemas do órgão orçada por cliente; migração DWG→PostGIS com diagnóstico topológico e acesso seguro pelo QGIS; dicionário de dados gerado do schema; linhagem da migração | M30, M78, M79, M81, M83, M84, M87, M88 | P2–P3 para importadores e dicionário; P4 para DWG e SIG | G | P1.2, P2.10, módulo SIG (R9) |
| E9 | **Saída de dados de edital**: exportação a qualquer momento; prazo de 10 dias úteis; CSV, JSON ou XML com dicionário; consulta somente leitura pós-contrato por prazo contratual (até 120 dias) antes da carência | M75, M76, M77 | P1 (P1.6) e P3 (P3.16) | M | Adendo ao ADR-0015 e ao RNF-42 |
| E10 | **Política comercial de manutenção e customização**: tabela de perfis e homem-hora; banco de horas; garantia de 12 meses; correção de vícios em 48 h; regra de customização por parâmetro ou flag, sem bifurcar o código; modelo de custo e preço por porte | M14, M15, M19, M20, M100 | P3, junto da P3.9 | P | P3.9; tabela de custo do ADR-0011 |
| E11 | **Rede de implantação e treinamento**: especialistas presenciais, técnico pós-implantação, instrutores, plano de treinamento de 40 h, material, ambiente de pré-treinamento por tenant, capacitação de TI, documentação de customizações | M85, M86, M89, M90, M91, M92 | Antes do primeiro contrato por licitação (P3–P4) | G | Decisão 5 |
| E12 | **Compatibilidade técnica de edital**: navegadores mínimos de Videira na matriz E2E; seletor de tamanho e salto de página conciliado com keyset; trilha por campo preservada nas alterações manuais (revisão da P2.3); argumento técnico sobre gatilhos e linguagem | M27, M28, M33, M68 | P1 (P1.12) e P2 (P2.3) | P | P0.22, P1.12 |

Fora desta área, e pré-requisito de qualquer licitação de lote único: omnichannel, BI, SIG, laboratório, apps
Android e iOS e impressão térmica (M02, M31, M67, M70, M71, M82), tratados nas matrizes funcionais.

---

## 6. Decisões do dono do produto

| # | Decisão | Opções | Recomendada |
|---|---|---|---|
| 1 | **POC passa a ser escopo?** | (a) Manter fora até o go-live, como no diário §1; (b) incluir a prontidão de POC (E3) como critério de aceite de fase a partir do P3, sem alterar o caminho crítico; (c) reordenar o roadmap para disputar editais em 2027 | **(b)**: o piloto continua prioritário, e cada fase passa a medir quanto do roteiro de Videira e da lista crítica do SG já passaria |
| 2 | **Hospedagem: certificação como requisito** | (a) AWS `sa-east-1` gerenciado; (b) Azure Brasil, com backup só local ou por zona; (c) VPS ou servidor próprio | **(a)**: atende às cinco certificações e aos serviços do TR sem a ressalva de geo-redundância; incluir as certificações como requisito no ADR-0011 até 30/10/2026. (c) elimina em editais do tipo Videira |
| 3 | **Versão instalável** | (a) SaaS puro, sem cláusula de reversão (deixar de disputar ou impugnar TR 4.4.12 e SG 2.2.3.4); (b) instância dedicada gerenciada, com distribuição instalável só como reversão contratual e percentual de dedução definido; (c) produto instalável de prateleira | **(b)**: responde às duas cláusulas sem abrir mão da operação; entrega E4 no P3 |
| 4 | **Estratégia de atestado** | (a) Piloto público em autarquia catarinense, por dispensa ou CPSI, com atestado contratado; (b) consórcio com detentor de atestado; (c) cliente privado | **(a)**, complementada por (c) para o segundo atestado; (b) só oportunista. Exige incluir "SC, prestadora de água e atestado" nos critérios da P0.30 |
| 5 | **Equipe de implantação e plantão** | (a) Equipe própria (2 especialistas, instrutor e plantão para severidade 1 em 2 h) contratada antes do primeiro contrato por licitação; (b) parceiro regional de implantação e treinamento, com suporte N2/N3 e plantão próprios; (c) operação remota e impugnação da presença física | **(b)**, com a ressalva de que em Videira a subcontratação é vedada e o parceiro só entra por consórcio ou contratação direta das pessoas. O plano hoje supõe "uma pessoa com IA" (plano §6), incompatível com 2 h de solução |
| 6 | **Metas contratuais de edital** (altera ADR-0012, ADR-0020 e RNF-42) | (a) Manter 99,5%, quatro prioridades e eliminação em 60 dias, e recusar cláusulas mais exigentes; (b) criar um nível de serviço contratável por tenant: 99,9% com janelas agendadas fora do cálculo, cinco severidades com prazo de solução, exportação em 10 dias úteis e consulta somente leitura pós-contrato de até 120 dias antes da carência; (c) adotar esses valores como padrão para todos | **(b)**: o padrão do produto continua o do ADR-0012; o contrato escolhe o nível, e a arquitetura-alvo (standby síncrono, 3 réplicas, Keycloak duplicado) já comporta 99,9% |
