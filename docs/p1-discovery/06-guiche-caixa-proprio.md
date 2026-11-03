# Template 06 — Checklist: guichê de caixa próprio (PD-07) (P1.1)

> **Aprovação do piloto: ⏳ PENDENTE — aguarda a decisão P0.30.**
> Aprovador: _em branco (preencher só após a P0.30)_ · Data: _em branco_ ·
> Evidência: _em branco_.
>
> Este template está EM BRANCO. Origem: P1.1,
> [PLANO-v3.0.md](../plano-v3/PLANO-v3.0.md) (linha 627), e decisão
> PD-07: verificado se o piloto mantém guichê de caixa próprio, resposta
> que decide se o item condicional de caixa da ED-26 é ligado por
> parâmetro (JOR-13). O parâmetro é `pay.own_cashier_enabled`,
> **desligado por padrão** (ver
> [PARAMETROS-DO-TENANT.md](../arquitetura/PARAMETROS-DO-TENANT.md)).

## Pergunta central (uma só, com evidência)

- [ ] O piloto mantém guichê de caixa próprio (caixa do prestador, com
      recebimento no balcão)? ☐ Sim ☐ Não ☐ Não verificado
      (aguarda P0.30).
- Evidência no legado / documento do piloto: _em branco_.

## Se a resposta for SIM, levantar (sem decidir nada aqui)

- [ ] Formas de recebimento no guichê (espécie, cartão, outras):
      _em branco_.
- [ ] Rotina de abertura e fechamento de caixa por operador:
      _em branco_.
- [ ] Sangria e conferência do fechamento: _em branco_.
- [ ] Comprovante entregue ao pagador: _em branco_.
- [ ] Classificação da baixa (mesma da P3.1?): _em branco_.
- [ ] Quantidade de guichês / postos: _em branco_.
- [ ] Volume de recebimentos pelo guichê (contagem, não valores
      individuais): _em branco_.

## Se a resposta for NÃO

- Registrar o "não mantém" verificado, com evidência. O item
  condicional de caixa da ED-26 permanece desligado; menu, permissões
  e rotas do caixa ficam indisponíveis com o parâmetro desligado.
- "Não verificado" NÃO é "não mantém": sem verificação no legado e
  sem aprovação do piloto, a resposta continua pendente.

## Decisão decorrente (só após verificação e aprovação do piloto)

| Decisão | Valor (só após P0.30) |
|---|---|
| Piloto mantém guichê próprio? | ⏳ PENDENTE (P0.30) |
| `pay.own_cashier_enabled` | desligado por padrão; ligado só se a P1.1 confirmar guichê (PD-07) |

Este template NÃO liga o parâmetro; a entrega do caixa, se
confirmado, é o item condicional da ED-26.

## Pendências da P0.30

- [ ] Piloto escolhido; levantamento P0.30 sobre guichê próprio.
- [ ] Resposta verificada no legado, com evidência.
- [ ] Aprovação do piloto registrada no campo do cabeçalho.
