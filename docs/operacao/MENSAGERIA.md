# Mensageria na borda da nuvem — e-mail (P3.5), SMS e WhatsApp (ED-41)

> Pacote LC.2 — Produtor, sessão `764b25ad-2ccf-40e5-afbf-986d42be0213`.
> Código correspondente: `infra/aws/modules/messaging/`.
> Correção (estágio 3), sessão `b36fd7a8-91ec-4322-9e7e-8bfe051efd54`:
> item 3 passou a declarar a ligação identidade→configuration set
> (R-3) e a obrigação da aplicação; item 4 deixou de citar a saída
> inexistente `ses_dkim_tokens` (menor da revisão). Ver
> `CORRECAO-LC.2.md`.

## E-mail — Amazon SES em `sa-east-1`, por tenant (P3.5, PLT-10)

O aceite da P3.5 fixou a fronteira: "provedor de e-mail decidido na LC.2,
com o recurso de tenants do Amazon SES em `sa-east-1` como referência".
**Decisão registrada nesta entrega:** o serviço de e-mail da AWS é o
**Amazon SES** (o aceite da LC.2 diz literalmente "serviço de e-mail da
AWS"; o SES é o serviço de e-mail da AWS e a referência nominal da P3.5).

Por tenant (item de `ses_tenants` no tfvars do ambiente):

1. **Identidade de domínio própria** do tenant
   (`msg.email_from_domain`, classe d, da P3.5) — nenhum tenant envia
   pelo domínio de outro nem pelo domínio da plataforma.
2. **MAIL FROM próprio** (`<mail_from_subdomain>.<domínio do tenant>`),
   para o SPF alinhar ao domínio do tenant.
3. **Configuration set próprio, ligado à identidade do próprio tenant**,
   com destino de eventos para um **tópico SNS próprio do tenant**:
   `SEND`, `DELIVERY`, `BOUNCE`, `COMPLAINT`, `REJECT`. A ligação é feita
   no recurso da identidade (`aws_sesv2_email_identity.tenant` declara
   `configuration_set_name` = configuration set do mesmo tenant —
   correção R-3), pelo que esse conjunto é o configuration set **por
   omissão** dos envios feitos a partir dessa identidade: os eventos
   fluem para o SNS do tenant sem depender de cada envio o nomear. São
   os eventos que a aplicação transforma em prova de entrega
   (`delivered`/`bounced`/`complained` da interface da P0.45,
   consumidos pela P3.5). **Obrigação restante da aplicação, precisa:**
   se um envio indicar explicitamente um configuration set (cabeçalho/
   parâmetro da API do SES), esse valor sobrepõe o por omissão — logo a
   aplicação só pode indicar, para um tenant, o configuration set desse
   tenant (saídas `ses_configuration_sets` /
   `ses_identity_configuration_sets` do módulo); indicar outro, ou um
   conjunto partilhado, quebra a prova de entregabilidade por tenant.
   Isolamento: reputação, métricas, pausa e supressão são operados por
   tenant na aplicação (`DeliveryStore` por tenant, P3.5); no provedor,
   configuration set e tópico já nascem separados por tenant.
4. **DNS no domínio do tenant (fora da zona desta IaC):** publicar os 3
   CNAMEs de DKIM do tenant, o TXT/MX do MAIL FROM (SPF) e o TXT
   `_dmarc` do tenant. Fonte dos tokens DKIM, com precisão (correção do
   menor que citava uma saída inexistente): o módulo **não** tem uma
   saída `ses_dkim_tokens` — os 3 tokens são lidos da identidade SES
   criada, no ato da aplicação, pela consola/API do SES
   (`GetEmailIdentity`), a partir da identidade identificada pela saída
   real `ses_identity_arns`; as saídas reais do módulo são
   `ses_configuration_sets`, `ses_event_topics`, `ses_identity_arns`,
   `ses_identity_configuration_sets` e `messaging_tenant_refs`. A IaC
   **não** publica DNS que não controla; a conferência de alinhamento
   SPF/DKIM/DMARC é a guarda `assertDomainAligned` da P3.5 antes de cada
   envio, e a taxa de reclamação < 0,10% (Gmail/Yahoo/Microsoft) é regra
   da mesma tarefa.
5. **Limite declarado (desvio D-2 do relatório):** a lista de supressão
   do SES é por conta, não por tenant; o isolamento de supressão por
   tenant exigido pela P3.5 vive na aplicação (`msg.suppression_policy`),
   não no provedor. Registrado para não prometer o que o provedor não
   separa.

## SMS e WhatsApp — custo por categoria (ED-41)

- **SMS:** saída pelo Amazon SNS; o remetente autenticado do tenant é a
  referência `msg.sms_sender_id` (chave proposta na ED-41), resolvida
  pela aplicação contra o catálogo do tenant. Nenhum número, credencial
  ou valor é declarado na IaC.
- **WhatsApp:** a AWS não é o provedor de WhatsApp da ED-41 — a conta é
  **do tenant** (`msg.whatsapp_account_id`, referência). A IaC carrega
  só as referências de `messaging_tenants`, expostas na saída
  `messaging_tenant_refs`; **nenhum recurso de WhatsApp, e nenhum
  recurso de alarme de gasto, é criado** pelo módulo (ver o fim de
  `modules/messaging/main.tf`). Provedor/BSP concreto: `[A DEFINIR]`
  (P-LC2-06).
- **Custo por categoria de modelo:** cada modelo de WhatsApp é
  classificado em `msg.template_category_map` como `MARKETING`,
  `UTILITY` ou `AUTHENTICATION` (ED-41), com estimativa antes do envio,
  teto mensal por tenant (`msg.monthly_cap`, alertas a 80% e 100%) e
  preço unitário vindo do contrato do tenant — **nenhum preço é fixado
  na IaC nem na aplicação** (`ChannelPricing`: sem preço vigente, o
  disparo é recusado). Na nuvem, o módulo expõe o tópico de alertas de
  mensageria do ambiente; o alarme fino por tenant/categoria é regra da
  aplicação (ED-41), não da IaC — fronteira declarada.
