# ED-92 — Ocorrência de rede como entidade (OPE-16)

Entidade única `network_incidents` no core (`br.com.ziipsan.core.incident`):
tipos/origens/situação SÓ do aceite; programada da ED-35 como subtipo
(`planned`+`PLANNED`+vínculo); área do início calculada pela ED-54 e
congelada em retrato; causas SÓ do parâmetro `outage.cause_catalog` do
tenant (única com fonte: `ALAGAMENTO`, da ED-64 — reconciliação no
relatório); limiares 6h (I-04) e 24h (emergência) com padrão do aceite e
regra só-reduz; crédito RAS-29 desligado por padrão e sem fórmula de
fábrica; achado de vazamento (DAD-27) sem ciclo de reparo.
Verificador do alvo `NETWORK_INCIDENT` da P1.31 registrado por
`NetworkIncidentTargetRegistration` (sqlVerifier sobre a tabela nova).
