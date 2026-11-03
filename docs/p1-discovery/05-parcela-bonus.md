# Template 05 — Checklist: parcela bônus (P1.1)

> **Aprovação do piloto: ⏳ PENDENTE — aguarda a decisão P0.30.**
> Aprovador: _em branco (preencher só após a P0.30)_ · Data: _em branco_ ·
> Evidência: _em branco_.
>
> Este template está EM BRANCO. Origem: P1.1,
> [PLANO-v3.0.md](../plano-v3/PLANO-v3.0.md) (linha 627): verificado se
> o piloto usa parcela bônus (perdão de juros na antecipação;
> prestação perdoada a cada três contas pagas em dia por resolução
> especial; 20c COB-36). Referência de levantamento: COB-36 em
> `docs/levantamento/gsan/20c-gsan-regras-arrecadacao-e-cobranca.md`.

## Perguntas a verificar NO LEGADO (não no levantamento GSAN)

O levantamento GSAN descreve dois mecanismos; o que vale para a P1.1
é o que o PILOTO usa, verificado no legado dele. Marcar cada mecanismo
separadamente — um pode existir sem o outro.

### Mecanismo 1 — Perdão de juros na antecipação

- [ ] O piloto usa perdão de juros quando prestações são antecipadas
      pelo documento de cobrança? ☐ Sim ☐ Não ☐ Não verificado
      (aguarda P0.30).
- [ ] Se sim, regra exata no legado (quais juros deixam de ser
      cobrados, em que condição): _em branco_.
- [ ] Quitação do débito de parcelamento de contas cancela os juros
      restantes? ☐ Sim ☐ Não ☐ Não verificado.

### Mecanismo 2 — Prestação perdoada a cada três contas pagas em dia (resolução especial)

- [ ] O piloto usa prestação perdoada a cada três contas pagas em dia
      por resolução especial? ☐ Sim ☐ Não ☐ Não verificado
      (aguarda P0.30).
- [ ] Resolução especial (identificação, sem inventar número):
      _em branco_.
- [ ] Tolerância de atraso aceita como "em dia" (dias):
      _em branco_.
- [ ] Contagem: a cada quantas consecutivas, quantas prestações
      perdoadas: _em branco_.
- [ ] Atraso ou falta de pagamento zera a contagem? ☐ Sim ☐ Não
      ☐ Não verificado.
- [ ] Janela de atuação do lote (ex.: até o número de prestações mais
      2 meses, se for o caso no legado): _em branco_.
- [ ] O perdão atinge débito e crédito do parcelamento, ou só um
      deles? _em branco_.

## Decisão decorrente (só após verificação e aprovação do piloto)

- Se o piloto usa parcela bônus, ela entra como OPÇÃO do parâmetro
  `inst.bonus_installments`, **desligada por padrão, nunca como regra
  fixa** (ver
  [PARAMETROS-DO-TENANT.md](../arquitetura/PARAMETROS-DO-TENANT.md)).
- Se o piloto NÃO usa, o parâmetro permanece desligado e este
  template registra o "não usa" verificado — o que NÃO autoriza
  implementar a regra por conta do levantamento GSAN.
- Este template NÃO liga, NÃO configura e NÃO implementa parâmetro
  nenhum; a implementação, se houver, é da P4.1.

| Decisão | Valor (só após P0.30) |
|---|---|
| Piloto usa parcela bônus? | ⏳ PENDENTE (P0.30) |
| Mecanismo(s) confirmado(s) | _em branco_ |
| `inst.bonus_installments` | desligado por padrão, em qualquer caso |

## Pendências da P0.30

- [ ] Piloto escolhido e acesso ao legado autorizado.
- [ ] Ambos os mecanismos verificados no legado (sim/não, com regra).
- [ ] Aprovação do piloto registrada no campo do cabeçalho.
