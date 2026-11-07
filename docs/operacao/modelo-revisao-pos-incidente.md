# Modelo de revisão pós-incidente (sem atribuição de culpa) — P3.11

Princípios: foco no sistema, nunca na pessoa; fatos com horário; o que o
sistema permitiu que acontecesse importa mais do que quem executou.
Proibido: nomear culpado, usar o documento em avaliação de desempenho.

## 1. Identificação
- Incidente (id/data): / Severidade: / Serviços e tenants afetados: / Responsável pela revisão:

## 2. Linha do tempo (UTC e horário local do tenant)
| Hora | Fato | Fonte (log/alerta/métrica) |
|---|---|---|

## 3. Impacto
- Duração, SLO afetado e consumo do orçamento de erro (burn rate do período, calculado por `BurnRateCalculator`):

## 4. Causa e fatores contribuintes (sistêmicos)
## 5. O que funcionou / o que não funcionou (detecção, runbook, comunicação)
## 6. Ações de correção
| Ação | Dono | Prazo | Verificação |
|---|---|---|---|

## 7. Resumo publicável (página de status — P3.18)
> Texto curto, sem dado pessoal nem detalhe interno de segurança:
> o que foi afetado, por quanto tempo, o que foi feito e o que muda.
> Este bloco é o artefato que a P3.18 publica (`status_incident`/
> resumo); a revisão interna completa NÃO é publicada.
