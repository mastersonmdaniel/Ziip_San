# Revisão pós-incidente em produção (AWS) — modelo e registro (LC.7)

Produtor: sessão `92031a19-4d6d-4174-92b7-164897d85503` (papel: Produtor).

Aceite LC.7: "revisão pós-incidente registrada", dentro da prática da
P3.11 (PLT-23). O modelo abaixo **reaplica sem afrouxar** o
`modelo-revisao-pos-incidente.md` da P3.11 (sem atribuição de culpa,
com resumo publicável para a página da P3.18), acrescentando apenas os
campos de contexto de produção/AWS. A revisão interna completa **não**
é publicada; publica-se só o bloco §7, pelo mecanismo da P3.18.

## Modelo (copiar por incidente/exercício)

### 1. Identificação
- Incidente (id/data): / Severidade: / Serviços e tenants afetados: /
  Responsável pela revisão:
- Contexto de produção: ambiente AWS (LC.2), exercício provocado ou
  incidente real, id do evento de alerta no serviço gerenciado:

### 2. Linha do tempo (UTC e horário local do tenant)
| Hora | Fato | Fonte (log/alerta/métrica) |
|---|---|---|

### 3. Impacto
- Duração, SLO afetado e consumo do orçamento de erro (burn rate do
  período, calculado por `BurnRateCalculator` da P3.11):

### 4. Causa e fatores contribuintes (sistêmicos)
### 5. O que funcionou / o que não funcionou (detecção, runbook, comunicação, página de status)
### 6. Ações de correção
| Ação | Dono | Prazo | Verificação |
|---|---|---|---|

### 7. Resumo publicável (página de status — P3.18)
> Texto curto, sem dado pessoal nem detalhe interno de segurança:
> o que foi afetado, por quanto tempo, o que foi feito e o que muda.
> Publicado via `PostIncidentSummary` da P3.18, só de incidente
> `RESOLVED`. Exercício sem impacto visível: registrar aqui
> "sem resumo público — exercício sem impacto" em vez de publicar.

## Registro de revisões de produção

> Vazio de propósito na entrega: nenhuma revisão existiu ainda
> (produção não lançada; exercício da LC.7 não executado — P-LC7-08).
> Cada revisão real acrescenta uma linha com data, id do incidente e
> onde o documento completo ficou guardado.

| Data | Incidente/exercício | Severidade | Resumo publicado na página? | Documento completo |
|---|---|---|---|---|
| _(a preencher na 1ª revisão real)_ | | | | |
