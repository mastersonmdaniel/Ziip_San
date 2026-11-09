# GL.4 — Dossiê de Aceite da Homologação Local

> **Produtor:** sessão `ca3d8c13-25d9-449d-828d-89c54bbff8fd` (papel: Produtor)
> **Data:** 09/11/2026 · **Sem acesso ao GitHub neste bloco (parada dura por HTTP 429) — trabalho 100% local.**
> **Destino no repo:** `docs/homologacao/DOSSIE-ACEITE-HOMOLOGACAO-GL4.md`

**Tarefa (fonte única):** GL.4 — "Aceite da homologação local"; origem `ADR-0001; ADR-0033`; tamanho `P`; deps: `GL.2, GL.3, P3.24`; aceite integral: *"decisão formal do dono do produto e do piloto registrada sobre o resultado da GL.2 e da GL.3; com optantes no piloto (P3.24, condicional), remessa e retorno FEBRABAN 150 conferidos com arquivos de teste, sem envio ao banco; sem optantes, o aceite registra que a P3.24 não se aplica; o sistema do piloto continua oficial e emissor; relatório do paralelo anexado como base do paralelo com dado real (LC.11)"* — `tarefas-GL-P4-LC-PEND-C.json`.

> **O que este dossiê é — e o que não é.** Este dossiê **compila fielmente** a evidência (GL.2, GL.3, FEBRABAN 150/P3.24) e deixa a **decisão formal pronta para ser tomada**. Ele **não é** a decisão: a decisão formal é do dono do produto e do piloto, é **pendência do dono** (pendência GL.4-01) e **não está preenchida, assinada nem datada** em parte alguma deste pacote. Não há piloto (decisão nº 34 do dono: sem cliente real; produto vendido em licitação após a conclusão) — o papel do piloto está **vacante** (pendência GL.4-02).

---

## 1. (a) Resultado compilado da GL.2 e da GL.3 — o que existe e o que NÃO existe

### 1.1. GL.2 — "Dois ou três ciclos de faturamento paralelo local, conta a conta"

Fontes: `gl.2/RELATORIO-GL.2.md`, `gl.2/REVISAO-GL.2.md` (veredito inicial: REPROVADO por dois furos fail-closed D1/D2), `gl.2/REVISAO-CONFIRMACAO-GL.2.md` (veredito: **APROVADO nos pontos D1/D2/R1/R2**).

**O que EXISTE (aparato, provado):**

- Orquestrador fail-closed do paralelo (`paralelo-local.sh`): pré-voo (config validada, GL.1 com resultado CONCLUIDO em linha ancorada, fiscal de produção desligado, insumos presentes), por ciclo faturamento → conteúdo → escopo → comparação → retificação/relatórios → devoluções → fechamento fiscal, com saídas distintas por etapa (2–7) e registo medido.
- Validador de config (`validar-config-paralelo.py`): só aceita 2 ou 3 ciclos (inteiros puros), `tpAmb=2`, perfil P2.31 ativo com vigência cobrindo o 1º ciclo e conferido **estritamente antes** dele, critério com fonte literal `P0.30`, conteúdo P2.32, escopo P3.30, `tenantId` UUID.
- Comparador conta a conta (`comparar-contas.py`): chave = código substituto; divergências de VALOR/AUSÊNCIA/CONSUMO/VENCIMENTO; exige critério monetário (por conta, total do ciclo, ausências) **e** explicação para **toda** divergência; saídas 0/1/2/3.
- Roteiro, checklist, config-modelo sintética validada e modelo de registo de ensaio de POC.
- **Prova:** harness com stubs **56/56** (47 originais + 9 dos pontos D1/D2/R1/R2), reexecutado pelo produtor deste dossiê em cópia, em 09/11/2026: **56 ok, 0 falhas, exit 0**. Mutações do revisor reprovam o harness (não é tautológico).

**O que NÃO EXISTE (declarado pela própria GL.2, sem inflar):**

- **Nenhum ciclo real executado.** Não há resultado de ensaio, tempo, contagem ou divergência medidos em ciclo real — o relatório da GL.2 afirma-o expressamente e nenhum registo de paralelo real existe na pasta de registos (só o modelo).
- **Não há critério de divergência registado** com valores: o critério entra na config como dado externo obrigatório de fonte `P0.30`; a P0.30-piloto não foi entregue e a decisão 34 substituiu-a pela base fictícia. A estrutura de critérios e os limiares por fixar estão agora no plano da **P3.13** (limiares L-01…L-04 em [A DEFINIR], pendência P3.13-01).
- **Não há contas reais de piloto** nem carga real da GL.1 executada (a GL.1 também é aparato: harness 37/37, reexecutado pelo produtor em 09/11/2026 — 37 ok, 0 falhas — mas a carga real depende de fiação dos importadores e de massa, e não foi executada).
- Os serviços donos (faturamento P2.5, conteúdo P2.32, retificação P2.20, relatórios P3.22, devoluções P3.29, escopo P3.30) entram por **hooks** cuja fiação é pendência de integração declarada da GL.2.

### 1.2. GL.3 — "Ensaio de restauração local"

Fontes: `gl.3/RELATORIO-GL.3.md`, `gl.3/REVISAO-GL.3.md` (veredito inicial: NÃO APROVADO — 1 defeito bloqueante de SQL, B1; correções aplicadas e relatadas no §6 do relatório, harness final 65/65; **não há ficheiro de confirmação independente separado** da GL.3 neste pacote local — facto registado tal como está).

**O que EXISTE (aparato, provado):**

- Orquestrador do ensaio (`ensaio-restauracao-local.sh`): pré-voo, restauração completa cronometrada em base isolada, contagem/checksum, teste de RLS no banco restaurado, restauração seletiva de um tenant (P3.33) com prova de testemunha, desfazer de lote por OS inversa, limpeza e registo medido; aborta na primeira falha com saída distinta por etapa (2–7).
- Três SQL de verificação (contagem/checksum canónico; RLS como `ziipsan_app` sem contexto = 0 / contexto próprio / contexto alheio = 0; prova da OS inversa), roteiro e modelo de registo com a frase do aceite: *"resultado registrado como ensaio, sem meta (a meta vale na LC.12)"*.
- Referência do aceite: restauração cronometrada **contra o RTO de referência de 4 h** (P0.28/GL.3).
- **Prova:** harness com stubs **65/65**, reexecutado pelo produtor deste dossiê em cópia, em 09/11/2026: **65 passaram, 0 falharam, exit 0** — incluindo a verificação estática que reprova o padrão do defeito B1 (variável psql dentro de `DO $$...$$`) e a sua prova contra mutação.

**O que NÃO EXISTE:**

- **Nenhum ensaio real executado.** Não há tempo de restauração medido, contagem real, checksum real nem resultado de RLS real: o primeiro `registro-ensaio.md` real nasce da execução no PC do dono, com backup real da P0.28. Os três SQL **nunca foram executados contra um PostgreSQL real** (não há PostgreSQL nesta VM — verificado e declarado pela GL.3); o defeito determinístico B1 foi eliminado por construção e coberto estaticamente, mas a prova em banco real continua pendente da primeira execução.
- O ensaio depende dos scripts da P3.33 presentes no ambiente; sem eles o pré-voo recusa (comportamento correto, fail-closed).

### 1.3. Síntese honesta para a decisão

| Pergunta da decisão | Resposta fiel |
|---|---|
| O aparato de homologação local existe e está provado? | **Sim** — GL.1 37/37, GL.2 56/56, GL.3 65/65 (harnesses com stubs, reexecutados neste bloco), com revisão cruzada da GL.2 confirmada e correções da GL.3 relatadas. |
| Houve ciclos reais de paralelo ou ensaio real de restauração? | **Não.** Zero ciclos reais, zero resultados medidos. |
| Há critério de divergência com valores para julgar um ciclo? | **Não ainda** — estrutura sim (P3.13), limiares em [A DEFINIR] (P3.13-01). |
| A decisão formal pode ser "aceite dos resultados"? | Só pode ser, com verdade, **aceite do aparato e do estado** aqui compilado — qualquer formulação que afirme resultados de ciclos reais seria falsa. O termo em branco (§4) está redigido para a decisão recair sobre este dossiê, com as ressalvas visíveis. |

## 2. (b) Conferência FEBRABAN 150 — arquivos de teste, sem envio ao banco

Enquadramento: o dono **confirmou que há optantes** e decidiu o **caminho duplo** (decisões, item 9): campanha de conversão ao Pix Automático (P4.5) **e** FEBRABAN 150 **mantido como legado** para a base atual; débito novo só por Pix Automático. Logo, o ramo aplicável deste aceite é o **"com optantes"**. O parecer jurídico formal RAS-06/PD-10 **não existe como documento** (declarado pela P3.24); vale a decisão do dono — este dossiê não finge parecer nenhum.

### 2.1. Artefatos localizados (pacote `p3.24/`)

| Peça | Caminho (no pacote P3.24) | Papel |
|---|---|---|
| `Febraban150Port.java` | `entrega/commercial/ziipsan-collection/src/main/java/br/com/ziipsan/commercial/collection/legacydebit/` | Porta do arquivo: registos **lógicos B, C, E e F** (`RecordType { B, C, E, F }`), `writeRemittance(batch)` / `readReturn(tenantId, fileReference)`; código de retorno guardado **verbatim** (`outcomeCode`). Leiaute posicional **[SEM FONTE]** — nenhum campo posicional inventado (marcador M1 da P3.24). |
| `LegacyDebitService.java` | idem | Geração da remessa (ordem B,E,C,F e soma de controlo no F, segundo o teste), processamento do retorno com **baixa idempotente** (`ReturnOutcome { BAIXADO, REPETIDO_SEM_EFEITO, DIVERGENTE, NAO_IDENTIFICADO }`; repetido não toca nem `settledAt` nem `outcomeCode`), reenvio, cancelamento de optante, trava de autorização nova (só Pix Automático, via trava real da P4.5). |
| Testes | `src/test/java/.../legacydebit/` (5 classes + fixture) e `persistence/LegacyDebitMigrationConsistencyTest.java` | **28 métodos `@Test`** no total (contagem mecânica do produtor: Import 6 + RemittanceReturn 8 + RemittanceIdempotency 4 + ShutdownDate 5 + Conversion 2 + MigrationConsistency 3). |

### 2.2. Conferência mecânica executada pelo produtor (09/11/2026, local, sem banco)

1. **Reexecução integral do harness da P3.24** a partir de cópia do `harness/run.sh` com a pasta de trabalho redirecionada para `/tmp` (o pacote P3.24 não foi alterado), JDK do projeto: compilação de produção **exit 0**, compilação de testes **exit 0**, JUnit **28 tests found, 28 successful, 0 failed, JUNIT_EXIT=0**. Log preservado neste pacote: `harness/febraban-reexecucao.log`.
2. **Conferência estática** (harness deste pacote, `harness/conferir-gl4.py`): porta declara B/C/E/F e os dois métodos de arquivo; serviço contém os quatro desfechos do retorno e a guarda de idempotência; **nenhum envio ao banco existe** — não há código de rede/transmissão nas fontes `legacydebit` e a única escrita de remessa nos testes é um dublê em memória (`Febraban150Port` de teste). Toda a E/S conferida é, portanto, **arquivo de teste, sem envio ao banco**, como pede o aceite.
3. **Desvio documental encontrado (D-GL4-01):** o `RELATORIO-P3.24.md` afirma "24 testes" e itemiza 24 (5+6+8+2+3), **omitindo** a classe `LegacyDebitRemittanceIdempotencyTest` (4 testes). A contagem real — log JUnit do pacote, contagem de `@Test` e a reexecução do produtor — é **28/28** nos três métodos. É um erro de contagem **a menor** no relatório da P3.24 (não infla; subdeclara), sem impacto funcional; fica nomeado aqui para correção por errata no pacote de origem, que este bloco não edita.

### 2.3. Ramo "sem optantes" — alternativa documentada

Se um tenant não tiver optantes: a importação simplesmente não acontece, a campanha de conversão fica vazia e o aceite regista que **a P3.24 não se aplica** (é a formulação do próprio aceite da GL.4 e, no corte, do aceite da LC.13). Nenhum comportamento adicional foi presumido ou codificado para esse ramo (declarado também no item 16 da tabela de aceite da P3.24). No enquadramento vigente, porém, o dono confirmou optantes — o ramo operante é o "com optantes" do §2.1–2.2.

## 3. (c) Declaração — sistema oficial e emissor

> **Declaração:** no modelo vigente (decisão nº 34), **não há nenhum sistema real em produção**: não há piloto, não há cliente e o ZiipSan não está em operação produtiva. A **base fictícia de testes** (12.000 UCs fictícias) é **só massa de teste**, marcada como fictícia, com limpeza posterior obrigatória — ela não é sistema oficial de ninguém e não emite nada com validade real.
>
> Por transposição fiel do aceite ("o sistema do piloto continua oficial e emissor"): **se e quando houver cliente**, durante toda a homologação local e durante o paralelo real (LC.11) o **sistema legado do cliente continuará oficial e emissor**, e o ZiipSan só assume esse papel no **corte (LC.13)**, mediante decisão formal registada. Até lá, nada neste dossiê transfere oficialidade nem emissão para o ZiipSan.

## 4. (d) Termo de Aceite — decisão formal, EM BRANCO

O **Termo de Aceite** acompanha este dossiê como artefato separado (`TERMO-DE-ACEITE-GL4.md`, mesmo destino), **pronto e em branco**: decisão, nomes, cargos, datas e assinaturas **não preenchidos**. Ele referencia, como base, o **relatório do paralelo da GL.2** (`RELATORIO-GL.2.md` e o aparato/registo da GL.2) — que é também a **base do paralelo com dado real (LC.11)**, nos termos do aceite. Enquanto o termo não for preenchido e assinado, **a decisão formal da GL.4 não existe** (pendências GL.4-01/GL.4-02 no relatório do pacote).

## Fontes (todas locais, lidas neste bloco)

- `tarefas-GL-P4-LC-PEND-C.json` (GL.2, GL.3, GL.4, LC.11, LC.13) e `tarefas-P3.json` (P3.24, P3.13).
- `gl.1/`, `gl.2/`, `gl.3/` — relatórios, revisões e confirmação; harnesses reexecutados em cópia (37/37, 56/56, 65/65).
- `p3.24/` — relatório, revisão, confirmação, `Febraban150Port.java`, `LegacyDebitService.java`, testes e harness (reexecutado: 28/28).
- `DECISOES-DO-DONO.md` — item 34 (sem cliente real; base fictícia) e item 9 (optantes; caminho duplo do débito automático).
- Pacote `p3.13/` deste bloco — critérios e limiares [A DEFINIR] referenciados no §1.3.

*Nenhum resultado medido foi inventado: onde não há execução real, este dossiê diz que não há.*
