# Aplicação das correções normativas, lote 3

Data da aplicação: 22/10/2026. Origem: verificação em fonte oficial do catálogo `docs/normas/catalogo-normas.json`. Escopo: 22 arquivos de `docs/` (arquitetura e levantamento) e 88 normas. Nenhuma decisão, tarefa, prazo ou parâmetro foi alterado; onde a correção atinge uma premissa, foi acrescentada a nota "(verificado em 22/10/2026: ...)" e o caso está em "Decisões a rever".

## (a) Resumo

| Item | Total |
|---|---|
| Normas no lote | 88 |
| Correções aplicadas (ao menos uma ocorrência editada) | 68 |
| Não aplicadas | 20 |
| Citações em arquivos fora do lote (registradas, sem edição) | 2 arquivos (seção e) |
| Casos em "Decisões a rever" | 11 |

Arquivos editados (12): `CERTIFICACOES-DE-QUALIDADE.md`, `PARAMETROS-DO-TENANT.md`, `ADR-0011`, `ADR-0015`, `ADR-0028`, `ADR-0041`, `08`, `09`, `10`, `12`, `21b` e `20f`. Arquivos do lote sem edição (por não conterem dado errado): `MARCA-E-TEMAS.md`, `REQUISITOS-NAO-FUNCIONAIS.md`, `ADR-0001`, `ADR-0010`, `ADR-0017`, `ADR-0018`, `ADR-0020`, `ADR-0030`, `ADR-0033`, `ADR-0042`.

Conferência de codificação após as edições: os 22 arquivos continuam UTF-8, sem CR e sem U+FFFD (contagem por split); a contagem de linhas mudou apenas em ADR-0011 (+2), ADR-0015 (+1), ADR-0028 (+3) e ADR-0041 (+2), onde a nota foi quebrada na largura do arquivo.

## (b) Aplicadas

Abreviações: `PT` = `arquitetura/PARAMETROS-DO-TENANT.md`; `CQ` = `arquitetura/CERTIFICACOES-DE-QUALIDADE.md`; `08`, `09`, `10`, `12` = relatórios de `levantamento/`; `21b` = `levantamento/certificacoes/21b-...`; `20f` = `levantamento/gsan/20f-...`.

| Arquivo | Norma (id) | Antes → depois |
|---|---|---|
| CQ §2 | abes-pnqs | "ISO 37120, PNQS | Perfil de exportação" → acrescenta nota: PNQS é prêmio/selos da ABES, sem formato de exportação, não certificável; classe adequada VOLUNTARY |
| ADR-0041 item 2 | abes-pnqs | mesma nota na linha de `EXPORT_PROFILE` |
| 21b §3.7 e item 2 das decisões | abes-pnqs | acrescenta que o regulamento 2026 (v1.2) não define layout/formato de troca de dados; inscrição pelo SINP; ABES ESG Index é iniciativa separada |
| ADR-0011 item 9 | aicpa-soc-isae-3402 | "SOC 1 e SOC 3" → nota: são relatórios de asseguração de auditor independente, não certificações; SOC 1 trata de relatório financeiro; SOC 3 é da família SOC 2 |
| PT `pay.pix_automatic` | cmn-res-5251-2025 | "recebedor PJ" → "PJ ou entidade não autorizada pelo BCB"; nota: adequação de convênios de serviços públicos prorrogada para 01/01/2027 (Res. CMN 5.278/2025) |
| ADR-0028 §f | cmn-res-5251-2025 | mesma nota sobre 01/01/2027 |
| 20f LEG-38 e seção 6 item 6 | cmn-res-5251-2025, bcb-res-505-2025, febraban-layout-debito-automatico-padrao-150 | acrescenta a prorrogação para 01/01/2027 em convênios de serviços públicos (Res. CMN 5.278/2025) |
| ADR-0028 §f | cmn-res-5299-2026 | "Res. CMN 5.299/2026, a conferir" → nota: norma de diretrizes; regime operacional na Res. BCB 565/2026 (a partir de 01/07/2027); Res. CMN 4.790/2020 em vigor até 30/06/2027 |
| PT `ret.billing_after_closure_years` | stj-resp-1117903, stj-tema-932, stj-resp-1532514 | "REsp 1.117.903, tema não confirmado" → "REsp 1.117.903/RS, Temas 251 a 254, julgado em 09/12/2009", prazo da pretensão de cobrança pelo prestador; "Súmula 412" → "Súmula 412 e Tema 932, REsp 1.532.514/SP" |
| ADR-0015 §guarda | stj-resp-1117903 | mesma correção do tema e do alcance (cobrança x repetição de indébito) |
| 20f LEG-33 | stj-resp-1117903, stj-tema-932 | "número do tema não localizado" → Temas 251 a 254 (cobrança); Súmula 412 → Temas 154 e 155 (REsp 1.113.403/RJ); Tema 932 → REsp 1.532.514/SP (repetição) |
| 10 (quadro, REsp 1.532.514) | stj-resp-1532514 | "(Súmula 412)" → "(Tema 932, julgado em 10/05/2017, Min. Og Fernandes; complementa a Súmula 412 e o Tema 154)" |
| 10 (Tema 1.315, quadro e cronologia) | stj-tema-1315 | "publicação 11/03/2026" → julgado em 05/03/2026, acórdão em 12/03/2026, notícia de 11/03; "texto formal da tese não consta" → tese oficial consta da página; ressalva de ler o inteiro teor |
| 20f LEG-31 | stj-tema-1315 | "acórdão publicado em 27/08/2026 (fonte secundária)" → 12/03/2026 (página oficial do STJ); registra a divergência e a ressalva sobre negativação |
| 10 (REsp 2.070.073) | stj-resp-2070073 | acrescenta UF (RS) e relatora (Min. Nancy Andrighi); situação "Em disputa" → "Parcialmente superado (Tema 1.315)" |
| 08 (quadro Lei 11.445 e E-12) | stf-adi-6492, 6536, 6583, 6882 | "resultado NÃO CONFIRMADO" → objeto é a Lei 14.026/2020; improcedentes em 02/12/2021; nenhum dispositivo declarado inconstitucional |
| 08 (Res. ANA 227/2024) | ana-res-227-2024 | "NÃO CONFIRMADO" → confirmado em fonte oficial (reúso, tema 9.2; instrumentos negociais); 227 revogada desde 01/01/2026, agenda vigente é a Res. ANA 277/2025 |
| 08 (NR 1/2021) | ana-res-114-2021, ana-in-1-2023, ana-in-2-2023, ana-in-2-2025 | "alt. pela 114" → só o item 7.5; "adoção por prazos da IN 1/2023, alt. IN 2/2023 e 2/2025" → comprovação da adoção (requisitos e procedimentos, prazos no art. 8º), alt. pela IN 2/2023 (só art. 7º) e IN 2/2025 (arts. 2º, 6º e 8º) |
| 08 (Lei Distrital 5.990/2017, 2 ocorrências) | lei-5990-2017-df | acrescenta "declarada inconstitucional" e a ressalva sobre a Res. ADASA 14/2011, art. 122 |
| 08 (nota sobre 2026) | pl-6365-2025 | "PL 6365/2025" → "PL 6.365/2025" com objeto (Comitê Interministerial, regularização fundiária); situação atualizada pela página do Senado (movimento de 14/10/2026) |
| 09 (Portaria 2.914/2011, 3 ocorrências) | portaria-ms-2914-2011 | "Vigorou em 2015–2021" → ato autônomo de 14/12/2011 até 2017, depois Anexo XX da PRC 5/2017; revogação formal pelo art. 864, CXXXIII, da PRC 5/2017 (não pela 888); citação recomendada |
| 09 (Anexo XXI da PRC 5/2017, 4 ocorrências) | prc-5-2017-anexo-xxi | "conteúdo não lido" → conferido; estrutura do anexo, art. 3º, limite de 1,5 mg/L no Anexo 9 do XX; faixas de 1975 |
| 09 (CONAMA, 4 ocorrências) | conama-nova-resolucao-lancamento-efluentes | "151ª reunião ordinária, 01–02/09/2026" → aprovada em 02/09/2026 (151ª ordinária); 01/09 foi a 64ª extraordinária (reúso); vistas, ausência de resultado oficial, Res. 517/2026 |
| 09 (Consulta Pública Inmetro 21/2025) | inmetro-consulta-publica-21-2025 | "19/08/2025" → datada de 19/08, publicada em 22/08, encerrada em 22/10/2025, convertida na Portaria 188/2026 |
| 09 (Portaria Inmetro 102/2022) | inmetro-portaria-102-2022 | "ENCE a partir de 01/01/2027" → nota: a Portaria 188/2026 adotou prazo limite 31/12/2027; pH 6–9,5 é critério do ensaio de extraíveis |
| 09 (CNEN PR 3.01/012:2020, 2 ocorrências) | cnen-pr-3-01-012-2020 | "texto não lido; Sim (presumido)" → revogada desde 02/05/2024; referência atual: Anexo L da NN 3.01; cita §§3 e 4 do art. 37 |
| 09 (DN COPAM/CERH-MG 1/2008) | mg-copam-cerh-dn-conjunta-1-2008, mg-dn-conjunta-8-2022 | "substituída em parte; revogação NÃO CONFIRMADO" → revogada integralmente pela DN 8/2022 (art. 52); valores do art. 36 a reconferir no SIAM |
| 09 (Res. SS-65/2016) | sp-ss-res-65-2016 | acrescenta que altera o art. 6º da Res. SS-65/2005 (Proagua) |
| 09 (Res. ADASA 59/2025) | adasa-res-59-2025 | "fonte secundária; Presumida" → fonte oficial (SINJ-DF, comunicado da ADASA); fundamento na NR ANA 9/2024 |
| 09 (SG-10) | abnt-nbr-15784, ansi-nsf-61 | "NBR 15.784" → "NBR 15784:2023" (não cobre materiais em contato); "ANSI/NSF 61" → NSF/ANSI/CAN 61, edição 2025 |
| 10 (quadro e cabeçalho) | lei-14058-2020, lei-15141-2025 | acrescenta o objeto da Lei 14.058 (benefício emergencial) e da Lei 15.141 (carreiras e remuneração; art. 216 inclui arts. 8º-A e 8º-B na LAI) |
| 10 (linhas 185 e 215) | lei-9784, lei-habeas-data | acrescenta "Lei 9.784/1999" e "Habeas Data (Lei 9.507/1997)"; nota sobre a Súmula 633 do STJ |
| 10 (quadro ANPD) | anpd-res-23-2024, anpd-res-30-2025 | "Res. 23, 30 e 31/2025 (agenda; mapa de temas)" → 23/2024 = Agenda 2025-2026; 30/2025 = Mapa de Temas Prioritários 2026-2027; 31/2025 = altera a agenda |
| 10 (Guia de Transparência) | anpd-cgu-guia-transparencia | "consulta aberta em jun/2026" → encerrada em 24/07/2026, em consolidação; sem numeração oficial |
| 10 (Informe CadÚnico 57/2024, 3 ocorrências) | mds-informe-57-2024 | "Não li; NÃO CONFIRMADO" → confirmado; data 29/10/2024; vedação recai sobre as coordenações estaduais e municipais |
| 10 (linha 349) | conarq-res-15 | "CONARQ (Res. 15, art. 10 §2º)" → "tabela de temporalidade própria ou definida pelo Conselho Nacional de Arquivos (Res. CD/ANPD 15/2024, art. 10 §2º)" |
| 12 (Lei 11.445, alterações) | mp-1154-2023 | "MP 1.154/2023 e Lei 14.600/2023" → MP de organização dos Ministérios, que altera a Lei 11.445 no art. 64; conferir se a Lei 14.600 manteve o texto |
| 12 (N10, logística reversa) | mma-portaria-gm-1102-2024, 1117-2024, 1560-2026, 1561-2026 | lista de portarias sem escopo → escopo de cada uma (entidades gestoras de embalagens; verificadores; eletroeletrônicos; alteradora da 1.102 e 1.117) |
| 12 (E01, SP) | sp-sima-res-27-2021 | "SIGOR MTR instituído pela Res. SIMA 27/2021" → módulo MTR do SIGOR; SIGOR existe desde o Decreto 60.520/2014 |
| 12 (E04, RS) | rs-fepam-portaria-12-2020, rs-fepam-portaria-432-2024 | "revoga as Portarias 87/2018, 12/2020 e 432/2024" → "revoga a 87/2018 e as que a alteraram, 12/2020 e 432/2024" |
| 12 (N23 e N22) | antt-res-5232-2016, antt-res-6016-2023 | 5.232 passa a "Instruções Complementares" (regulamento: 3.665/2011 e 5.848/2019), revogação confirmada em texto oficial; 6.016/2023 "(15/05/2023)" → data de publicação, assinatura em 11/05/2023 (fonte secundária) |
| 12 (N14, N13, N24) | ibama-in-12-2021, ibama-in-23-2024, ibama-in-24-2024 | IN 12/2021: objeto é a inscrição no CTF/AIDA (PGRS é uma hipótese, art. 14); IN 23/2024: assinada em 14/11, publicada em 19/11/2024; IN 24/2024: importação de resíduos, NBR 10004:2004 "ou sua sucedânea" no art. 1º, § 1º |
| 12 (N24, seções de pendências, 3 ocorrências) | abnt-nbr-10004-2024 | "fontes divergem; NÃO CONFIRMADO" → duas classes (Classe 1 e Classe 2), sem IIA/IIB, quatro passos de classificação; ainda fonte secundária |
| 21b §4.1 itens 8 e 9, F-09, V-03, MT-09 | inmetro-portaria-293-2021, 295-2021, 657-2025, 713-2025 | "permanecem verificação inicial e declaração de conformidade nas condições das Portarias 293/2021, 295/2021 e 78/2022" → 78/2022 para a verificação inicial dos medidores; art. 3º da 295/2021 para a declaração; 293/2021 não alcança medidores de água; inclusão dos medidores na 657 confirmada no PDF oficial; 713 apenas suspende a 657 |
| 21b §4.1 item 9 e MT-01 | abnt-nbr-8194, abnt-nbr-5426 | "NBR 8194" → "NBR 8194:2019 (Medidores de água potável — Padronização)", complementa as Partes 1, 2 e 3 da NBR 16043; "NBR 5426" → "NBR 5426:1985"; edição vigente da 8194 passa de "A CONFIRMAR" a 2019 |
| 20f (LEG-38, seção 5) | bcb-in-743-2026 | "IN BCB 743/2026 (11/06/2026, agendamento e liquidação)" → publicada em 12/06/2026; altera a IN BCB 513/2024 |
| 20f (LEG-39) | bcb-in-777-2026 | "Vigente, efeitos em 01/02/2027" → nota: publicada, ainda sem efeitos em 22/10/2026 |
| 20f (LEG-40) | svrs-nfag-e-modelo-75 | "Vigente segundo quatro fontes secundárias; texto do DOU não lido" → vigência futura (01/12/2026) confirmada pela Receita e pelo texto do Ato Conjunto 4/2026; base no Decreto 12.955/2026, art. 114, sem Ajuste SINIEF |
| 20f (LEG-48) | tcesc-in-ntc-28-2021 | "IN N.TC-28/2021 (alterada pela IN N.TC-35/2024)" → data 08/03/2021; alterada também pela IN N.TC-31/2022; remessa mensal desde 01/01/2022 |
| 20f (2 ocorrências) | aris-rn-4-2012 | "ARIS RN 004/2012" → "Resolução ARIS nº 04/2012 (não é Resolução Normativa)" |

## (c) Decisões a rever

Nenhuma decisão foi alterada. Os pontos abaixo têm premissa afetada pela correção e exigem decisão do dono do produto.

1. **PNQS como `EXPORT_PROFILE`** (CQ §2; ADR-0041 item 2; 21b §3.7, decisão 2 e L-17; ED-82 no plano). O regulamento do PNQS não define perfil de exportação; a classe adequada seria `VOLUNTARY`, não certificável. A tarefa L-17/ED-82 ("Perfis de exportação para ISO 37120 e PNQS") perde a base para o PNQS.
2. **Débito em conta e Pix Automático** (PT `pay.pix_automatic`; ADR-0028 §f; 20f LEG-38 e seção 6). Para convênios de serviços públicos (caso do saneamento), a adequação de contratos e autorizações foi prorrogada para 01/01/2027 (Res. CMN 5.278/2025); a premissa "só Pix Automático desde 13/10/2025" precisa ser lida com essa ressalva. O enquadramento exato de água e esgoto em "serviços públicos" exige análise jurídica (não confirmado).
3. **Data de desligamento do FEBRABAN 150** (ADR-0028 §f). A Res. CMN 5.299/2026 não institui o regime operacional (vem da Res. BCB 565/2026, a partir de 01/07/2027); a Res. CMN 4.790/2020 segue em vigor até 30/06/2027. Ver também o conflito registrado em "Não aplicadas" sobre a data 01/07/2027.
4. **Requisito eliminatório de SOC 1 e SOC 3** (ADR-0011, requisito 9 e alternativa "VPS"). Não são certificações, e sim relatórios de asseguração; o SOC 1 cobre controles de relatório financeiro e o SOC 3 é da família SOC 2. Confirmar com o órgão contratante o que a exigência abrange (editais costumam pedir SOC 2 Tipo 2).
5. **Piso de guarda de 10 anos** (PT `ret.billing_after_closure_years`; ADR-0015). O REsp 1.117.903/RS (Temas 251 a 254) trata da pretensão de cobrança pelo prestador; a repetição de indébito está na Súmula 412 (que remete ao Código Civil) e no Tema 932. A pendência do relatório 14 deve separar as duas pretensões.
6. **Corte de água e classes protegidas no DF** (08, quadro de classes protegidas e seção 5.2 ADASA). A Lei Distrital 5.990/2017 (12 meses de carência) foi declarada inconstitucional; a Res. ADASA 14/2011, art. 122, ainda a cita. Conferir a redação vigente antes de parametrizar o corte.
7. **Comprovação da verificação inicial de medidores** (21b MT-09). A Portaria 657/2025 está suspensa (713/2025) e nunca produziu efeito; a 293/2021 não alcança medidores de água. Não construir funcionalidade que dependa da declaração de conformidade em substituição à verificação inicial sem nova norma.
8. **Canal da notificação prévia à negativação** (10 NEG-05 e quadro; 20f LEG-31; ED-30). O Tema 1.315 trata do art. 43 §2º do CDC e valida a notificação eletrônica com prova de envio e entrega efetiva; a página oficial não menciona a Súmula 359 nem a negativação. Ler o inteiro teor antes de fixar o canal.
9. **Retenção de registro de incidente por ente público** (10, quadro comparativo). A remissão à tabela de temporalidade vem do art. 10 §2º da Res. CD/ANPD 15/2024; não existe "Res. CONARQ 15" sobre o tema. Se a intenção era citar uma tabela do CONARQ, indicar a norma específica.
10. **Limites de fluoreto e radioatividade** (09 QA-12 e QA-13). As faixas do Anexo XXI reproduzem a referência de 1975 (o Guia 2024 reconhece a necessidade de atualização); a PR CNEN 3.01/012:2020 está revogada, e a referência técnica passa a ser o Anexo L da NN 3.01, embora a Portaria 888 ainda remeta à PR.
11. **Modelo de classes de resíduo** (12 RES-32 e D-MTR-6). Se a série NBR 10004:2024 usa só Classe 1 e Classe 2, o modelo `waste_class` precisa prever as duas edições por jurisdição (confirmação ainda em fonte secundária).

## (d) Não aplicadas

| Norma (id) | Motivo |
|---|---|
| lei-15251-2010-sc | O `12` cita a lei apenas como base da Portaria IMA 009/2026 (MTR); não a descreve como política geral. |
| mp-1393-2026 | O `20f` já descreve a MP como Desenrola 3.0 e limita a alteração do CDC ao art. 57 §2º (combustíveis). Sem texto errado. |
| cgnfse-nfse-padrao-nacional | ADR-0018, 0020 e 0033 mencionam "NFS-e Nacional" sem citar ato, confiança ou número. O `20f` não cita o padrão. |
| ibge-rpr-1-2005 | ADR-0028 e `08` citam só "SIRGAS 2000", sem a resolução do IBGE. |
| ietf-rfc-7636, ietf-rfc-8252, ietf-rfc-9449, ietf-rfc-9700 | ADR-0030 cita as RFCs com número e assunto corretos; as correções são do catálogo (kind, ano, BCP). |
| iso-19128 | ADR-0010 cita "WMS 1.3.0" e não a ISO 19128. |
| ogc-api-features | ADR-0010 cita "OGC API – Features" sem indicar partes; nada errado. |
| ogc-geopackage-1-4 | ADR-0028 cita "GeoPackage"/"GPKG" sem versão. |
| pl-2338-2023 | ADR-0042 cita o PL como "em tramitação", o que está correto; não informa o emissor nem a classificação de alto risco. |
| rs-consema-res-286-2014, rs-consema-res-317-2016 | O `09` cita-as apenas como revogadas pela Res. CONSEMA 355/2017; sem descrição incorreta. |
| sc-fatma-portaria-206-2016, sc-ima-portaria-232-2021 | O `12` lista-as apenas entre as portarias revogadas pela IMA 009/2026, sem descrever o escopo. Observação: o `12` (RES-19 e quadro de DMR) atribui a DMRSU mensal de SC aos arts. 7º e 8º da IMA 009/2026; o verificador não localizou menção à DMRSU nos trechos lidos da 009/2026. Conferir antes de usar. |
| mma-comunicado-mtr-dgr-001-2025, mma-comunicado-mtr-dgr-002-2025 | O `12` já atribui o incidente de 06 a 19/12/2024 ao Comunicado 001 e o bloqueio por cadastro ao 002. |
| mma-portaria-219-2020 | O `12` cita a portaria sem afirmação de escopo incorreta. |
| w3c-wcag-2-2 | MARCA-E-TEMAS, RNF, ADR-0017 e `20f` citam "WCAG 2.1 AA" (e "2.2 AA" no `20f`) sem a NBR 17225, sem tradução e sem critérios; nada errado. Observação: a NBR 17225:2025 é norma própria baseada na WCAG 2.2, e a tradução autorizada é do Ceweb.br/NIC.br. |

Ressalvas de aplicação parcial (as normas constam como aplicadas em (b)):

- **febraban-layout-debito-automatico-padrao-150**: aplicada a prorrogação para 01/01/2027. A data 01/07/2027 do `20f` (LEG-38 "novo regime em 01/07/2027" e cronograma) **não foi alterada**, porque os verificadores divergem: a nota da Res. CMN 5.299 diz que a Res. BCB 565/2026 altera a Res. BCB 51/2020 a partir de 01/07/2027, e a nota do padrão FEBRABAN diz que 01/07/2027 refere-se ao Pix Automático em contas-salário (Res. BCB 587/2026) e que não se confirmou como início do novo regime de débito automático. A Res. BCB 587/2026 é citada em PT (`pay.hybrid_charge`, "facultativo a partir de 01/02/2027") e no `20f` LEG-39; a conferência fica para o lote que cobre o BCB.
- **bcb-res-505-2025**: não foram alteradas as observações de que a 505 não cria o Pix Automático e de que seu texto não cita a Res. CMN 5.251/2025 (base: Res. CMN 4.282/2013, art. 13); o `20f` apenas lista as duas resoluções juntas. A ligação com a CMN 5.251 deve ser verificada no lote do CMN.
- **stf-adi-6492**: a ADI 4.058, citada junto no quadro da Lei 11.445, não consta do catálogo deste lote e foi mantida como "não verificada".

## (e) Fora do lote

Citações encontradas em arquivos não pertencentes a este lote, sem edição:

| Arquivo | Norma | Situação |
|---|---|---|
| `docs/DIARIO-DE-BORDO.md` (linha 1192) | lei-5990-2017-df | Cita a Lei Distrital 5.990/2017 sem a nota de inconstitucionalidade (arquivo vedado a este lote). |
| `docs/levantamento/editais/18h-aderencia-nao-funcionais-servicos-habilitacao.md` (linhas 162, 308, 320, 324 e 377) | aicpa-soc-isae-3402 | Tratam SOC 1 e SOC 3 como certificação do provedor sem a nota (a nota existe só na linha 85). |

Os arquivos `13`, `14`, `11`, `18`, `18e` e o plano já trazem as correções de REsp 1.117.903, Res. CMN 5.299/2026, PNQS e SOC 1/SOC 3 com a nota "verificado em 22/10/2026" (aplicadas por outros lotes).
