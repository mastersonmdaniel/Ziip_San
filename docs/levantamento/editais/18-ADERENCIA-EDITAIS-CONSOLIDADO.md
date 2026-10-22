# 18 — Aderência aos editais: consolidado

Data: 12/10/2026. Produto Ziip SAN (código interno SIGSAN, renomeado `ziipsan` na P0.33). Consolida as extrações
[18a](18a-videira-termo-de-referencia.md) (VISAN Videira/SC, PE 06/2026, Termo de Referência, IDs `TR-`),
[18b](18b-videira-edital-etp-precos.md) (VISAN, edital, ETP e pesquisa de preços, IDs `ED-`, `ETP-`, `PP-`) e
[18c](18c-sigcos-pe-2-2026.md) (SAMAE São Ludgero/SC, PE 2/2026, IDs `SG-`), e as cinco matrizes de aderência:
[18d](18d-aderencia-cadastro-medicao-gis.md) (cadastro, medição e GIS), [18e](18e-aderencia-faturamento-arrecadacao-cobranca.md)
(faturamento, arrecadação, cobrança e contabilidade), [18f](18f-aderencia-atendimento-os-portal.md) (atendimento, OS,
portal e canais), [18g](18g-aderencia-operacao-qualidade-bi.md) (operação, qualidade da água e BI) e
[18h](18h-aderencia-nao-funcionais-servicos-habilitacao.md) (não funcionais, serviços, POC e habilitação).

As matrizes foram feitas contra o plano v2.2. Este documento as lê contra o plano v2.4, atualizado pelas v2.5 a v2.12 (abaixo)
([PLANO-EXECUCAO-PDCA-R0-R9.md](../../PLANO-EXECUCAO-PDCA-R0-R9.md)) e contra as decisões do dono do produto de
12/10/2026, registradas nos ADRs [0011](../../arquitetura/adr/ADR-0011-hospedagem-a-decidir.md) (aceito),
[0023](../../arquitetura/adr/ADR-0023-estrategia-de-editais-e-prova-de-conceito.md),
[0024](../../arquitetura/adr/ADR-0024-instancia-dedicada-e-clausula-de-reversao.md),
[0025](../../arquitetura/adr/ADR-0025-nivel-de-servico-setor-publico-contratavel.md),
[0026](../../arquitetura/adr/ADR-0026-apps-hibridos-com-capacitor.md),
[0027](../../arquitetura/adr/ADR-0027-bi-hibrido-com-apache-superset.md) e
[0028](../../arquitetura/adr/ADR-0028-escopo-funcional-ampliado-pelos-editais.md). As matrizes 18d a 18h ficam como
estão, como linha de base; a situação viva passa a ser mantida pelo processo do §7.

**Atualização de 12/10/2026 (plano v2.5).** Aplicadas as decisões do dono do produto registradas nos ADRs
[0029](../../arquitetura/adr/ADR-0029-capacidade-e-equipe-da-trilha-de-editais.md) (equipe da trilha),
[0030](../../arquitetura/adr/ADR-0030-autenticacao-nos-apps.md) (autenticação nos apps),
[0031](../../arquitetura/adr/ADR-0031-calculo-offline-e-nota-fiscal-na-emissao-em-campo.md) (emissão em campo) e
[0032](../../arquitetura/adr/ADR-0032-material-da-os-com-alcada-e-baixa-automatica.md) (material da OS com alçada) e
nos ajustes da v2.5 dos ADRs 0024 (Windows Server) e 0028 (ERP no recorte "núcleo saneamento + integração", conectores
Messenger, Instagram e Telegram, FEBRABAN 150 condicional ao piloto). Mudaram as entregas ED-04, ED-11, ED-18, ED-19,
ED-20, ED-27, ED-36, ED-39, ED-42, ED-58, ED-59 e ED-62; ED-60, ED-61 e a parte de compras da ED-58 saem da trilha
para 2029. O esforço (§5.2) e a capacidade (§8.1) foram recalculados.

**Atualização de 12/10/2026 (plano v2.6).** Por decisão do dono do produto, a ED-58 (almoxarifado com reserva, alçada
e baixa automática, ADR-0032) passa da onda C para a onda A, junto da ED-36 (OS completa), para que a requisição de
material da OS afete o estoque desde a primeira onda; compras continua em 2029. Onda A de 108,9 para 116,4
semanas-pessoa, onda C de 22,9 para 15,4, trilha igual (206,3); prontidão de POC de cerca de 192 para cerca de 199
semanas-pessoa e necessidade para junho de 2028 de cerca de 4,5 para 4,6 pessoas-equivalentes (§5.2, §5.4 e §8.1).
Mudaram as ED-20, ED-36, ED-58 e ED-62 (dependências) e o item 2.17 do SAMAE (§3.2).

**Atualização de 12/10/2026 (plano v2.7).** Por decisão do dono do produto (ADR-0033), todo o desenvolvimento passa a
ser local até concluir as fases, e a AWS `sa-east-1` (ADR-0011, provedor mantido) só sobe no lançamento comercial,
nova fase do plano depois do P4. O go-live do piloto de jun–jul/2027 vira homologação local, com dados sintéticos ou
anonimizados e sem dado pessoal real; produção real, emissão fiscal em produção e atestado só acontecem no lançamento.
Efeitos neste documento: o primeiro atestado depende do lançamento e de uma primeira operação real (§1, §4); a POC das
licitações, que exige o sistema em nuvem, só é disputável depois do lançamento (§3.3); o ED-03 passa ao lançamento e o
ED-04 tem a instância dedicada depois dele (§5.1); a meta de licitações no 2º semestre de 2028 só se mantém se o dono
escolher lançar logo depois das tarefas originais do P4 (opção B do plano §2.4); na leitura literal da decisão (opção
A), a primeira licitação com POC vai para o 3º trimestre de 2029 (§8).

**Atualização de 12/10/2026 (plano v2.8).** O dono do produto escolheu a opção A do plano §2.4 (ADR-0033, ajuste
v2.8): lançamento comercial depois de todas as fases, inclusive a trilha de editais, a partir de outubro de 2028;
primeira produção do piloto por volta de fevereiro de 2029; primeiro atestado entre maio e julho de 2029; licitações
com POC a partir do 3º trimestre de 2029. A meta de disputar no 2º semestre de 2028 deixa de valer. A contratação do
ADR-0029 não muda; o limite passa a ser a trilha completa até 30/09/2028, com necessidade menor que a da prontidão em
junho (§8.1). Efeitos neste documento: §1, §4 (H5 e sequência), §8.1 (capacidade na opção A e recomendações) e §8.2.

**Atualização de 12/10/2026 (plano v2.9).** O dono do produto fixou a equipe da trilha em 4 contratações a partir de
agosto de 2027 (ADR-0029, ajuste v2.9). Com o lançamento em outubro de 2028, a trilha completa (cerca de 215
semanas-pessoa) até 30/09/2028 tem folga de cerca de 15%, dentro da incerteza de ±30%, e a prontidão de POC fica por
volta do fim de julho de 2028 (faixa de junho a agosto). Se a velocidade medida de agosto a outubro de 2027 ficar abaixo
de 90% da planejada, a quinta pessoa é contratada no meio do caminho. Efeitos neste documento: §1, §4 (H6), §8.1
(tabela da opção A, gatilho e recomendação 2) e §8.2.

**Atualização de 12/10/2026 (plano v2.10).** Oito decisões do dono do produto sobre o GIS e o cadastro técnico
([ADR-0034](../../arquitetura/adr/ADR-0034-gis-cadastro-tecnico.md); projeto em
[GIS-CADASTRO-TECNICO.md](../../arquitetura/GIS-CADASTRO-TECNICO.md), com as entregas GIS-01 a GIS-19). O núcleo do GIS
(modelo, catálogo, topologia, pacotes revisados, vínculo com imóvel, ligação e ramal, mapa web de consulta, importador
e temáticos, cerca de 22 semanas-pessoa) passa para o P1 a P3, antes da homologação local do piloto: a ED-52 sai da
onda B, a parte de migração da ED-55 vai à P3.26 e a base da ligação da ED-13 (0,5) vai à P2.23. A ED-53 passa à onda
A; a ED-54 (4 → 5), a ED-55 (4 → 5 na trilha, agora QGIS e importação ampliada) e a ED-56 (3 → 4, com a NR 16/2026)
crescem; entram as ED-63 (EPANET e SWMM), ED-64 (drenagem), ED-65 (resíduos sólidos), ED-66 (campo GIS) e ED-67
(exportação, devolução da base e OGC), todas na onda B. Antes do P4, cerca de 35,3 semanas-pessoa (antes 13,3); onda
A, 120,9 (antes 116,4); B, 87,5 (antes 74,5); C, 16,4 (antes 15,4); trilha, 224,8 (antes 206,3); até 2028, 260,1
(antes 219,6). Prontidão de POC em cerca de 216,8 (antes 199) e trilha completa com as tarefas originais do P4 em cerca
de 233,2 (antes 215): com 4 contratados, folga de cerca de 6% até 30/09/2028 (antes 15%) e prontidão por volta de
meados de agosto de 2028. Efeitos neste documento: §1, §3.2, §5.1 (ED-13, ED-20, ED-52 a ED-56, ED-63 a ED-67), §5.2,
§5.3, §5.4, §8.1 e §8.2.

**Atualização de 12/10/2026 (plano v2.11).** O dono do produto resolveu as pendências do GIS (ADR-0029, ADR-0033 e
ADR-0034, ajustes v2.11). O perfil 3 do ADR-0029 (GIS e dados) é antecipado para dez/2026–jan/2027 e executa o núcleo
do GIS no P1 a P3, como uma das 4 contratações; com o onboarding, entrega cerca de 16 das 22 semanas-pessoa do núcleo
até o fim do P3, e o restante (cerca de 6) termina por volta do início de julho de 2027, durante a homologação local,
cujas datas não mudam. Na trilha, ele chega integrado: capacidade de cerca de 251 semanas-pessoa até 30/09/2028 (antes
247). Os limiares do gatilho da quinta contratação passam a 95% e 85%. A base da ligação na P2.23 fica confirmada; a
rede real do piloto (redes, peças e estruturas) entra na homologação local, com ligações e imóveis anonimizados; fora do
backoffice autenticado, imóveis e ligações só agregados por quadra ou setor com no mínimo 5 imóveis; as normas ABNT são
compradas no P1; o DGN v8 entra por conversão assistida; e a publicação OGC autenticada por tenant (OGC API – Features e
WMS) fica na onda B, com o WMS servidor como escopo novo da ED-67 (5 → 6). Onda B de 87,5 para 88,5; trilha de 224,8
para 225,8; até 2028, de 260,1 para 261,1; prontidão de POC em cerca de 217,8 e trilha completa com as tarefas
originais do P4 em cerca de 234,2: folga de cerca de 7% até 30/09/2028 e prontidão por volta de meados de agosto de
2028. Efeitos neste documento: §1, §3.2, §4 (H6), §5.1 (ED-13, ED-55, ED-67), §5.2, §5.4, §8.1 e §8.2.

**Atualização de 12/10/2026 (plano v2.12).** Duas decisões do dono do produto. (1) Gestão de equipe e capacidade sai do
plano: o ADR-0029 fica revogado, e este documento passa a tratar só do que será feito e de como, com o esforço por
entrega e por onda como indicação, sem conversão em equipe. Saem as análises de capacidade, as tabelas por número de
contratados, o gatilho da quinta contratação e os riscos de equipe (§1, §4 H6 e H9, §5.2, §5.4, §7.3, §8.1 e §8.2); a
ED-11 fica com o plano, o material e o ambiente de treinamento. As datas de fases e marcos são metas de calendário do
plano, e o núcleo do GIS continua no P1 a P3. As menções a equipe e capacidade nas atualizações anteriores ficam só como
registro. (2) O WMS servidor da ED-67 passa a ser publicado pelo GeoServer (exceção no ADR-0010; ADR-0034, ajuste "GIS,
v2.12"), com workspace e acesso ao banco por tenant e simbologia em SLD gerado da fonte única; OGC API – Features e
WFS continuam na `api`; estimativa mantida (§5.1).

**Convenções.** Esforço na escala do plano (§4): **P** até 2 dias, **M** até 1 semana, **G** mais de 1 semana, para uma
pessoa com agentes. Para somar, P vale 0,4 semana, M vale 1 semana e G vale a estimativa da matriz de origem ou, na
falta dela, 3 semanas; incerteza de ±30%. Um mês-pessoa equivale a 4,33 semanas. Entregas de edital levam o código
**ED-nn** (não confundir com os IDs `ED-0nn` do edital de Videira, que têm três dígitos). Os códigos de origem são
citados com o prefixo da matriz: `18d-E4`, `18e-E1`, `18f-ENT-03`, `18g-E-REL`, `18h-E3`.

---

## 1. Sumário executivo

**Veredito.** Hoje o Ziip SAN reprova nas duas provas de conceito. Dos 2.663 requisitos distintos dos dois editais,
91 (3%) são atendidos pelo código, 560 (21%) em parte e 2.010 (75%) não. Na POC da VISAN o produto faria hoje cerca
de 5 dos 162 pontos (a aprovação exige 146); na do SAMAE falharia em todos os 23 grupos de itens críticos, e qualquer
falha crítica desclassifica sem chance de correção.

**Ao fim do P3,** com as tarefas que o plano v2.4 acrescenta ao P2 e ao P3, a estimativa sobe para cerca de 60 pontos
na VISAN (37%), e só as seções 2.4 (banco) e 2.5 (desempenho) do SAMAE passariam. Pelo plano v2.3, o piloto iria ao
go-live sem retificação de conta e sem relatório operacional, duas lacunas que as matrizes mostraram e que o plano v2.4
corrige na P2.20 e na P3.22.

**Meta do plano v2.4.** Aderência total aos dois editais pela trilha de editais do P4 (ADR-0023), com 62 entregas
deduplicadas (ED-01 a ED-62, §5), para disputar licitações em 2028. A prontidão de POC (ensaio interno com pelo menos
146 de 162 pontos na VISAN e nenhuma falha crítica no SAMAE) passa a ser medida em toda fase a partir do P1 e entra no
aceite de fase a partir do P3. O marco de 30/07/2027 passou a ser a homologação local do piloto, sem dado real (plano
v2.7, ADR-0033); a primeira produção vem no lançamento comercial. Desde o plano v2.8 (opção A do lançamento), as
licitações com POC começam no 3º trimestre de 2029.

**Esforço.** Com o GIS do ADR-0034 e o WMS da ED-67, o esforço estimado até 2028 é de cerca de 261 semanas-pessoa
(±30%): cerca de 35 antes do P4 (13,3 de acréscimos da v2.4 e 22 do núcleo do GIS, no P1 a P3) e cerca de 226 na
trilha do P4; compras, RH e orçamento (cerca de 9 semanas) passaram para 2029. A prontidão de POC (ondas A e B e as
tarefas originais do P4) soma cerca de 217,8 semanas-pessoa, e a trilha completa com as tarefas originais do P4, cerca
de 234,2 (§5.2, §8.1). As datas são metas de calendário do plano: prontidão de POC por volta de meados de agosto de
2028, trilha completa até 30/09/2028 e lançamento comercial a partir de outubro de 2028; desde a v2.12, o plano não
converte esforço em equipe (ADR-0029, revogado).

**Habilitação.** O primeiro atestado vem do piloto, que precisa ser público, em Santa Catarina, contratado por
dispensa ou por CPSI (LC 182/2021) e com cláusula de atestado (critério já incluído na P0.30). Desde o plano v2.7
(ADR-0033), o atestado só pode vir depois do lançamento comercial e de uma primeira operação real: a homologação local
com dados anonimizados não é execução do objeto. Do início do lançamento ao atestado são de 7 a 9 meses. O dono do
produto escolheu a leitura literal da decisão (opção A do plano §2.4, v2.8): lançamento depois de toda a trilha, a
partir de outubro de 2028, e atestado entre maio e julho de 2029. Com o atestado, editais do tipo SAMAE (um atestado, sem quantitativo) ficam acessíveis, sujeitos à
prontidão de POC. Editais do tipo VISAN (dois atestados, 8.500 ligações e e-Sfinge) exigem ainda o segundo atestado
(cliente privado ou segundo piloto) e a remessa e-Sfinge em operação (§4).

---

## 2. Totais por área e edital

### 2.1 Por matriz e edital, como as matrizes trazem

Contagem por ID de requisito. "Coberto" junta planejado e planejado em parte; "implícito ou indireto" é requisito cuja
fonte de dados ou escopo está numa tarefa que não o nomeia (18e "Implícito"; 18g "Indireto": o plano entrega o dado,
mas não o relatório ou painel); "não planejado" inclui o que o plano v2.2 deixava para 2028. Em 18h a situação no
plano por ID foi apurada por script sobre a matriz (a matriz só a traz por linha); 22 IDs de 18h estão em linhas "sem
ação necessária" e entram em "Coberto".

| Área (matriz) | Edital | Requisitos | Atende | Parcial | Não atende | Coberto | Implícito ou indireto | Não planejado |
|---|---|---|---|---|---|---|---|---|
| Cadastro, medição e GIS (18d) | VISAN TR | 242 | 14 | 77 | 151 | 63 | 0 | 179 |
| | SAMAE SG | 332 | 16 | 105 | 211 | 81 | 0 | 251 |
| | VISAN ED | 2 | 0 | 0 | 2 | 1 | 0 | 1 |
| Faturamento, arrecadação, cobrança e contabilidade (18e) | VISAN TR | 412 | 0 | 70 | 342 | 141 | 60 | 211 |
| | SAMAE SG | 406 | 0 | 68 | 338 | 152 | 63 | 191 |
| | VISAN ED | 3 | 0 | 0 | 3 | 0 | 0 | 3 |
| Atendimento, OS, portal e canais (18f) | VISAN TR | 225 | 3 | 20 | 202 | 84 | 0 | 141 |
| | SAMAE SG | 341 | 4 | 35 | 302 | 124 | 0 | 217 |
| Operação, qualidade e BI (18g) | VISAN TR | 305 | 0 | 8 | 297 | 21 | 204 | 80 |
| | SAMAE SG | 438 | 1 | 34 | 403 | 112 | 203 | 123 |
| | VISAN ED | 5 | 0 | 0 | 5 | 2 | 0 | 3 |
| Não funcionais, serviços e habilitação (18h) | VISAN TR | 177 | 18 | 39 | 120 | 115 | 0 | 62 |
| | SAMAE SG | 232 | 21 | 89 | 122 | 146 | 0 | 86 |
| | VISAN ED | 83 | 9 | 26 | 48 | 48 | 0 | 35 |
| | VISAN ETP | 21 | 9 | 5 | 7 | 15 | 0 | 6 |
| | VISAN PP | 23 | 0 | 23 | 0 | 0 | 0 | 23 |
| **Soma das matrizes** | | **3.247** | **95** | **599** | **2.553** | **1.105** | **530** | **1.612** |

### 2.2 Por edital: soma e requisitos distintos

As matrizes se sobrepõem: relatórios aparecem em 18d, 18e e 18g; revisão de consumo e emissão em campo em 18d e 18e;
ajustes de fatura e débito automático em 18e e 18f. A conferência por script (expansão das faixas de IDs das linhas
das matrizes) mostrou que as cinco matrizes juntas cobrem **todos** os 1.114 requisitos do TR da VISAN e os 1.415 do
SAMAE; 584 citações são repetidas entre matrizes. Na linha "distintos", cada ID entra uma vez, com a situação mais
favorável entre as matrizes em que aparece.

| Edital | Soma das matrizes | Distintos | Atende | Parcial | Não atende | Coberto | Implícito ou indireto | Não planejado |
|---|---|---|---|---|---|---|---|---|
| VISAN, Termo de Referência (TR) | 1.361 | 1.114 | 33 | 201 | 880 | 395 | 191 | 528 |
| SAMAE São Ludgero (SG) | 1.749 | 1.415 | 40 | 305 | 1.070 | 565 | 190 | 660 |
| VISAN, edital (ED) | 93 | 90 (88 com situação) | 9 | 26 | 53 | 51 | 0 | 37 |
| VISAN, ETP | 21 | 21 | 9 | 5 | 7 | 15 | 0 | 6 |
| VISAN, pesquisa de preços (PP) | 23 | 23 | 0 | 23 | 0 | 0 | 0 | 23 |
| **Total** | **3.247** | **2.663 (2.661 com situação)** | **91 (3%)** | **560 (21%)** | **2.010 (75%)** | **1.026 (39%)** | **381 (14%)** | **1.254 (47%)** |

Ficaram fora das matrizes, por não serem requisito do produto, um item do edital, o ETP-001 (resíduos sólidos como
atividade da VISAN, fora do objeto) e o PP-019 (escopo excluído da cotação de um concorrente).

### 2.3 Leitura

1. **A base técnica atende; os módulos não existem.** Quase todos os "Atende" são de arquitetura, segurança de acesso,
   cadastro e cláusulas comerciais. Faturamento, arrecadação e medição têm motor puro sem serviço, API, lote nem tela;
   portal, cobrança, contabilidade, campo, OS, qualidade operacional, GIS de produto, relatórios e BI não existem.
2. **O plano cobre o regulatório do piloto, não o edital.** O que está coberto (39%) é cadastro, ciclo, NFAg-e,
   arrecadação, portal mínimo e R7 mínimo. O não planejado (47%) concentra os módulos que as POCs mais cobram: app de
   campo com emissão simultânea, revisão de consumo, dívida ativa, contabilidade, omnichannel, app do cliente, BI,
   qualidade da água e GIS.
3. **O "implícito ou indireto" (14%) é risco, não cobertura.** São relatórios cujo dado o plano produz, mas que nenhuma
   tarefa entregava, e parâmetros de geração da conta, de corte e de parcelamento que cabem numa tarefa sem estar
   nomeados. A P3.22 (relatórios operacionais) e a P2.20 (retificação de conta) do plano v2.4 resolvem a parte que o
   piloto precisa; o restante está nas ED-46 e ED-29.

---

## 3. Provas de conceito

### 3.1 VISAN: roteiro do Anexo II (81 itens, 162 pontos, mínimo 146)

Regras (18a §3.1; 18b §1.8): POC da vencedora em nuvem própria; 2 pontos por item atendido, 1 por parcial, 0 por não
atendido; aprovação com pelo menos 90% (146 de 162, perda máxima de 16 pontos); a comissão tem 5 dias úteis após a
adjudicação e pode cobrar qualquer funcionalidade dos itens 5.1 a 23.1, que devem ser pelo menos 90% nativas (TR 4.19).
O SIG (item 24) não é avaliado na POC, mas é obrigatório na implantação.

A coluna "Fim do P3" é estimativa sobre o plano v2.4 (com P2.20, P3.22 e P3.23); "pontos estimados" conta 2 por item
demonstrável e 1 por item parcial.

| Alínea | Tema | Itens (pontos) | Hoje | Fim do P3 | Pontos estimados no fim do P3 | Entregas que fecham |
|---|---|---|---|---|---|---|
| a | Arquitetura, interface, banco e hospedagem | 3 (6) | sem ambiente | demonstrável no ensaio local (P0.27); na POC real, só com a nuvem do lançamento (LC.2, ADR-0033) | 6 | ED-03 |
| b | Controle de acesso | 4 (8) | parcial (bloqueio em 5 tentativas no realm, ADMIN global) | demonstrável com a P0.13 e bloqueio de 3 tentativas no realm | 8 | ED-06 (por tenant) |
| c | Cadastros (usuários, endereços, unidades, catálogos) | 4 (8) | parcial (API, tela bloqueada) | parcial (P1.3; faltam RG, filiação, caixa d'água, cisterna, pavimentos, endereço alternativo, tipo de feriado) | 4 | ED-12, ED-13 |
| d | Hidrômetros | 3 (6) | não | não | 0 | ED-14 |
| e | Leitura móvel, impressão, crítica, fatura por e-mail | 7 (14) | não | parcial (importação P2.7, crítica P2.5; sem app nem impressão) | 6 | ED-18, ED-19, ED-41 |
| f | Faturas, tabela tarifária, mensagens | 4 (8) | não | demonstrável (P2.5, P2.8) | 8 | — |
| g | Revisão de consumo por vazamento | 1 (2) | não | não | 0 | ED-23 |
| h | Arrecadação e débito automático FEBRABAN | 3 (6) | não | parcial (P3.1: importação e não identificado) | 4 | ED-27 |
| i | Parcelamento de fatura e de serviço | 2 (4) | não | não (R8 na P4.1) | 0 | P4.1, ED-34 |
| j | Notificações de corte | 2 (4) | não | demonstrável (P3.4) | 4 | ED-29 |
| k | Dívida ativa | 3 (6) | não | não | 0 | ED-31 |
| l | Contabilidade | 4 (8) | não | não | 0 | ED-32 |
| m | Catálogo de serviços | 1 (2) | não | não | 0 | ED-34 |
| n | Ordens de serviço | 5 (10) | não | parcial (OS mínima da P3.4; anexos da P1.20) | 3 | ED-36 |
| o | Atendimento | 5 (10) | não | parcial (protocolo da P3.4; alterar e cancelar fatura pela P2.20) | 5 | ED-35, ED-22 |
| p | App de OS e monitoramento de campo | 3 (6) | não | não | 0 | ED-20 |
| q | App do usuário Android e iOS | 1 (2) | não | não | 0 | ED-39 |
| r | 13 relatórios e portal | 14 (28) | não | parcial (cerca de metade dos relatórios na P3.22; portal da P3.3 e P3.20 sem simulações completas) | 13 | ED-46, ED-38, ED-32 |
| s | Omnichannel, chatbot e IA | 7 (14) | não | não | 0 | ED-42, ED-43 |
| t | Painéis de BI | 5 (10) | não | não | 0 | ED-47, ED-48, ED-49, ED-62 |
| **Total** | | **81 (162)** | **cerca de 5** | | **cerca de 61** | |

Conclusões: (1) as alíneas d, g, i, k, l, m, p, q, s e t somam 60 pontos que nenhuma tarefa até o P3 toca; como a
aprovação admite perder só 16 pontos, as alíneas s e t, sozinhas (24 pontos), já reprovam; (2) mesmo com toda a trilha comercial e de campo (onda A, §5.2) o máximo
possível sem q, s e t é 136 pontos, abaixo de 146: canais digitais e BI (onda B) são obrigatórios para passar.

### 3.2 SAMAE: itens críticos eliminatórios

Regras (18c §3): convocação com 7 dias úteis; POC presencial na sede; sistema completo em nuvem; base de pelo menos 10
mil unidades fictícias ou anonimizadas (descumprir a LGPD desclassifica); celulares, impressora e internet da
licitante; 1 a 2 especialistas; 80 itens escolhidos pela comissão, binários (sem parcial); 90% dos essenciais; sessão
gravada. Falha em qualquer item crítico (Edital 7.7.9, sempre com subitens) desclassifica sem implementação posterior.
Cerca de 1.037 das 1.415 linhas pertencem a seções críticas, e o "Item 1.1.2" declara crítico o TR inteiro.

| Item crítico | Hoje | Fim do P3 (v2.4) | Entregas que fecham |
|---|---|---|---|
| 1.1.2 (todo o TR, inclusive SLA, segurança e LGPD) | falha | falha | todas |
| "Item 0" (todos os módulos em base única, nativos) | falha (BI, GIS, omnichannel e laboratório inexistentes) | falha (núcleo do GIS presente desde o P3, v2.10) | ED-42, ED-47, ED-50, ED-52 (P1 a P3) |
| 2.4 Banco de dados | falha (sem produção) | passa no ensaio local (PostgreSQL 17); na POC real, só com o RDS do lançamento (ADR-0011, ADR-0033) | — |
| 2.5 Desempenho (10 s; arquivos e relatórios em 5 min; alertas) | falha | parcial (P0.23, P2.9, P3.22; faltam alertas ao cliente) | ED-07, ED-05 |
| 2.7 Controle de acesso (inclui Active Directory) | falha | falha (sem AD nem perfis definidos pelo cliente) | ED-06 |
| 2.8 Cadastro | falha (tela bloqueada) | falha (catálogos 2.8.10, ligações; base da ligação na P2.23 desde a v2.10) | ED-12, ED-13, ED-53 (unidades operacionais, SG-148 a SG-152) |
| 2.9 Hidrometria | falha | falha | ED-14, ED-17 |
| 2.10 Leituras e consumos | falha | parcial (P2.5, P2.7) | ED-15, ED-17 |
| 2.11 Leitura e faturamento móvel | falha | falha | ED-18, ED-19, ED-16 |
| 2.12 e 2.12.5 Revisão de consumo, em dispositivo móvel | falha | falha | ED-23, ED-20 |
| 2.13 Faturamento (inclui resíduos sólidos) | falha | falha (resíduos, situações especiais) | ED-22, ED-25, ED-24 |
| 2.14 Arrecadação | falha | parcial (P3.1, P3.2) | ED-26, ED-27 |
| 2.15 Cobrança (negativação, protesto, dívida ativa) | falha | falha | P4.1, ED-29, ED-30, ED-31 |
| 2.16.18 Importação e exportação de lançamentos contábeis | falha | falha | ED-32, ED-62 |
| 2.17 Gestão de serviços (inclui empreiteiras e estoque) | falha | falha | ED-34, ED-36, ED-37, ED-58, ED-62 |
| 2.18 OS móvel | falha | falha | ED-20 |
| 2.19 Atendimento | falha | parcial (P3.4) | ED-35, ED-21 |
| 2.22 Omnichannel | falha | falha | ED-42, ED-43 |
| 2.24 App de autoatendimento nas lojas | falha | falha | ED-39 |
| 2.26 Qualidade da água | falha | falha | ED-50, ED-51 |
| 2.27 SIG/GIS (e implantação do GIS, 3.11.10) | falha | parcial desde a v2.10: mapa, camadas, pesquisas, temáticos, vínculo comercial e importação DWG/SHP (núcleo, P1.26 a P3.27); faltam edição web, QGIS, manobras e OGC autenticado por tenant (OGC API – Features e WFS na `api`, WMS pelo GeoServer desde a v2.12) | ED-52 (P1 a P3), ED-53 a ED-55, ED-63, ED-67 |
| 2.28 Relatórios gerenciais | falha | parcial (P3.22) | ED-46 |
| Item 3 Manutenção e suporte (severidades, telefone, 99,9%) | falha | parcial (P2.15 com cinco severidades, ADR-0025) | ED-05 |

Seções essenciais não críticas (contam nos 90%): 2.1, 2.3, 2.6, 2.16 (salvo 2.16.18), 2.20 portal (ED-38), 2.21 totem
(ED-40) e 2.29 BI (ED-47 a ED-49). Avaliáveis: 2.2, 2.23 IA (ED-43) e 2.25 telefonia (ED-44).

### 3.3 O que é preciso demonstrar

- **Ambiente de demonstração permanente** (ADR-0023), separado de homologação e produção, com tenant de pelo menos 10
  mil unidades fictícias geradas pela massa sintética da P0.23, sem dado pessoal real, recriável a cada fase (P3.23).
  Montar ambiente e massa em 5 ou 7 dias úteis não é viável. Até o lançamento comercial o ambiente é local (ADR-0033),
  o que basta aos ensaios internos; as duas POCs exigem o sistema em nuvem, e a versão na nuvem é criada pela IaC do
  lançamento (plano LC.2) antes da primeira POC de licitação.
- **Roteiros internos** (P1.25): VISAN com os 81 itens e a forma de demonstração de cada um; SAMAE com a lista crítica
  completa, com subitens, e os 80 itens sorteáveis entre os demais; cada item ligado ao requisito, à entrega e à fase.
- **Kit de campo**: aparelhos Android, um iPhone para o app do cliente, impressora Zebra série ZQ com bobinas
  (ADR-0026), acesso 4G próprio; no SAMAE tudo é da licitante.
- **Demonstrações que hoje nenhum concorrente pequeno improvisa**: leitura com emissão e impressão da conta em campo
  (VISAN e; SAMAE 2.11), revisão de consumo no celular sem papel (SAMAE 2.12.5), inscrição em dívida ativa com CDA
  (VISAN k), atendimento por WhatsApp dentro do sistema com IA em linguagem natural (VISAN s), painéis de BI (VISAN t),
  app nas lojas com o nome do prestador (SAMAE 2.24), qualidade da água (SAMAE 2.26) e GIS com edição (SAMAE 2.27).
- **Itens contrários à lei** (§6): demonstrar a regra legal configurada e citar a norma, com pedido de esclarecimento
  protocolado antes da sessão (ADR-0023, item 5).
- **Ensaio cronometrado** ao fim de cada fase (a partir do P1), com o prazo de preparo do edital e o resultado
  registrado; a partir do P3, meta por fase no aceite (plano v2.4, P3.23 e trilha do P4).

---

## 4. Barreiras de habilitação e caminho decidido

| # | Barreira | VISAN | SAMAE | Caminho decidido | Onde está |
|---|---|---|---|---|---|
| H1 | Atestado de capacidade técnica | 2 atestados em prestadora de água, ≥ 8.500 ligações somadas, implantação, suporte, treinamento e relatórios do e-Sfinge | 1 atestado compatível, sem quantitativo | 1º: piloto público em SC, prestadora de água, por dispensa (Lei 14.133, art. 75, II) ou CPSI (LC 182/2021), com cláusula de atestado após os ciclos em produção, que só começam no lançamento comercial (ADR-0033); 2º: cliente privado; consórcio só oportunista | ADR-0023 item 3 (ajuste v2.7); P0.30; LC.13, LC.14; ED-02; ED-33 (e-Sfinge em operação no piloto) |
| H2 | POC eliminatória com produto completo | 81 itens, ≥ 146 de 162 | 80 itens binários, críticos eliminatórios | POC passa a ser escopo; ambiente de demonstração permanente; ensaio por fase; meta no aceite a partir do P3 | ADR-0023 item 2; P1.25, P3.23; ED-01 |
| H3 | Provedor de nuvem certificado | ISO 9001, 27001, 27017, SOC 1 e SOC 3 do provedor (verificado em 22/10/2026: SOC 1 e SOC 3 são relatórios de asseguração de auditor independente, e não certificações) | não exigido | AWS `sa-east-1` gerenciada, implantada no lançamento comercial; relatórios do AWS Artifact no kit a cada proposta | ADR-0011, ADR-0033; P0.26; LC.2–LC.5; ED-03 |
| H4 | Lote único completo | omnichannel, BI e SIG obrigatórios, sem subcontratação | idem, mais laboratório e CTI | trilha de editais com todos os módulos como componentes do produto (Superset licenciado, LLM por API como suboperador) | ADR-0026 a ADR-0028; ED-42, ED-43, ED-47, ED-50, ED-52 |
| H5 | Balanço de 2 exercícios | exigido | exigido (empresa nova: último exercício) | empresa licitante definida e escrituração por contador já em 2026; para licitar a partir do 3º trimestre de 2029 (plano v2.8), balanços de 2027 e 2028 | P0.44; ED-02 |
| H6 | Implantação e equipe presencial | 90 dias, 2 especialistas presenciais, técnico por 30 dias, treinamento presencial de 40 h | 60 dias, especialistas presenciais, 2 técnicos por 30 dias | implantação e treinamento remotos no piloto; metodologia, importadores, plano e material de treinamento padronizados; a presença exigida no edital é atendida na proposta, fora do plano de produto (plano v2.12) | ADR-0023 item 4; ED-09, ED-11 |
| H7 | Registro no INPI e vedação a software livre (ETP) | no ETP | — | registro do programa no INPI; bibliotecas livres não tornam o produto software livre | P0.44; ED-02 |
| H8 | Hospedagem local e licença instalável | VISAN pode assumir a infraestrutura, com desconto na mensalidade (TR 4.4.12); acesso administrativo da TI (TR 4.15) | licenças instaláveis em qualquer servidor (SG-020); Windows Server 2022+ | instância dedicada gerenciada; pacote em contêiner só como cláusula de reversão, em Linux ou em Windows Server 2022+ com Docker no WSL2, homologado uma vez; acesso da TI do órgão só na instância dedicada | ADR-0024 (ajuste v2.5); ED-04 |
| H9 | Metas contratuais acima do padrão | 99% em horário comercial; cinco severidades (2 a 32 h); dados em 10 dias úteis; consulta por 120 dias | 99,9%; cinco severidades (2 a 160 h) com multa; monitoramento 24/7 | nível "Setor Público" contratável; o padrão do produto não muda; escala de plantão da S1 montada até 31/01/2028 (ED-05) | ADR-0025; P2.15, P3.16, P3.22; ED-05 |
| H10 | Preço | teto de R$ 18.866,65/mês e R$ 77.900 de implantação | teto de R$ 7.636,74/mês com hospedagem, BI e Google Maps | tabela de custo da AWS (ADR-0011) e preço por porte; o teto do SAMAE só fecha com vários tenants no mesmo cluster | ED-10 |

Sequência que decorre (plano v2.8, opção A do lançamento): (1) até 13/11/2026, piloto escolhido com o critério de
atestado e o prazo de produção da opção A no termo de intenção (P0.30); (2) em paralelo, desde já, empresa licitante,
contador, INPI e certidões (P0.44); (3) trilha de editais completa até 30/09/2028 e lançamento comercial a partir de
outubro de 2028, com a primeira produção do piloto por volta de fevereiro de 2029 (LC.13); (4) atestado do piloto de 3
a 5 meses depois do corte (LC.14), entre maio e julho de 2029, que habilita para editais do tipo SAMAE a partir do 3º
trimestre de 2029; (5) segundo atestado e remessa e-Sfinge em operação, que habilitam para editais do tipo VISAN,
depois disso. A conclusão da trilha até setembro de 2028 (§8.1) é a condição que pode atrasar cada passo.

---

## 5. Entregas de edital (ED), deduplicadas

### 5.1 Quadro consolidado

As matrizes propuseram 74 entregas (18d: 16; 18e: 17; 18f: 16; 18g: 13; 18h: 12), com sobreposições. A consolidação
abaixo junta as equivalentes, separa as que misturavam etapas de natureza diferente (app de campo, relatórios) e
acrescenta os módulos de ERP que o ADR-0028 (item i) decidiu construir. Resultado: 62 entregas. Com o recorte da v2.5
(ADR-0028, item i, ajustado), 60 ficam na trilha até 2028; ED-60, ED-61 e a parte de compras da ED-58 passam a 2029,
sob demanda, e continuam listadas para manter a numeração. Na v2.10, o ADR-0034 acrescenta as ED-63 a ED-67 (GIS):
67 entregas, 65 até 2028; a ED-52, parte da ED-55 e a base da ED-13 passam ao P1 a P3.

Colunas: **Fase/onda** (fase do plano ou onda da trilha, §5.2); **Esf.** em semanas-pessoa; **POC** com a alínea da
VISAN (V) e a seção do SAMAE (S, "crít." quando crítica). Requisitos por faixa de IDs; "exceto" remete ao que outra ED
cobre.

**Plataforma, serviço e habilitação**

| ED | Entrega | Origem | Requisitos cobertos | POC | Fase/onda | Esf. | Dependências | ADR |
|---|---|---|---|---|---|---|---|---|
| ED-01 | Prontidão de POC: roteiros VISAN e SAMAE, ambiente de demonstração permanente com tenant ≥ 10 mil unidades fictícias, kit de campo, ensaio cronometrado por fase | 18h-E3 (M22–M25) | ED-055, ED-056, ED-074; TR-038–TR-045, TR-112; SG-001; roteiro POC-01–POC-81; 18c §3 | todas | P1 (P1.25), P3 (P3.23), P4 onda B | 4,4 | P0.23, P0.27, P2.9 | 0023 |
| ED-02 | Kit de habilitação e propriedade intelectual: empresa licitante, certidões, balanço, declarações, e-CNPJ, INPI, cláusula de licença das customizações, prova do vínculo com o provedor | 18h-E1 (M03, M05, M07, M12, M13) | ED-010–ED-016, ED-022, ED-025, ED-070, ED-090; ETP-005, ETP-007; TR-103, TR-105, TR-1100–TR-1103; SG-1398; 18c §2.2 | habilitação | P0 (P0.44), renovado a cada proposta | 0,4 | P0.26 | 0023, 0011 |
| ED-03 | Hospedagem certificada e serviços de segurança do provedor (detecção de ameaças, inspeção de vulnerabilidades, registro de configuração, backup só no Brasil) | 18h-E2 (M38–M43) | ED-063–ED-066; TR-061, TR-062, TR-064, TR-068–TR-092, TR-095, TR-096, TR-098, TR-100, TR-101; SG-003, SG-033, SG-667, SG-1406 | V a; S 2.4 crít. | lançamento comercial (LC.2–LC.5; até a v2.6, P0.26–P0.28), com a tabela de custo e as conferências na P0.26 | 1 | — | 0011, 0033 |
| ED-04 | Instância dedicada gerenciada e pacote de reversão em contêiner, com requisitos mínimos, runbook e acesso administrativo restrito do órgão; pacote também em Windows Server 2022+ com Docker no WSL2, homologado uma vez | 18h-E4 (M35, M36, M44) | ED-067, ED-071; TR-065, TR-067, TR-097, TR-099, TR-104; SG-004, SG-019, SG-020 | proposta e contrato | P4 onda B (pacote de reversão sobre o ambiente local da P0.27); IaC parametrizada e instância dedicada no lançamento (LC.2) ou depois | 3,5 | P0.5, P0.27, P4.6; LC.2 para a instância dedicada | 0024, 0033 |
| ED-05 | Nível de serviço Setor Público: 99,9%, cinco severidades com prazo, aderência e multa, exportação em 10 dias úteis com dicionário, consulta somente leitura por até 120 dias, relatórios em 2 ou 5 min (RNF-71), telefone e canal de contingência | 18h-E7, E9 (M49, M56, M75–M77, M93–M97) | TR-029, TR-030, TR-102, TR-108, TR-1068, TR-1076–TR-1090, TR-1097, TR-1098; SG-043, SG-668, SG-1284–SG-1313; ED-051, ED-052, ED-069, ED-081, ED-084–ED-086, ED-088; ETP-006, ETP-013 | S Item 3 crít.; S 2.5 | P2 (P2.15), P3 (P3.15, P3.16, P3.22), P4 onda A | 3 | P1.23, P2.12, P1.6 | 0025 |
| ED-06 | Controle de acesso de edital: bloqueio e expiração de senha por tenant, LDAP/AD por organização, perfis compostos pelo administrador, sessões e trilha visíveis ao tenant, consulta da trilha por usuário | 18h-E5 (M58–M63, M68, M69) | TR-115–TR-128; SG-049–SG-064, SG-1250, SG-1251, SG-1253; ED-075 | V b; S 2.7 crít. | P4 onda A | 3 | P0.13, P0.15, P0.43, P1.7 | — |
| ED-07 | Monitoramento e alertas para o cliente: eventos de negócio com alerta, painel de uso e desempenho do tenant, alerta de lote ao responsável | 18h-E6 (M50–M52) | ED-073; TR-109–TR-111; SG-044–SG-046 | S 2.5 crít. | P4 onda A | 1 | P1.9, P1.17, P2.1, P3.5 | — |
| ED-08 | Compatibilidade técnica: navegadores mínimos da VISAN, tamanho de página e salto de página com keyset, argumentação sobre gatilhos e linguagem | 18h-E12 (M27, M28, M33) | TR-055, TR-057–TR-060; SG-012, SG-021, SG-023–SG-026 | V a | P4 onda A | 0,4 | P0.22, P1.12 | — |
| ED-09 | Serviços de implantação padronizados: metodologia e plano-modelo de 60 e 90 dias, importador por legado (GSAN primeiro, se confirmado), promoção de parâmetros entre ambientes, dicionário de dados gerado do schema, linhagem da migração | 18h-E8, sem o DWG (M30, M78, M79, M81, M83, M84, M87) | TR-007, TR-008, TR-010–TR-018, TR-023, TR-026, TR-027, TR-051; SG-011, SG-1326, SG-1328–SG-1332, SG-1382, SG-1384; ED-029, ED-043, ED-045, ED-048, ED-049, ED-062; ETP-002 | contrato | P4 onda C | 4 | P1.2, P2.10, P2.19 | 0023 |
| ED-10 | Política comercial: custo e preço por porte, perfis e homem-hora, banco de horas, garantia de 12 meses, correção de vícios em 48 h, customização por parâmetro ou flag, percentual de desconto da reversão | 18h-E10 (M14, M15, M19, M20, M100) | ED-004–ED-007, ED-030, ED-036–ED-040, ED-083; ETP-011, ETP-014; PP-001–PP-024 (exceto PP-019); TR-1075; SG-1190, SG-1283, SG-1367–SG-1374, SG-1395, SG-1396, SG-1399, SG-1400, SG-1407–SG-1415 | proposta | P4 onda C | 0,4 | ED-04; tabela de custo do ADR-0011 | 0024, 0011 |
| ED-11 | Implantação e treinamento: plano de 40 h por módulo, material online, tenant de pré-treinamento, capacitação de TI, documentação de customizações | 18h-E11 (M85, M86, M89–M92) | ED-044, ED-046, ED-053, ED-054; TR-009, TR-019, TR-020, TR-031–TR-037, TR-1072; SG-1280, SG-1314–SG-1325, SG-1327, SG-1333, SG-1381 | contrato | P4 onda C | 3 | ED-09 | 0023 |

**Cadastro, medição e campo**

| ED | Entrega | Origem | Requisitos cobertos | POC | Fase/onda | Esf. | Dependências | ADR |
|---|---|---|---|---|---|---|---|---|
| ED-12 | Cadastro comercial ampliado e catálogos auxiliares: RG e filiação cifrados com finalidade, anexos, faixa de logradouro, unidades por órgão pagador, termo de adesão, catálogos da SAMAE 2.8.10, endereço alternativo | 18d-E1, 18d-E2; 18e §2.19; 18f (destino cadastro) | TR-134, TR-135, TR-141, TR-151, TR-577–TR-580, TR-585, TR-611, TR-777; SG-070, SG-071, SG-078, SG-083, SG-091, SG-095–SG-144 (22 catálogos), SG-554–SG-557, SG-562, SG-591, SG-1004 | V c; S 2.8 crít. | P4 onda A | 2 | P1.3, P1.20 | — |
| ED-13 | Ligações de água e esgoto como entidade, com catálogos técnicos e atualização pelo atendimento | 18d-E3 | TR-578; SG-098, SG-100, SG-101, SG-106, SG-117, SG-118, SG-124, SG-125, SG-136, SG-555 | V c; S 2.8 crít. | P4 onda A; base da ligação (`service_connections`) na P2.23 desde a v2.10, confirmada na v2.11 | 2,5 (+0,5 na P2.23) | P1.3, P3.4, P2.23 | 0034 |
| ED-14 | Hidrometria: parque, estoque, lacre, instalação, retirada, troca, aferição, validade Inmetro com OS em lote | 18d-E4; 18f §2.15 | TR-156–TR-170, TR-591, TR-592; SG-099, SG-113, SG-114, SG-135, SG-157–SG-172, SG-568, SG-569 | V d; S 2.9 crít. | P4 onda A | 3 | P2.5, P3.4, P1.20 | — |
| ED-15 | Ciclo de leitura: roteiro com reordenação, sequenciamento, boletim de contingência, regra de condomínio, autoleitura | 18d-E5; 18e-E17 (parte) | TR-172, TR-173, TR-188, TR-190; SG-174, SG-175, SG-185, SG-186, SG-190, SG-192, SG-755 | S 2.10 crít. | P4 onda A | 2 | P2.5, P2.7, P3.3 | — |
| ED-16 | Recadastramento: lote, coleta no app, aprovação campo a campo, aplicação e cinco relatórios | 18d-E11 | TR-234, TR-684–TR-688; SG-232, SG-910–SG-914 | S 2.11 e 2.28 crít. | P4 onda A | 3 | ED-18, P1.3 | — |
| ED-17 | Telemetria e supervisório por REST e arquivo: leituras AMR, grandes consumidores com alarmes, registro manual de paradas da ETA; MQTT só sob demanda | 18d-E10; 18g-E-AMR, E-SUP | TR-167, TR-210; SG-169, SG-208, SG-209, SG-810 | S 2.9, 2.10 e 2.26 crít. | P4 onda A | 2 | P1.9, P2.3, P2.7 | 0028 (j) |
| ED-18 | App de campo Android (Capacitor), base: projeto, build e assinatura, autenticação pelo navegador do sistema com PKCE e token no Keystore, dispositivo registrado com bloqueio remoto, banco local cifrado, leitura offline com sincronização idempotente por `task_id`, fotos por ocorrência. É a P4.4 redefinida | 18d-E6 (parte), 18d §4 (decisão 1), 18f §4 (D1) | TR-621, TR-639 (APK e banco local); SG-782; base de ED-19 e ED-20 | V e; S 2.11 crít. | P4 onda A (P4.4) | 5 | P2.3, P2.7 | 0026, 0030 |
| ED-19 | Emissão simultânea e impressão térmica: cálculo da conta no dispositivo pelo mesmo módulo Java do servidor, vetores comuns no JVM e no emulador, pacote da rota com versões de parâmetro, recálculo no servidor e fila de divergências, Zebra série ZQ com editor de leiaute ZPL e CPCL, código de barras e QR Pix, comunicados, retidas, reimpressão, bobinas, cabeçote e bateria, chave da NFAg-e em contingência se o leiaute permitir | 18d-E6, 18e-E6 | TR-207, TR-209, TR-221–TR-232, TR-237–TR-244, TR-404; SG-220–SG-230, SG-235–SG-241, SG-388 | V e (POC-15, 16, 21); S 2.11 crít. | P4 onda A | 8 | ED-18, P2.5, P2.6, P2.20, P3.2; leiaute de contingência da NFAg-e | 0026, 0031 |
| ED-20 | OS em campo e acompanhamento: app de OS offline com fotos, GPS e crachá, material usado por tarefa, trilha do leiturista, mapa por status, painel de deslocamento, tempo e paradas | 18f-ENT-05, 18d-E7 | TR-193, TR-196, TR-200–TR-205, TR-208, TR-217, TR-620–TR-652, TR-762, TR-767; SG-195, SG-201–SG-207, SG-216, SG-506–SG-519, SG-989, SG-994 | V p; S 2.18 e 2.12.5 crít. | P4 onda A | 5 | ED-18, ED-36, ED-52 (núcleo, P3.25) | 0026, 0030 |

**Faturamento, arrecadação, cobrança e contabilidade**

| ED | Entrega | Origem | Requisitos cobertos | POC | Fase/onda | Esf. | Dependências | ADR |
|---|---|---|---|---|---|---|---|---|
| ED-21 | Retificação e manutenção de conta pelo backoffice e pelo atendimento: cancelar, alterar vencimento, leitura, consumo e serviços, recalcular, retirar água ou esgoto, crédito avulso, "pagamento a confirmar", emitir fora do lote, 2ª via e espelho, com motivo e anotação | 18e-E1, 18f-ENT-15 | TR-307–TR-316, TR-541, TR-553–TR-562, TR-564–TR-568, TR-573, TR-609; SG-296–SG-304, SG-529–SG-538, SG-540–SG-542, SG-544, SG-549, SG-550, SG-589 | V o (POC-49, 50); S 2.13 e 2.19 crít. | **P2 (P2.20)** | 3 | P2.5, P2.6, P1.11 | — |
| ED-22 | Situações especiais de faturamento, usuário avulso e órgão centralizador: isenção, cópia de tabela, guia avulsa, faturas desvinculadas, agrupadas e centralizadora, conta final, contrato especial, locação, taxa de entrega | 18e-E2, 18f-ENT-16 | TR-276, TR-290, TR-294, TR-302, TR-318–TR-320, TR-323, TR-324, TR-327–TR-329, TR-337, TR-587, TR-589, TR-600–TR-603, TR-617–TR-619; SG-266, SG-279, SG-283, SG-291, SG-306, SG-307, SG-310, SG-311, SG-314–SG-316, SG-564, SG-566, SG-579–SG-582, SG-598–SG-600 | V o (POC-51); S 2.13 crít. | P4 onda A | 4 | P2.5, P1.3, ED-26 | — |
| ED-23 | Revisão de consumo e desconto por vazamento: protocolo e fases, formulário e vistoria, agenda, execução no celular, alteração das faturas, laudo, parâmetros de desconto | 18d-E9, 18e-E3 | TR-245–TR-273, TR-303–TR-305, TR-315, TR-563; SG-242–SG-263, SG-292–SG-294, SG-303, SG-539 | V g; S 2.12 e 2.12.5 crít. | P4 onda A | 4 | P3.4, ED-21, ED-20, P3.5 | — |
| ED-24 | Controle de fraudes e fiscalização de consumo | 18e-E4 | TR-325, TR-331–TR-336, TR-761; SG-312, SG-318–SG-323, SG-988 | S 2.13 crít. | P4 onda A | 1 | ED-36, ED-21 | — |
| ED-25 | Resíduos sólidos na conta: componente tarifário, isenção, contrato especial, fatura e carnê separados | 18e-E15, 18g-E-RES (faturamento) | SG-324, SG-325 e as partes de resíduos de SG-222, SG-264, SG-281, SG-310, SG-311 | S 2.13 crít. | P4 onda A | 3 | P2.5, ED-22 | — |
| ED-26 | Arrecadação ampliada: documento agrupador com quitação em cascata, fechamento mensal congelado, depósitos identificados e não identificados, VAN | 18e-E7 | TR-345, TR-350–TR-353; SG-333, SG-338–SG-341 | S 2.14 crít. | P4 onda A | 1 | P3.1, P2.1 | — |
| ED-27 | Débito em conta: Pix Automático para autorizações novas e FEBRABAN 150 para as legadas migradas, com data de desligamento; optantes, reenvio, relatórios | 18e-E8 (D2); amplia P4.5 | TR-321, TR-322, TR-339, TR-354, TR-566, TR-583, TR-733–TR-735; SG-308, SG-309, SG-327, SG-342, SG-543, SG-560, SG-748, SG-960–SG-962 | V h (POC-29); S 2.14 crít. | P4 onda A; a parte FEBRABAN 150 (≈ 1,5) vai ao P3 (P3.24), como requisito da homologação local e do corte no lançamento, se a P0.30 encontrar optantes no piloto | 2 | P3.1, P4.5 | 0028 (f) |
| ED-28 | Cartão no parcelamento por adaptador para a credenciadora do cliente, com conciliação, sem dado de cartão no produto | 18e-E14 (D3) | TR-374 | V (avaliável) | P4 onda B | 1 | P3.1, P4.1 | 0028 (h) |
| ED-29 | Cobrança: cartas, prioridade e cota diária de OS de corte, níveis sucessivos, equipes especiais, corte e religação de água e esgoto por nível, cartas de liberação de esgoto, alteração de vencimento do aviso, carnê | 18e-E9, 18f-ENT-04 (corte e religação) | TR-372, TR-390, TR-409, TR-410, TR-417, TR-529, TR-539, TR-540, TR-590; SG-375, SG-389, SG-393, SG-394, SG-400, SG-486, SG-501–SG-505, SG-567, SG-571 | V j; S 2.15 crít. | P4 onda A | 4 | P3.4, P4.1, ED-36 | 0015 |
| ED-30 | Negativação e protesto por arquivo, com carta de aviso prévio, baixa no prazo legal e retorno importado | 18e-E10 (D4) | TR-572, TR-715; SG-402–SG-411, SG-424, SG-425, SG-548, SG-725, SG-942 | S 2.15 crít. | P4 onda A | 3 | P4.1, ED-31 | 0028 (g) |
| ED-31 | Dívida ativa (núcleo): notificação, inscrição individual e periódica, livro, CDA, parcelamento próprio, saldo mensal congelado, arquivo para a procuradoria | 18e-E11 (D1) | TR-419–TR-460 (exceto TR-453), TR-702, TR-703; SG-412–SG-423, SG-426–SG-428, SG-929, SG-930 | V k; S 2.15 crít. | P4 onda A | 4 | ED-21, ED-26, ED-29, ED-32 | 0028 (d) |
| ED-32 | Eventos contábeis e integração contábil: plano de contas e rubricas, regras evento → lançamento, PDD, retidos, fechamento congelado, exportação diária, relatórios contábeis | 18e-E12 (D5); 18d-E16 (parte) | TR-155, TR-447, TR-461–TR-475, TR-665, TR-717–TR-728, TR-750; SG-429–SG-447, SG-891, SG-944–SG-955, SG-977, SG-1059; ED-078 | V l (POC-37–40, 66–68); S 2.16.18 crít. | P4 onda A | 4 | P2.5, P3.1, P1.9, P2.1 | 0028 (e) |
| ED-33 | Remessa e-Sfinge (TCE/SC) gerada e validada pelo produto e enviada pelo cliente, com registro de hash, leiaute e retorno | 18e-E13 | TR-476–TR-494; ED-019, ED-077 | habilitação VISAN (atestado) | P4 onda A | 3 | ED-31, ED-32 | 0028 (e), 0023 |

**Atendimento, OS e canais**

| ED | Entrega | Origem | Requisitos cobertos | POC | Fase/onda | Esf. | Dependências | ADR |
|---|---|---|---|---|---|---|---|---|
| ED-34 | Catálogo de serviços, termos e carta de serviços; serviços e materiais faturáveis com tabela de preço e vigência, rubrica e cobrança no encerramento da OS; parcelamento de serviço | 18f-ENT-01, 18e-E5 | TR-496–TR-513, TR-519, TR-530, TR-535–TR-538; SG-358, SG-449–SG-467, SG-473, SG-487, SG-497–SG-500, SG-760, SG-763 | V m, i (POC-31); S 2.17 crít. | P4 onda A | 4 | P0.19, P3.4, P3.6, ED-32 | — |
| ED-35 | Atendimento completo: protocolo multicanal, visão 360, fila por prazo, documentos do cliente | 18f-ENT-02 | TR-544–TR-552, TR-574, TR-576; SG-520–SG-528, SG-551, SG-553 | V o (POC-47, 48); S 2.19 crít. | P4 onda A | 3 | P3.4, P3.5, P1.20 | — |
| ED-36 | OS completa no backoffice: estados, programação, baixa parametrizável, desdobramento, lote, retrabalho, mapa, equipes, veículos e materiais na OS (quantidade prevista e usada; requisição com alçada e baixa no estoque pela ED-58, na mesma onda desde a v2.6, ADR-0032) | 18f-ENT-03; 18g-E-ALMOX (cadastros na OS) | TR-495, TR-514–TR-534 e TR-594–TR-616, exceto os atribuídos a ED-22, ED-29, ED-34 e ED-62; SG-448, SG-468–SG-491 e SG-572–SG-597, idem; POC-46 | V n; S 2.17 crít. | P4 onda A | 4 | ED-34, P3.4, P1.9, P2.1 | — |
| ED-37 | Empreiteiras terceirizadas: cadastro, contratos, penalidades e medição | 18f-ENT-06 | SG-492–SG-496 | S 2.17 crít. | P4 onda A | 1 | ED-36 | — |
| ED-38 | Portal e autoatendimento ampliados: ficha, histórico de consumo, simulações de fatura e de parcelamento, declaração anual de quitação, certidão, tabela tarifária, qualidade da água, OS e solicitações | 18f-ENT-08, 18e-E17 | TR-543, TR-658–TR-660, TR-837–TR-849; SG-602–SG-624 (exceto SG-614), SG-713, SG-715, SG-717, SG-721–SG-724, SG-726, SG-761, SG-766, SG-767 | V r (POC-69); S 2.20 | P4 onda A | 4 | P3.3, P3.20, P1.5, ED-34 | 0022 |
| ED-39 | App do cliente Android e iOS nas lojas com a marca do tenant, gerado por pipeline, com login pelo navegador do sistema no realm `ziipsan-portal` e push sem dado pessoal | 18f-ENT-09 | TR-653, TR-654, TR-656, TR-657, TR-661–TR-663; SG-704–SG-782, exceto os do portal (ED-38) e das notificações (ED-41) | V q; S 2.24 crít. | P4 onda B | 4 | ED-18, ED-38, P3.21 | 0026, 0030 |
| ED-40 | Totem em modo quiosque no hardware do cliente; senhas com prioridade legal, chamada na tela de atendimento e painel em navegador | 18f-ENT-10 | TR-836; SG-614, SG-625, SG-626 | S 2.21 | P4 onda B | 1 | ED-38, ED-35 | 0028 (k) |
| ED-41 | Notificações multicanal e fatura por e-mail: link autenticado por padrão, PDF anexo por opt-in, SMS, WhatsApp e push com opt-in por canal e finalidade | 18f-ENT-11; 18f §2.15 | TR-206, TR-273, TR-403, TR-518, TR-542, TR-570, TR-571, TR-581, TR-850, TR-891; SG-263, SG-472, SG-546, SG-547, SG-558, SG-627, SG-759, SG-774–SG-776; POC-20 | V e (POC-20); S 2.19 crít. | P4 onda A | 3 | P1.9, P3.5, P3.9 | 0028 (l), 0022 |
| ED-42 | Omnichannel híbrido: tela de atendimento, filas e protocolo no produto, conectores oficiais (WhatsApp, Messenger e Instagram pela API da Meta, Telegram pela Bot API, e-mail, chat do portal), conversa gravada, logs íntegros, medição de mensagens | 18f-ENT-12 | TR-851–TR-853, TR-869–TR-881, TR-883–TR-885, TR-887–TR-890; SG-628–SG-630, SG-632, SG-633, SG-647–SG-665, SG-669 | V s (POC-70, 75); S 2.22 crít.; VISAN TR 21.1 e SAMAE item 4 do lote (Messenger, Instagram, Telegram) | P4 onda B | 7 | ED-35, ED-41 | 0028 (c) |
| ED-43 | Chatbot e IA com modelo de linguagem de terceiro por API, só com funções do portal, minimização de dado pessoal, transbordo humano e auditoria | 18f-ENT-13 | TR-854–TR-868, TR-882, TR-886; SG-631, SG-634–SG-646, SG-670–SG-702 | V s (POC-71–74, 76); S 2.22 crít., 2.23 | P4 onda B | 5 | ED-42, ED-38 | 0028 (c) |
| ED-44 | Telefonia mínima: CTI com o PABX do cliente pelo número chamador, API de consulta para URA de terceiros, listas de discagem | 18f-ENT-14 (forma reduzida) | SG-783–SG-788 | S 2.25 (avaliável) | P4 onda C | 1 | ED-35, ED-43 | 0028 (k) |

**Relatórios, BI, operação, qualidade e GIS**

| ED | Entrega | Origem | Requisitos cobertos | POC | Fase/onda | Esf. | Dependências | ADR |
|---|---|---|---|---|---|---|---|---|
| ED-45 | Relatórios operacionais nativos: núcleo declarativo sobre views na réplica, exportação PDF, XLSX e CSV, agendamento, permissão por papel, e cerca de 20 relatórios para o piloto e a POC | 18g-E-REL (núcleo); 18e-E16 e 18d-E8 (parte) | subconjunto da ED-46: arrecadação TR-666–TR-680, TR-770, SG-892–SG-906, SG-997; leitura TR-191–TR-199, SG-193–SG-200; fechamento do ciclo e corte | V r; S 2.28 crít. | **P3 (P3.22)** | 5 | P0.28, P2.1, P2.5, P2.7, P3.1, P3.4 | 0027, 0025 |
| ED-46 | Catálogo completo de relatórios das seções 19 (VISAN) e 2.28 (SAMAE), com teste por relatório | 18g-E-REL; 18d-E8; 18e-E16; 18f §2.15 | TR-191–TR-199 e TR-664–TR-835; SG-193–SG-200 e SG-890–SG-1062; exceto os da ED-45, de qualidade (ED-50) e de fonte própria (ED-16, ED-31, ED-32) | V r (POC-56–68); S 2.28 crít. | P4 onda A (P4.3) | 12 | ED-45 e as fontes de cada relatório | 0027 |
| ED-47 | BI embarcado (Apache Superset) com SSO do Keycloak, role de banco por tenant, réplica, marca do produto, auditoria e teste de isolamento | 18g-E-BI | TR-892–TR-896; SG-1063–SG-1071, SG-1081, SG-1191–SG-1277 | S 2.29; "Item 0" crít. | P4 onda B (P4.3) | 5 | P0.28, ED-45 | 0027, 0010 |
| ED-48 | Indicadores gerenciais parametrizáveis, catálogo SINISA pré-configurado, macromedição e balanço por setor | 18g-E-IND, E-MACRO | TR-749, TR-897–TR-907, TR-982–TR-985, TR-999–TR-1002, TR-1045; SG-856, SG-1072–SG-1080, SG-1159–SG-1162, SG-1177, SG-1178, SG-1182, SG-1186, SG-1187; ED-079 | V t (POC-81); S 2.29 | P4 onda B | 7 | P4.2, ED-47 | 0004, 0027 |
| ED-49 | Sete painéis padrão: arrecadação, atendimento, cobrança, faturamento, medição, operacional, indicadores estratégicos | 18g-E-PAIN | TR-908–TR-1007 e SG-1082–SG-1189, exceto os da ED-48 e da ED-62; ED-060 | V t (POC-77–81); S 2.29 | P4 onda B (P4.3) | 7 | ED-47, ED-48 | 0027 |
| ED-50 | Qualidade da água operacional: catálogo normativo versionado, plano de amostragem, cadeia de custódia, conformidade, bloco na conta, portal, SISAGUA assistido, relatórios | 18g-E-QUAL | SG-789–SG-821 (exceto SG-810), SG-802, SG-804, SG-1007–SG-1016; TR-225 (parte), TR-780–TR-789, TR-846, TR-1008 | S 2.26 e 2.28 crít. | P4 onda B | 9 | P2.5, P2.8, P3.3, ED-45 | 0028 (a) |
| ED-51 | Apoio à ISO/IEC 17025: laboratório com escopo, calibração, Westgard, incerteza, validação por segunda pessoa | 18g-E-LIMS17025 | SG-792, SG-822 | S 2.26 crít. | P4 onda B (entrega em até 6 meses da implantação de quem exigir) | 5 | ED-50 | 0028 (a) |
| ED-52 | Núcleo do GIS (ADR-0034; GIS-01 a GIS-06 e GIS-08): modelo de papel topológico fixo com classes, tipos e catálogo de fábrica extensíveis, histórico e metadados de precisão; topologia persistida e validação contínua; pacotes revisados e "rede na data"; vínculo feição ↔ imóvel ↔ ligação ↔ ramal; tiles vetoriais, simbologia padrão e mapa web de consulta (camadas, WMS, pesquisas, seleção); temáticos, precisão, OS no mapa e relatório de extensão | 18d-E12 | TR-534, TR-1009–TR-1024, TR-1054, TR-1057–TR-1063; SG-491, SG-823–SG-836, SG-842, SG-869–SG-883, SG-888, SG-1365 | S 2.27 crít. | **P1 a P3** (P1.26, P1.27, P2.21 a P2.23, P3.25, P3.27; antes, onda B) | 17,5 | P0.4, P0.19, P1.3, P1.9, P2.5, P3.1, P3.4, P3.22 | 0028 (b), 0034 |
| ED-53 | GIS: edição web completa (GIS-09): editor de feições, cadastro de unidades operacionais, integração com o cadastro (ligação desenhada vira ligação), permissões por camada e operação, projetos e obras, pacotes de projeto e de as-built, retroalimentação pelas OS | 18d-E13 | TR-154, TR-626, TR-652, TR-1011, TR-1025–TR-1047, TR-1050–TR-1053; SG-148–SG-152, SG-154, SG-837–SG-866 (exceto os da ED-54) | S 2.8 e 2.27 crít. | P4 onda A (antes B; v2.10) | 5 | ED-52, ED-13, ED-36 | 0028 (b), 0034 |
| ED-54 | Traçados, isolamento e manobras (GIS-11): montante e jusante, setores de manobra, válvulas a fechar, imóveis e clientes sensíveis afetados, aviso pela ED-41, OS em lote, período de desabastecimento, áreas de influência e polígonos de roteamento | 18d-E14 | TR-1036, TR-1055, TR-1056, TR-1064, TR-1065; SG-153, SG-852, SG-859, SG-867, SG-868, SG-887, SG-889 | S 2.27 crít. | P4 onda B | 5 (antes 4) | ED-52, ED-36, ED-41 | 0028 (b), 0034 |
| ED-55 | Migração do cadastro técnico e acesso QGIS: importador DWG (via DXF pelo ODA File Converter), DXF, SHP e GPKG com staging, perfis e relatório ISO 19157 no núcleo (GIS-07, P3.26); na trilha, QGIS por role individual com escrita só por views `INSTEAD OF` que gravam no pacote, projeto gerado e endpoint próprio (GIS-10), e importação ampliada com DGN v7, INP do EPANET e do SWMM, casamento de unidades e implantação em três etapas (GIS-18); DGN v8 por conversão assistida na implantação, para DWG ou DXF (v2.11) | 18d-E15; 18h-E8 (M88) | TR-1066, TR-1067; SG-884–SG-886, SG-1334–SG-1363; ED-080 | S 2.27 e 3.11.10 crít.; implantação VISAN | P3 (P3.26) e P4 onda B | 4 + 5 (antes 4) | P1.9, P1.25, ED-52; P3.8 para o QGIS | 0028 (b), 0010, 0034 |
| ED-56 | Ativos, manutenção de rede e inventário regulatório (GIS-19): cadastro com vínculo patrimonial e códigos MCPSB da NR 16/2026, extensão por subdivisão, conciliação com a contabilidade, manutenção preventiva e corretiva ligada ao GIS, risco e plano de renovação | 18g-E-ATIVOS | TR-155 (parte patrimonial), TR-1010 (parte); SG-1180 | — | P4 onda C | 4 (antes 3) | ED-53, ED-54, ED-32, ED-36 | 0034 |
| ED-57 | Integrações de cadastro: beneficiários de Bolsa Família e BPC por lista com base legal (sem API pública) | 18d-E16 (parte) | TR-144 | V (avaliável) | P4 onda C | 1 | P1.5 | — |
| ED-63 | Hidráulica (GIS-12): EPANET (água) e SWMM 5.2 (esgoto e drenagem) gerados da topologia, com demanda do consumo faturado; importação de `.inp` EPANET e SWMM como pacote; mapeamento tipo → elemento por tenant | ADR-0034 (decisão 8); 19e | SG-1339 (parte INP); exportação EPANET em 9 de 12 editais de software (19e) | — | P4 onda B (nova na v2.10) | 4 | ED-52, ED-64 | 0034 |
| ED-64 | Drenagem no GIS (GIS-13): catálogo, regras de conectividade, declividade e sentido, estado de conservação, foto e inspeção (NR 12/2025 da ANA), sub-bacias para o SWMM, relatório do cadastro de drenagem | ADR-0034 (decisão 1); 18g-E-RES (camadas) | TR-1048 | — | P4 onda B (nova na v2.10) | 3 | ED-52, ED-53 | 0034 |
| ED-65 | Resíduos sólidos no GIS (GIS-14): contêineres, PEV, ecopontos, lixeiras, pontos de coleta e de descarte irregular, setores e rotas de coleta sobre logradouros com pgRouting, imóvel ligado ao setor para a taxa | ADR-0034 (decisão 1); 18g-E-RES (camadas) | TR-1049; SG-1340 (parte de resíduos) | — | P4 onda B (nova na v2.10) | 3 | ED-52, ED-25 | 0034, 0010 |
| ED-66 | Campo GIS no app de campo (GIS-15): pacote offline por área, coleta e edição como deltas em pacote revisado, GNSS/RTK externo, fotos e croqui, conflito por atributo | ADR-0034 (decisões 3 e 5); 18d-E13 (acesso móvel) | TR-1011 (parte móvel) | — | P4 onda B (nova na v2.10) | 5 | ED-18, ED-20, ED-53 | 0026, 0030, 0034 |
| ED-67 | Exportação, devolução da base e OGC autenticado por tenant (GIS-16, GIS-17): GPKG, SHP, DXF, KML, GeoJSON, CSV e perfis Corsan e Saneago com metadados MGB; devolução da base ao fim do contrato; OGC API – Features (partes 1 a 3; verificado em 22/10/2026: a Parte 4, de criação e atualização, segue em minuta, e a Parte 5, de esquemas, foi aprovada em 30/04/2026) e WFS 2.0 com `Transaction` no pacote, na `api`, e WMS servidor (desde a v2.11) pelo GeoServer (desde a v2.12: só leitura, acessível só pela `api`, um workspace e uma role de banco somente leitura por tenant ou views filtradas pelo tenant da role, simbologia em SLD gerado da fonte única), sem acesso anônimo; fora do backoffice autenticado, imóveis e ligações só agregados por quadra ou setor com no mínimo 5 imóveis | ADR-0034; 19e (F10) | SG-1357, SG-1365 (KML); devolução da base (19e, F10); WMS (expresso em 5 de 12 editais, 19e) | S 2.27 crít. (WFS) | P4 onda B (nova na v2.10) | 6 (antes 5; +1 do WMS; mantido com o GeoServer) | ED-52 | 0034, 0010 |

**ERP (ADR-0028, item i, ajustado na v2.5: "núcleo saneamento + integração")**

| ED | Entrega | Origem | Requisitos cobertos | POC | Fase/onda | Esf. | Dependências | ADR |
|---|---|---|---|---|---|---|---|---|
| ED-58 | Almoxarifado (materiais e estoque para a OS): estoque por local (hidrômetros e materiais de rede), entradas, ajustes, custo médio, inventário; requisição de material da OS com alçada por valor e por item, reserva na aprovação, entrega, baixa automática da quantidade usada e devolução da sobra no encerramento, custo na OS; saldo negativo permitido com alerta de reposição e regularização; baixa do material de campo na sincronização por `task_id`, com alçada a posteriori para item controlado. Compras (requisição de compra, cotação e pedido) sai para 2029 | 18g-E-ALMOX (parte); 18f-ENT-07 | relatórios de materiais e insumos TR-808–TR-810, TR-817, TR-820, TR-821, TR-990; SG-1035–SG-1037, SG-1044, SG-1047, SG-1048, SG-1167; estoque da SAMAE 2.17 quando o estoque oficial for o do produto | S 2.17 crít. (estoque), quando sem ERP do cliente | P4 onda A (antes C; antecipada na v2.6, junto da ED-36) | 7,5 (5 do almoxarifado, 2,5 do ADR-0032) | ED-36, ED-12, ED-14; ED-20 só para a baixa de campo | 0028 (i), 0032 |
| ED-59 | Frota e equipes de campo: veículos, abastecimento, manutenção, custo por veículo e consumo na OS; equipes de campo (composição, veículo, escala) na programação e no custo da OS | 18g-E-ALMOX, E-ERPIND (parte) | SG-1181 e indicadores de frota | — | P4 onda C | 3 | ED-36 | 0028 (i) |
| ED-60 | RH no recorte do saneamento: lotação, competências e indicadores de RH (equipes de campo passaram à ED-59) | 18g-E-ERPIND (parte) | indicadores de RH (TR 22.6.19; SG 2.29), atendidos até 2029 pela ED-62 | — | **2029, sob demanda** (fora da trilha) | (3, fora do total) | ED-36; RIPD e DPA atualizados | 0028 (i) |
| ED-61 | Orçamento: dotações, rubricas ligadas à ED-32, previsto × realizado | 18g-E-ERPIND (parte) | indicadores orçamentários (TR 22.6.18; SG 2.29), atendidos até 2029 pela ED-62 | — | **2029, sob demanda** (fora da trilha) | (3, fora do total) | ED-32 | 0028 (i) |
| ED-62 | Integração com ERP do cliente (Betha, IPM, Fiorilli) por API e/ou arquivo: contabilidade, estoque e RH; previsão de materiais da OS ao estoque; movimentos de material da OS quando o estoque oficial for o do ERP; indicadores de compras, RH e orçamento lidos do ERP no painel estratégico | 18g-E-ERPIND; 18f-ENT-07; 18e-E12 (destino) | TR-468, TR-521, TR-1001, TR-1003, TR-1004, TR-1007; SG-437, SG-476, SG-1179–SG-1184, SG-1189, SG-1277 | S 2.16.18 e 2.17 crít.; V t | P4 onda A | 7 | ED-32, ED-36; ED-58 para os movimentos de material | 0028 (i) |

### 5.2 Ondas da trilha e esforço

As ondas ordenam a trilha do P4 pela criticidade na POC, como pede o ADR-0023. A prontidão de POC exige as ondas A e B;
a aderência total exige as três.

| Onda | Critério | Entregas | Esforço (sem.) | Meta de ensaio ao fim da onda |
|---|---|---|---|---|
| Antes do P4 | o que o piloto precisa e o que os ADRs puseram no P0 a P3 (o ED-03 passou ao lançamento comercial na v2.7, sem mudar o total; o núcleo do GIS entrou na v2.10) | ED-02 (P0); ED-03 (lançamento, LC.2); ED-01 parte (P1, P3); ED-21 (P2); ED-05 parte (P2, P3); ED-45 (P3); ED-52 (P1 a P3); ED-55 parte (P3); ED-13 parte (P2) | 35,3 (v2.9: 13,3) | fim do P3: VISAN ≥ 50 de 162; SAMAE 2.4 e 2.5 sem falha |
| A | itens que pontuam nas alíneas b a p e r da VISAN ou são críticos no SAMAE nas áreas comercial, de campo e de suporte; habilitação (e-Sfinge); edição web do GIS (cadastro de unidades da SAMAE 2.8) | ED-05 (resto), 06, 07, 08, 12–20, 22–27, 29–38, 41, 46, 53, 58, 62 | 120,9 (v2.9: 116,4) | VISAN ≥ 120; SAMAE sem falha em 2.4–2.19, 2.28 e Item 3 |
| B | canais digitais, BI, qualidade e GIS (VISAN q, s, t; SAMAE 2.22, 2.24, 2.26, 2.27 e "Item 0") e itens de proposta | ED-01 (resto), 04, 28, 39, 40, 42, 43, 47–51, 54, 55 (resto), 63–67 | 88,5 (v2.10: 87,5; v2.9: 74,5) | VISAN ≥ 146 e nenhuma falha crítica no SAMAE, em dois ensaios seguidos (prontidão de POC) |
| C | aderência total fora das POCs e serviços de implantação | ED-09, 10, 11, 44, 56, 57, 59 | 16,4 (v2.9: 15,4) | requisitos restantes da matriz viva atendidos ou com pedido de esclarecimento registrado |
| **Total até 2028** | | **65** | **261,1 (≈ 60 meses-pessoa; v2.10: 260,1; v2.9: 219,6)** | |
| 2029, sob demanda (fora do total) | compras (parte retirada da ED-58), ED-60 (RH) e ED-61 (orçamento) | 2 e parte da ED-58 | 9 | — |

Distribuição das 13,3 semanas antes do P4: P0 1,4 (P0.44 e serviços do provedor, que passaram da P0.27 à LC.2 na
v2.7); P1 0,4 (P1.25); P2 4
(P2.20 e cinco severidades na P2.15); P3 7,5 (P3.22, P3.23 e itens do ADR-0025 na P3.15 e P3.16). Se a P0.30 encontrar
optantes de débito automático, a parte FEBRABAN 150 da ED-27 (≈ 1,5) vai para o P3 (P3.24): o P3 passa a 9 semanas e a
onda A a 114,9, sem mudar o total. Na v2.10, o núcleo do GIS (ADR-0034) soma 22 semanas antes do P4: P1 +5 (P1.26 e
P1.27), P2 +7 (P2.21 a P2.23, com 0,5 da ED-13) e P3 +10 (P3.25 a P3.27); a distribuição passa a P0 1,4, P1 5,4, P2 11
e P3 17,5, total 35,3. Com optantes, o P3 passa a 19 e a onda A a 119,4.

**Recálculo da v2.5** (semanas-pessoa):

| Entrega | Onda | v2.4 | v2.5 | Motivo |
|---|---|---|---|---|
| ED-18 | A | 4 | 5 | autenticação, armazenamento seguro, registro e bloqueio de dispositivo (ADR-0030) |
| ED-19 | A | 6 | 8 | módulo de cálculo compartilhado, vetores no emulador, pacote com versões, fluxo de divergência (ADR-0031) |
| ED-04 | B | 3 | 3,5 | homologação única em Windows Server com WSL2 (ADR-0024, ajuste) |
| ED-42 | B | 5 | 7 | conectores Messenger, Instagram e Telegram (ADR-0028, item c, ajuste) |
| ED-58 | C | 8 | 7,5 | −3 de compras, que vai para 2029; +2,5 de alçada, reserva, baixa automática, saldo negativo e baixa de campo (ADR-0032) |
| ED-59 | C | 2 | 3 | equipes de campo, antes na ED-60 |
| ED-60 | C → 2029 | 3 | — | RH para 2029, sob demanda |
| ED-61 | C → 2029 | 3 | — | orçamento para 2029, sob demanda |
| **Ondas A + B** | | **177,9** | **183,4** | +5,5 |
| **Onda C** | | **28,4** | **22,9** | −5,5 |
| **Trilha do P4** | | **206,3** | **206,3** | igual, por compensação |

**Recálculo da v2.6** (semanas-pessoa): a ED-58 muda de onda sem mudar de tamanho.

| Onda | v2.5 | v2.6 | Motivo |
|---|---|---|---|
| A | 108,9 | 116,4 | +7,5 da ED-58, antecipada para junto da ED-36 (decisão do dono do produto) |
| B | 74,5 | 74,5 | — |
| C | 22,9 | 15,4 | −7,5 da ED-58 |
| **Ondas A + B** | **183,4** | **190,9** | +7,5 |
| **Trilha do P4** | **206,3** | **206,3** | igual |
| **Até 2028, com o P0–P3** | **219,6** | **219,6** | igual |

**Recálculo da v2.10** (semanas-pessoa; ADR-0034):

| Entrega | v2.9 | v2.10 | Motivo |
|---|---|---|---|
| ED-13 | A, 3 | P2 0,5 + A 2,5 | base da ligação antecipada para o vínculo do núcleo do GIS (P2.23) |
| ED-52 | B, 4 | P1 a P3, 17,5 | núcleo do GIS antes da homologação local (decisão 4): modelo, catálogo, topologia, pacotes, vínculo, mapa web, temáticos |
| ED-53 | B, 5 | A, 5 | edição web completa na onda A; fecha o cadastro de unidades da SAMAE 2.8 (SG-148 a SG-152) |
| ED-54 | B, 4 | B, 5 | aviso aos afetados, clientes sensíveis, período de desabastecimento, áreas de influência |
| ED-55 | B, 4 | P3 4 + B 5 | importador com staging, perfis e relatório ISO 19157 no núcleo; QGIS por views com pacote e projeto gerado; importação ampliada |
| ED-56 | C, 3 | C, 4 | inventário pela NR 16/2026 e risco |
| ED-63 a ED-67 | — | B, 20 | EPANET e SWMM (4), drenagem (3), resíduos (3), campo GIS (5), exportação, devolução e OGC (5) |
| **Antes do P4** | **13,3** | **35,3** | +22 |
| **Onda A** | **116,4** | **120,9** | +4,5 |
| **Onda B** | **74,5** | **87,5** | +13 |
| **Onda C** | **15,4** | **16,4** | +1 |
| **Trilha do P4** | **206,3** | **224,8** | +18,5 |
| **Até 2028** | **219,6** | **260,1** | +40,5 |

**Recálculo da v2.11** (semanas-pessoa; ADR-0034, ajuste v2.11): só a ED-67 muda.

| Entrega | v2.10 | v2.11 | Motivo |
|---|---|---|---|
| ED-67 | B, 5 | B, 6 | WMS servidor autenticado por tenant, decidido junto da OGC API – Features (antes, só por exceção no ADR-0010) |
| **Onda B** | **87,5** | **88,5** | +1 |
| **Trilha do P4** | **224,8** | **225,8** | +1 |
| **Até 2028** | **260,1** | **261,1** | +1 |

O total antes do P4 (35,3) não muda. Na v2.12, o WMS da ED-67 passa ao GeoServer, sem mudar a estimativa nem os
totais.

### 5.3 De onde veio cada ED

| Matriz | Entrega de origem → ED |
|---|---|
| 18d | E1, E2 → ED-12; E3 → ED-13; E4 → ED-14; E5 → ED-15; E6 → ED-18, ED-19; E7 → ED-20; E8 → ED-45, ED-46; E9 → ED-23; E10 → ED-17; E11 → ED-16; E12 → ED-52; E13 → ED-53; E14 → ED-54; E15 → ED-55; E16 → ED-57, ED-32, ED-56 |
| 18e | E1 → ED-21; E2 → ED-22; E3 → ED-23; E4 → ED-24; E5 → ED-34; E6 → ED-19; E7 → ED-26; E8 → ED-27; E9 → ED-29; E10 → ED-30; E11 → ED-31; E12 → ED-32, ED-62; E13 → ED-33; E14 → ED-28; E15 → ED-25; E16 → ED-45, ED-46; E17 → ED-38, ED-39, ED-15 |
| 18f | ENT-01 → ED-34; ENT-02 → ED-35; ENT-03 → ED-36; ENT-04 → ED-29, ED-34; ENT-05 → ED-20; ENT-06 → ED-37; ENT-07 → ED-62, ED-58; ENT-08 → ED-38; ENT-09 → ED-39; ENT-10 → ED-40; ENT-11 → ED-41; ENT-12 → ED-42; ENT-13 → ED-43; ENT-14 → ED-44; ENT-15 → ED-21; ENT-16 → ED-22 |
| 18g | E-REL → ED-45, ED-46; E-BI → ED-47; E-IND → ED-48; E-PAIN → ED-49; E-QUAL → ED-50; E-LIMS17025 → ED-51; E-SUP, E-AMR → ED-17; E-MACRO → ED-48; E-ALMOX → ED-36, ED-58, ED-59; E-ERPIND → ED-62, ED-59–ED-61; E-ATIVOS → ED-56; E-RES → ED-25, ED-53 |
| 18h | E1 → ED-02; E2 → ED-03; E3 → ED-01; E4 → ED-04; E5 → ED-06; E6 → ED-07; E7, E9 → ED-05; E8 → ED-09, ED-55; E10 → ED-10; E11 → ED-11; E12 → ED-08 |
| ADR-0028 (i) | sem origem em matriz: ED-58 a ED-61 (ERP construído; ED-60, ED-61 e compras em 2029 desde a v2.5) |
| ADR-0032 | ampliação da ED-58 (material da OS com alçada e baixa automática) |
| ADR-0034 | ED-52 redefinida como núcleo do GIS (P1 a P3); ED-53 a ED-56 redefinidas; novas ED-63 a ED-67; base da ED-13 na P2.23 |

### 5.4 Pontos em aberto que afetam as ED

Resolvidos na v2.5 do plano: os conectores Facebook Messenger, Instagram e Telegram entram na ED-42 (ADR-0028, item c,
ajustado); o recorte do ERP ficou em almoxarifado e frota construídos e integração pela ED-62, com compras, RH e
orçamento em 2029 (item i, ajustado; folha de pagamento, eSocial e contabilidade pública continuam fora); o FEBRABAN
150 passa a ser levantado na escolha do piloto (P0.30) e, havendo optantes, entra no P3 (P3.24). Resolvido na v2.6: a
onda do almoxarifado; a ED-58 passa à onda A, junto da ED-36, com +7,5 semanas na prontidão de POC (§5.2, §8.1),
compras continuando em 2029. Continuam em aberto:

- **Licença do ODA File Converter** para uso comercial, a conferir no P1 (P1.25), antes de planejar a ED-55.
- **Contingência da NFAg-e** (manual e XSD oficiais): define se a conta de campo sai com a chave fiscal (ADR-0031,
  item 4), antes da ED-19.
- **DPoP no Keycloak** para ligar o token ao aparelho do app de campo (ADR-0030, item 6), a conferir no início da ED-18.
- **GIS (v2.10; resolvidos na v2.11 e na v2.12, plano §2.4):** base da ligação na P2.23 (confirmada); rede do piloto
  na homologação local (redes, peças e estruturas reais; ligações e imóveis anonimizados, com coordenada deslocada ou
  agregada por quadra); piso de agregação (5 imóveis por quadra ou setor, classe c); normas compradas no início do P1
  (P1.28); DGN v8 por conversão assistida na implantação; WMS servidor na ED-67, autenticado por tenant e publicado pelo
  GeoServer (v2.12). Continuam em aberto: WCS só por exceção no ADR-0010, quando um edital exigir; `pgrouting`,
  `pg_jsonschema`, `pgaudit` e SRID 5396 no RDS.

---

## 6. Conflitos legais e tratamento

Regra do ADR-0023 (item 5): o produto segue a lei, com as travas do ADR-0015, e não oferece configuração que as afrouxe
para atender a um edital. O Ziip mantém modelos de pedido de esclarecimento e de impugnação (Lei 14.133/2021, art. 164),
preparados na P1.25 e protocolados antes da sessão; na POC, demonstra a regra legal e cita a norma.

| # | Item do edital | Norma | Tratamento |
|---|---|---|---|
| L1 | Perda da tarifa social por faturas em atraso (TR-286) | Lei 14.898/2024 veda condicionar a tarifa social à adimplência | Trava do ADR-0015 (piso da tarifa social); esclarecimento ou impugnação |
| L2 | Geração de ordens de corte em dias e horários livres (TR-415, SG-398) | Lei 14.015/2020 (vedações de sexta, fim de semana, feriado e véspera) | Vedações travadas (ADR-0015, G9); a ED-29 recusa geração fora da janela legal; esclarecimento |
| L3 | Transferência à contratada das multas do TCE/SC por atraso ou erro na remessa e-Sfinge (TR-494) | Responsabilidade pela remessa é do jurisdicionado | Remessa gerada e validada pelo produto e enviada pelo cliente (ADR-0028, e); esclarecimento ou impugnação |
| L4 | Débito em conta com autorização no regime antigo (TR-339, SG-327) | Res. CMN 5.251/2025 (autorização com recebedor pessoa jurídica pelo Pix Automático); Res. CMN 5.299/2026 (norma de diretrizes, que revoga a Res. CMN 4.790/2020 em 01/07/2027; regime operacional pela Res. BCB 565/2026; verificado em 22/10/2026: o prazo de adequação ao Pix Automático para convênios de serviços públicos é 01/01/2027, pela Res. CMN 5.278/2025, regra distinta da vigência de 01/07/2027) | Pix Automático para novas autorizações; FEBRABAN 150 só para as legadas, com data de desligamento (ED-27) |
| L5 | Atestado com relatórios de envio ao e-Sfinge (ED-019) e mínimo de 8.500 ligações | Lei 14.133/2021, art. 67 (exigência proporcional ao objeto) | Restringe a disputa a quem já atua em SC; impugnação possível em editais futuros, sem contar com o resultado; caminho principal é o atestado do piloto (§4) |
| L6 | Vedação a software livre e registro de todos os módulos no INPI (ETP-005) | Lei 9.609/1998; o uso de bibliotecas livres não torna o programa software livre | Registro no INPI (P0.44); esclarecimento quando a exigência aparecer no edital |
| L7 | Linguagem "orientada a objetos, interpretada" (TR 4.2.5; SG-005) | Lei 14.133/2021, art. 9º, I (vedação a preferência sem justificativa) | Argumento técnico (bytecode da JVM; o próprio SAMAE aceita J2EE); esclarecimento |
| L8 | Subcontratação vedada com nuvem de terceiro exigida (TR 27.4; A-05) | Lei 14.133/2021, art. 122 | Provedores (nuvem, WhatsApp, modelo de linguagem, Superset) como componentes e insumos licenciados, não como subcontratação; esclarecimento antes da sessão |
| L9 | Titularidade das customizações (art. 93; A-16) | Lei 14.133/2021, art. 93 | Cláusula-padrão de licença das customizações, sem cessão de direitos (ED-02, ED-10) |
| L10 | Consulta por 120 dias após o contrato (TR-030) × eliminação de dados do padrão | LGPD, art. 16 (conservação para cumprimento de obrigação contratual) | Nível Setor Público: consulta somente leitura pelo prazo do contrato antes da carência, prevista no DPA e no certificado de descarte (ADR-0025) |
| L11 | Envio da fatura ao e-mail do usuário (TR-570, SG-546, POC-20) | LGPD, art. 46 (segurança) | Link autenticado por padrão; PDF anexo só por opt-in registrado (ADR-0028, l) |
| L12 | Vínculo do consumidor só por nome e CPF/CNPJ, com mensagem orientativa (SG-708, SG-709, SG-711) | LGPD, art. 46; anti-enumeração (ADR-0008, ADR-0022) | Mantidos o código de vinculação e a resposta indistinta; esclarecimento; na POC, demonstrar o fluxo seguro |
| L13 | Acesso administrativo da TI do órgão ao ambiente e a todos os logs (TR-097, TR-099, TR-104) | LGPD, art. 46 (dados de outros controladores no ambiente compartilhado) | Só em instância dedicada, somente leitura, com MFA e auditoria (ADR-0024) |
| L14 | Aderência do software à ISO/IEC 17025 (SG-822) | A acreditação é do laboratório (Inmetro/Cgcre) | O produto apoia (ED-51); esclarecimento sobre o que se exige do software |
| L15 | Anexo XX da Portaria de Consolidação 5/2017 citado sem a redação vigente (SAMAE 2.26) | Portaria GM/MS 888/2021 | Catálogo normativo pela redação vigente, revisado por responsável técnico (ED-50) |
| L16 | Validação de beneficiário por "API do governo" (TR-144, "BPM") | LGPD; não há API pública aberta do CadÚnico | Importação de lista com base legal ou convênio (ED-57); esclarecimento |
| L17 | Negativação e protesto "automáticos" (SG-402 a SG-411, SG-424, SG-425) | CDC, art. 43, §2º (aviso prévio); STJ, Súmula 548 (baixa em 5 dias úteis); prazo máximo de 5 anos | Carta de aviso prévio com entrega registrada antes da inclusão; baixa e teto travados (ED-30) |
| L18 | Multa moratória de 10% por dia "sobre o valor do exame" e suspensão "com a Prefeitura de Peruíbe" (contrato do SAMAE) | Erro material de modelo | Esclarecimento antes da sessão; precificação do risco (ED-10) |
| L19 | Resíduos sólidos e drenagem no GIS sem estarem no objeto (VISAN 24.3.7, 24.3.13) | Lei 14.133/2021, art. 40 (objeto definido) | Esclarecimento; camadas entregues na ED-53 se mantidas |
| L20 | Transferência internacional de dados (contrato do SAMAE 12.1.6; TR 4.4) | LGPD, art. 33 | Hospedagem e backups em `sa-east-1` (ADR-0011); provedor de modelo de linguagem fora do Brasil só com registro no RIPD e no DPA (ADR-0028, c) |

---

## 7. Rastreabilidade: matriz viva

### 7.1 Estrutura

A matriz viva substitui, para acompanhamento, as tabelas estáticas de 18d a 18h. É criada na P1.25 a partir delas,
com uma linha por ID de requisito, e mantida no repositório em formato tabular (CSV ou YAML), versionado e conferido
no CI.

| Campo | Conteúdo |
|---|---|
| `requirement_id` | ID na extração (`TR-221`, `SG-220`, `ED-019`) |
| `canonical_id` | requisito canônico (`RC-nnnn`) que agrupa os equivalentes entre editais do mesmo modelo |
| `tender` | edital (VISAN-PE-06-2026, SAMAE-PE-2-2026, próximos) |
| `section`, `area` | seção do edital e área da matriz (cadastro e medição; faturamento e cobrança; atendimento e canais; operação, qualidade e BI; não funcionais e habilitação) |
| `poc` | `V:POC-nn`, `S:critical`, `S:essential`, `S:assessable`, `V:possible`, `none` |
| `code_status` | `MEETS`, `PARTIAL`, `MISSING`, com evidência (arquivo ou teste) |
| `plan_status` | `PLANNED`, `PARTIAL`, `IMPLICIT`, `UNPLANNED`, `NOT_APPLICABLE` |
| `delivery`, `task` | ED-nn e tarefa do plano (P2.20, P3.22, ED-19) |
| `tests` | nomes dos testes automatizados que provam o requisito |
| `rehearsal` | data, fase e resultado do último ensaio do item (2/1/0 na VISAN; atende/não atende no SAMAE) |
| `legal_note` | referência ao §6 quando houver conflito, e o protocolo do pedido de esclarecimento |

### 7.2 Como se mantém viva

1. **Requisito → tarefa.** Toda tarefa do plano que toca requisito de edital cita a ED no campo "Origem"; a matriz viva
   liga o requisito à ED e à tarefa. Requisito sem tarefa aparece no relatório como "não planejado".
2. **Tarefa → teste.** Testes que provam requisito levam o ID no nome ou em etiqueta (`@Tag("req:SG-221")` no JUnit;
   `[SG-221]` no título do Playwright). Um script do CI lê as etiquetas e preenche `tests`; requisito marcado `MEETS`
   sem teste é recusado.
3. **Teste → roteiro de POC.** Cada item do roteiro (P1.25) lista os requisitos e os testes que o sustentam; o ensaio de
   fim de fase registra o resultado em `rehearsal`. O relatório de cobertura (por edital, área, onda e item crítico) é
   gerado a cada aceite de fase e anexado ao diário.
4. **Gatilhos de atualização:** fechamento de tarefa (diário de bordo), aceite de fase (ensaio), ADR novo ou alterado,
   edital novo.

### 7.3 Regra para novos editais

1. **Extração** no formato de 18a e 18c: identificação, itens e preços, uma linha por requisito com ID de prefixo
   próprio do edital, página e item; regras da POC; habilitação; SLA e penalidades; integrações; pontos ambíguos.
   Prazo: até 3 dias úteis da publicação.
2. **Equivalência:** cada requisito é ligado a um `canonical_id` existente quando o texto for equivalente (os editais
   de saneamento de SC derivam do mesmo modelo); só o que não tiver equivalente gera requisito canônico novo.
3. **Matriz por área**, nas cinco áreas acima, com as mesmas colunas e o mesmo vocabulário de situação; requisito novo
   entra numa ED existente ou gera ED nova, com origem, esforço, dependências e ADR.
4. **Conflitos legais** conferidos contra o §6; pedido de esclarecimento ou impugnação protocolado no prazo do edital.
5. **Decisão de disputar (go/no-go)**, registrada no diário antes do prazo de impugnação: habilitação completa (atestados,
   balanço, certidões), resultado do último ensaio contra o roteiro do edital, prazo de implantação contra a
   metodologia (ED-09), preço contra a tabela de custo (ED-10).

---

## 8. Riscos

### 8.1 Esforço e ordem

| Item | Semanas-pessoa |
|---|---|
| Acréscimos ao P0–P3 (P0.44, P1.25, P2.20, P2.15, P3.15, P3.16, P3.22, P3.23) | 13,3 |
| Núcleo do GIS antes do P4 (ADR-0034: P1.26, P1.27, P2.21 a P2.23, P3.25 a P3.27) | 22 |
| Tarefas originais do P4 que continuam (P4.1, P4.2, P4.5, P4.7, P4.8) | ≈ 8,4 |
| Trilha de editais, ondas A e B, com a ED-58 e a ED-53 na onda A e o GIS da trilha na onda B | 209,4 (v2.10: 208,4; v2.9: 190,9; v2.5: 183,4; v2.4: 177,9) |
| Prontidão de POC: ondas A e B e as tarefas originais do P4 | ≈ 217,8 (v2.10: 216,8; v2.9: 199) |
| Trilha completa (ondas A, B e C) | 225,8 (v2.10: 224,8; v2.4 a v2.9: 206,3) |
| Trilha completa e tarefas originais do P4 | ≈ 234,2 (v2.10: 233,2; v2.9: 215) |
| Fora da trilha: compras, RH e orçamento (2029, sob demanda) | 9 |

Os valores estão na escala do plano e têm incerteza de ±30%; a matriz 18g, sozinha, já estimava de 13 a 16
meses-pessoa para operação, qualidade e BI. Indicam esforço por entrega e por onda, sem conversão em equipe (plano
v2.12; as análises de capacidade das versões 2.5 a 2.11 saíram com o ADR-0029, revogado). As datas são metas de
calendário do plano: núcleo do GIS no P1 a P3, cada parte no marco da sua fase; início da trilha em 02/08/2027;
prontidão de POC por volta de meados de agosto de 2028; trilha completa até 30/09/2028; lançamento comercial a partir
de outubro de 2028.

**Recomendações.**

1. **Proteger a homologação local do piloto** (antes, go-live; plano v2.7). As 13,3 semanas acrescidas ao P0–P3 são,
   em grande parte, necessidade do próprio piloto (retificação de conta e relatórios operacionais), não de edital. Se
   o P2 ou o P3 atrasar, escorregam primeiro, nesta ordem: a meta de ensaio do P3 (o ensaio continua, só a meta sai
   do aceite), os itens do ADR-0025 da P3.15 e da P3.16 e as tarefas fora do caminho crítico já listadas no plano;
   P2.20 e P3.22 ficam, porque o paralelo (GL.2) depende delas. No núcleo do GIS, a P3.26, que carrega a rede real do
   piloto, vem antes da P3.27 e da parte não essencial da P3.25 (cliente WMS, medição).
2. **Seguir as ondas.** A antes de B antes de C; dentro de cada onda, primeiro o que é crítico no SAMAE e pontua na
   VISAN. A onda C (implantação, frota, ativos, telefonia, Bolsa Família) não pesa na POC e soma cerca de 16 semanas.
   O ERP construído soma 10,5 semanas além da integração (ED-62): o almoxarifado (7,5) na onda A, junto da OS, desde a
   v2.6, e a frota (3) na onda C. Compras, RH e orçamento ficam para 2029.
3. **Mirar primeiro editais do tipo SAMAE.** Exigem um atestado, que o piloto só pode dar depois do lançamento
   comercial e da primeira operação real (ADR-0033): entre maio e julho de 2029, pela opção A do plano §2.4 (v2.8).
   Os do tipo VISAN exigem dois atestados, 8.500 ligações e e-Sfinge em operação, o que, com o primeiro atestado em
   meados de 2029, dificilmente se reúne antes de 2030.
4. **Recalibrar a cada fase.** Comparar o tempo real das tarefas do P0 ao P3 com a escala P/M/G e corrigir as
   estimativas deste documento antes de assumir datas de licitação.
5. **Momento do lançamento escolhido** (plano v2.8). O dono do produto escolheu a opção A do plano §2.4: lançamento
   depois de toda a trilha, a partir de outubro de 2028, e primeira licitação com POC a partir do 3º trimestre de
   2029; a meta de 2028 deixou de valer. A trilha inteira passa ao caminho até o atestado: atraso além de setembro de
   2028 move o lançamento, o atestado e a primeira licitação na mesma medida, e a recalibração a cada onda (item 4)
   passa a proteger essa data.
6. **GIS antes do P4** (plano v2.10 a v2.12). O núcleo do GIS (22 semanas-pessoa) fica no P1 a P3, com modelo e
   catálogo no M6, topologia, pacotes e vínculo comercial no M7 e mapa web, importador e temáticos no M8. Na trilha,
   o WMS da ED-67 é publicado pelo GeoServer, com isolamento por tenant (v2.12).

### 8.2 Outros riscos

| Risco | Efeito | Mitigação |
|---|---|---|
| Piloto escolhido não ser público, catarinense ou contratado com cláusula de atestado | Sem atestado depois do lançamento; nenhum edital com POC disputável | Critério na P0.30 (até 13/11/2026); cliente privado como alternativa para editais do tipo SAMAE |
| Atestado só depois do lançamento comercial (ADR-0033) | Com a opção A, escolhida na v2.8, primeira licitação com POC a partir do 3º trimestre de 2029; as de 2028 ficam perdidas | Trilha completa até 30/09/2028 para manter o lançamento em outubro; piloto como cliente inicial em produção logo no lançamento; cláusula de atestado com prazo contado do corte; cliente privado como segundo atestado |
| POC em nuvem exigida pelos editais e ambiente de demonstração local até o lançamento | Nenhuma POC de licitação antes do lançamento | Ensaios internos no ambiente local; ambiente de demonstração na nuvem criado pela IaC do lançamento (plano LC.2) antes da primeira POC |
| Balanço de dois exercícios | Inabilitação nas licitações a partir do 3º trimestre de 2029 sem os balanços de 2027 e 2028 | P0.44: empresa licitante e contador desde já |
| Editais de 2029 diferentes dos de 2026 | Entregas feitas para o texto antigo; itens novos sem tarefa | Matriz viva com requisito canônico; extração em 3 dias úteis; go/no-go (§7.3) |
| Divergência entre conta impressa em campo e recálculo (ED-19) | Conta errada entregue ao consumidor; retrabalho fiscal | Mesmo módulo de cálculo no servidor e no app, vetores comuns no CI, fila de divergências com retificação (ADR-0031) |
| Saldo negativo no almoxarifado (ED-58) | Custo médio e inventário distorcidos; perda ou desvio escondidos | Alerta de reposição, relatório diário, regularização com prazo, inventário periódico, alerta na Central (ADR-0032) |
| Custo da AWS acima do teto do SAMAE (≈ US$ 2 mil/mês contra R$ 7.636,74/mês) | Proposta inexequível ou prejuízo | Vários tenants por cluster; dimensionamento mínimo nos primeiros clientes; tabela de custo (ADR-0011) antes de qualquer proposta |
| Modelo de linguagem de terceiro (ED-43) | Resposta errada ao consumidor; transferência internacional; custo por uso | Só funções do portal; minimização; transbordo; DPA e RIPD; franquia ou repasse no contrato (ADR-0028, c) |
| Publicação nas lojas por tenant (ED-39) | Prazos de revisão fora do controle do Ziip; recusa da Apple a app gerado de modelo | Conta de desenvolvedor do prestador; pipeline por tenant; publicar com antecedência à POC (ADR-0026) |
| Autenticação nos apps incompatível com o BFF de cookie (ADR-0006) | Retrabalho no app e risco de segurança | Decidida no ADR-0030: navegador do sistema com PKCE, token no armazenamento seguro, dispositivo registrado no app de campo (ED-18, ED-39) |
| QGIS com conexão direta ao banco (ED-55) | Caminho de escrita fora da `api` | Desde a v2.10 (ADR-0034), escrita só por views `INSTEAD OF` que gravam no pacote de edição, com validação e revisão por outra pessoa; endpoint próprio com TLS fora do pooler; role individual, auditoria, acesso por rede restrita, pentest (ADR-0028, b) |
| Catálogo do GIS com valores sem norma conferida | Peça errada como padrão; perda de confiança da prestadora na POC | Itens marcados como valor de referência a validar, fora do padrão ativo até a curadoria; perfis Corsan e Saneago como referência; normas compradas no início do P1 (P1.28, v2.11) |
| Rede real do piloto no ambiente local (ADR-0033, ajuste v2.11) | Reidentificação de imóvel pela ligação ou pelo ramal; infraestrutura do cliente fora do controle dele | Só redes, peças e estruturas reais; ligações, ramais e imóveis anonimizados na origem, com coordenada deslocada ou agregada por quadra e método registrado; autorização no termo de cooperação e acesso restrito; demonstração só com massa sintética |
| Superset como segunda pilha (ED-47) | Operação, atualização e superfície de ataque a mais; contestação de "autoria própria" | Role por tenant com teste de isolamento; modo síncrono; exceção no ADR-0010; pedido de esclarecimento-modelo (ADR-0027) |
| GeoServer como segunda pilha de mapas (ED-67, v2.12) | Vulnerabilidades próprias; leitura cruzada entre tenants por erro de configuração; simbologia diferente da do mapa web e do projeto QGIS | Só leitura, sem porta publicada, acesso só pela `api`; workspace e role de banco somente leitura por tenant, ou views filtradas pelo tenant da role; configuração gerada pelo produto pela API REST; teste de isolamento no CI; SLD gerado da fonte única de estilos; versão fixada e atualização de segurança (exceção no ADR-0010) |
| Licença do ODA File Converter | Etapa de migração do DWG sem ferramenta | Conferência no P1 (P1.25); alternativa: SDK licenciado da ODA, com custo |
| Impugnação ou esclarecimento sem efeito | A comissão avalia pelo texto do edital | Demonstrar a regra legal e citar a norma; registrar a recusa para recurso |
| Volume do omnichannel e hardware de campo e totem sem responsável definido (18a §6, itens 21 e 31) | Custo variável com a contratada | Hardware a cargo do cliente na proposta; franquia de mensagens precificada (ED-10) |
