# RELATÓRIO CONSOLIDADO — Lote R9-B (Rodada 9 da auditoria contínua do ZiipSan)

- **Coordenador (sessão):** `2b0bfc0c-978a-4e39-892a-7e48b3dfec9e` — 2026-11-09T09:53Z UTC.
- **Fonte dos achados:** `~/workspace/goals/concluir-o-ziipsan/hidden_files/auditoria-continua/rodadas/2026-11-09-0543/00-CONSOLIDADO-RODADA9.md` (§2 menores; §6: lote R9-B = R9-C4-2 + R9-01 + R9-02 + C4-01).
- **Trabalho 100% local, ZERO chamadas ao GitHub** (parada dura por HTTP 429).
- **Lote:** `~/workspace/ziipsan-producao/correcoes-r9-b/` — RELATORIO-R9-B.md, LANDING.md, REVISAO-R9-B.md, REAPRECIACAO-R9-B.md, CONFIRMACAO-R9-B.md, PORTAO-REGISTRO-R9-B.md, portao-saida.txt, entrega/ (3 cópias corrigidas), trabalho/ (5 harnesses + 5 logs integrais).

## Veredito formal

**Lote R9-B APROVADO E CONFIRMADO, pronto para o pouso** (com a guarda de rebase/hash no acto, LANDING §2).

- Produção: 3/3 itens executados (R9-C4-2, R9-01, R9-02); C4-01 excluído por ser decisão do dono.
- Revisão: **APROVADO COM RESSALVAS** — 0 graves, 0 moderados, 1 menor (R9B-R-01).
- Correcção do R9B-R-01: aplicada, hash recomputado, re-prova executada.
- Reapreciação: **APROVADO COM RESSALVAS** — 0 menores a exigir correcção; 2 observações residuais (R9B-RP-01, R9B-RP-02), sem acção, sem bloqueio.
- Confirmação: **CONFIRMADO** — 0 divergências; posição fundamentada sobre RP-01/RP-02 (aceitáveis como registo) + 1 observação residual nova (R9B-RP-03).
- Portão mecânico: exit 1, só a Regra 3, **3 falsos positivos demonstrados** (PORTAO-REGISTRO-R9-B.md §1 — o parêntese `(sessão)` da convenção de cabeçalho não casa nos `PADROES_AUTOR`; instância (ii) do registo acumulado da Rodada 9). Regras 1, 2, 4, 5, 6 OK. Portão verde em substância; o portão NÃO foi editado.

## Cadeia (5 sessões distintas, verificada pelo confirmador)

| Estágio | Sessão | Hora (UTC) | Veredito |
|---|---|---|---|
| Produção | `f8e87956-1fd8-42f4-b223-143eb234d733` | 09:22Z | — |
| Revisão | `a074d132-349d-4b94-b1c1-9083cefdc719` | 09:31Z | APROVADO COM RESSALVAS |
| Correcção | `9c9dab72-c751-42f1-86e3-13205a7cebfc` | 09:38Z | R9B-R-01 corrigido |
| Reapreciação | `c9692297-46a4-4f04-82b7-7233b9d2f830` | 09:44Z | APROVADO COM RESSALVAS |
| Confirmação | `89bde696-2409-463f-b331-5c0ee06ae889` | 09:49Z | CONFIRMADO |

## Por item

**R9-C4-2 — CORRIGIDO.** Validação fail-fast `^[0-9]{7}$` na fronteira do `resolveFieldCharges` (`FieldChargeResolver.java`: javadoc `@throws` +6 linhas; guarda +10 linhas), com mensagem byte-idêntica à da C1 (`ChargeDefinition.java:130`, comparação literal). `null` continua a significar "sem município derivado". A opção (b) (registo formal de que a derivação garante o formato) foi rejeitada com fundamento: o valor chega como `String` arbitrária de um chamador externo à C4 (o montador de rotas P-C4-02 ainda não existe) e a Camada 1 não valida (`ChargeResolver.java:89-110`, só igualdade). +2 testes negativos novos (6/8 dígitos e letras; branco e espaços). Controlo negativo do revisor: os 2 testes novos falham exactamente os 2 contra a base — provam alguma coisa, não são tautologias. ED-19 byte-intocada (INV-C4-04 3/3).

**R9-01 — CORRIGIDO.** Javadoc do `MunicipalityReportSelection.java` corrigido na cópia entregue (veículo (b)): "bastam os bloqueadores 2 e 3, cada um só por si, com a guarda anti-duplicado do `registerExternal`"; item 1 como facto parcial (`final class` sem interface, sem subclasse) com a costura de extensão desenhada explicitada; itens 2 e 3 byte-idênticos. Formulação correcta: a da `ERRATA-MM-09.md` §2. A adenda à errata foi rejeitada porque o defeito vive no artefacto entregue — o código pousado continuaria a afirmar uma tese que a própria errata declara incorrecta. 0 resíduos do excesso confirmados pelo confirmador ("três bloqueadores"/"decorador": 0 ocorrências).

**R9-02 — MANTIDO como defesa documentada.** Inalcançabilidade PROVADA com o mecanismo concreto, re-verificada por revisor, reapreciador e confirmador: filtro `:167-170` (no ficheiro corrigido) só deixa passar linhas com o IBGE pedido; `closeReference` (:187-196) + `build` (:198-220) agrupam parcelas só pelos IBGEs das linhas recebidas; `BillLine`/`ConsumptionLine` são records imutáveis — logo `filtered.parcels()` ⊆ {IBGE pedido} e a guarda é insatisfazível, até por construção manual de entrada. 0 cobertura confirmada por busca. O comentário da guarda foi substituído por documentação da defesa em profundidade (0 mudança de comportamento). A remoção futura fica como pendência condicional P-R9B-03 (só com re-prova completa).

**C4-01 — EXCLUÍDO.** Não executado, não decidido: é decisão do dono (aceitar a prova proporcional da INV-C2-11 ou ordenar a execução sobre a base 12k), apresentada em cartão à parte na sessão principal. Viaja como pendência P-R9B-02.

## Hashes (SHA-256, estado final)

| Ficheiro | Base | Corrigido (final) |
|---|---|---|
| `FieldChargeResolver.java` | `29057da8c438c06fc4ed7e00387412c21de34425a708f1fb108e1439a107728a` | `faa28997c66dea3684b2f701d3e0605bd0ade4a1aa846ffca895ba0acda08fcc` |
| `FieldChargeResolverTest.java` | `e3d0d8691d479bb0ef25a39392fc371077690f13a52209b7a177b8b45599cecb` | `e14f0329f8789688a2388e04803bf0754eb2896dadebf8a4c22b1474fc42106f` |
| `MunicipalityReportSelection.java` | `69a0bba1b77d8e3da755f1e450f2f9abc84a27a674fc2e282bcbed8dedc9ef54` | `35a0e150baf767a358fbbba88e81b91064a2776c58b675122e638ecc1c4fdfc8` |

O hash do `MunicipalityReportSelection.java` mudou uma vez no ciclo: `118e4975…` (estado revisto) → `35a0e150…` (estado final, após a correcção do R9B-R-01, 1 linha, :177). Prova de unicidade da mudança: reconstruir a versão pré-correcção por troca inversa só na :177 reproduz exactamente `118e4975…` (verificado pelo reapreciador e pelo confirmador). ED-19 byte-intocada: `FieldBillCalculator 36304381da919d5a603d8169e3b41ff465c00994bea6cd0c4d1245cc7d0a09d9`, `FieldTariffSnapshot 648e41ec4622adbccd4c56670fa698623c2b3a2f28000a390dfe44ea37f62835`, `RoutePackage 773759fd701015de6528b5036bc0d4bc96b6c834c4946424f9b09ea123fc6145`. Bases 7/7 intactas (nenhum ficheiro de origem editado); `entrega/` tem exactamente os 3 ficheiros.

## Placares (lidos dos logs integrais, 5 execuções independentes)

| Prova | Produtor | Revisor | Corrector | Reapreciador | Confirmador |
|---|---|---|---|---|---|
| C4 FieldChargeResolverTest + FieldSnapshotProjectorEquivalenceTest | 31/31 | 31/31 | 31/31 | 31/31 | 31/31 |
| C1 (4 suites do catálogo) | 46/46 | 46/46 | 46/46 | 46/46 | 46/46 |
| MM-09 (OfficialConsolidation + Selection) | 13/13 | 13/13 | 13/13 | 13/13 | 13/13 |
| MM-04 regressão | 9/9 | 9/9 | 9/9 | 9/9 | 9/9 |

Logs integrais em `trabalho/` (333 linhas cada, salvo o do revisor com 392 linhas pelo bloco extra de controlo negativo): `saida-harness-r9-b.txt`, `saida-harness-revisao-r9-b-a074d132-349d-4b94-b1c1-9083cefdc719.txt`, `saida-harness-correcao-r9-b-9c9dab72-c751-42f1-86e3-13205a7cebfc.txt`, `saida-harness-reapreciacao-r9-b-c9692297-46a4-4f04-82b7-7233b9d2f830.txt`, `saida-harness-confirmacao-r9-b-89bde696-2409-463f-b331-5c0ee06ae889.txt`. 0 `✘`, 0 `error:` em todos. Nenhuma asserção afrouxada; nada inventado. PG do MM-09 (19/19) não re-executada: só prosa no MM-09, sem SQL nem semântica de valores; P-R9B-01 cobre a prova de pouso.

Correcções ao brief registadas: `ChargeResolverTest` tem 7 `@Test` (46 = total das 4 suites C1); `MunicipalityReportSelectionTest` tem 6 `@Test` (13 = total MM-09).

## Achado da revisão e ciclo de correcção

**R9B-R-01 (menor) — RESOLVIDO.** A citação `(:162-165)` no comentário entregue do R9-02 referia a numeração da base; no ficheiro corrigido o filtro está em :167-170 e a guarda em :183-188 (deslocamento de +5 linhas da reescrita do Javadoc do R9-01). Correcção: 1 linha (:177) + P-R9B-03 + duas imprecisões do relatório (§3.1 requireNonNull base :70-71 / corrigido :76-77; §2 records :81-115). Registada com hash antigo → novo em RELATORIO §9 e LANDING §6. Reapreciação e confirmação verificaram a correcção por reconstrução inversa do hash.

## Observações residuais (registadas, sem acção, sem bloqueio)

- **R9B-RP-01:** três intervalos aproximados nas margens — records `:81-115` (exacto :81-113), `closeReference :187-198` (exacto :187-196), `build :202-217` (exacto :198-220). Contêm o conteúdo certo; funcionam como localizadores. Intervalos exactos registados na CONFIRMACAO §7.
- **R9B-RP-02:** a RELATORIO §6 linha 121 (não 119) cita o hash antigo `118e4975…` sem rótulo no ficheiro, e a §9 não explica essa manutenção como o corrector afirmou. Em substância é registo histórico correcto (o log do produtor, linha 247, contém de facto esse hash); risco baixo — §5.2, LANDING §3 e §9.1 têm o hash novo.
- **R9B-RP-03 (do confirmador):** as caixas de revisão/confirmação na §5 do LANDING continuam por assinalar apesar de ambos os estágios estarem concluídos e documentados. Assinalam-se no registo do pouso/diário.

## Pendências nomeadas

- **P-R9B-01** — CI formal Maven no PC do dono (prova de pouso).
- **P-R9B-02** — C4-01: decisão do dono pendente (sem execução até à decisão).
- **P-R9B-03** — Remoção futura da guarda R9-02 só com re-prova completa (linhas do ficheiro corrigido: guarda :183-188, filtro :167-170).
- **P-R9B-04** — Pouso aditivo sobre pacotes ainda não pousados (C4, MM-09), com guarda de rebase/hash no acto (destino tem de ser o hash-base; divergência ⇒ PARAR e voltar).
- **P-R9B-05** — O futuro montador de rotas deve ser testado com IBGE malformado (passa a receber `IllegalArgumentException` em vez de fallback silencioso).

## Pouso

Posição na fila: depois dos lotes da base fictícia, junto às correcções da CI, antes da conclusão; pousa junto com a C4 e o MM-09, por substituição ficheiro a ficheiro no acto (LANDING.md). Pré-condições herdadas: MM-04 antes do MM-09; C1 antes da C4; C7 (rebase verificado) no acto; verificação pós-pouso por hash + diário.
