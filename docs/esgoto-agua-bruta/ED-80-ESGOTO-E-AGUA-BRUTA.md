# ED-80 — Pacote de esgoto e água bruta

Pacote próprio (`br.com.ziipsan.core.sewerraw`, módulo
`shared/ziipsan-core`), separado da qualidade da água (ED-50).

## O que consome, sem duplicar

- **ED-105**: catálogo de padrão de efluente (`EffluentCatalog`),
  automonitoramento (resultados médios declarados sobre ele) e
  eficiência de DBO (`BodEfficiency.Result`) alimentam a Declaração
  de Carga Poluidora; o lodo (`SludgeRecord`) alimenta o biossólido.
- **ED-50**: qualidade operacional lida pela porta
  `RawWaterQualityPort` (linhas do fechamento mensal dela).
- **ED-79**: a captação referencia o sistema de abastecimento
  (`systemCode`, `SourceKind`); manancial declarado vive lá.
- **P3.31**: a ligação outorga/licença ancora-se num requisito
  legal registrado (`LegalRequirement`).

## Água bruta

Captação ligada a outorga (Lei 9.433/1997; Resolução ANA 236/2024
para rios de domínio da União) e a licença ambiental, ambas com
vigência e condicionantes declaradas do documento real. Alerta de
vencimento usa a janela do parâmetro do tenant
`sewerraw.expiry_alert_window_days` — nenhum prazo de fábrica.
Condicionante sem cumprimento declarado conta como pendente e
bloqueia a operabilidade.

## DCP

Gerada do automonitoramento da ED-105. Portão: só sai depois da
confirmação, conferida no catálogo, da publicação da norma que
substitui a CONAMA 430/2011 (em revisão) e com padrão revisado
vigente no catálogo para cada parâmetro. Carga (kg) = concentração
média (mg/L) × volume (m³) / 1000 (identidade dimensional).

## Biossólido

Produzido do lodo despachado/fechado da ED-105. Critérios da
CONAMA 498/2020 são declarados pelo responsável técnico (chave,
descrição, referência, avaliação com evidência) — nenhum critério,
limite ou classificação é enumerado pelo código [SEM FONTE para
valores]. Critério sem avaliação ⇒ lote INDETERMINATE.

## Reúso

Não codificado: o `ReuseGate` só registra prontidão. Sem a
confirmação de publicação da resolução conjunta CNRH/CONAMA
aprovada em 01/09/2026 no catálogo, o estado é NOT_CODIFIED e
autorizar reúso é recusado; CNRH 54/2005 e 121/2010 (em revisão)
ficam como referências anteriores.
