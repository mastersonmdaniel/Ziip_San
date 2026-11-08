# P3.20 — Portal do consumidor: notas de integração

- Migration tenant `V8_2` (core): `consumer_portal_links` (só índices cegos),
  `consumer_portal_link_attempts`, `consumer_contract_deliveries`.
- O critério axe (sem violação `serious`/`critical`) é prova do FRONTEND na CI
  (P0.36): nenhuma tela nova foi criada nesta entrega; as telas de vínculo,
  histórico e documentos devem entrar no gate axe existente antes do go-live.
- Remissões do aceite (não implementadas aqui, por desenho do plano):
  taxa e documentos da ligação nova = ED-34; assinatura eletrônica = P3.34;
  autenticidade pública da fatura = P3.35.
- Adaptadores pendentes: portas sobre billing (contas/pagamentos 120 meses e
  documentos), registry (titularidade), personal_data_access_log (V1_6) e o
  alerta de volume na Central (P3.15).

## Correção pós-revisão independente (D1–D7)

- **D1:** a `V8_2` registra as 3 tabelas com `register_tenant_table(...)` e
  classifica os identificadores em `public.data_classification` (classe `ID`),
  no padrão exato da V8_1/V1_15/V1_8; o teste de consistência trava os dois
  pontos.
- **D2 (canais laterais do "sem oráculo", fechados no código):** (a) tempo —
  documento e código são sempre computados pelos índices cegos, sem
  curto-circuito, contra unidade real ou fictícia, mesmo sob bloqueio;
  (b) limitador — o 429 só sai das dimensões do solicitante (IP/tenant); o
  bloqueio só pela unidade é absorvido na resposta uniforme de falha.
- **D3/D3b:** a ciência do contrato preenche `acknowledged_at` na entrega
  existente (UPDATE pela porta `acknowledgeDelivery`), sem inserir cópia;
  `saveLink` grava os índices cegos do documento e do código conferidos, e a
  coluna `proof_method` da V8_2 registra o método de comprovação do plano
  §3 (`CODE_AND_DOCUMENT`, único método do desenho, por DEFAULT).
- **D5/D7:** o controller expõe todas as frentes do aceite (quitação,
  acessível, alteração de dados, contrato, troca de titularidade) com o
  guard antes de cada serviço; conta inexistente e não vinculada respondem
  igual (403) em documentos e no formato acessível.
