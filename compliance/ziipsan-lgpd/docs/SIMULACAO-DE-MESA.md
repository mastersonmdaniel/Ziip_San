# Simulação de mesa — resposta a incidente (P3.10)

Roteiro exercitado sobre o código desta entrega (`IncidentClockTest`, relógio fixo) e sobre
os três modelos. Cenário fictício, sem dado real: vazamento simulado de planilha de
cobrança com documento e telefone de titulares.

## Roteiro e resultado exercitado

| Passo | Exercício | Resultado esperado/conferido |
|---|---|---|
| T0 | Conhecimento simulado em sex 2026-11-06 10:00 UTC; `IncidentClock.start(Clock.fixed, 48)` | Relógio inicia no conhecimento; prazo do aviso = 2026-11-08 10:00 UTC (teste `startsAtKnowledgeAndDeadline`) |
| T0+24 h | Avaliação de `dueCentralAlerts` | Alerta de 24 h devido e só ele (teste `dueCentralAlerts`) |
| T0+48 h | Nova avaliação sem aviso registrado | Alertas de 24 h e 48 h devidos; com o de 24 h emitido, só o de 48 h (idem) |
| Aviso | Registro do aviso ao controlador antes do prazo | `operatorNoticeOverdue` falso no instante exato do prazo e verdadeiro 1 s depois (teste `overdue`) |
| Escopo | Colunas simuladas classificadas (documento=ID, telefone=CT) | Registro recusa escopo vazio e exige `occurrenceId` da P2.25 (teste `incidentRequiresOccurrenceAndScope`) |
| ANPD | Conhecimento do controlador na sex 2026-11-06 | Comunicação até qua 2026-11-11; complementação até sex 2026-12-04; feriado em 11/11 empurra para 12/11 (teste `anpdBusinessDays`, datas conferidas à mão) |
| Modelos | Preenchimento simulado dos modelos ANPD e titulares a partir do registro | Campos do registro (ocorrência, escopo DAD-29, prazos) têm correspondência 1:1 nos itens 5, 7 e 10 do modelo ANPD e no bloco de categorias do aviso |
| RIPD | Abertura do RIPD do módulo de cobrança pelo modelo | Seções de vínculos apontam ocorrência e `processingRecordRef` (nulo até a P3.40) |
| Retenção | Fechamento simulado | `retainUntil` = 2031-11-06 (5 anos, `ret.incident_record_years`) (teste `retentionFiveYears`) |

## Limite honesto desta simulação

A simulação foi exercitada **nos testes unitários e na leitura cruzada dos modelos**
nesta entrega local. A simulação de mesa **com pessoas** (operador, tenant piloto e
encarregado, com ata) depende do piloto e fica registrada como pendência no relatório —
não é afirmada como realizada.
