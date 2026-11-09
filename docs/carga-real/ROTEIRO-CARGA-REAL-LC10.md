# ZiipSan — LC.10 · Roteiro de carga real do legado

- **Sessão produtora:** `1fb44147-00cd-4ed1-a19a-ac6f72c78874` — papel: **Produtor**
- **Data de produção:** 09/11/2026
- **Natureza deste documento:** roteiro de execução para o evento real de
  carga. **Nenhuma carga real foi executada.** Não há cliente/piloto real
  (decisão do dono, item 34 do `DECISOES-DO-DONO.md`, 09/11/2026 00:19:
  o produto será vendido em licitação após a conclusão; os testes correm
  sobre base fictícia). Este roteiro fica pronto para ser executado
  quando existir cliente com dado real de legado.
- **Aceite da LC.10 (fonte única, íntegra):**
  `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json`,
  registro `LC.10` — origem `17:X5; ADR-0022`, tamanho M,
  deps `LC.3, LC.4, LC.5, LC.9, GL.4`:

  > "importadores da P1.2, da P2.10 e da P2.19 executados sobre o dado
  > real, só na produção; conciliação assinada pelo piloto; XML
  > autorizados reais importados, com o relatório de recusas aceito pelo
  > piloto; homologação e demonstração na nuvem só com massa sintética,
  > com o teste de varredura da P0.45 verde nesses ambientes"

---

## 1. Os importadores (referências reais, verificadas nos pacotes locais)

Todos no módulo `commercial/ziipsan-registry`, pacote
`br.com.ziipsan.commercial.registry.legacy` (e `legacyxml` na P2.19).

### 1.1. P1.2 — Importador v0 do cadastro com conciliação (migration `V14_1`)

- Motor: `LegacyImportService`
  (`commercial/ziipsan-registry/src/main/java/br/com/ziipsan/commercial/registry/legacy/LegacyImportService.java`;
  espelho local em `~/workspace/ziipsan-producao/p2.10/espelho/`).
- Leitores de fonte: `CsvLegacyReader` e os tipos de fonte do framework
  (`CSV`, `SQL_DUMP`, `LEGACY_DB`).
- Pipeline do próprio motor, sempre nesta ordem: **carga** (lê a fonte
  pelo mapeamento declarativo versionado, transforma, valida e encena)
  → **ensaio** (perfil de qualidade + conciliação, sem gravar) →
  **conciliação gravada** → **aplicação** (unidades + OS interna de lote
  que as referencia, efeitos pela regra da P1.31).
- Conciliação automática **por setor, rota, categoria e saldo**;
  relatório de linhagem por registro; reexecução idempotente
  (recarregar a mesma (sistema, chave externa) devolve o lote existente
  sem duplicar).
- Aceite da P1.2 (fonte: `tarefas-P1.json`): "totais por setor e rota
  iguais aos do legado"; "dado real só na produção do lançamento
  (LC.10)" — é este roteiro que cumpre essa cláusula.

### 1.2. P2.10 — Importador v1 com 120 meses (migration `V14_2`)

- Motor: `V1ImportService`
  (`.../registry/legacy/V1ImportService.java`; entrega local em
  `~/workspace/ziipsan-producao/p2.10/entrega/`), construído sobre o
  framework declarativo da P1.2 (PLT-16).
- Declaração de mapeamento do histórico:
  `src/main/resources/legacy/mappings/v1-history-v1.json`.
- Conferência de saldo: `V1BalanceConference` — saldo por matrícula
  igual ao do legado, **com as prestações a vencer dos parcelamentos
  ativos incluídas** (cada prestação vira um débito a cobrar com
  `source_type` de parcelamento e `legacy_reference` do acordo, nas
  colunas de origem da P2.5/DAD-20); matrícula divergente não gera
  débito (códigos `BALANCE_MISSING`, `BALANCE_NEGATIVE`,
  `INSTALLMENTS_EXCEED_BALANCE`).
- Janela de 120 meses aplicada na carga: competência fora da janela,
  após o corte, duplicada ou sem unidade no lote é recusada e volta no
  relatório da carga.
- Unidade sem usuário vigente importada na situação da P1.41 (CAD-68)
  via `V1UnitSituation`/`JdbcUnitSituationGateway`.

### 1.3. P2.19 — Importador de XML autorizados anteriores à primeira produção (migration `V14_3`)

- Motor: `LegacyXmlImportService`
  (`.../registry/legacyxml/LegacyXmlImportService.java`; entrega local
  em `~/workspace/ziipsan-producao/p2.19/entrega/`).
- Declaração de mapeamento:
  `src/main/resources/legacy/mappings/legacy-xml-v1.json`.
- Recusas (vocabulário `LegacyXmlRejection`): `XML_INVALID`,
  `ACCESS_KEY_INVALID`, `SIGNATURE_INVALID` (assinatura inválida),
  `MISSING_AUTHORIZATION_PROTOCOL` (sem protocolo de autorização),
  `ISSUER_MISMATCH` (outro emitente), `MODEL_NOT_ACCEPTED`,
  `DUPLICATE` (repetido) — com **relatório por lote**.
- Aceitos vão para o armazenamento imutável da P2.18 com hash e origem
  `LEGACY_IMPORT`, sem alteração do arquivo (bytes exatos), via porta
  `LegacyXmlRetentionPort`.
- Aceite da P2.19 (fonte: `tarefas-P2.json`): no ambiente local, o
  importador é testado **só com lotes de XML sintéticos** assinados com
  certificado de teste, "porque os XML reais do piloto trazem dado
  pessoal do destinatário e são importados só na LC.10" — é este
  roteiro que cumpre essa cláusula.
- Contagem por competência conferida com o legado
  (`conferirContagem`; ausência da contagem do legado = erro, nunca
  "confere").
- **Pendência de integração herdada da P2.19 (declarada no relatório
  dela, não resolvida aqui):** o adaptador de `LegacyXmlRetentionPort`
  para a P2.18 real (a origem `LEGACY_IMPORT` não existe no enum da
  P2.18 como entregue) e as implementações JDBC de
  `LegacyXmlImportStore`/`LegacyHistoryDataPort` são da integração.
  Sem o adaptador ligado, nada é aceito. Ver §3, passo P-4.

---

## 2. Pré-condições do aceite — estado atual declarado (sem presumir)

A LC.10 declara deps `LC.3, LC.4, LC.5, LC.9, GL.4`. Estado verificado
localmente em 09/11/2026 (pacotes em `~/workspace/ziipsan-producao/` e
mapa do bloco em `conclusao/MAPA-COORDENACAO.md`):

| Dep | O que o aceite dela exige | Estado atual (declarado) |
|---|---|---|
| **LC.3** — RDS, backup e PITR | RDS PostgreSQL 17 Multi-AZ, PITR com RPO ≤ 15 min e RTO ≤ 4 h **medidos**, cofre isolado com Vault Lock, tudo em `sa-east-1` | **NÃO CUMPRIDA.** Pacote em produção neste bloco de conclusão (preparação/IaC produzível); RPO/RTO medidos e teste do cofre exigem a AWS real. Sem LC.3 medida, não há rede de segurança para a carga real. |
| **LC.4** — KMS e Secrets Manager: R1 em produção | `TenantKeyProviderContract` verde sobre o KMS real; DEK por tenant; segredos de produção no Secrets Manager, sem reaproveitar segredo/chave do cofre local | **NÃO CUMPRIDA.** Pacote em produção neste bloco; a suíte de contrato verde sobre o KMS real exige a AWS. |
| **LC.5** — S3 com Object Lock | Suíte de contrato da P0.45 verde contra o S3; apagar/sobrescrever XML retido recusado pelo Object Lock (teste); prefixo e chave por tenant | **NÃO CUMPRIDA.** Pacote em produção neste bloco; a prova de recusa do Object Lock exige S3 real. É pré-condição direta do passo de XML (§4.4): sem Object Lock provado, XML retido não tem a imutabilidade que o aceite da P2.19 pressupõe. |
| **LC.9** — Contrato SaaS, DPA e acordo de tratamento assinados com o piloto | Assinatura **antes do primeiro dado real**, a partir das minutas aprovadas na P3.9, após o pentest da LC.8 sem achado crítico/alto aberto; ZiipSan operador, cliente controlador | **NÃO CUMPRIDA e hoje impossível:** as minutas da P3.9 foram produzidas/aprovadas (08/11/2026), mas **não há piloto/cliente** (item 34) — não há quem assine. No modelo de licitação, assina-se com o cliente futuro. Sem LC.9 assinada, **nenhum dado real entra** (é também exigência do próprio aceite da LC.9: "assinados com o piloto antes do primeiro dado real"). |
| **GL.4** — Aceite da homologação local | Decisão formal do dono do produto e do piloto registrada sobre GL.2/GL.3; relatório do paralelo (GL.2) anexado como base do paralelo real (LC.11) | **NÃO CUMPRIDA.** Dossiê em produção neste bloco; a decisão formal é ato do dono do produto e do piloto — o piloto não existe (item 34). |

**Regra de portão deste roteiro:** se qualquer pré-condição estiver
NÃO CUMPRIDA no dia da execução, a carga real **não começa**. Não há
execução parcial "para adiantar": o aceite pede os importadores sobre
o dado real **só na produção**, e produção sem LC.3/LC.4/LC.5/LC.9 não
é o ambiente que o plano define.

**Pré-condição adicional (fator externo nomeado):** existência de um
cliente (o "piloto" do plano) com sistema legado acessível e dado real
entregue. Hoje esse fator **não existe** — decisão do dono no item 34:
sem cliente real; venda por licitação após a conclusão do produto.

---

## 3. Preparação (antes da janela de carga)

- **P-1. Ambiente:** confirmar que o alvo é o ambiente de **produção**
  (AWS `sa-east-1`, LC.2/LC.3/LC.4/LC.5 de pé). Registrar a
  identificação do ambiente e do tenant do cliente no registo de
  execução (modelo em `MODELO-REGISTRO-EXECUCAO-IMPORTADOR-LC10.md`).
- **P-2. Ponto de restauração:** confirmar PITR operacional (LC.3) e
  registrar o marcador temporal anterior à carga — é o caminho de
  volta se a conciliação falhar (a restauração em si é a LC.12).
- **P-3. Mapeamentos:** confirmar que o legado do cliente entra como
  **arquivo de mapeamento declarativo + testes**, sem importador novo
  (regra do framework da P1.2/PLT-16). Legado sem mapeamento
  versionado e testado = carga adiada, não improvisada.
- **P-4. Integração da P2.19:** confirmar o adaptador de
  `LegacyXmlRetentionPort` ligado à retenção real da P2.18 com a
  origem `LEGACY_IMPORT` (pendência de integração herdada, §1.3).
  Sem adaptador, o passo de XML (§4.4) não executa.
- **P-5. Fonte do dado:** registrar a origem de cada extração do legado
  (sistema, data/hora da extração, responsável no cliente, meio de
  entrega) no registo de execução. Extração sem proveniência
  registrada não é carregada.
- **P-6. Janela e papéis:** janela combinada com o cliente registrada;
  papéis nomeados: operador da carga (ZiipSan), conferente (ZiipSan,
  pessoa distinta do operador), representante do cliente (assina a
  conciliação e aceita o relatório de recusas). Nomes: `[A DEFINIR]`
  na execução real — nenhum nome é presumido neste roteiro.

## 4. Execução — ordem obrigatória

A ordem segue as dependências entre os importadores: P2.10 depende da
P1.2; P2.19 depende da P2.10 e da P2.18.

### 4.1. Passo 1 — P1.2: cadastro v0 sobre o dado real (só na produção)

1. Carga da fonte do legado pelo `LegacyImportService` com o
   mapeamento do legado do cliente (leitores CSV/dump SQL/banco
   legado, conforme a fonte entregue).
2. **Ensaio sem gravar:** perfil de qualidade (completude, duplicidade,
   integridade referencial, documento inválido) e conciliação prévia.
   Divergência no ensaio = parar e tratar com o cliente antes de
   aplicar; o ensaio não grava nada no cadastro.
3. Conciliação do ensaio **por setor, rota, categoria e saldo** contra
   os totais do legado (totais por setor e rota iguais aos do legado —
   aceite da P1.2).
4. Aplicação: unidades aplicadas com a OS interna de lote que as
   referencia (efeitos pela P1.31). Registrar no registo de execução:
   lote (sistema + chave externa), contagens encenadas/aplicadas/
   recusadas e o relatório de linhagem.
5. Reexecução, se necessária, é idempotente — nunca gera duplicidade;
   registrar cada reexecução no registo.

### 4.2. Passo 2 — P2.10: histórico v1 de 120 meses

1. Carga do histórico pelo `V1ImportService` com a declaração
   `v1-history-v1.json` (ou declaração nova do legado, §3 P-3).
   Competências fora da janela de 120 meses, após o corte, duplicadas
   ou órfãs voltam no relatório da carga — não são encenadas.
2. Prestações a vencer dos parcelamentos ativos encenadas pela P1.2
   são aplicadas como débitos a cobrar, uma a uma, com a referência ao
   acordo do legado.
3. **Conferência de saldo por matrícula** (`V1BalanceConference`):
   saldo do ZiipSan = saldo do legado, prestações incluídas. Matrícula
   divergente não gera débito e entra na conciliação (§5) com o código
   da divergência.
4. Situações de unidade sem usuário vigente aplicadas conforme a
   P1.41 (CAD-68).
5. Registrar a execução no registo de execução (modelo próprio).

### 4.3. Passo 3 — Conciliação do cadastro + histórico

1. Gerar o relatório de conciliação a partir dos relatórios dos
   Passos 1 e 2 (modelo:
   `MODELO-RELATORIO-CONCILIACAO-LC10.md`).
2. Toda divergência tem código, explicação e disposição (corrigida na
   fonte, corrigida no mapeamento, ou aceita pelo cliente com
   justificativa). Divergência sem explicação bloqueia a assinatura.
3. **Assinatura do cliente** no relatório de conciliação — é o item de
   aceite "conciliação assinada pelo piloto". Sem assinatura, a carga
   não é dada por concluída, qualquer que seja o estado técnico.

### 4.4. Passo 4 — P2.19: XML autorizados reais

1. Receber os lotes de XML autorizados que o cliente entregar (sistema
   antigo ou emissor usado no intervalo) e registrar a proveniência
   (§3 P-5).
2. Executar o `LegacyXmlImportService` sobre os lotes **na produção**.
   Recusas esperadas e nomeadas: assinatura inválida, sem protocolo de
   autorização, outro emitente, modelo não aceito, duplicado, XML
   inválido/chave inválida.
3. Aceitos: confirmar gravação na retenção imutável da P2.18 com hash
   conferido e origem `LEGACY_IMPORT`, arquivo sem alteração.
4. **Contagem por competência conferida com o legado:** a contagem
   declarada pelo legado é entrada explícita; sem ela, a conferência é
   erro — nunca se presume que confere.
5. Relatório de recusas por lote (modelo:
   `MODELO-RELATORIO-RECUSAS-XML-LC10.md`) apresentado ao cliente.
6. **Aceite do cliente** no relatório de recusas — é o item de aceite
   "relatório de recusas aceito pelo piloto". Recusa não aceita volta
   como divergência para tratamento; não se apaga nem se reclassifica
   recusa para fechar número.

### 4.5. Passo 5 — Fechamento da carga

1. Completar o checklist de aceite (`CHECKLIST-ACEITE-CARGA-LC10.md`)
   com evidência por item.
2. Registrar a execução no diário de bordo do projeto (a carga real é
   marco do lançamento; o registro é parte do fechamento, como no
   restante do plano).
3. Base carregada fica **somente** na produção. Nenhuma cópia do dado
   real desce para homologação, demonstração ou ambiente local — ver
   a guarda do §5.

---

## 5. Guarda dos ambientes de homologação e demonstração (massa sintética)

Item de aceite da LC.10: "homologação e demonstração na nuvem só com
massa sintética, com o teste de varredura da P0.45 verde nesses
ambientes".

1. **Regra:** nos ambientes de homologação e de demonstração na nuvem,
   só entra massa **sintética** (ou anonimizada na origem pelo roteiro
   da P0.45, quando o plano o prevê para a homologação local — GL.1).
   Dado real do cliente, nunca.
2. **Passo obrigatório de varredura:** antes de qualquer uso de uma
   base de homologação/demonstração carregada, executar o **teste de
   varredura da P0.45** sobre essa base: ele recusa dado pessoal real
   na base (documento sem a marca de sintético). Resultado exigido:
   **verde**. Varredura vermelha = base inutilizada para homologação/
   demonstração até saneamento e nova varredura verde; registrar o
   resultado (ambiente, data, massa varrida, resultado) no registo de
   execução ou em registro próprio referenciado por ele.
3. **Simetria:** o dado real carregado por este roteiro vive só na
   produção (§4). A base fictícia de testes do item 34 (12.000 UCs)
   **não** é massa desta carga: é massa de teste do produto, com
   limpeza própria, e não substitui nem simula a carga real.
4. Esta guarda é verificada no checklist de aceite
   (`CHECKLIST-ACEITE-CARGA-LC10.md`, seção D).

## 6. Falhas, parada e volta

- **Parada:** divergência de conciliação sem explicação, varredura
  vermelha, retenção de XML sem Object Lock provado (LC.5) ou
  assinatura/aceite do cliente recusados param a carga no passo em
  que ocorrerem. Não se avança "para ver o resto" com o aceite furado.
- **Volta:** carga não concluída volta pelo ponto de restauração do
  §3 P-2 (PITR da LC.3; restauração é a LC.12). Reexecução dos
  importadores é idempotente (P1.2/P2.10/P2.19), o que permite
  corrigir mapeamento/fonte e reexecutar sem duplicar.
- **Nenhum resultado é afirmado neste roteiro:** contagens, saldos e
  recusas reais só existirão nos documentos preenchidos na execução,
  com o dado real do cliente que hoje não existe.
