# Trust Center e divulgação responsável — P3.38

Artefatos desta tarefa:

- **Dados:** core `V10_6` — `support.vulnerability_report` +
  `support.vulnerability_report_event` (trilha) e
  `platform.subprocessor`.
- **Lógica:** pacote `br.com.ziipsan.platform.trustcenter` em
  `platform/ziipsan-platform` — `DisclosurePolicy` (canal/prazos como
  configuração), `SecurityTxt` (gerador/validador RFC 9116),
  `VulnerabilityReport`/`VulnerabilityReportFlow` (fluxo registrado),
  `Subprocessor` (regra das cláusulas-padrão da P3.9 e aviso de
  mudança) e `TrustCenterService` (visão pública: fato com fonte ou
  pendência declarada).
- **Endpoint público:** `GET /api/v1/public/trust-center`
  (`PublicTrustCenterController`), JSON somente via classe `Json` do
  core.
- **Páginas (esta pasta):** `index.html` (Trust Center, só exibe o JSON
  do endpoint), `politica-divulgacao-responsavel.md` (política pública),
  `fluxo-relato-vulnerabilidade.md` (fluxo interno),
  `privacidade-e-retencao.md`, `questionario-seguranca-respostas.md` e
  `.well-known/security.txt` (**modelo** — ver abaixo).

## Teste no ambiente local

1. Aplicar as migrations core; a `V10_6` cria as tabelas vazias —
   nenhum suboperador é semeado.
2. Exercitar o fluxo pelos testes unitários: `SecurityTxtTest`,
   `VulnerabilityDisclosureFlowTest` (o teste de ponta a ponta com o
   relato **fictício** do aceite), `SubprocessorTest` e
   `TrustCenterServiceTest`.
3. Com a fiação da integração (abaixo), cadastrar um suboperador
   nacional e conferir a lista no endpoint e na página; cadastrar um
   estrangeiro sem cláusulas-padrão e conferir que ele NÃO aparece na
   visão pública e que o `CHECK` da `V10_6` recusa o INSERT público
   equivalente.

## Publicação do security.txt (quando houver decisão do dono)

O `.well-known/security.txt` desta pasta é um **modelo com
marcadores**: o canal oficial e o prazo de confirmação não constam da
spec nem dos ADR-0020/0021 e não foram inventados. Com a decisão:

1. configurar a `DisclosurePolicy` (canal, URL da política publicada,
   prazo de confirmação; canônica quando a LC.7 definir o domínio);
2. gerar o arquivo final com `SecurityTxt.render(...)` — ele recusa
   política não publicável e `Expires` fora da janela da RFC 9116;
3. publicar em `/.well-known/security.txt` do domínio da hospedagem
   separada (LC.7), junto das páginas de status e de confiança.

## O que NÃO é desta tarefa (fronteiras)

- **Hospedagem separada da produção: LC.7** (a mesma da P3.18). Aqui as
  páginas são artefatos estáticos + endpoint; nada foi publicado.
- **Lista real de suboperadores: P3.9.** As minutas ainda não foram
  aprovadas pelo piloto (decisão do dono de 07/11/2026: produzir na
  onda da P3.9); a estrutura, as regras e a página estão prontas.
- **Resumo do pentest: LC.8** (pentest externo sobre a produção na
  AWS, antes do primeiro dado real). A seção existe e se declara
  pendente até lá.
- **Evidências do ASVS: P3.8** (matriz requisito/teste/evidência gerada
  no CI). O critério (ASVS 5.0, L2 geral/L3 em autenticação, pagamento
  e dados pessoais) é declarado; a evidência entra quando a fonte a
  entregar via `TrustCenterSource`.
- **Encarregado do tenant: P3.40** (portal, fonte `lgpd.dpo_contact`).
  O Trust Center trata do encarregado da PLATAFORMA, pendente de
  registro.
- **Fiação pendente (declarada, não fingida):** implementação JDBC de
  `TrustCenterSource` (suboperadores, relatório de certificações do kit
  da P0.44, resumo da LC.8, encarregado, política configurada), a
  liberação da rota `/api/v1/public/trust-center` sem autenticação e o
  cadastro operacional dos suboperadores após a P3.9.
