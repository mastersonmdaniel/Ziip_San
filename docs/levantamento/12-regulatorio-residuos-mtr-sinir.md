# 12 — Regulatório de resíduos sólidos: MTR, CDF, SINIR, PGRS e limpeza urbana (2015–2026)

Data da pesquisa: 12/10/2026. Escopo: validar o módulo `grs/sigsan-mtr` e o que falta para resíduos sólidos contra as exigências legais e regulatórias de 2015 até hoje.

> **Aviso: isto não é parecer jurídico.** É um levantamento técnico de requisitos para software. Toda conclusão sobre obrigatoriedade, enquadramento do tenant, prazos e penalidades exige revisão por advogado ambiental/regulatório antes de virar decisão de produto. Onde não consegui confirmar, está escrito **NÃO CONFIRMADO**.

**Convenção de confiança.** Alta = li o texto do ato em fonte primária hoje. Média = li fonte primária só em parte, ou fonte secundária consistente com outras. Baixa = só fonte secundária, ou fontes que se contradizem.

**Convenção de referências.** `N..` = norma federal/técnica (quadro (a)); `E..` = norma estadual/municipal (quadro (a)); `M-SINIR` = Manual de Ajuda ao Usuário do MTR Nacional v1.10 (documento operacional do MMA, **não é norma**, mas descreve regras que o sistema impõe); `TU-MTR` = Termo de Uso e Política de Privacidade do MTR Nacional (v. 24/05/2024).

---

## 0. Resumo executivo

1. **O MTR do SIGSAN, como está, não tem valor legal.** O MTR é "emitido pelo SINIR" (Portaria MMA 280/2020, arts. 1º §1º e 3º VIII) ou por sistema estadual compatível; o número é do sistema. O SIGSAN gera número local e o stub do SINIR devolve `PENDING` para sempre.
2. **O CDF do SIGSAN também não vale.** A Portaria 280 (art. 14 §§3º-7º) diz que o CDF é do destinador e só é reconhecido se emitido pelo sistema. Além disso, o CDF oficial agrega vários MTRs de um período; o SIGSAN grava 1 CDF por MTR (`waste_cdf.mtr_id UNIQUE`).
3. **Existe Web Service oficial do MTR Nacional, mas não achei a documentação pública.** O Termo de Uso do MTR Nacional (v. 24/05/2024) afirma que geradores, transportadores, destinadores e armazenadores "podem ter seus próprios sistemas integrados" via WS, com login/senha e autorização do perfil. **NÃO CONFIRMADO** onde o MMA entrega o manual. Os sistemas de MG, ES, SP e SC publicam WS com a mesma família de rotas (`gettoken`, `salvarManifestoLote`, `retornaLista*`), o que é pista forte, mas não prova, de que o federal segue o mesmo desenho.
4. **Desde 1º/08/2026 o acesso humano ao MTR Nacional é só por Gov.br.** O impacto sobre credenciais de WS **NÃO CONFIRMADO**. A decisão 5 do Diário ("OAuth2 Gov.br, ICP-Brasil") não corresponde ao que o sistema oficial exige: o MTR é "autodeclaratório" (Portaria 280, art. 1º §1º) e não pede assinatura ICP-Brasil na emissão.
5. **O tenant de saneamento é gerador sujeito a PGRS e, portanto, a MTR**, pelo lodo de ETA/ETE (Lei 12.305, art. 13 I "e" e art. 20 I). A CETESB lista "geradores de resíduos dos serviços públicos de saneamento básico" entre os obrigados. **RSU (coleta urbana) não exige MTR**, mas exige DMR/inventário. O SIGSAN não tem nenhuma entidade de lodo.
6. **Há 5 estados com sistema próprio e regras próprias confirmadas em fonte primária (SP, MG, RJ, RS, SC) e o ES com WS publicado.** Fluxo interestadual exige MTR duplo (SP, SC). Prazos de validade do MTR, DMR e CDF variam. O sistema precisa ser parametrizável por tenant.
7. **A Portaria 280 está em revisão.** Consulta pública de 23/01 a 22/02/2025 (1.154 contribuições). A minuta revoga a 280. Em 12/10/2026 a página de legislação do SINIR ainda lista a 280 como norma do MTR e nenhuma portaria nova de MTR. Há uma dúvida de base legal: a 280 regulamenta artigos do Decreto 7.404/2010, revogado pelo Decreto 10.936/2022.
8. **Catálogo de classes: a Lista IBAMA oficial existe em XLS tratável** (878 códigos de 6 dígitos, 449 marcados com asterisco) e o modelo `WasteClass` está errado: `hazard_class` aceita `I, II, III`, mas a NBR 10004:2004 usa I, IIA, IIB. A NBR 10004 foi substituída pela série 2024 (publicada em 27/11/2024); o conteúdo das classes novas é **NÃO CONFIRMADO** (fontes secundárias divergem). Em SP a edição nova vale a partir de 01/01/2027.
9. **O código tem defeitos que impedem o fluxo de fechar:** `IN_TRANSIT` é inalcançável (o `receiveMtr` sempre falha a partir de `COLLECTED`), `Map.toString()` grava JSON inválido em `jsonb`, o hash em cadeia cai silenciosamente para UUID aleatório, e o status de sincronização nunca é gravado. Zero testes.
10. **Referência de transporte desatualizada em todo lugar:** a ANTT 5.232/2016 foi revogada em 1º/07/2021 pela 5.947/2021, que por sua vez foi revogada pela **5.998/2022** (vigor 01/06/2023; alterada pelas 6.016/2023 e 6.056/2024). O próprio manual do SINIR ainda cita a 5.947.

---

## 1. O que existe hoje no SIGSAN (conferido no código em 12/10/2026)

Fontes lidas: `docs/DIARIO-DE-BORDA.md` (seções 1, 2, 9, 11, 15), `docs/levantamento/00-CONSOLIDADO-...md` e o módulo `grs/sigsan-mtr` inteiro. Os caminhos abaixo são relativos a `G:\Meu Drive\PROJETOS\Ziip SAN\sigsan`.

| Item | Estado | Evidência |
|---|---|---|
| Módulo `grs/sigsan-mtr` | Compila, 6 entidades, 6 repositórios, 3 serviços; **sem nenhum teste**; migration `V2_4` roda no `FreshInstallTest` | `grs/sigsan-mtr/src/main/...`; Diário seções 2 e 11.1 |
| `grs/sigsan-pgrs`, `grs/sigsan-collection-waste`, `grs/sigsan-disposal` | **Diretórios vazios, fora do reactor** (o `pom.xml` raiz só lista `grs/sigsan-mtr`) | `find`; `pom.xml` linhas 128-135 |
| `SinirIntegrationService` | Monta e valida payload, devolve `PENDING`, nunca `SYNCED`. O retorno é **ignorado** pelo `MtrService` e `sinir_sync_status` não é gravado | `SinirIntegrationService.syncMtr`; `MtrService.collectMtr` e `destinateMtr` |
| `GovBrSignatureService` | Devolve `PENDING`, assinatura nula; **não é chamado em lugar nenhum** | Diário seção 9; leitura do código |
| `MtrPdfGenerator` | Gera PDF de MTR e de CDF; o PDF do MTR gerado em `destinateMtr` é descartado ("Store MTR PDF in document storage" é comentário) | `MtrService.destinateMtr` |
| Catálogo `waste_class` | **Vazio de propósito** (seed com códigos inventados foi removido) | `V2_4`, final do arquivo; Diário 15.4 |
| Entidades | `WasteMtr` (1 classe de resíduo por MTR, só `quantity_kg`), `WasteCdf` (1:1 com MTR), `WasteClass`, `WasteGenerator`, `WasteTransporter`, `WasteDestination` | `V2_4` |
| Modelo de ator | Gerador, transportador, destinador. **Não há armazenador temporário**, DMR, PGRS, inventário, perfil de resíduo do gerador, nem lodo | grep no repositório; `quality` só tem `SamplingPoint SLUDGE` (ponto de coleta, não resíduo) |

### Defeitos de código relevantes ao regulatório (leitura estática; nada foi executado)

| # | Defeito | Onde | Efeito |
|---|---|---|---|
| D-MTR-1 | `validateTransition` permite `COLLECTED -> IN_TRANSIT`, mas **nenhum método** leva a `IN_TRANSIT`; `receiveMtr` valida o alvo `RECEIVED` a partir de `COLLECTED`, que só admite `IN_TRANSIT` ou `CANCELLED` | `MtrService.validateTransition`, `receiveMtr` | `IllegalStateException`: nenhum MTR chega a `RECEIVED`/`DESTINED`. Também não existem `cancelMtr`, `rejectMtr`, nem job de expiração (`EXPIRED` só existe no enum) |
| D-MTR-2 | `request.properties().toString()` e `processDetails().toString()` são gravados em coluna `jsonb` | `createMtr`, `destinateMtr` | `Map.toString()` gera `{a=b}`, que **não é JSON**: o INSERT falha em runtime sempre que `properties` vier preenchido |
| D-MTR-3 | `generateHashChain` serializa a **entidade JPA** com Jackson, usa `getBytes()` com charset padrão e, em qualquer exceção, devolve `UUID.randomUUID()` | `MtrService.generateHashChain` | Cadeia de integridade não reproduzível entre JVMs/plataformas e **falha silenciosa** (o oposto da regra do Diário "falha barulhenta") |
| D-MTR-4 | `generateMtrNumber` usa `countByTenantId + 1` e `new Random()` para o "dígito" | `MtrService.generateMtrNumber` | Colisão sob concorrência; número sem relação com o sistema oficial |
| D-MTR-5 | `syncMtr` roda dentro da transação e `buildRequest` lança `IllegalArgumentException` para payload inválido | `collectMtr`, `destinateMtr` | Validação de payload do SINIR pode desfazer a operação local; o status de envio nunca é persistido |
| D-MTR-6 | `waste_class.hazard_class CHECK IN ('I','II','III')` e enum `HazardClass { I, II, III }` | `V2_4`; `WasteClass.java` | Valores **inexistentes** na NBR 10004 (ver D02/D03). Grupos A-E de RSS e classes A-D de RCC nem cabem |
| D-MTR-7 | `@Index(name="idx_waste_mtr_delivered", columnList="delivered_at")` na entidade; a coluna real é `received_at` | `WasteMtr.java` vs `V2_4` | Menor: só causa problema se o Hibernate gerar DDL; hoje o `validate` não olha índices |

---

## (a) Quadro de normas

Fontes primárias lidas hoje: Planalto, DOU (`in.gov.br`), SINIR, IBAMA, CONAMA, ANA, ANTT, SEMAD/MG, IMA/SC, CETESB (manual do usuário). Quando só achei espelho ou resumo de busca, está dito na coluna Confiança.

### Federais, agências e técnicas

| ID | Norma | Esfera | Data / última alteração | Vigente? | Fonte (URL) | Confiança |
|---|---|---|---|---|---|---|
| N01 | **Lei 12.305/2010 (PNRS)** | Federal | 02/08/2010. Alterada no período por **Lei 14.026/2020** (art. 19 XIX: revisão do plano municipal em no máx. 10 anos; art. 54: prazos de disposição final) e **Lei 15.088/2025** (art. 49: proíbe importar resíduos e rejeitos, com exceções por regulamento) | Sim | https://www.planalto.gov.br/ccivil_03/_ato2007-2010/2010/lei/l12305.htm | Alta |
| N02 | **Lei 14.026/2020 (Marco do Saneamento)** | Federal | 15/07/2020 | Sim | https://www.planalto.gov.br/ccivil_03/_Ato2019-2022/2020/Lei/L14026.htm (efeitos lidos nos textos compilados de N01 e N03) | Alta |
| N03 | **Lei 11.445/2007** (saneamento), com as alterações do período: Lei 13.308/2016, 13.312/2016, 13.329/2016, MPs 844 e 868/2018 (vigência encerrada), 14.026/2020, 14.546/2023, MP 1.154/2023 e Lei 14.600/2023, 15.012/2024, 15.112/2025. Arts. relevantes: 3º-C (limpeza urbana e manejo de RSU), 29 II, 35, 53 (SINISA) | Federal | Última alteração: Lei 15.112/2025 | Sim | https://www.planalto.gov.br/ccivil_03/_ato2007-2010/2007/lei/l11445.htm | Alta |
| N04 | **Decreto 10.936/2022** (regulamenta a PNRS) | Federal | 12/01/2022. Revogou Decretos 7.404/2010, 9.177/2017, 5.940/2006. **Nenhuma alteração indicada no texto compilado do Planalto** lido hoje | Sim | https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2022/decreto/D10936.htm | Alta (ausência de alterações: média) |
| N05 | **Decreto 11.043/2022 (Planares)** | Federal | 13/04/2022. Horizonte de 20 anos, revisão a cada 4 anos (resumo de busca) | Sim | https://planalto.gov.br/ccivil_03/_Ato2019-2022/2022/Decreto/D11043.htm | Média (metas lidas em fonte secundária; o anexo não foi lido) |
| N06 | **Portaria MMA 280/2020** (MTR Nacional e Inventário Nacional) | Federal (MMA) | 29/06/2020, DOU 30/06/2020. Obrigatoriedade desde 01/01/2021 (art. 19). Prorrogação excepcional de prazos em 2025 pela Portaria GM/MMA 1.376/2025 | Sim, tratada como vigente pelo SINIR; **ressalva**: regulamenta arts. 56 e 76 do Decreto 7.404/2010, **revogado** pelo Decreto 10.936/2022 | https://www.in.gov.br/en/web/dou/-/portaria-n-280-de-29-de-junho-de-2020-264244199 | Alta |
| N07 | **Portaria GM/MMA 1.376/2025** | Federal (MMA) | 25/04/2025. Só para 2025: Inventário até 15/05/2025; informações dos entes ao SINIR até 31/05/2025 | Esgotada (efeito de 2025) | https://www.in.gov.br/en/web/dou/-/portaria-gm/mma-n-1.376-de-25-de-abril-de-2025-626078863 | Alta |
| N08 | **Portaria MMA 412/2019** e **219/2020** (SINIR; informação anual de UF e municípios) | Federal (MMA) | 412: 25/06/2019 (art. 4º: a partir de 2020, prazo **30/04** de cada ano). 219: 29/04/2020 | Sim (a 412 cita o Decreto 7.404; o equivalente atual é o art. 79 do N04) | https://www.in.gov.br/en/web/dou/-/portaria-n-412-de-25-de-junho-de-2019-176002314 | Alta |
| N09 | **Revisão da Portaria 280** (proposta de nova portaria que "dispõe sobre o módulo MTR Nacional e o módulo de elaboração do PGRS do Sinir", dispõe sobre o Inventário e **revoga a 280**) | Federal (MMA) | Consulta pública 23/01 a 22/02/2025, 1.154 contribuições, encerrada | **Ato não publicado até onde pude ver** (a página de legislação do SINIR em 12/10/2026 não lista portaria nova de MTR). Se sair, substitui N06 | https://www.gov.br/participamaisbrasil/revisao-da-portaria-n-280-2020-mtr | Média (ausência de publicação não é prova) |
| N10 | **Logística reversa (conjunto)**: Decretos 10.240/2020 (eletroeletrônicos), 10.388/2020 (medicamentos), 11.300/2022 (vidro), 11.413/2023 (certificados de crédito), 12.688/2025 (embalagens plásticas); Portarias GM/MMA 1.102/2024, 1.117/2024, 1.560/2026, 1.561/2026 e **1.778/2026** (Sisrev-BR, 16/09/2026) | Federal | A 1.778/2026 cria o Sisrev-BR no SINIR; relatórios anuais até 30/07; aceita CDFs do MTR Nacional como lastro | Sim | https://sinir.gov.br/informacoes/legislacao/ ; https://www.in.gov.br/web/dou/-/portaria-gm/mma-n-1.778-de-16-de-setembro-de-2026-732885596 | Alta (existência e escopo); aplicabilidade ao tenant: baixa |
| N11 | **IN IBAMA 13/2012** (Lista Brasileira de Resíduos Sólidos) | Federal (IBAMA) | 18/12/2012, DOU 20/12/2012. O IBAMA "atualizará a Lista sempre que necessário" (art. 1º, par. único). **Nenhuma atualização localizada** | Sim | https://www.gov.br/ibama/pt-br/assuntos/emissoes-e-residuos/residuos/politica-nacional-de-residuos-solidos-pnrs (arquivo oficial: `.../arquivos/ibama-lista-brasileira-de-residuos-solidos.xls`) | Alta (norma e arquivo); ausência de atualização: média |
| N12 | **IN IBAMA 01/2013** (Cadastro Nacional de Operadores de Resíduos Perigosos) | Federal (IBAMA) | 2013 | Sim (citada pelo IBAMA em 2026) | https://www.gov.br/ibama/pt-br/assuntos/emissoes-e-residuos/residuos/arquivos/in_1_2013_cnorp_public_dou_30jan2013.pdf (texto não lido; só a referência da página do IBAMA) | Média |
| N13 | **IN IBAMA 13/2021** (obrigação de inscrição no CTF/APP). Substituiu a IN 6/2013 e outras | Federal (IBAMA) | 23/08/2021. Alterada pelas INs IBAMA 6/2022, 8/2023, 23/2024 (14/11/2024) e **23/2025 (23/12/2025)** | Sim | https://www.gov.br/ibama/pt-br/servicos/cadastros/ctf/ctf-app/ctf-app/20260105_IN_13_2025_V_2026.pdf | Alta |
| N14 | **IN IBAMA 12/2021** (CTF/AIDA: responsável técnico pelo PGRS) | Federal (IBAMA) | 20/08/2021 | Sim | https://www.gov.br/ibama/pt-br/servicos/cadastros/ctf/ctf-aida | Alta |
| N15 | **Lei 6.938/1981, art. 17-C** (TCFA: relatório anual até **31 de março**), com a redação da Lei 10.165/2000 | Federal | 2000 | Sim | https://www.planalto.gov.br/ccivil_03/leis/l6938.htm | Alta |
| N16 | **Resolução CONAMA 313/2002** (Inventário Nacional de Resíduos Sólidos Industriais). Art. 2º I inclui no conceito os **lodos de sistemas de tratamento de água** | Federal (CONAMA) | 29/10/2002. Nenhum ato revogador localizado; a Portaria 280 (art. 16) a usa como base do Inventário | Sim | https://www.normasbrasil.com.br/norma/resolucao-313-2002_97682.html (espelho) | Média (fonte secundária) |
| N17 | **Resolução CONAMA 307/2002** (RCC), alterada pelas 348/2004, 431/2011, 448/2012 e **469/2015** | Federal (CONAMA) | 05/07/2002. Última alteração: 469/2015 | Sim | https://conama.mma.gov.br/?option=com_sisconama&task=arquivo.download&id=305 | Alta |
| N18 | **Resolução CONAMA 358/2005** (tratamento e disposição final de RSS) | Federal (CONAMA) | 29/04/2005. Sem alteração indicada na versão do portal | Sim | https://conama.mma.gov.br/?option=com_sisconama&task=arquivo.download&id=453 | Alta |
| N19 | **RDC ANVISA 222/2018** (boas práticas de gerenciamento de RSS; PGRSS) | Federal (ANVISA) | 28/03/2018; substituiu a RDC 306/2004; vigor 180 dias após publicação. Nenhuma alteração localizada | Sim | https://www.fitoterapiabrasil.com.br/sites/default/files/legislacao/rdc_222-2018.pdf (cópia do texto, não o portal da ANVISA) | Média |
| N20 | **Resolução CONAMA 498/2020** (produção e aplicação de **biossólido** em solos). **Revogou a 375/2006 e a 380/2006** (art. 36) | Federal (CONAMA) | 19/08/2020, DOU 21/08/2020 | Sim | https://conama.mma.gov.br/?option=com_sisconama&task=arquivo.download&id=797 | Alta |
| N21 | **Decreto 96.044/1988** (regulamento do transporte rodoviário de produtos perigosos) | Federal | 18/05/1988 | Sem revogação localizada; a competência regulatória hoje é da ANTT (Lei 10.233/2001, art. 22 VII) | https://planalto.gov.br/ccivil_03/decreto/Antigos/D96044.htm (URL dos resultados de busca; texto não lido) | Média |
| N22 | **Resolução ANTT 5.998/2022** (regulamento e instruções complementares do transporte rodoviário de produtos perigosos). Alterada pelas **6.016/2023** (15/05/2023) e **6.056/2024** (29/11/2024) | Agência (ANTT) | 03/11/2022 (publicação 04/11/2022); vigor **01/06/2023**; página da ANTT atualizada em 09/07/2026 sem alteração posterior listada | Sim | https://www.gov.br/antt/pt-br/assuntos/cargas/produtos-perigosos | Alta |
| N23 | Resoluções ANTT **5.947/2021** e **5.232/2016** | Agência (ANTT) | A 5.232 (14/12/2016) foi revogada, com a 5.848/2019, pela 5.947 a partir de 01/07/2021; a 5.947 foi revogada pela 5.998 em 01/06/2023 | **Revogadas** | https://anttlegis.antt.gov.br/action/ActionDatalegis.php?acao=detalharAto&tipo=RES&numeroAto=00005947&seqAto=000&valorAno=2021&orgao=DG%2FANTT%2FMI&codTipo=&desItem=&desItemFim=&cod_menu=5408&cod_modulo=161&pesquisa=true | Alta (cadeia confirmada na página da ANTT; a cláusula de revogação da 5.232 veio de resumo de busca: média) |
| N24 | **ABNT NBR 10004** (classificação de resíduos): 10004:2004 substituída por **NBR 10004-1:2024** e **10004-2:2024** (publicadas em 27/11/2024). Norma privada, referenciada por atos legais (ex.: IN IBAMA 24/2024) | Técnica | Em SP, a CETESB manteve a edição 2004 até 31/12/2026 e adota a 2024 a partir de **01/01/2027** (DD 078/2025/I/C). Fontes secundárias dizem que a série 2024 tem só duas classes (Classe 1 perigosos e Classe 2 não perigosos); outra fonte comercial diz o contrário: **NÃO CONFIRMADO** | Transição | https://normas.com.br/visualizar/abnt-nbr-nm/13386/abnt-nbr10004-1-residuos-solidos-classificacao-parte-1-requisitos-de-classificacao ; DD CETESB (HTTP 403 ao baixar; só o trecho da busca): https://cetesb.sp.gov.br/wp-content/uploads/2025/10/DD%20078.2025.I.C%20-%20Per%C3%ADodo%20de%20transi%C3%A7%C3%A3o%20para%20implanta%C3%A7%C3%A3o%20da%20Norma%20NBR%2010.004%20-%20Res%C3%ADduos%20S%C3%B3lidos.pdf | Baixa a média |
| N25 | **ANA, Norma de Referência 1/2021** (Resolução ANA 79, 14/06/2021): cobrança do manejo de RSU | Agência (ANA) | Aplica-se a contratos desde 01/01/2022; adequação de normas locais até 31/12/2022; instrumento de cobrança informado à ANA até 31/12/2021 | Sim | https://participacao-social.ana.gov.br/api/files/Resolucao_ANA_79-2021_-_Aprova_Norma_de_Refereencia_N_1__-_cobranca_RSU-1623872066281.pdf | Alta |
| N26 | **ANA, Norma de Referência 7/2024** (Resolução ANA 187, 19/03/2024): condições gerais de limpeza urbana e manejo de RSU (plano operacional e relatório de atendimento) | Agência (ANA) | Vigor 01/04/2024; contratos a partir de 01/04/2025; prazos de adequação escalonados (resumo de busca: até 01/04/2025 capitais e RMs; 31/12/2025 acima de 100 mil hab.; 31/12/2026 de 50 a 100 mil; 31/12/2027 abaixo de 50 mil) | Sim | https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2024/187 | Alta (norma); média (prazos por porte, conferir no texto) |
| N27 | **ANA, Norma de Referência 14/2025** (Resolução ANA 276, 18/12/2025): indicadores de limpeza urbana e manejo de RSU | Agência (ANA) | Observância a partir de **maio de 2029** (notícia da ANA). Uma NR sobre modelos de regulação tarifária de RSU está planejada para o 2º semestre de 2027 | Sim | https://www.gov.br/ana/pt-br/assuntos/noticias-e-eventos/noticias/diretoria-da-ana-aprova-normas-de-referencia-para-indicadores-de-residuos-solidos-e-reducao-de-perdas-de-agua | Média a alta (li a notícia, não o texto da resolução) |
| N28 | **Decreto 11.599/2023** (prestação regionalizada de saneamento, inclui RSU) | Federal | 12/07/2023 | Sim | https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2023/decreto/D11599.htm | Média (li o cabeçalho e trechos) |
| N29 | **Proposta de Resolução CONAMA** sobre critérios procedimentais para fiscalização e gerenciamento integrado de resíduos sólidos | Federal (CONAMA) | Consulta pública 21/05 a 06/07/2026 (aviso no DOU de 20/05/2026); minuta não lida | **Em elaboração**. Monitorar: pode mexer em rastreabilidade | https://brasilparticipativo.presidencia.gov.br/processes/rcgerenciamentors | Média |

Não aplicáveis ao produto, só para registro: Lei 15.088/2025 (proibição de importação) e Decreto 12.451/2025 (exceções), Lei 14.260/2021 (incentivo à reciclagem), Decreto 12.082/2024 (Estratégia Nacional de Economia Circular), Resolução CONAMA 516/2026 (RoHS).

### Estaduais e municipais (resumo; detalhe em (d))

| ID | Norma / sistema | Esfera | Data | Vigente? | Fonte (URL) | Confiança |
|---|---|---|---|---|---|---|
| E01 | **SP — SIGOR MTR (CETESB)**: instituído pela **Resolução SIMA 27/2021**; DD CETESB 024/2022/P (verificação das informações); DD 130/2022/P (termo de referência de PGRS); DD 078/2025/I/C (transição NBR 10004). Manual do usuário v1 de 30/03/2026 | Estadual | 2021 a 2026 | Sim | https://mtr.cetesb.sp.gov.br/assets/pdf/Manual_MTR_CETESB_20260330.pdf ; https://semil.sp.gov.br/legislacao/2022/07/resolucao-sima-027-21/ | Alta (manual lido); média (as DDs, citadas pelo manual) |
| E02 | **MG — MTR-MG (FEAM/SEMAD)**: DN COPAM 232/2019. Manual de WS (SEMAD, arquivo de 14/02/2025) | Estadual | 27/02/2019 | Sim | https://meioambiente.mg.gov.br/documents/d/semad/manual-webservice-sistema-mtr_14022025-pdf ; texto da DN: https://blog.iusnatura.com.br/wp-content/uploads/2019/03/COPAM232_19.doc | Alta (manual WS); média (DN, via resumo e cópia) |
| E03 | **RJ — Sistema MTR online (INEA)**: Resolução CONEMA 79/2018 aprova a NOP-INEA-35 | Estadual | 07/03/2018 | Sim (revisões posteriores **NÃO CONFIRMADAS**) | https://www.inea.rj.gov.br/wp-content/uploads/2023/02/Res_CONEMA_79_NOP-INEA-35-MTR_07março2018.pdf (resumo de busca; texto não lido) | Média |
| E04 | **RS — MTR Online (FEPAM)**: **Portaria FEPAM 576/2026** (revoga as Portarias 87/2018, 12/2020 e 432/2024) | Estadual | 08/01/2026 | Sim | https://www.legisweb.com.br/legislacao/?id=489282 (texto lido, espelho); manuais de WS: https://fepam.rs.gov.br/manuais-de-integracao-para-o-desenvolvimento-de-webservices | Média a alta |
| E05 | **SC — Sistema MTR do IMA**: **Portaria IMA 009/2026** (14/01/2026; revoga as Portarias FATMA 242/2014, 162/2015, 272/2015, 194/2016, 206/2016 e IMA 21/2019, 232/2021), com base na Lei estadual 15.251/2010 | Estadual | 14/01/2026 | Sim | https://mtr.ima.sc.gov.br/documentos/Portaria_IMA_009_2026_MTR.pdf | Alta |
| E06 | **ES — MTR-ES (IEMA)**: WS publicado (manual de maio/2025), mesma família de rotas de MG. **Base legal NÃO CONFIRMADA** | Estadual | 2025 | Sistema existe | https://iema.es.gov.br/Media/iema/MTR-ES/Manual%20webservice%20IEMA%20maio25.pdf | Alta (existência do WS); baixa (base legal) |
| E07 | **Município de São Paulo — CTR-E (SP Regula)**. O manual da CETESB diz que geradores do município obrigados a registrar no CTR-E usam só ele, salvo exigência expressa da CETESB | Municipal | — | Sim (segundo o manual da CETESB) | Manual da CETESB, seção 2.5 (acima) | Média |

PR, BA, PE, MS, GO e demais UFs: **NÃO CONFIRMADO** se usam sistema próprio ou o MTR Nacional. Guias comerciais divergem (por exemplo, sobre o Paraná). Não usar esses guias como base.

---

## Regras operacionais do MTR Nacional que o SIGSAN precisa respeitar (M-SINIR v1.10 e Portaria 280)

Estas regras estão no manual operacional do MMA e na Portaria 280. As do manual podem mudar sem portaria. Devem virar **parâmetros**, não constantes.

| Regra | Valor atual | Fonte |
|---|---|---|
| Quem emite o MTR | Só o **gerador** (perfil Gerador). Transportador confirma dados e mantém uma via; destinador faz o aceite e a baixa; armazenador temporário recebe e emite MTR Complementar | N06 arts. 7º, 12, 13, 14 |
| Validade do MTR regular | **90 dias** da emissão; depois, cancelamento automático. MTR provisório não expira antes do uso; depois de recebido, vale 90 dias (comunicado do MMA de 26/12/2024, resumo de busca) | M-SINIR 3.6 e 3.12 |
| Prazo do destinador para a baixa | **10 dias** do recebimento (sujeito a sanções ambientais); correção de quantidade, tecnologia ou tipologia é permitida | N06 art. 14 e §§1º-2º |
| Alteração após recebimento | Uma só vez; exige validação do gerador por notificação; CDF já emitido é atualizado | M-SINIR 3.9 |
| Itens do MTR | Vários resíduos por MTR se for direto ao destinador (mesmo veículo e destinador); com armazenador temporário, **1 resíduo por MTR**. Por resíduo: código IBAMA de 6 dígitos (com `*` quando houver), quantidade, unidade (t, kg, litro, m³ ou unidade), estado físico, classe (I, IIA, IIB; RSS grupos A-E; RCC classes A-D), acondicionamento, tecnologia de tratamento, densidade se a unidade for volume | M-SINIR 3.2 a 3.4; N06 art. 10 |
| Limites | 45 t, 45.000 kg, 45 m³ ou 45.000 L por resíduo e por MTR; mínimo 0,1000 kg e passo 0,1000 kg; RSS só em kg ou litro; lâmpadas (código 200121) sem limite de unidades | M-SINIR 3.3 e 3.4 |
| Perigosos | Obrigatórios número ONU, classe de risco, nome de embarque e grupo de embalagem; declaração conforme a ANTT (o manual ainda cita a 5.947/2021: ver N23) | M-SINIR 3.3 |
| Indisponibilidade do sistema | MTR Provisório (numeração `P####`), 2 vias, regularização obrigatória antes de emitir novos | N06 art. 11; M-SINIR 3.12 |
| CDF | Emitido **só pelo destinador**, pelo sistema, para um **período** e um ou mais geradores/MTRs; responsável técnico com registro profissional e assinatura (imagem de até 100 kb no sistema); existe CDF para movimentos sem MTR (ex.: RSU) e para resíduos de acidente. O relatório de recebimento e o MTR **não substituem** o CDF | N06 art. 14 §§3º-7º; M-SINIR 5 |
| DMR | **Trimestral**, gerador, transportador e destinador (não armazenador). Prazo: até o último dia do mês seguinte ao trimestre (30/04, 31/07, 31/10, 31/01). DMR pendente bloqueia nova DMR e pode virar notificação. Movimentos sem MTR (ex.: RSU) entram por digitação | M-SINIR 4 |
| Inventário Nacional | Reporte anual **até 31/03** de informações complementares do ano anterior | N06 art. 20 |
| PGRS | Elaborado no módulo PGRS do SINIR; revisão no mínimo a cada 4 anos ou no fim da licença (o manual cita o Decreto 7.404, já revogado) | M-SINIR 6 |
| Cadastro | Licenças ambientais e dados das unidades devem estar válidos; o MMA avisa que cadastro desatualizado causa **bloqueio** | Comunicado MTR DGR/MMA 002/2025; N06 art. 6º |
| Acesso | Desde **01/08/2026**, só Login Único Gov.br (CPF vinculado a unidade cadastrada). Perfil "Usuário API" existe no fluxo de logística reversa | https://sinir.gov.br/sistemas/mtr/ ; Guia Rápido Gov.br v1.0 (05/01/2026) |
| Web Service | Termo de Uso: "é permitido utilizar Web Service", com autenticação e autorização do perfil; APIs trocam as mesmas informações do sistema. **Manual do WS federal NÃO ENCONTRADO** | TU-MTR |
| Incidente de 06 a 19/12/2024 | Perda de dados de 06 a 10/12/2024 (restauração do backup de 05/12/2024) e duplicidade de números de MTR Provisório | Comunicado MTR DGR/MMA 001/2025 |

---

## Lista Brasileira de Resíduos Sólidos (IN IBAMA 13/2012): como carregar o catálogo

**Onde obter, em formato tratável:** o IBAMA hospeda a lista em `.xls` (225 KB) e `.doc` (359 KB) na página da PNRS: https://www.gov.br/ibama/pt-br/assuntos/emissoes-e-residuos/residuos/politica-nacional-de-residuos-solidos-pnrs . O arquivo XLS que baixei hoje (230.400 bytes) traz o aviso "Este texto não substitui o publicado no D.O.U., Seção 1, nº 245, de 20/12/2012". Um endereço antigo (`api.ibama.gov.br/phocadownload/...`) **não resolve mais**. O PDF do DOU no site do IBAMA devolveu HTTP 403 ao `curl`. Qualquer cópia de terceiros (universidades, órgãos estaduais) serve só de conferência.

**Estrutura confirmada no XLS oficial** (planilha `Listagem`, 1.025 linhas, cabeçalho na linha 6):

| Coluna | Conteúdo |
|---|---|
| A, Código | Capítulo (célula **numérica**: `1.0` a `20.0`), subcapítulo `AA BB` (111), resíduo `AA BB CC` (**878 códigos distintos**, 6 dígitos com espaços) |
| B, Descrição | Texto; prefixo `(*)` quando perigoso (449 códigos), sufixo ` –` |
| C, Classificação | `Perigoso` (448 códigos) ou vazio |
| D, Unidade | `Kg` (a grande maioria), `L` (88), `Un.` (1: código 20 01 21, lâmpadas) |

**Regras da IN (anexo I):** o asterisco marca resíduo perigoso por origem ou por característica (inflamabilidade, corrosividade, reatividade, toxicidade, patogenicidade, carcinogenicidade, teratogenicidade, mutagenicidade). Os 20 capítulos seguem a lista europeia. A identificação procura primeiro a fonte geradora nos capítulos 01 a 12 e 17 a 20 (códigos terminados em 99 só por último), depois 13, 14, 15 e 16. **A lista não substitui a classificação pela NBR 10004** (nota 4 do anexo).

**Armadilhas para o ETL (verificadas):**
1. O capítulo vem como número (`1.0`); formatar com 2 dígitos.
2. Linha 250: `07 02 15` veio com **código e descrição fundidos na coluna A**. Linha 347: o subcapítulo `08 04` também.
3. Linha 211: `06 10 02` tem `(*)` na descrição, mas a coluna Classificação está vazia. A regra deve ser o `(*)` (é o que a IN define) e conferir com o DOU.
4. Há um `kg` em minúsculo entre os `Kg`.
5. Para o MTR, os sistemas oficiais usam o código **sem espaços** (`010101`, `190902`) e identificam classe, tecnologia, unidade, estado físico e acondicionamento por **códigos inteiros de catálogos próprios** (rotas `retornaListaClasse/Unidade/Tecnologia/EstadoFisico/Acondicionamento` nos manuais de MG e ES). **Não usar IDs de sistemas estaduais como chave** do SIGSAN.
6. O SINIR acrescenta ao fim da lista uma "listagem complementar" de RSS (RDC 222/2018) e de RCC (CONAMA 307/2002), segundo o manual. Em que formato obter: **NÃO CONFIRMADO**.

**Contagens de teste do ETL** (devem bater antes de liberar): 20 capítulos, 111 subcapítulos, 878 códigos, 449 com `(*)`, 448 com `Perigoso`, 1 divergência (`06 10 02`).

**Esboço de modelo (não aplicado; sujeito a revisão do responsável técnico, como manda o Diário):**

```sql
-- catálogo nacional, único para todos os tenants (schema public, somente leitura para sigsan_app)
CREATE TABLE public.waste_list_item (
  code6              CHAR(6) PRIMARY KEY,         -- '190902'
  chapter            CHAR(2) NOT NULL,
  subchapter         CHAR(4) NOT NULL,
  description        TEXT    NOT NULL,
  hazardous_by_list  BOOLEAN NOT NULL,            -- vem do '(*)' da IN 13/2012
  default_unit       VARCHAR(4),                  -- Kg / L / Un.
  source_ref         TEXT    NOT NULL,            -- 'IN IBAMA 13/2012, DOU 20/12/2012'
  source_sha256      CHAR(64) NOT NULL
);
-- por tenant: perfil de resíduo do gerador = código da lista + classificação técnica + laudo
--   (nbr_edition: '2004' | '2024', nbr_class, rss_group, rcc_class, laudo_ref, valid_until)
```

---

## (b) Requisitos acionáveis para um software

Prioridade: **P0** = obrigatório e sem o qual o módulo não opera de forma legítima; **P1** = obrigatório ou de alto risco, pode vir logo depois; **P2** = relevante, aplicável conforme o perfil do tenant. "Situação" foi conferida no código e na migration em 12/10/2026.

### A. Emissão e ciclo do MTR

| ID | Requisito | Origem | Módulo SIGSAN | Situação atual | Lacuna | Recomendação | Prio. |
|---|---|---|---|---|---|---|---|
| RES-01 | O MTR é emitido pelo sistema oficial (SINIR ou sistema estadual compatível) e leva o número do sistema. Documento gerado fora dele não vale | N06 arts. 1º §1º, 3º VIII, 7º; E01 manual 2.6 | `MtrService.createMtr`, `SinirIntegrationService` | **NÃO ATENDE** | Número local por `countByTenantId+1` e dígito aleatório (D-MTR-4); `syncMtr` só devolve `PENDING`; o retorno é ignorado (D-MTR-5) | Tratar o MTR do SIGSAN como **rascunho** que vira "emitido" só com número oficial, protocolo e PDF oficial. Número local vira `internal_ref`. Persistir sempre o resultado de cada tentativa (status, motivo, data) | P0 |
| RES-02 | Integração com o sistema oficial | TU-MTR (WS permitido); sites do SINIR; manuais WS de MG, ES, SC, SP | `SinirIntegrationService`, `GovBrSignatureService` | **NÃO ATENDE** | WS federal sem documentação pública localizada; Gov.br exclusivo para usuário humano desde 01/08/2026; a premissa "OAuth2 Gov.br + ICP-Brasil" (Diário, decisão 5) não está em nenhum documento oficial lido | Porta `MtrProvider` com 3 adaptadores: (1) **assistido** (SIGSAN gera o pacote com todos os campos, o usuário emite no sistema e registra o número oficial); (2) **WS estadual** (SP, MG, ES, RS, SC, onde há manual); (3) **WS federal**, só depois de obter o manual do MMA (pedir por `mtr.sinir@mma.gov.br` e pelo DGR) | P0 |
| RES-03 | Sistema por jurisdição: usar o sistema do estado para fluxos internos; MTR duplo no interestadual (SP e SC dizem isso expressamente) | E01 manual 2.5; E05 art. 6º; E04 arts. 3º e 16 | (inexistente) | **NÃO ATENDE** | Só existe o campo "SINIR" | Regra por (UF de origem, UF de destino, tipo de resíduo) definindo qual(is) sistema(s) emitem. Ver (d) | P0 |
| RES-04 | Atores: gerador, transportador, armazenador temporário, destinador; cadastro e dados atualizados; perfil múltiplo; unidades por CNPJ | N06 arts. 3º, 6º; M-SINIR 2.1 | entidades `Waste*` | **PARCIAL** | Falta armazenador temporário; falta código da unidade no sistema oficial (`pessoaCodigo` nos WS); falta perfil múltiplo | Entidade `waste_party` única com papéis N:N e `unit_codes` por provedor | P1 |
| RES-05 | Cada ator opera só o seu papel: gerador emite; transportador confirma; destinador recebe e baixa | N06 arts. 7º, 12, 14 | `collectMtr`, `receiveMtr`, `destinateMtr` | **PARCIAL** | O mesmo tenant "coleta", "recebe" e "destina" o próprio MTR | Importar do sistema oficial os eventos dos outros atores; permitir ao tenant só as ações do seu papel (ex.: aterro/ETE recebendo lodo de fossa é destinador) | P1 |
| RES-06 | Itens do MTR: N resíduos; unidade (t, kg, L, m³, un.); estado físico; classe; acondicionamento; tecnologia; densidade quando volume | M-SINIR 3.3, 3.4; N06 art. 10 §§1º-2º | `WasteMtr`, `WasteClass` | **NÃO ATENDE** | 1 `waste_class_id` por MTR, só `quantity_kg`, sem estado físico, acondicionamento, tecnologia, densidade | Tabela `waste_mtr_item` com os campos do payload dos WS (ver exemplo no manual de MG: `residuo`, `quantidade`, `codigoUnidade`, `codigoClasse`, `codigoTecnologia`, `codigoTipoEstado`, `codigoAcondicionamento`, `tipoDensidade*`, `classeDeRisco`, `numeroONU`) | P0 |
| RES-07 | Limites por MTR e item: 45 t / 45.000 kg / 45 m³ / 45.000 L; mínimo 0,1 kg com passo 0,1 kg; RSS só kg/L; armazenador exige 1 resíduo por MTR | M-SINIR 3.2 a 3.4 | validação | **NÃO ATENDE** | `DECIMAL(12,3)` sem regra | Validadores parametrizáveis (podem mudar sem portaria) | P1 |
| RES-08 | Perigosos: número ONU, classe de risco, nome de embarque, grupo de embalagem; declaração do expedidor conforme a ANTT | M-SINIR 3.3; N22 | `WasteClass.unNumber`, `packagingGroup` | **PARCIAL** | Faltam classe de risco e nome de embarque; sem a declaração. O item exato da declaração na 5.998 **NÃO CONFIRMADO** (o manual cita o item 5.4.1.7.1 da 5.947, revogada) | Campos obrigatórios quando perigoso; conferir o item na 5.998/2022 e na 6.056/2024 | P1 |
| RES-09 | Transporte: placa de cada unidade (bitrem), motorista, data de expedição; veículo e condutor regulares | N06 arts. 7º §1º, 12 §§3º-4º; N22 art. 11 (certificação de veículos a granel) | `vehicle_plate`, `driver_cpf`, `waste_transporter.vehicles/drivers` (JSONB) | **PARCIAL** | Uma placa só; CNH, MOPP e CIV/CIPP sem validação; CPF do motorista é dado pessoal (LGPD) | Tabelas `vehicle` e `driver` com validade; só o necessário no MTR; base legal e retenção definidas com o módulo LGPD | P1 |
| RES-10 | Validade do MTR (90 dias; RS 60) com cancelamento automático | M-SINIR 3.6; E04 art. 13 | `MtrStatus.EXPIRED` | **PARCIAL** | Estado sem transição nem job | Job diário parametrizado por jurisdição; alerta antes de vencer | P1 |
| RES-11 | Recebimento: baixa em até 10 dias; conferir quantidade, tecnologia, motorista, placa; justificar divergência; uma alteração com validação do gerador | N06 art. 14 §§1º-2º; M-SINIR 3.9 | `receiveMtr` | **NÃO ATENDE** | Só grava data e assinatura; sem quantidade recebida, divergência, responsável pelo recebimento, validação | Campos e fluxo de divergência/aceite; fila de prazo. **P0 se o tenant for destinador** (ETE que recebe lodo de fossa, aterro municipal) | P1 |
| RES-12 | Máquina de estados correta e testada, com cancelamento, rejeição e expiração | N06; manuais | `MtrService.validateTransition` | **NÃO ATENDE (defeito)** | D-MTR-1 | Reescrever o ciclo a partir dos estados do sistema oficial. Os nomes oficiais dos estados **NÃO CONFIRMADOS** (o manual cita "Armaz Temporário"); começar por testes de transição | P0 |
| RES-13 | MTR Provisório e regularização (sistema fora do ar) | N06 arts. 3º X, 11; M-SINIR 3.12 | — | **NÃO ATENDE** | Inexistente | Modelo `provisional_mtr` (série `P####`), regularização obrigatória, bloqueio de nova emissão enquanto houver pendência | P1 |
| RES-14 | MTR Complementar (armazenador temporário consolida cargas) | N06 arts. 3º IX, 13 | `is_complementary`, `parent_mtr_id` | **PARCIAL** | Um complementar agrega **vários** MTRs; o modelo tem 1 pai | Tabela de vínculo N:1. Só se o tenant atuar como armazenador | P2 |
| RES-15 | Cancelamento de MTR | WS (MG/ES: rota 15) | — | **NÃO ATENDE** | Inexistente | Ação auditada com motivo | P1 |
| RES-16 | MTR de importação e de exportação | N06 arts. 8º, 9º | — | **N/A** | O tenant não importa nem exporta resíduos controlados (e a importação passou a ser proibida: Lei 15.088/2025) | Nenhuma | — |

### B. CDF, DMR, Inventário e PGRS

| ID | Requisito | Origem | Módulo SIGSAN | Situação atual | Lacuna | Recomendação | Prio. |
|---|---|---|---|---|---|---|---|
| RES-17 | O CDF só é válido se emitido pelo **destinador** no sistema oficial; cobre período e vários MTRs; assinado pelo responsável técnico; MTR e relatório de recebimento não substituem o CDF | N06 art. 14 §§3º-7º; M-SINIR 5 | `WasteCdf`, `MtrPdfGenerator.generateCdfPdf`, `destinateMtr` | **NÃO ATENDE** | CDF 1:1 com MTR (`mtr_id UNIQUE`); PDF gerado localmente com a frase "Lei 12.305/2010", o que dá aparência de documento legal; hash próprio | (1) Para geradores: **importar/registrar** o CDF emitido pelo destinador (número, período, MTRs cobertos, PDF). (2) Para tenant destinador: emitir pelo provedor oficial. (3) Renomear o PDF local para "comprovante interno, não substitui o CDF" | P0 |
| RES-18 | CDF sem MTR (RSU e isentos) e para resíduos de acidente | M-SINIR 5.2, 5.3 | — | **NÃO ATENDE** | Inexistente | Tipo `cdf_without_mtr` | P2 |
| RES-19 | DMR trimestral (SINIR, SP, RS, SC); semestral em MG (baixa confiança); DMRSU mensal em SC para prefeituras e aterros | M-SINIR 4; E04 art. 15; E05 arts. 7º, 8º | — | **NÃO ATENDE** | Sem entidade nem calendário | Consolidar MTRs recebidos mais movimentos sem MTR; calendário por jurisdição; alerta de DMR pendente. **Próximo vencimento: 31/10/2026 (3º tri)** | P1 |
| RES-20 | Inventário Nacional de Resíduos Sólidos: reporte anual até 31/03 | N06 arts. 16 a 20 | — | **NÃO ATENDE** | Inexistente | Relatório anual pré-preenchido pelo consolidado do ano | P1 |
| RES-21 | PGRS: conteúdo mínimo (art. 21), responsável técnico, revisão, envio pelo módulo do SINIR, juntada ao licenciamento; PGRS de perigosos | N01 arts. 20, 21, 38, 39; N04 arts. 58, 69; M-SINIR 6; E01 DD 130/2022/P | `grs/sigsan-pgrs` (diretório vazio) | **NÃO ATENDE** | Não existe | Módulo PGRS por estabelecimento (ETA, ETE, lab, oficina, frota), com diagnóstico alimentado pelos dados reais de geração | P1 |
| RES-22 | Informação anual do PGRS aos órgãos | N04 art. 58 | — | **NÃO ATENDE** | Inexistente | Relatório anual (pode vir do módulo PGRS) | P2 |
| RES-23 | RAPP/TCFA até 31/03 para quem está no CTF/APP com atividade do Anexo I | N15; N13 | — | **NÃO ATENDE** | Inexistente | Lembrete e consolidação dos dados de resíduos do ano. O preenchimento no portal do IBAMA continua manual | P2 |

### C. Cadastros e conformidade de terceiros

| ID | Requisito | Origem | Módulo SIGSAN | Situação atual | Lacuna | Recomendação | Prio. |
|---|---|---|---|---|---|---|---|
| RES-24 | Conferir se transportador e destinador estão regularizados antes de enviar (responsabilidade do gerador) | N06 art. 10 §3º; M-SINIR 3 (nota de responsabilidades) | `createMtr` | **PARCIAL** | Só confere se o destinador aceita a classe; não confere licença nem CTF | Bloquear emissão quando licença, CTF/APP ou RNTRC estiverem vencidos ou ausentes; log de quem liberou exceção | P1 |
| RES-25 | Licenças ambientais com validade (o MMA bloqueia unidades com cadastro desatualizado) | Comunicado MTR DGR/MMA 002/2025; N06 art. 6º | JSONB `environmental_license`, `operational_license`, `antt_license` | **PARCIAL** | Sem estrutura nem alerta de validade | Tabela `party_license` (tipo, número, órgão, validade, arquivo) e alertas em 90/60/30 dias | P1 |
| RES-26 | CTF/APP ativo e Certificado de Regularidade (validade de 3 meses, art. 46 §2º da IN 13/2021) | N13 | — | **NÃO ATENDE** | Sem campos | `ctf_app_number`, `regularidade_valid_until`, conferência manual no início | P1 |
| RES-27 | Responsável técnico habilitado (CTF/AIDA), com registro no conselho e ART | N01 art. 38 §2º; N06 art. 14 §5º; N14 | `responsible_technician` (JSONB), `technician_crea` | **PARCIAL** | Texto livre; sem validade de ART | Entidade `technical_responsible` com conselho, registro, ART e validade | P1 |
| RES-28 | Cadastro Nacional de Operadores de Resíduos Perigosos: PJ que opera resíduos perigosos | N01 art. 38; N04 arts. 74 a 76; N12 | — | **NÃO ATENDE** | Inexistente | Marcador no estabelecimento (cloro, reagentes, resíduos de laboratório e de oficina) | P1 |
| RES-29 | Dados de CADRI (SP) e outros documentos estaduais | E01 manual 2.6 | `cadri_number`, `cadri_validity` no gerador | **PARCIAL** | O campo está no gerador; no SIGOR o CADRI entra no MTR | Mover para configuração por jurisdição | P2 |

### D. Catálogos e classificação

| ID | Requisito | Origem | Módulo SIGSAN | Situação atual | Lacuna | Recomendação | Prio. |
|---|---|---|---|---|---|---|---|
| RES-30 | Identificar o resíduo pela Lista Brasileira (código de 6 dígitos, asterisco para perigoso) | N06 art. 3º VI; N11 | `waste_class` (vazio) | **NÃO ATENDE** | Sem catálogo: nenhuma emissão é possível | ETL do XLS oficial (seção acima), catálogo global versionado, conferido por responsável técnico | P0 |
| RES-31 | Classificação técnica do resíduo (NBR 10004; grupos A-E de RSS; classes A-D de RCC) | M-SINIR 3.3, 3.4; N24; N19; N17 | `WasteClass.HazardClass`, CHECK `('I','II','III')` | **NÃO ATENDE (modelo errado)** | A NBR 10004:2004 tem I, **IIA**, IIB; "II" e "III" não existem. A lista IBAMA só dá o flag de perigoso; a classe vem da análise do gerador | Separar `hazardous_by_list` (catálogo) de `technical_class` (perfil do gerador, por tenant). Valores: edição 2004 (I, IIA, IIB), RSS (A-E), RCC (A-D) e a edição 2024 quando confirmada | P0 |
| RES-32 | Transição para a NBR 10004:2024 (em SP, 01/01/2027) | N24; E01 DD 078/2025/I/C | — | **NÃO ATENDE** | Sem versionamento | Campo `nbr_edition` e data de corte por jurisdição; revisão dos laudos antigos; confirmar o conteúdo da 2024 (ver (e)) | P1 |
| RES-33 | Catálogos oficiais de unidade, estado físico, acondicionamento e tecnologia de tratamento | M-SINIR; WS de MG e ES (rotas 2 a 7) | `DestinationMethod` (8 valores) e `destination_type` fixos | **NÃO ATENDE** | Enums próprios não correspondem às tabelas oficiais (os WS de MG, por exemplo, usam "Aterro Classe I" e "Aterro Classe IIA e IIB" como tecnologias) | Tabelas de catálogo por provedor, com mapeamento para o modelo interno; carregar pelas rotas `retornaLista*` quando houver credencial | P1 |
| RES-34 | Perfil de resíduo do gerador: código da lista + classificação + laudo/validade + origem | N24; N01 art. 21 II | — | **NÃO ATENDE** | Inexistente | Entidade `waste_profile` por estabelecimento | P1 |

### E. Resíduos próprios de saneamento

| ID | Requisito | Origem | Módulo SIGSAN | Situação atual | Lacuna | Recomendação | Prio. |
|---|---|---|---|---|---|---|---|
| RES-35 | **Lodo de ETA e de ETE**, resíduos de gradeamento e desarenação: sujeitos a PGRS e MTR; códigos da lista: 19 09 01/02/03/04/05/06 (ETA), 19 08 01/02/05 (ETE), 20 03 04 (lodo de fossa), 20 03 06 (limpeza de esgotos e bueiros) | N01 art. 13 I "e", art. 20 I; N11; N16 art. 2º I; E01 manual 2.3 | `quality.SamplingPoint SLUDGE` (só ponto de coleta) | **NÃO ATENDE** | Sem entidade de geração (massa, % sólidos, origem, destino); principal fluxo de resíduos do tenant | Registro de geração por unidade (ETA/ETE), ligado ao perfil de resíduo, ao MTR e à DMR. Não existe norma federal específica para lodo de ETA que eu tenha localizado (**NÃO CONFIRMADO**): aplicam-se PNRS, NBR 10004, licenciamento e normas estaduais | P1 |
| RES-36 | Biossólido: Unidade de Gerenciamento de Lodo licenciada, plano com ART, rastreabilidade informada anualmente (anexo da Res. 498/2020); vedações de uso em solo | N20 art. 6º par. único, anexo, art. 36 | — | **NÃO ATENDE** | Inexistente | Só se o tenant fizer ou fornecer biossólido. Rastreabilidade por lote | P2 |
| RES-37 | CTF/APP: ETA consta no Anexo I (código 21-33, pessoa jurídica); destinação de resíduos urbanos, 17-4; a IN 13/2021 art. 15 isenta o **titular** que delega o serviço e obriga a delegada | N13 Anexo I, art. 15 | — | **NÃO ATENDE** | Sem marcador por estabelecimento | Cadastro do estabelecimento com a atividade do Anexo I e a situação do CTF | P1 |
| RES-38 | Resíduos de laboratório de qualidade da água (reagentes e meios de cultura) como RSS: PGRSS, grupos A e B | N19 arts. 5º a 10; N18 | `LabService` (módulo `quality`) | **NÃO ATENDE** | Sem PGRSS nem ligação a resíduos | **Se a RDC 222 alcança laboratório de análise de água é pergunta aberta** (ver (e)); enquanto isso, tratar como gerador de perigosos (Lista IBAMA, capítulos 18 e 16) | P2 |
| RES-39 | RCC de reparos de rede (CONAMA 307 e alterações; classes A a D; classe D perigosa) | N17 | — | **NÃO ATENDE** | Inexistente | Perfil de resíduo com classe RCC; em SC, RCC (exceto classe D) é dispensado de MTR estadual | P2 |
| RES-40 | Serviço de limpa-fossa e destinação de lodo de fossa (MTR "Romaneio" no RS; dispensa para pessoa física em SC; obrigatório para fossas de indústrias e condomínios) | E04 arts. 7º e 8º; E05 art. 3º VII e §3º | — | **NÃO ATENDE** | Inexistente | Documento por rota/coleta quando o tenant prestar o serviço | P2 |

### F. Limpeza urbana e manejo de RSU

| ID | Requisito | Origem | Módulo SIGSAN | Situação atual | Lacuna | Recomendação | Prio. |
|---|---|---|---|---|---|---|---|
| RES-41 | Cobrança do manejo de RSU por taxa ou tarifa; art. 35 considera destinação adequada e nível de renda e **pode** considerar características do lote, consumo de água e frequência de coleta (peso/volume médio por habitante: o texto compilado do Planalto está ambíguo, **conferir**); cofaturamento na fatura de outro serviço, com anuência do prestador (§1º); titular que não propõe o instrumento em 12 meses de vigência da Lei 14.026 comete renúncia de receita (§2º) | N03 arts. 29 II, 35; N25 itens 5 e 6 | `commercial/*` não existe | **NÃO ATENDE** | Módulo comercial inexistente | No motor de tarifa: regime (taxa/tarifa), parâmetros por categoria, cofaturamento com ressarcimento ao prestador da fatura, **cobrança social** (CadÚnico recomendado), reajuste com intervalo mínimo de 12 meses | P1 |
| RES-42 | NR 7/2024: plano operacional do titular aprovado pela entidade reguladora e relatório de atendimento do prestador | N26 | — | **NÃO ATENDE** | Inexistente | Módulo de plano e relatório, parametrizado por porte de município | P2 |
| RES-43 | NR 14/2025: indicadores de gestão e de qualidade (a partir de maio/2029) | N27 | — | **NÃO ATENDE** | Fórmulas **NÃO CONFIRMADAS** (não li o texto) | Reservar espaço no modelo de dados; ler a Resolução ANA 276/2025 antes de implementar | P2 |
| RES-44 | Municípios e UFs informam ao SINIR anualmente (30/04). Prestadores preenchem o módulo de resíduos do SINISA (ciclo ano-base 2025 teve prazo 03/09/2026, fonte secundária) | N08 art. 4º; N04 art. 79; N03 art. 53 | — | **NÃO ATENDE** | Inexistente | Exportação de dados no formato dos formulários (não confirmei os layouts) | P2 |
| RES-45 | RSU não exige MTR: entra na DMR e no inventário; destinador pode emitir CDF sem MTR | N01 arts. 13 I "c" e 20; M-SINIR 4.2, 5.2; E05 art. 3º I | — | **N/A** (regra a respeitar na parametrização) | — | Marcar o tipo de resíduo como "dispensado de MTR" por jurisdição | — |
| RES-46 | Metas e planos: Planares (horizonte de 20 anos), plano municipal com revisão em no máx. 10 anos, prazos do art. 54 para disposição final | N01 arts. 19 XIX, 54; N05 | — | **N/A** (responsabilidade do titular) | — | Relatórios de apoio ao titular, se o tenant for autarquia municipal | P2 |

### G. Transversais

| ID | Requisito | Origem | Módulo SIGSAN | Situação atual | Lacuna | Recomendação | Prio. |
|---|---|---|---|---|---|---|---|
| RES-47 | Resiliência a falha do sistema oficial: fila, reenvio, cópia local do que foi emitido, conciliação | Comunicados MTR 001/2025 (perda de dados em dez/2024) e 002/2025 | `SinirSyncStatus.RETRYING` | **PARCIAL** | Só o enum | Outbox transacional, retentativa com backoff, reconciliação diária por número oficial | P1 |
| RES-48 | Trilha de auditoria e integridade (hash em cadeia) | boa prática; N06 art. 1º | `generateHashChain`, `auditLog` (vazio) | **PARCIAL** | D-MTR-3; `auditLog` é método vazio | Hash sobre JSON canônico (UTF-8), erro explícito, verificação periódica. O hash é diferencial do SIGSAN, **não** exigência legal | P2 |
| RES-49 | Persistência de campos JSON | — | `createMtr`, `destinateMtr` | **NÃO ATENDE (defeito)** | D-MTR-2 | Serializar com Jackson | P0 |
| RES-50 | Assinatura digital | N06 art. 1º §1º (MTR autodeclaratório); art. 14 §5º (CDF assinado pelo responsável técnico) | `GovBrSignatureService` | **N/A para o MTR; PARCIAL para o CDF** | No sistema oficial o RT assina por imagem anexada. Assinatura ICP-Brasil/Gov.br **não é requisito para emitir MTR**. Nunca chamado | Despriorizar o serviço de assinatura; só reavaliar se o MMA ou um estado exigir | P2 |
| RES-51 | Parametrização por tenant e por jurisdição | (d) | — | **NÃO ATENDE** | Tudo fixo | Tabela de regras versionadas, com vigência | P0 |
| RES-52 | Testes de aceite do módulo (transições, payload, prazos, catálogo) | Diário 11.1 | — | **NÃO ATENDE** | 0 testes | Antes de qualquer integração: testes do ciclo, do ETL e do cálculo de prazos | P0 |

---

## (c) Prazos e marcos

"Passou" significa vencido em relação a 12/10/2026.

### Já venceram

| Data | Marco | Fonte |
|---|---|---|
| 01/01/2021 | Início da obrigatoriedade do MTR Nacional | N06 art. 19 |
| 31/12/2021 | Entes informam à ANA o instrumento de cobrança de RSU (NR 1) | N25 item 7.5 |
| ≈ jul/2021 | 12 meses da Lei 14.026 para o titular propor o instrumento de cobrança (art. 35 §2º da Lei 11.445). Dia exato **NÃO CONFIRMADO** | N03 |
| 02/08/2021, 2022, 2023 e 02/08/2024 | Prazos escalonados do art. 54 da Lei 12.305 para disposição final de rejeitos (capitais e RMs, mais de 100 mil, 50 a 100 mil, menos de 50 mil habitantes), para quem cumpria as condições do caput | N01 art. 54 |
| 31/12/2022 | Adequação de normas locais de cobrança de RSU à NR 1 | N25 item 7.3 |
| ≈ 11/07/2022 | Sistemas de logística reversa integrados ao SINIR (180 dias do Decreto 10.936) | N04 art. 15 |
| 01/06/2023 | Vigor da Resolução ANTT 5.998/2022 | N22 |
| 06 a 19/12/2024 | Incidente no MTR Nacional (perda de dados de 06 a 10/12) | Comunicado MTR 001/2025 |
| 23/01 a 22/02/2025 | Consulta pública da revisão da Portaria 280 | N09 |
| 15/05 e 31/05/2025 | Prazos prorrogados só em 2025 (Inventário; informações dos entes) | N07 |
| 01/04/2025 | Primeiro prazo da NR 7 (ERIs, capitais, RMs) | N26 |
| 31/12/2025 | Prazo da NR 7 para municípios acima de 100 mil habitantes (resumo de busca) | N26 |
| 31/07/2026 | Último dia do login interno do MTR Nacional | SINIR |
| 01/08/2026 | Acesso ao MTR Nacional só por Gov.br | SINIR |
| 21/05 a 06/07/2026 | Consulta pública da Resolução CONAMA sobre fiscalização e gerenciamento de resíduos | N29 |
| 30/09/2026 | SC: DMR semestral do 1º semestre de 2026 devia ser reportada no 3º trimestre | E05 art. 7º §7º |
| 03/09/2026 | Fim do ciclo SINISA ano-base 2025 (fonte secundária) | TCE/SC (busca) |

### Próximos

| Data | Marco | Fonte |
|---|---|---|
| **31/10/2026** | DMR do 3º trimestre (SINIR, SP, RS; SC: primeiro trimestral) | M-SINIR 4; E05 art. 7º |
| 31/12/2026 | Fim do período em que a CETESB aceita a NBR 10004:2004 | E01 DD 078/2025 |
| 31/12/2026 | NR 7: municípios de 50 a 100 mil habitantes (conferir no texto) | N26 |
| **01/01/2027** | NBR 10004:2024 passa a valer em SP (e, segundo fontes secundárias, a norma entra em vigor em geral) | N24 |
| 31/01/2027 | DMR do 4º trimestre de 2026 | M-SINIR 4 |
| **31/03/2027** | Inventário Nacional (ano-base 2026); RAPP/TCFA | N06 art. 20; N15 |
| 30/04/2027 | Informação anual de UFs e municípios ao SINIR; DMR do 1º trimestre | N08 art. 4º |
| 30/07 de cada ano | Relatório anual de resultados no Sisrev-BR (só logística reversa) | N10 (Portaria 1.778/2026 art. 5º) |
| 31/12/2027 | NR 7: municípios abaixo de 50 mil habitantes | N26 |
| 2º semestre de 2027 | NR de modelos tarifários de RSU (planejada pela ANA) | ANA (resumo) |
| maio/2029 | Observância da NR 14/2025 (indicadores de RSU) | N27 |
| 2040 | Horizonte das metas do Planares | N05 |
| Sem data | Possível nova Portaria de MTR/PGRS/Inventário (revoga a 280); Resolução CONAMA decorrente da consulta de 2026; audiência da ANTT sobre produtos perigosos (só fonte secundária) | N09, N29 |

Planares: revisão a cada 4 anos implica revisão em torno de abril de 2026; **NÃO CONFIRMADO** se ocorreu.

---

## (d) Variação regulatória: o que muda por estado e agência

### Confirmado em fonte primária

| Item | Federal (SINIR) | SP (CETESB) | MG (FEAM/SEMAD) | RJ (INEA) | RS (FEPAM) | SC (IMA) | ES (IEMA) |
|---|---|---|---|---|---|---|---|
| Sistema | MTR Nacional | SIGOR MTR (Res. SIMA 27/2021) | MTR-MG (DN COPAM 232/2019) | Sistema MTR online (NOP-INEA-35) | MTR Online (Portaria 576/2026) | Sistema MTR IMA (Portaria 009/2026) | Sistema MTR-ES (IEMA) |
| Integração com o SINIR | — | Automática, "apenas consolidação pelo MMA" | Não confirmada | Informada como concluída (resumo de busca, 2021) | O sistema estadual é "o único válido" no RS; não dispensa reporte ao SINIR "quando aplicável" (art. 19) | Regras próprias por origem/destino (art. 6º) | Não confirmada |
| Validade do MTR | 90 dias | 90 dias | NÃO CONFIRMADO | NÃO CONFIRMADO | **60 dias** (art. 13) | NÃO CONFIRMADO | NÃO CONFIRMADO |
| DMR | Trimestral | Trimestral | Semestral (28/02 e 31/08, **baixa confiança**) | NÃO CONFIRMADO | Trimestral | Trimestral desde 07/2026; **DMRSU mensal** para prefeituras e aterros | NÃO CONFIRMADO |
| CDF | Pelo destinador, sem prazo na 280 | Pelo destinador | Pelo destinador | NÃO CONFIRMADO | Até **90 dias** do recebimento (art. 14 §2º) | Pelo destinador (art. 10) | NÃO CONFIRMADO |
| Interestadual | MTR no SINIR | **2 MTRs** (SIGOR + SINIR ou sistema do outro estado) | NÃO CONFIRMADO | NÃO CONFIRMADO | Art. 18: "demais requisitos legais" | Art. 6º: sistema de ambos os estados, ou IMA + SINIR | NÃO CONFIRMADO |
| Dispensas | RSU (sem PGRS); ME/EPP só com resíduo domiciliar até 200 L/dia (Decreto 10.936, art. 63) | Condomínios residenciais e equiparados | NÃO CONFIRMADO | NÃO CONFIRMADO | Pequeno gerador, **exceto** resíduo de esgotamento sanitário (art. 4º II) | RSU, RCC (exceto classe D), LR com documento próprio, fossa de pessoa física (art. 3º) | NÃO CONFIRMADO |
| Documento extra | — | CADRI e parecer técnico no MTR; **CTR-E** no município de SP | — | — | MTR Romaneio (limpa-fossa), MTR Provisório, MTR Complementar | PGRS pelo sistema do IMA | — |
| Web Service | Existe segundo o TU-MTR; manual NÃO ENCONTRADO | v1.15 (21/08/2024), 21 rotas (a URL do manual devolve 403; dado vem de resultado de busca) | Manual público, 22 rotas, token de 1 h, header `chave_feam` | Existe (resumo de busca) | Manuais públicos | Documentos públicos no site do IMA | Manual público (maio/2025) |
| Classificação | NBR 10004 e lista IBAMA | NBR 10004:2004 até 31/12/2026; 2024 depois | NÃO CONFIRMADO | NÃO CONFIRMADO | NÃO CONFIRMADO | NÃO CONFIRMADO | NÃO CONFIRMADO |

### Parâmetros que devem ser configuráveis (por tenant e por vigência)

1. **Jurisdição e sistemas:** UF da operação; para cada par (UF de origem, UF de destino, tipo de resíduo), a lista de sistemas obrigatórios (SINIR, estadual, municipal como o CTR-E).
2. **Prazos e validades:** validade do MTR (90 d; RS 60 d); prazo de baixa pelo destinador (10 d); prazo de emissão do CDF (RS 90 d); datas e periodicidade da DMR (trimestral, semestral, mensal para RSU) e da DMRSU; data do Inventário (31/03); data de informação dos entes (30/04).
3. **Limites e regras de item:** peso/volume máximo, mínimo e passo; unidades permitidas; regra de 1 resíduo por MTR com armazenador temporário; unidades permitidas para RSS.
4. **Dispensas de MTR:** lista de tipos de resíduo e origens dispensadas (RSU, RCC, LR com documento próprio, fossa de pessoa física, peças anatômicas etc.).
5. **Catálogos versionados:** Lista IBAMA (com versão e hash); classes, unidades, estados físicos, acondicionamentos e tecnologias **por provedor** (os códigos inteiros diferem entre sistemas); edição da NBR 10004 aplicável e a data de corte por UF.
6. **Documentos extras:** CADRI/parecer técnico (SP); ART; certificados de veículo (CIV/CIPP); MOPP do condutor; exigência de CTF/APP.
7. **Credenciais e ambiente por provedor:** URL de homologação e produção, código da unidade, usuário e segredo (cofre), vínculo Gov.br do usuário administrador, janelas de manutenção.
8. **Cobrança de RSU (quando houver):** taxa ou tarifa; parâmetros do art. 35 e ordem de aplicação; categorias; cofaturamento e valor de ressarcimento; tarifa social e fonte (CadÚnico); periodicidade e índice de reajuste (mínimo 12 meses); entidade reguladora (ANA fixa normas de referência; a regulação local é estadual, municipal ou consorciada); prazos da NR 7 por porte do município.
9. **Titularidade e regionalização:** município titular, bloco regional e prestador (Decreto 11.599/2023).

---

## (e) Perguntas em aberto para o jurídico e o regulatório

1. **Base legal da Portaria 280.** Ela regulamenta artigos do Decreto 7.404/2010, revogado pelo Decreto 10.936/2022, que só instituiu expressamente o MTR para a logística reversa (art. 15 §1º). A 280 segue plenamente eficaz? Há risco de questionamento do MTR geral?
2. **Revisão da Portaria 280.** Existe minuta final, prazo de publicação e regra de transição? Como acompanhar (a consulta recebeu 1.154 contribuições)?
3. **Web Service do MTR Nacional.** O MMA entrega o manual e credenciais a sistemas de terceiros? Em que condições? A mudança para Gov.br em 01/08/2026 alterou a autenticação do WS (login e senha, segundo o Termo de Uso)? Um SaaS pode operar em nome do cliente com delegação (procuração) ou cada tenant precisa de credencial própria?
4. **Termo de Uso do MTR Nacional.** O uso de WS por software de terceiros em nome de várias unidades é compatível com o item "login e senha pessoais e intransferíveis"? (O termo diz que o login só pode ser usado pelo usuário cadastrado.)
5. **Enquadramento do tenant.** Confirmar que lodo de ETA/ETE gerado pelo prestador é "resíduo dos serviços públicos de saneamento básico" (art. 13 I "e"), sujeito a PGRS e MTR em todas as UFs, inclusive quando o prestador atua por contrato, concessão ou consórcio. Quem é o gerador quando o titular é o município e o operador é uma concessionária?
6. **Lodo de ETA.** Existe norma federal específica de destinação? Não encontrei. Quais normas estaduais valem para cada cliente (por exemplo, normas de licenciamento de ETA)?
7. **Classificação.** Qual edição da NBR 10004 vale em cada UF e desde quando? As fontes secundárias discordam sobre o conteúdo da NBR 10004:2024 (duas classes ou manutenção de I, IIA, IIB). Quem emite o laudo e com que validade? O SINIR já aceita as novas classes?
8. **Laboratório de qualidade da água** como gerador de RSS: a RDC 222/2018 se aplica? Exige PGRSS e licença sanitária? Os resíduos devem ser tratados como RSS ou como resíduos perigosos da Lista IBAMA?
9. **CDF.** Para geradores, o SIGSAN pode guardar uma cópia do CDF emitido pelo destinador como prova? Por quanto tempo? O que a Portaria 280 (art. 14 §5º, "assinatura digital do profissional") exige além da imagem anexada no sistema?
10. **Retenção.** Prazo de guarda de MTR, CDF, DMR, PGRS e laudos (não encontrei prazo na PNRS nem no Decreto 10.936). Interação com a LGPD (CPF do motorista).
11. **Cobrança de RSU.** (a) A Súmula Vinculante 19 do STF e a jurisprudência sobre taxa de lixo continuam sendo o limite para taxas? **NÃO CONFIRMADO nesta pesquisa**. (b) O texto compilado do art. 35 da Lei 11.445 deixa dúvida sobre o inciso III (peso ou volume): conferir o texto sancionado da Lei 14.026. (c) Cofaturamento: quem responde pela inadimplência do serviço de RSU na fatura de água (corte de água por débito de RSU)?
12. **Regionalização e titularidade.** Em prestação regionalizada, quem emite os documentos (DMRSU, SINIR, SINISA) e quem é o "titular" para a NR 1 e a NR 7?
13. **Logística reversa.** O tenant tem alguma obrigação direta (frota: pneus, óleo lubrificante, baterias; lâmpadas; eletroeletrônicos)? A Portaria 1.778/2026 (Sisrev-BR) alcança o prestador ou só entidades gestoras e empresas?
14. **Estados fora do quadro.** PR, BA, PE, MS, GO, DF e demais: sistema próprio ou MTR Nacional? Os guias comerciais conflitam; confirmar com cada órgão antes de prometer cobertura.
15. **ANTT.** A declaração de resíduos perigosos citada pelo manual do SINIR (item 5.4.1.7.1 da 5.947/2021) existe com o mesmo conteúdo na 5.998/2022, após a 6.056/2024? Haverá nova revisão em 2026 (só fonte secundária)?
16. **Penalidades.** Valores de multa por ausência de MTR, atraso de DMR e CDF irregular (Decreto 6.514/2008, alterado pelo Decreto 10.936/2022, art. 62 XVI e XVII): não li os valores nem a gradação; necessário antes de definir alertas e bloqueios.

---

## Anexo: o que não consegui acessar ou confirmar

| Item | Situação |
|---|---|
| Manual do WS do MTR Nacional | Não encontrado publicamente. O Termo de Uso confirma que o WS existe |
| Nomes oficiais dos estados do MTR (além de "Armaz Temporário") | Não confirmados |
| Catálogo oficial de tecnologias de tratamento do SINIR | Não encontrado; só há amostras nos manuais de WS de MG e ES |
| Manual de WS da CETESB (v1.15) e DDs da CETESB | HTTP 403 ao baixar; usei o manual do usuário (acessível) e resultados de busca |
| PDF do DOU da IN IBAMA 13/2012 | HTTP 403; usei o XLS oficial do portal do IBAMA |
| Conteúdo da NBR 10004:2024 | Só fontes secundárias, que divergem |
| Texto das Resoluções ANA 187/2024 (prazos por porte) e 276/2025 | Li a página da ANA e resumos; não o texto integral |
| Planares (anexo com metas), Decreto 96.044 consolidado, RDC 222 no portal da ANVISA, DN COPAM 232/2019 em fonte oficial, NOP-INEA-35 | Não lidos em fonte primária |
| Súmula Vinculante 19 do STF | Não verificada |
| Valores de multa (Decreto 6.514/2008) | Não lidos |

Arquivos de apoio desta pesquisa estão no diretório temporário da sessão e não fazem parte do repositório.
