# Aplicação de correções de normas: lote 5

Data de referência da verificação: 22/10/2026. Fontes: `correcoes-agente-5.json` (15 normas da reverificação, grupo A), fatos confirmados em fonte oficial em 22/10/2026 (grupo B) e pontos deixados fora dos lotes 1 a 4 (grupo C). Arquivos editados (15), todos em UTF-8 com LF, com 0 caracteres U+FFFD e 0 CR depois de cada edição: `PLANO-EXECUCAO-PDCA-R0-R9.md`, `arquitetura/PARAMETROS-DO-TENANT.md`, `arquitetura/adr/ADR-0022-portais-historico-e-documentos.md`, `arquitetura/adr/ADR-0028-escopo-funcional-ampliado-pelos-editais.md`, `levantamento/08`, `09`, `11`, `13`, `levantamento/editais/18-ADERENCIA-EDITAIS-CONSOLIDADO.md`, `18c`, `18e`, `levantamento/gis/19c`, `levantamento/gsan/20f`, `levantamento/lacunas/22-INCORPORACAO-NO-PLANO.md` e `22d`. A contagem de linhas mudou só em `18e` (713 para 715) e no ADR-0028 (294 para 297), por quebras de linha em prosa; nos demais, as edições ficaram dentro das linhas existentes (45 linhas alteradas no total, conferidas por comparação com a cópia anterior). Nenhuma decisão, tarefa, prazo ou parâmetro foi alterado. Não houve commit.

## (a) Totais

| Item | Total |
|---|---|
| Normas do grupo A (reverificação) | 15 |
| Grupo A com ao menos uma edição | 10 |
| Grupo A sem edição (ver "Não aplicadas") | 5 |
| Pontos do grupo B (débito automático e Pix Automático; MP 1.393/2026) | 2, em 8 arquivos |
| Pontos do grupo C | 4 (3 com edição, 1 já aplicado por lote anterior) |
| Substituições exatas efetuadas | 41 |
| Arquivos alterados | 15 |
| Decisões a rever | 9 |

## (b) Aplicadas

Onde a correção alterou uma premissa, o texto recebeu a nota "(verificado em 22/10/2026: ...)".

### Grupo A

| Arquivo | Norma ou tema | Antes → depois |
|---|---|---|
| `levantamento/editais/18c` (L67) | Decreto 29/2023 de São Ludgero | "Decreto Municipal 29/2023" sem verificação → acrescentado que o ato foi localizado no DOM/SC (ato 4701594, 03/04/2023), confirma a assinatura avançada via gov.br (art. 4º, II e parágrafo único) e traz a ressalva do § 1º do art. 5º (sistema do próprio município só vale como avançada após ratificação do chefe do Executivo) |
| `levantamento/13` (L60) | Lei RS 12.498/2006 | "NÃO CONFIRMADO" → revogada por consolidação pelo art. 127, XXI, da Lei RS 13.320/2009; o direito consta do art. 54 e parágrafo único da 13.320/2009, em vigor |
| `levantamento/13` (L60) | Lei RR 886/2013 | "NÃO CONFIRMADO" → texto lido no SAPL da Assembleia de Roraima, sem alterações posteriores, não revogada pela Lei RR 2.335/2026, vigente; a célula de confiança ficou "salvo RS e RR" |
| `levantamento/08` (L382) | Lei PR 14.471/2004 | Lei citada como base da regra de religação da AGEPAR → acrescentado que ela se dirige apenas à Sanepar e que, para os demais prestadores de água do Paraná, a regra consta da Lei 22.130/2024, arts. 154 a 156 e 159 |
| `levantamento/09` (L22, L42) | Nova resolução CONAMA de efluentes | "aprovou em 02/09/2026" → nota de que o comunicado do MMA não indica em qual plenária (01/09 ou 02/09) cada norma foi aprovada; a associação à 151ª reunião vem da página do processo no SINCONAMA, que não registra o resultado; continua sem publicação no DOU |
| `levantamento/gsan/20f` (L185, L331) | Res. ARIS 04/2012 | "não localizada / presumivelmente absorvida pela RN 19/2019" → revogada expressamente pelo art. 27 da RN ARIS 41/2024 (09/10/2024, DOM/SC 6532226); os prazos da 04/2012 não valem mais |
| `levantamento/gis/19c` (L78, L199) | AGENERSA RF 222/2024 | "provável Relatório de Fiscalização, documento não localizado" → identificado (RF AGENERSA/CASAN nº 222/2024, Câmara Técnica de Saneamento, processo SEI-480002/001229/2024, assinado em 09/09/2024; Iguá, Bloco 2), localizado no site da AGENERSA; documento de fiscalização, não norma; amostra não representativa |
| `levantamento/lacunas/22d` (L359) | ARSESP NT.F-0083-2020 | "sem ato de aprovação localizado" → aprovação localizada em atas da Diretoria (processo ARSESP.ADM 0180-2020); ato normativo correspondente: Deliberação ARSESP 1.107, de 29/12/2020, cujo texto não foi aberto. O título já estava correto no 22d (L39) |
| `levantamento/lacunas/22d` (L51, L221, L479, L696) | PL 2.338/2023 | "o texto original classifica avaliação de acesso a serviços essenciais e de crédito como alto risco" → o texto enviado à Câmara (autógrafo de 31/01/2025) lista 12 hipóteses no art. 14, sem item específico sobre crédito; úteis: inciso I (infraestruturas críticas, inclusive redes de água) e inciso IV (acesso e elegibilidade a serviços essenciais); lista regulamentada pelo SIA (arts. 15 e 16). "Versão do Senado não conferida" removida |
| `levantamento/11` (L126) | CNAB 444 | "chave da NF-e nas posições 401 a 444" → as posições variam entre administradoras (401 a 444 no manual da Athenabanco; 395 a 438 no PDF da FIDD); a correção de que não é padrão FEBRABAN já constava (lote 2) |

### Grupo B (1): débito automático e Pix Automático, duas regras com datas diferentes

Padrão aplicado: 01/01/2027 = fim do prazo de adequação ao Pix Automático para convênios de serviços públicos (Res. CMN 5.278/2025, de 24/12/2025, que alterou o art. 2º-A, §2º, da Res. CMN 4.790/2020; enquadramento da água e esgoto a analisar juridicamente); 01/07/2027 = vigência do novo regime geral do débito automático (Res. BCB 565/2026, de 04/05/2026, que altera a Res. BCB 51/2020; a Res. CMN 5.299/2026 é norma de diretrizes).

| Arquivo | Antes → depois |
|---|---|
| `levantamento/11` (L34, L42, L66, L136, L228, L268) | Ressalva das duas regras no resumo; "nova regra de débito automático (01/07/2027)" → "novo regime geral" com remissão ao prazo de 01/01/2027; 5.278 passa a ser identificada como alteradora do art. 2º-A, §2º; R15 e calendário de 01/07/2027 marcam a data como regra distinta; a pergunta sobre "migração obrigatória dos clientes legados até 01/07/2027" recebeu a nota de que o prazo de adequação ao Pix Automático é outro |
| `levantamento/gsan/20f` (L95, L253, L297) | LEG-38, calendário e item 6 da lista de divergências: mesma distinção, com a data de 01/07/2027 atribuída à Res. BCB 565/2026 |
| `levantamento/editais/18e` (D2) | "para convênios de prestação de serviços públicos, caso do saneamento" → o enquadramento do saneamento passa a "exige análise jurídica"; acrescentadas as duas regras e suas datas |
| `levantamento/editais/18` (L4) | Nota sobre o prazo de 01/01/2027 ao lado da vigência de 01/07/2027 |
| `arquitetura/adr/ADR-0028` (§ f) | Mesma nota em duas pontas do parágrafo (5.278 e 4.790 em vigor até 30/06/2027) |
| `arquitetura/PARAMETROS-DO-TENANT.md` (`pay.pix_automatic`) | Mesma nota na coluna de justificativa |
| `PLANO-EXECUCAO-PDCA-R0-R9.md` (P3.24) | Nota de 22/10/2026 sem alterar tarefa nem prazo: a data de desligamento do legado depende de duas regras distintas do Banco Central |

### Grupo B (2): MP 1.393/2026

| Arquivo | Antes → depois |
|---|---|
| `levantamento/13` (L41, L293) | "altera o art. 57 apenas para combustíveis; não afeta requisitos de software" → a MP, em vigor e dependente de conversão, institui o Desenrola Brasil 3.0 (até 30/06/2028); os arts. 31-B e 31-C (Lei 14.690/2023) autorizam a União a adquirir carteiras de dívidas de pessoas físicas, inclusive com prestadores de serviços públicos e de utilidade pública; pode afetar cobrança, negativação e parcelamento; adesão é decisão do prestador ou do ente, com análise jurídica (Q11) |
| `levantamento/gsan/20f` (L9, L173) | "sem efeito no produto" → vale apenas quanto ao art. 57; acrescentados os arts. 31-B e 31-C, deságio mínimo de 90%, inadimplência entre 720 e 1.645 dias contados de 25/09/2026, valor original abaixo de R$ 10.000; a pergunta 14 do `13` fica aberta quanto à conversão |

### Grupo C

| Arquivo | Ponto | Antes → depois |
|---|---|---|
| `levantamento/11` (L148, L278) | REsp 1.117.903/RS | "tema repetitivo e número NÃO CONFIRMADOS" → Temas 251 a 254, julgado em 09/12/2009 (DJe 01/02/2010), pretensão de cobrança pelo prestador; repetição de indébito pelo usuário na Súmula 412 e no Tema 932. Pendente do lote 2 (item 3 de "Não aplicadas"), agora aplicado; a nota do Migalhas não foi reaberta |
| `arquitetura/adr/ADR-0022` (L84) | WCAG 2.1 contra 2.2 | "WCAG 2.1 AA" → nota: a WCAG 2.2 (W3C, 05/10/2023; também ISO/IEC 40500:2025) é compatível com a 2.1 e acrescenta critérios; o RNF-60 permanece em 2.1 AA; a elevação a 2.2 AA está proposta no PLT-31 (22c) e fica a decidir |
| `levantamento/lacunas/22-INCORPORACAO-NO-PLANO.md` (L160) | Fatores Q, P e U (CP 03/2026 da ANA) | GES-30 sem ressalva → nota de que a menção não foi encontrada nas fontes lidas (mesma nota já existente no 22d) |
| `levantamento/gsan/20f` (L85) | REsp 1.117.903/RS | Já corrigido pelo lote 3 (Temas 251 a 254; Súmula 412; Tema 932); conferido, sem nova edição |
| `levantamento/11` (L67) | Res. CMN 5.299/2026 e 5.251/2025 ("Fora do lote" do lote 4) | Já corrigidos pelo lote 2 (diretrizes; regime operacional na Res. BCB 565/2026; 5.251 altera a 4.790); conferido, sem nova edição. O rótulo "01/07/2027" que o lote 2 deixou sem conferir ("Não aplicadas", item 1) foi resolvido pelo grupo B |

## (c) Decisões a rever

Nenhuma decisão, tarefa, prazo ou parâmetro foi alterado. Os pontos abaixo mudaram uma premissa.

1. **Prazo de adequação ao Pix Automático e novo regime geral.** São duas regras (01/01/2027 e 01/07/2027). Se a cobrança de água e esgoto é "convênio de prestação de serviço público" é questão jurídica em aberto; a urgência da P4.5 e a existência da P3.24 (FEBRABAN 150 legado) dependem dessa resposta e de os bancos do piloto aceitarem o legado só em caso intrabanco. A P3.24 e a ED-27 não foram alteradas.
2. **MP 1.393/2026 (Desenrola Brasil 3.0).** A premissa "sem efeito no produto" vale só para o art. 57 do CDC. A adesão à compra de carteiras pela União é decisão do prestador ou do ente e exige parecer (Q11); o efeito é possível em cobrança, negativação e parcelamento (P3.1, P4.1). O cronograma divulgado é chamamento em novembro de 2026, propostas em dezembro, homologação em janeiro de 2027 e contratos em fevereiro de 2027.
3. **PL 2.338/2023.** A justificativa de risco regulatório do escore de crédito (22d, GES-24, L479) apoia-se em "acesso a serviço essencial" (art. 14, IV), e não em crédito; reavaliar se o texto do GES sobre "perfilamento financeiro" continua precisando de apoio no PL.
4. **ARIS.** A RN ARIS 41/2024 revogou a Res. 04/2012 e trata da Ouvidoria; o 20f (LEG-25 e LEG-27) cita ARIS arts. 23 e 24 (RN 19/2019) para prazos de resposta e ouvidoria. Conferir se a RN 41/2024 alterou esses prazos antes de pré-carregar o perfil SC.
5. **Lei PR 14.471/2004.** Vale apenas para a Sanepar; o perfil AGEPAR (Res. 3/2020) trata a Sanepar, mas a regra de corte e religação para prestadores de água do PR é a da Lei 22.130/2024. O 08 §5.5 não distingue os prestadores; se um SAAE do Paraná for tenant, usar a lei de 2024.
6. **Assinatura em sistema próprio do município (Decreto 29/2023 de São Ludgero).** A assinatura gerada pelo sistema do município só vale como avançada após avaliação e ratificação do chefe do Executivo (§ 1º do art. 5º); isso afeta o requisito de assinatura avançada do edital (Edital 8.2.3) quando a assinatura vier do Ziip SAN.
7. **WCAG.** O RNF-60 e o ADR-0022 seguem em 2.1 AA, enquanto o PLT-31 propõe 2.2 AA. A WCAG 2.2 é compatível e é a base da ISO/IEC 40500:2025; a decisão é do dono do produto.
8. **Fatores Q, P e U (GES-30).** A justificativa depende de uma menção que não foi encontrada na CP 03/2026; a nota está no 22d e agora no 22-INCORPORACAO.
9. **CONAMA, efluentes.** A data de 02/09/2026 e a 151ª reunião são da página do processo; o comunicado do MMA não distingue as plenárias. Não codificar limites antes da publicação.

## (d) Não aplicadas

| Norma | Motivo |
|---|---|
| `decreto-46-2023-sao-ludgero` | O único trecho (`18c`, SG-1394) é transcrição literal do edital ("Conforme Decreto Municipal n. 46/2023 ... IN RFB 1.234/2012"); não há "não localizado" a corrigir. A correção melhora só o catálogo |
| `samae-slu-portaria-19-2024` | O `18c` (L16) cita a Portaria SAMAE SLU 19/2024 sem dizer que não foi localizada; sem dado errado a corrigir |
| `arsae-resolucao-189-2024` | O `08` (L98) já lista a Res. 189/2024 entre as alteradoras da Res. 131/2019, o que a verificação confirma; nenhum documento do projeto afirma o contrário |
| `pl-4752-2025` | O `13` (L23, L91) já traz CCT, relatoria redistribuída em 03/09/2026 e última movimentação de 04/09/2026; a reverificação não encontrou fato novo |
| `pl-6365-2025` | O `08` (L36) já registra o último movimento de 14/10/2026; o despacho de 06/04/2026 (CCJ e depois CMA) é detalhe de catálogo e não corrige dado do texto |
| `febraban-cnab-444` (parte) | A correção principal (layout próprio das administradoras, não FEBRABAN) já estava nos relatórios 00, 03 e 11 (lote 2); só a variação de posições da chave foi acrescentada. A frase "muitos bancos já migram" no `03` §7 (L588) não foi confirmada e não foi alterada |
| Melhorias só de catálogo | Os campos novos dos itens do grupo A (por exemplo, as atas da ARSESP e a descrição dos arts. da Lei 22.130/2024) que o texto dos documentos não cita foram deixados no catálogo |
| `levantamento/14` (L112) | A linha "Pix Automático | 01/02/2027, 01/07/2027" vem da Res. BCB 587/2026 (conta-salário), não do novo regime geral; não mistura as duas datas do grupo B, e por isso não foi editada. O `14` L35 e L61 já estão coerentes com as duas datas |
| `levantamento/lacunas/22a`, `22c`, `22d` (débito automático) | Já distinguem a prorrogação do prazo de 01/01/2027 (Res. CMN 5.278/2025) do novo regime (Res. BCB 565/2026); fora da lista do grupo B e sem mistura de datas |

Observação: o grupo B (2) da tarefa cita a Q11 de `docs/normas/questoes-para-parecer-juridico.md`; esse arquivo não foi editado.
