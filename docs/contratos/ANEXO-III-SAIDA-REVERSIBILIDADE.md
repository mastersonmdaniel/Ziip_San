# Anexo III — Saída, exportação, consulta pós-contrato e eliminação

> Fontes: **P3.16** (ensaio de saída de tenant sintético, core `V10_5`),
> **P3.19** (tabela de temporalidade/listagem — RAS-18), ADR-0015, ADR-0018,
> ADR-0025, RNF-42. Todos os prazos abaixo **estão fixados nas fontes** —
> nenhum marcador de indefinição foi necessário neste anexo, exceto onde
> indicado. A redação contratual continua sendo da P3.9; aqui vai a
> **tabela normativa** que a cláusula de saída incorpora por remissão.

## 1. Tabela de prazos da saída (nível Setor Público × padrão)

| Etapa | Nível Setor Público | Nível padrão | Fonte |
|---|---|---|---|
| Exportação completa | até **10 dias úteis** | até **15 dias** | P3.16; ADR-0025 |
| Formatos da exportação | CSV, JSON ou XML, com **manifesto, hash e dicionário de dados** gerado do schema | idem | P3.16 |
| Consulta somente leitura pós-contrato | pelo **prazo do contrato, no máximo 120 dias**, **antes** da carência; escrita **recusada** | não prevista no aceite da P3.16 para o padrão | P3.16; core `V10_5`; ADR-0025 |
| Carência | **30 dias**, contados do fim do contrato (no Setor Público, do **fim da consulta**) | 30 dias do fim do contrato | P3.16 |
| Resposta a contestação na carência | até **15 dias**, com alerta na Central antes do vencimento | idem | P3.16; ADR-0019 |
| Certificado de eliminação | até **30 dias** após o fim da carência ou da resposta à última contestação (o que ocorrer depois); **máximo de 75 dias** com contestação (**60 dias** sem contestação), contados do **início da carência** | idem | P3.16 |

> **Âncora do teto do certificado (leitura declarada da P3.16,
> `TenantExitCalculator`):** o teto de 75/60 dias conta do **início da
> carência** (`graceAnchor`), não do fim do contrato. A âncora é: no
> nível padrão, o **fim do contrato**; no Setor Público, o **fim da
> consulta somente leitura** (que pode chegar a 120 dias após o fim do
> contrato) — é de onde a carência conta neste nível. Sem essa leitura,
> o teto seria inalcançável no Setor Público sempre que houvesse
> consulta.

## 2. Regras da eliminação (P3.16)

- Eliminação **agendada** só por `PLATFORM_OWNER`, **com MFA**, e
  **cancelável durante a carência**; execução **antes** do fim da carência é
  **recusada**.
- **Contestação** registrada na carência **suspende** a execução.
- **Ordem judicial** registrada **suspende** a execução enquanto vigorar.
- Método: **crypto-shredding** (destruição da chave do tenant).
- O **certificado** emitido contém: hashes, **expiração de backups** e de
  **cópias imutáveis** e a **exceção dos chamados** (retenção de vigência +
  5 anos — Anexo II §3).
- Durante a carência: **download da exportação** e **contestação** são
  registrados.

## 3. Tenant de natureza pública (P3.16 + P3.19)

- Para tenant público, a eliminação usa a **tabela de temporalidade** e a
  **listagem de eliminação** da P3.19 (RAS-18) e é **recusada sem elas**.
- No nível Setor Público, é este portão que liga a saída ao regime
  arquivístico do contratante público. O conteúdo da tabela de
  temporalidade é **do tenant** (aprovado por ele), não deste modelo:
  **[A DEFINIR NA P3.9]** apenas quanto à redação da remissão contratual.

## 4. Instância dedicada e reversão (fronteira)

- Instância dedicada para quem exigir em contrato: **LC.15** (depois da
  LC.2) — ADR-0024. Não é cláusula deste modelo até a LC.15 existir.
- Pacote de reversão em contêiner: **ED-04** (onda B). Este anexo não promete
  o pacote; promete a **exportação + consulta + certificado** da P3.16.
