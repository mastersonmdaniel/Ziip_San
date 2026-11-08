# Autenticidade pública da fatura e canais oficiais (P3.35, PLT-11)

## Verificação pública

- Rota: `GET /api/v1/public/documents/verify?code=<codigo>` — o código curto
  impresso na conta (leiaute da gráfica v2, campo `VERIFICACAO=`) ou o QR que
  codifica essa URL com o código.
- A resposta confirma **valor, vencimento, mês de referência, recebedor
  (razão social e CNPJ do tenant) e hash do documento**. Não contém nome do
  titular, endereço, documento do titular, matrícula, unidade nem consumo —
  esses campos não existem na resposta nem são lidos pelo serviço.
- Código inexistente, revogado, de fatura cancelada, malformado ou com hash
  divergente responde sempre o mesmo `authentic=false` sem nenhum outro dado.
  Com `portal.verify_enabled` desligado, a resposta é a mesma. A
  indistinguibilidade é de corpo e status, não de tempo de resposta (não há
  promessa de tempo constante nesta rota).
- Limites por origem e por código: os mesmos tetos do pagamento sem conta da
  P3.3 (`portal.account_throttle`); estouro responde 429. Toda consulta é
  registrada sem dado do titular (tabela `bill_verification_queries`, V8_3).

## Código de verificação

Gerado por CSPRNG com 80 bits (16 símbolos, exibido `XXXX-XXXX-XXXX-XXXX`),
distinto do código de vinculação ao portal. No banco fica só o índice cego
(contexto `portal.bill_verification_code`). Sequências e matrículas não
funcionam como código.

## Canais oficiais

A lista publicada (`portal.official_channels`, classe d) é derivada de
`branding.contact` e conferida contra ele antes de publicar: e-mail confere
com o e-mail do contato, telefone/WhatsApp com o telefone, site com o site;
portal exige o domínio próprio do tenant (P2.27) e app o identificador do
app (ED-39). Canal divergente não é publicado. A lista aparece na conta
(modelo da P2.30), no portal (`GET /api/v1/public/documents/official-channels`)
e nos e-mails (motor da P3.5 consome a mesma fonte).

Texto padrão exibido com os canais: o tenant **nunca pede pagamento por
link avulso** (ver `FraudProtectionTexts.NEVER_LOOSE_LINK_TEXT`).

## Pix

Antes de confirmar, o pagador confere no aplicativo do banco: recebedor =
razão social do tenant e CNPJ do tenant (orientação impressa gerada por
`FraudProtectionTexts.pixReceiverGuidance`). A conformidade do recebedor
cadastrado no PSP com o cadastro do tenant é verificada por
`PixReceiverCheck` — divergência de nome ou CNPJ é não-conformidade nomeada.

## Golpe: alerta, denúncia e telemetria

- Alerta configurável no portal: `portal.scam_alert` (classe d,
  `{enabled, text}`), servido junto dos canais oficiais.
- Denúncia pelo portal: `POST /api/v1/public/documents/report-fraud`
  (canal, valor relatado e descrição) — registrada na V8_3 e levanta alerta
  na Central.
- Telemetria das verificações e denúncias por janela: aba Segurança da
  Central (`VerificationTelemetry`).

## E-mail

Política da P3.35 sobre a infraestrutura da P3.5: DMARC publicado do
domínio de envio do tenant em `p=reject`, remetente fixo por tenant e
domínio próprio (`msg.email_from_domain`) — conferência em
`EmailSenderPolicy`.
