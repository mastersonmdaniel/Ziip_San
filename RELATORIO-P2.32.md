# P2.32 — Conteúdo mínimo da fatura por lei e por agência e motor de mensagens legais

Execução local, 07/11/2026. Base: HEAD conferido via MCP nas listagens
(ref 0fbcd22, que já inclui a P2.5 e a P2.30). **Nenhum push foi feito.**
Nada compilou nesta VM (sem JDK/Maven): conferência estática + revisão
declarada em REVISAO-P2.32.md (sem spawn disponível nesta profundidade —
a revisão por agente distinto deve ser refeita pelo agente principal).

## Espelho
`~/workspace/ziipsan-producao/p2.32/espelho/` — 10 arquivos do motor da
P2.30 (`platform/templates`: TemplateBlock, TemplateEngine,
TemplateVersion, FactoryTemplates, catálogos), lidos via MCP sem 429.
Achado-chave: a P2.30 reservou explicitamente os blocos SYSTEM à P2.32
(TemplateBlock.Origin.SYSTEM; o motor recusa SYSTEM vindo do tenant).

## Migration (conferida no plano v3.0 §3)
Reservadas à P2.32: **`V4_10`** (billing) + repetível **`R__`** do conteúdo
mínimo. Entregues: `V4_10__bill_required_content_and_legal_messages.sql`
(3 tabelas: invoice_required_fields, legal_message_catalog,
bill_message_emissions; RLS via register_tenant_table; índices únicos de
chave estável), `R__invoice_required_content.sql` (16 linhas) e
`R__legal_message_catalog.sql` (7 linhas) — fontes únicas nos catálogos
Java, conferidas por teste de consistência. V4_10 estava livre na listagem
real do repo (V4_1–V4_6, V4_13–V4_15 ocupadas).

## Pacote (17 arquivos, caminhos do repo)
Billing, pacote novo `br.com.ziipsan.commercial.billing.content`:
RequiredBillContent (dado versionado, CATALOG_VERSION=1), BillFieldRequirement,
InvoiceTemplateContentRule, LegalMessageCatalog, LegalMessageDef,
LegalMessageEngine (seleção + ordem travada + prova), BillContentService +
DefaultBillContentService (API dos consumidores), PendingTaxDisclosure
(RAS-28 isolado); domain/BillMessageEmissionEntity +
repository/BillMessageEmissionRepository (prova persistível).
Platform: templates/LegalTemplateBlocks (ponte SYSTEM/LOCKED da P2.30).
Testes: InvoiceTemplateContentRuleTest (7), LegalMessageEngineTest (7),
RequiredContentCatalogConsistencyTest (3), LegalTemplateBlocksTest (3).

## Cobertura do acceptance, item a item
1. **Campos obrigatórios como dado versionado, por lei e por ERI** — ✔
   Base federal: Lei 11.445/2007 art. 39 (identificação do modelo da ERI);
   CDC art. 42-A/Lei 12.039/2009 (bloco travado supplier.identification_block);
   Decreto 5.440/2005 (qualidade da água mensal); Lei 12.007/2009
   (declaração anual); nome do titular (P2.5). Por perfil, só com ato/artigo
   já confirmados na P2.31: ARIS (108/160-161/153), ARESC (104/87-88/79),
   AGIR (129/151-152/143), CISAM-MO RN01 (104), CISAM-MO RN05 (aviso 30d,
   artigo declarado não discriminado). AGR-Tubarão e CISAM-SUL: só federal
   (texto não confirmado — nada inventado; declarado e testado).
2. **bill.invoice_template só aprovado com todos os campos do perfil** — ✔
   InvoiceTemplateContentRule: faltante nomeia campo+norma e recusa; bloco
   do CDC fora do travado recusa; teste de conteúdo por perfil (completo
   aprova nos 7 × faltante recusa).
3. **Motor de mensagens legais** — ✔ Catálogo versionado, chave por norma,
   classes do parâmetro governante (a nas incondicionais, c no aviso de perda
   da tarifa social, d no de vencimento de benefício — corrigido na revisão
   cruzada; a redação original dizia "classes a/b"), disparo, vigência e
   ordem; legais SEMPRE antes das demais
   (ordenação por grupo travada no comparador, testada com slots ativados);
   prova de emissão (EmissionProof + entidade/tabela) registrando mensagem
   × versão da conta × versão do modelo × versões dos catálogos (teste).
4. **Mensagens de fábrica** — ✔ Perda da tarifa social (Lei 14.898/2024;
   social.loss_notice_months com piso travado de 3; cálculo à mão testado:
   01/04→01/07/2026 cumpre, 02/04 não; parâmetro 2 não reduz o piso),
   declaração anual, bloco de qualidade da água (ambos para a P2.24) e
   aviso de vencimento de benefício CAD-88 usando BenefitExpiryRule da
   P2.5 (janela testada: abre 20/04/2026 para vencimento 20/05 − 30 dias).
5. **Slots** — ✔ ED-88 (90 dias), ED-25 (60 dias) e P3.5 (consumo anormal)
   criados INATIVOS (slot_only + activatingTask); não emitem sem ativação
   (teste); quando ativados, entram depois das legais.
6. **Nome do titular pela bill.holder_name_display** — ✔
   BillContentService.printedHolderName delega a HolderNameDisplay da
   P2.5 (teste das duas opções).
7. **RAS-28 — PENDENTE DE PARECER, ISOLADO** — ✔ (como pendência)
   PendingTaxDisclosure declara a chave reservada
   taxes.approximate_total_disclosure, LEGAL_OPINION_CONFIRMED=false e o
   ponto de extensão. O campo NÃO está no catálogo, na R__ nem na V4_10,
   e a regra de conteúdo RECUSA modelo que o contenha (teste). Nenhum
   texto/alíquota/fonte foi inventado.
8. **Consumidores P2.8/P2.17/P2.24** — ✔ API BillContentService exposta
   (campos, aprovação, mensagens, prova, nome impresso); consumidores não
   implementados, como manda a spec.

## Riscos para a CI
1. Sem compilação local: sintaxe conferida estaticamente; APIs reusadas
   (HolderNameDisplay, BenefitExpiryRule, TemplateBlock) copiadas dos
   espelhos reais da P2.5/P2.30.
2. **Fiação P2.30 pendente (integração)**: TemplateEngine.validationProblems
   recusa qualquer bloco SYSTEM; precisa aceitar os SYSTEM cujo id venha
   de LegalTemplateBlocks para o tipo fatura. Até lá, a aprovação pelo
   conteúdo do billing é a trava efetiva e o motor segue recusando —
   comportamento seguro, mas a camada de integração deve fechar isso.
3. R__ com ${tenant_id}: mesmo precedente GIS/P2.31; se o migrador não
   substituir em repetíveis do billing, a carga falha na CI (primeiro
   ponto a olhar).
4. Entidade nova × teste de consistência de migrations do billing
   (MigrationConsistencyTest e irmãos enumeram entidades): a CI pode
   exigir registrar BillMessageEmissionEntity nas guardas existentes —
   fix de integração esperado, sem afrouxar asserção.
5. DefaultBillContentService usa @Service do Spring (presente no billing
   pelos repositórios); se o módulo montar serviços por configuração
   explícita, a anotação é inócua e o bean se registra na configuração.
