# Aplicação das correções normativas, lote 1

Data: 22/10/2026. Fonte das correções: `correcoes-agente-1.json` (35 normas). Arquivos editados: 5 dos 8 do lote. Codificação conferida após a gravação (UTF-8, sem CR, sem U+FFFD; contagem de linhas inalterada em todos os arquivos).

## (a) Resumo

| Item | Total |
|---|---|
| Normas do lote | 35 |
| Normas com ao menos uma edição aplicada | 31 |
| Edições aplicadas (substituições) | 41 |
| Normas sem alteração necessária (o texto do projeto já estava correto; o erro era só do catálogo) | 4 |
| Correções não aplicadas | 0 |
| Citações fora do lote | 0 |
| Decisões a rever | 9 |

Arquivos sem edição: `arquitetura/adr/ADR-0034-gis-cadastro-tecnico.md`, `levantamento/gis/19a-gisbase.md`, `levantamento/gis/19e-requisitos-de-editais-gis.md` (as citações neles, WFS 2.0, OGC API – Features partes 1 a 3, GeoPackage, SIRGAS 2000, não contêm o dado errado apurado).

## (b) Aplicadas

Siglas de arquivo: ALVOS = `arquitetura/ALVOS-DA-OS-E-HIERARQUIA-DE-ATIVOS.md`; GIS-CT = `arquitetura/GIS-CADASTRO-TECNICO.md`; 19b, 19c, 19d = `levantamento/gis/19b-concorrentes.md`, `19c-normativos-e-padroes.md`, `19d-melhores-praticas-modelo-de-dados.md`.

| Arquivo | Norma (id) | Antes → depois (resumo) |
|---|---|---|
| ALVOS (§2) | arsesp-nt-f-0064-2022 | "aprovou em 2022 (NT.F-0064-2022, em vigor desde 01/01/2024)" → versão 1 (Deliberação 1.371/2022), vigência de 01/01/2024 para ativos imobilizados a partir dessa data, atualizada pela versão 2 de 2025 (Deliberação 1.636/2025), com nota de verificação |
| ALVOS (§7) | arsesp-nt-f-0064-2022 | "versão 2022" → "versão 2022, ou versão 1, aprovada pela Deliberação 1.371/2022"; versão 2 passa a constar como a vigente |
| ALVOS (§8) | arsesp-nt-f-0064-2022 | coincidência das listas de UAR com o manual da ARSESP → nota de que a comparação foi feita com a versão 1 |
| ALVOS (§5) | ana-voto-14-2026 | "(Voto 14/2026 da ANA)" → "item 4.8 do Voto 14/2026/DIREC, ato preparatório da NR 16/2026; o voto fala em tabela nacional de vidas úteis e taxas de depreciação e não menciona UAR" |
| ALVOS (§7) | ana-voto-14-2026 | "Voto 14/2026 (tabela única de vidas úteis)" → "Voto 14/2026/DIREC (ato preparatório da NR 16/2026; item 4.8, ...)" |
| 19c §2.1 | abnt-nbr-12266 | "NBR 12266 (edição a confirmar) / Valas para assentamento" → NBR 12266:1992, cancelada em 08/2022, substituída pela NBR 17015:2022; título completo; marcada como referência histórica |
| 19c §2.1 | abnt-nbr-12266 (efeito sobre a 17015) | "NBR 17015:2023 ... não confirmei que substitui a NBR 12266" → NBR 17015:2022 (1ª edição de 30/08/2022; listagem de 07/2023 a confirmar), cancela e substitui a NBR 12266 e as NBR 9814, 9822 e 12595 |
| 19c §2.1 | abnt-nbr-8890 | "NBR 8890 (tubos de concreto, edição a confirmar)" → "NBR 8890:2020 (tubo de concreto de seção circular para água pluvial e esgoto sanitário; classes a conferir)" |
| GIS-CT §4 (materiais de esgoto e drenagem) | abnt-nbr-8890 | "NBR 8890 (edição a confirmar)" → "NBR 8890:2020" (2 ocorrências) |
| 19c §2.1 | abnt-nbr-10068, 10647, 13142, 8196, 8402, 8403, abnt-nbr-16861-2020 | linha única "regras gerais de desenho ... indício de consolidação em NBR 16752 e 16861 (2020), a confirmar" → escopo de cada norma e situação individual (8402 e 8403 substituídas pela 16861:2020; 10068 pela 16752:2020; 13142 e 8196 canceladas sem substituta formal; 10647 cancelada em 07/2005 sem sucessora vigente identificada); NBR 16861 não consolida as demais |
| 19c §4 item 2 | abnt-nbr-12266, abnt-nbr-8890, normas de desenho | "NBR 12212, 12266, 8890, 5626 e normas de desenho (consolidação nas NBR 16752 e 16861)" → NBR 12266 retirada da pendência; 8890 reduzida às classes; substituição apurada em fonte secundária |
| 19c §2.2 | abnt-nbr-14645-2 | "cancelou a NBR 14645-2" → "NBR 14645-2:2005 (levantamento planimétrico para registro público, para retificação de imóvel urbano)" |
| 19c §2.2 | abnt-nbr-14645-3 | "14645-3 (cancelada 09/2022, substituta a confirmar)" → "14645-3:2005 (cancelada em 09/2022 e substituída pela NBR 17058:2022, ...)" |
| 19c §2.2 | dsg-et-cqdg-2016 | "ET-CQDG (DSG, 2016)" → ET-CQDG, EB80-N-72.004, 1ª edição, Portaria 009-DCT de 10/02/2016, incorpora o PEC-PCD (PAP-PCD, Tabela 32), em atualização pela CONGEO |
| 19c §2.2 | dsg-et-adgv-pec-pcd | "ET-ADGV (PEC-PCD)" → ET-ADGV, EB80-N-72.005, versão 3.0, Portaria 115-DCT de 20/11/2018; PEC-PCD estabelecido na ET-ADGV, tabelas lidas na ET-CQDG |
| 19c §2.2 e §4 item 3 | dsg-et-cqdg-2016 | "EP 0,17 mm, a confirmar no texto" → acrescenta o valor da Tabela 32 (0,28 m e 0,17 m em 1:1.000, classe A) e a ressalva de leitura automática |
| 19c §2.2 | mdr-portaria-3242-2022 | "Portaria MDR 3.242/2022 (revogou a Portaria MCidades 511/2009)" → inclui emissor MDR, DOU de 11/11/2022, art. 2º, extinção do MDR em 2023 e vigência mantida; nota sobre "saneamento é cadastro temático" |
| 19c §2.3 | agenersa-rf-222-2024 | "a AGENERSA fiscalizou ... (diferença de 192 km entre bases)" → fonte identificada como RF 222/2024, provável Relatório de Fiscalização, não norma, não localizada |
| 19c Fontes | agenersa-rf-222-2024 | "AGENERSA, RF 222/2024" → "citado como RF 222/2024 (provável Relatório de Fiscalização, não norma; documento não localizado em 22/10/2026)" |
| 19c §2.4 | iso-19125 | "OGC Simple Features / ISO 19125" → OGC Simple Feature Access 1.2.1 (06-103r4) / ISO 19125-1:2004; Parte 2 retirada em 2018; "O (técnico)" com nota de que é decisão de projeto |
| 19c §2.4 | iso-19128, ogc-wmts-1-0 | "WMS 1.3.0 (ISO 19128); WMTS 1.0" → "WMS 1.3.0 (OGC; ISO 19128:2005, Interface de servidor de mapas web); WMTS 1.0.0 (OGC 07-057r7, 2010)" |
| 19c §2.4 | iso-19142 | "WFS 2.0 (ISO 19142)" → "ISO 19142:2010, Serviço de feições web, que inclui as operações de transação" |
| 19c §2.4 | ogc-api-features | "partes 1 a 3 (... Filtering/CQL2); parte 4 (CRUD)" → CQL2 como norma separada (21-065r2); parte 4 em minuta; parte 5 (Schemas 1.0.0) aprovada em 30/04/2026 |
| 19c §4 item 6 | ogc-api-features, ogc-muddi | "Aprovação da parte 4 e da MUDDI" → parte 4 segue em minuta; aprovação da MUDDI Parte 1 confirmada (07/06/2024) |
| GIS-CT §7.2 | ogc-api-features | "Filtering/CQL2" → "Filtering; o CQL2 é norma OGC separada, referenciada pela parte 3"; parte 4 com a situação de 22/10/2026 e a parte 5 aprovada |
| 19c §2.4 | iso-19177-1-2026 | "OGC API – Tiles 1.0 (2022; ISO 19177-1:2026)" → título oficial da ISO (Parte 1: Núcleo), adaptação do OGC 20-057, norma paga; nota sobre a codificação dos tiles |
| 19c §2.4 | ogc-citygml-3-0 | "CityGML 3.0 (2021)" → Parte 1 (20-010, 2021) e Parte 2 (21-006r2, 2023) |
| 19c §2.4 | ogc-infragml-landinfra-1-0, ogc-muddi | "LandInfra/InfraGML 1.0 (2016); MUDDI (aprovação a confirmar)" → LandInfra 15-111r1 (2016), InfraGML (2017), MUDDI Parte 1 23-024 aprovada em 07/06/2024; nota de que é só modelo conceitual |
| 19c §2.4 | inspire-utility-services-d2-8-iii-6 | "D2.8.III.6 v3.0" → versão 3.2.0 de 31/01/2024 (prefácio ainda diz 3.0, 2013); título e emissor (Comissão Europeia/MIG) |
| GIS-CT §4 (compra de normas) | abnt-nbr-10004-2024 | "17076 e 10004" → "17076, 10004-1 e 10004-2 (série de 2024)" |
| GIS-CT §4.5 (resíduos) | abnt-nbr-10004-2024 | "NBR 10004 (classe I, II A, II B; edição vigente a confirmar)" → "NBR 10004-1 e 10004-2:2024 (classe 1, perigoso, e classe 2, não perigoso)", com nota de verificação |
| GIS-CT §4.4 (drenagem, bueiro) | dnit-es-030-2004 | "DNIT 030/2004-ES como referência" (para bueiro) → DNIT 023/2006-ES (tubulares) e 025/2004-ES (celulares), com nota de que a 030/2004-ES trata de drenagem pluvial urbana |
| GIS-CT §4 (perfis), §9.2 | saneago-in-00-0229-03 | "IN 00.0229.03" → "IN 00.0229, rev. 04 de 11/03/2024" (2 ocorrências, a segunda com nota de verificação) |
| 19b (Fontes) | saneago-in-00-0229-03 | URL `.../2016/fornecedores/arquivos/IN00.0229.03.pdf` → `.../arquivos/FGQ/normativos/IN00.0229.pdf`, com rótulo "rev. 04, de 11/03/2024" |
| GIS-CT §11 | iso-55000 | "ISO 55000/55001" → "ISO 55000:2024 e ISO 55001:2024" |
| 19d §9 | iso-55000 | "ISO 55000/55001" → "ISO 55000:2024 e ISO 55001:2024" |

Normas sem alteração necessária (texto do projeto já correto; o erro era do catálogo). Nas ocorrências de `iso-19128` e `iso-19142` em GIS-CT e ADR-0034 ("WMS 1.3.0", "WFS 2.0" sem número ISO) também não havia fato errado; a correção dessas duas normas foi aplicada no 19c:

- `mcidades-portaria-511-2009`: 19c cita "Portaria MCidades 511/2009" com o emissor correto e já registra a revogação pela 3.242/2022. Não existe a atribuição ao MMA nos arquivos do lote.
- `ibge-rpr-1-2005`: 19c cita "IBGE R.PR 1/2005; fim da transição pela R.PR 1/2015", correto. ADR-0034, GIS-CT e 19a citam apenas SIRGAS 2000 e EPSG 4674, sem número da resolução.
- `ietf-rfc-7946`: GIS-CT e 19c já citam "RFC 7946" em WGS-84, consistente com a ressalva do verificador.
- `ogc-geopackage-1-4`: GIS-CT, 19b, 19c e 19e citam GeoPackage 1.4 (2024), dado confirmado; a correção refere-se a campos do catálogo.

## (c) Decisões a rever

Em nenhum caso a decisão, a tarefa, o prazo ou o parâmetro foi alterado. Cada item abaixo recebeu no texto a nota "(verificado em 22/10/2026: ...)" ou o fato corrigido.

1. **Manual da ARSESP: versão de referência (ALVOS §2, §8).** O projeto leu a versão 1 (NT.F-0064-2022, 385 páginas, 29 UP). A versão vigente é a 2 de 2025 (Deliberação ARSESP 1.636/2025), com tabelas de UP, UAR e atributos revisadas. Rever se a codificação de referência e a coincidência de listas com o MCPSB (§8) devem passar a usar a versão 2; os números 385 páginas e 29 UP não foram verificados.
2. **Voto 14/2026 da ANA como base da tabela de vidas úteis (ALVOS §5).** O voto é ato preparatório; a norma exigível é a NR ANA 16/2026 (Resolução ANA 302/2026), a ser cadastrada à parte. O voto não menciona UAR; o "por UAR" do texto vem do MCPSB. Rever se a vida útil por perfil de agência (ADR-0038) deve citar a NR 16/2026 e o MCPSB, não o voto.
3. **Série NBR 10004:2024 e domínio de classe de resíduo (GIS-CT §4.5).** A série de 2024 usa classe 1 e classe 2 e abandona I, II A e II B. Se o catálogo de resíduos do GIS prevê o domínio "classe I, II A, II B", rever os valores do domínio (e o período de transição até 31/12/2026). Conferir no texto da ABNT; a fonte é secundária.
4. **Bueiro e a DNIT 030/2004-ES (GIS-CT §4.4).** A referência para a feição `CULVERT` não trata de bueiros. Rever a fonte do catálogo (DNIT 023/2006-ES e 025/2004-ES). A DNIT 023 pode ter edição nova (023/2024-ES, a confirmar no IPR).
5. **Perfil Saneago e revisão da IN (GIS-CT §4, §9.2).** O projeto citava a revisão 03; a publicada é a 04 (11/03/2024). Rever o conteúdo do perfil de importação, que dependia da leitura da IN, e registrar a revisão adotada.
6. **"Saneamento é cadastro temático" (19c §2.2, Portaria MDR 3.242/2022).** A portaria não menciona saneamento nominalmente; a inclusão decorre do art. 1º, §2º. A recomendação de guardar a inscrição imobiliária/ID da parcela do CTM permanece, mas sem apoio textual direto.
7. **"O (técnico)" para OGC Simple Features/ISO 19125 (19c §2.4).** A obrigatoriedade é decisão de projeto, não obrigação normativa; a legenda do quadro reserva "O" a obrigações legais ou contratuais. Rever a classificação.
8. **MUDDI e intercâmbio de dados subterrâneos (19c §2.4).** A MUDDI Parte 1 é só modelo conceitual; não há esquema de troca pronto. A frase "referência para intercâmbio de as-built e dados subterrâneos" precisa ser lida como referência conceitual.
9. **Tiles vetoriais na ISO 19177-1 / OGC API – Tiles (19c §2.4).** A norma define a API de tiles; o formato de codificação (MVT, por exemplo) deve ser verificado à parte. Rever a premissa "tiles vetoriais" antes de fixar o formato em ADR.

Observação sem edição de decisão: a evidência da AGENERSA (diferença de 192 km entre bases) vem de um documento não localizado e provavelmente não normativo (19c §2.3); o argumento de que "a exigência costuma estar no contrato de concessão" não depende dele, mas a evidência deve ser tratada como não verificada.

## (d) Não aplicadas

Nenhuma. As ressalvas dos verificadores sobre pontos fora do que o projeto afirma (por exemplo, certificação OGC de produtos, selo de conformidade de tubos, CETESB DD 078/2025/I/C, IN IBAMA 24/2024, Utility Network ADE) não geraram edição, por não haver trecho do lote a corrigir. A afirmação do 19c de que o Utility Network ADE não é norma OGC foi mantida como ressalva, conforme a própria correção.

## (e) Fora do lote

Nenhuma citação errada foi encontrada em arquivo fora de `owned_files` durante as buscas feitas nos arquivos do lote. Não foram varridos os demais arquivos de `docs\`, que cabem a outros lotes. Observação: o 19c mantém, fora das correções do lote, itens que o verificador não tratou (por exemplo "Res. 302, 28/08/2026" e "em vigor desde 31/08/2026" para a NR 16/2026 no 19c e no ALVOS, quando a nota de vigência cita publicação reportada em 31/08/2026 por fonte secundária); não foram alterados.
