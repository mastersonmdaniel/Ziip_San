# Assinatura eletronica — P3.34

Servico de assinatura dos documentos do consumidor e do tenant, nos tres
niveis da Lei 14.063/2020 (art. 4o), modulo `platform/ziipsan-platform`,
pacote `br.com.ziipsan.platform.signing`, migration tenant `V1_14`.
(Inclui as correcoes D1–D10 da revisao cruzada — ver `CORRECAO-P3.34.md`.)

## Como assinar

1. O tenant registra os parametros classe d (P0.19): `sign.min_level.<tipo>`
   (SIMPLE, ADVANCED ou QUALIFIED) para cada tipo que vai assinar. Sem nivel
   minimo registrado, o servico recusa — a definicao e de cada ente.
2. A chamada HTTP (`POST /signatures`) exige principal autenticado: o TENANT
   vem do contexto autenticado da requisicao (`TenantContextHolder`) e o
   SIGNATARIO do `sub` do token — nunca do corpo. Corpo com `tenantId` ou
   `signerSubject` divergente do contexto e recusado (403).
3. Nivel SIMPLE: exige aceite previo no `platform.document_acceptance` da
   P1.24, localizado pela CHAVE REAL da tabela (V9_5): `user_id` do
   signatario + `document_code` + `document_version` do aceite + hash do
   texto (data, IP e autenticacao conferidos na evidencia).
4. Nivel ADVANCED: selo do provedor configurado na integracao, registrado
   SO na evidencia (`psc_provider` e coluna exclusiva da qualificada). O
   gov.br nao e caminho: qualquer identificador normalizado que contenha
   `govbr` e recusado, antes do selo quando a identidade ja e conhecida.
5. Nivel QUALIFIED: exige `sign.psc_provider` (PSC credenciado, certificado
   em nuvem, PAdES, politica declarada conforme o DOC-ICP-15.03) e
   `sign.timestamp_authority` (ACT credenciada, carimbo RFC 3161). O retorno
   e amarrado ao pedido: certificado do signatario, hash do conteudo
   assinado, messageImprint do carimbo e instante do carimbo conferidos.
   PSC/ACT reais sao homologacao externa — ver relatorio da tarefa.

## Codigo de verificacao e verificacao publica

O codigo de verificacao tem 80 bits de entropia (CSPRNG, 16 simbolos
Crockford agrupados — `VerificationCodes`, desenho da P3.35): a fiacao dos
signers injeta `VerificationCodes::generate` e os signers recusam codigo
fora desse formato. O documento impresso traz o codigo (e o QR com a URL
`<base da implantacao>/validar-assinatura/<codigo>`).
`GET /public/signature-validations/{codigo}` devolve o desfecho, o nivel, o
signatario MASCARADO, o instante, o hash e o link do VALIDAR do ITI
(https://validar.iti.gov.br/). As consultas sao limitadas por codigo e por
origem (fabrica das irmas do portal: 10/codigo e 30/origem em 15 min;
estouro = 429) e cada uma fica registrada em
`signature_verification_attempts`. A origem so e confiavel atras de proxy
que sobrescreva o `X-Forwarded-For` — requisito da implantacao.

**Integridade do registro — alegacao exata:** o `verification_digest` e um
CHECKSUM (SHA-256 sem segredo sobre dados publicos). Ele detecta corrupcao
e reescrita inconsistente, e NAO protege contra adulteracao por quem tem
escrita na tabela (o checksum e recomputavel — ha teste provando). A
protecao real (HMAC com segredo fora da base ou assinatura do digest) nao
esta na spec da P3.34 e e pendencia nomeada no relatorio.

## Documentos de entrega ao tenant

DPA e contrato SaaS so usam o servico com a decisao do dono registrada COM
PROVENIENCIA (`SigningParameters.TenantDeliveryDecision`: quem decidiu,
quando, referencia do registro — lida do registro do dono da plataforma,
nunca de parametro editavel pelo tenant); sem decisao habilitadora, recusa
403 explicita.
