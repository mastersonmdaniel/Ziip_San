# ED-77 — Motor de fichas de indicador (ISO 24510/24512, NR 9/ANA)

A ficha é uma visão sobre o registro único da P3.41 (JOR-19): definição,
variáveis com fonte, fórmula versionada, contexto, grau de confiança,
meta, fechamento e semáforo continuam na P3.41. A ED-77 acrescenta
arcabouço/nível, rateio por município com separação urbano/rural, a base
global versionada de domicílios do IBGE, o cálculo de IAA/ICA, a regra de
solução alternativa por agência (RAS-33), as metas de universalização com
o piso federal travado (Lei 11.445, art. 11-B), a apuração auditável da
regulação por incentivo (GES-30) e os temas manuais de confiança baixa
(GES-21, até a ED-90).

## O que NÃO está nesta entrega (honesto)
- Fichas das resoluções ARIS RN 57/2026, AGIR RN 025/2026 e ARIS RN 36/2022
  (RAS-07): perfis registrados com conteúdo `[A DEFINIR]` — variável,
  fórmula e unidade de cada ficha exigem o texto conferido da resolução
  (pendência P-ED77-01). Textos ISO 24510:2024/24512:2024 não são
  reproduzidos (licença; padrão ED-73/ED-76).
- Valores das metas de perdas da Portaria MCID 788/2024: parâmetro
  `loss.target_*` sem valor de fábrica, conferência antes da carga
  (P-ED77-03).
- Fatores Q, P e U: não presumidos (não encontrados na CP 03/2026 da ANA).
- Sentido da regra de solução alternativa por agência (RAS-33): perfis
  nascem `UNDECLARED` e o cálculo recusa até a declaração na integração
  a partir do texto de ARIS RN 47/2025, AGIR RN 024/2025 e ARESC 431/2026.
