# Template 08 — Lista de parcelamentos ativos e prestações a vencer (P1.1)

> **Aprovação do piloto: ⏳ PENDENTE — aguarda a decisão P0.30.**
> Aprovador: _em branco (preencher só após a P0.30)_ · Data: _em branco_ ·
> Evidência: _em branco_.
>
> Este template está EM BRANCO. Nenhuma linha abaixo é parcelamento
> real: a linha de exemplo está marcada `SINTÉTICO` e é fictícia.
> Origem: P1.1, [PLANO-v3.0.md](../plano-v3/PLANO-v3.0.md) (linha 627):
> lista dos parcelamentos ativos e das prestações a vencer, base da
> carga da P1.2 e da P2.10. Na P1.2, as prestações a vencer dos
> parcelamentos ativos do piloto são mapeadas e levadas ao staging,
> com referência ao acordo do legado.

## Totais (resumo da lista; contagens, não dados individuais)

| Total | Valor |
|---|---|
| Parcelamentos ativos | _em branco_ |
| Prestações a vencer (total) | _em branco_ |
| Data de referência da lista | _em branco_ |
| Fonte / consulta usada no legado | _em branco_ |

## Lista de parcelamentos ativos (uma linha por acordo)

| Referência do acordo no legado | Código substituto da unidade | Data do acordo | Nº de prestações (total / a vencer) | Situação | Referência para a P2.10 |
|---|---|---|---|---|---|
| _em branco_ | _em branco_ | _em branco_ | _em branco_ | _em branco_ | _em branco_ |
| `ACORDO-EXEMPLO` — SINTÉTICO | `SUBST-EXEMPLO-001` — SINTÉTICO | _exemplo_ — SINTÉTICO | 0 / 0 — SINTÉTICO | ativo — SINTÉTICO | _exemplo_ — SINTÉTICO |

## Prestações a vencer (uma linha por prestação)

| Referência do acordo no legado | Nº da prestação | Vencimento | Situação | Observações |
|---|---|---|---|---|
| _em branco_ | _em branco_ | _em branco_ | _em branco_ | _em branco_ |
| `ACORDO-EXEMPLO` — SINTÉTICO | 1 — SINTÉTICO | _exemplo_ — SINTÉTICO | a vencer — SINTÉTICO | Linha de exemplo; apagar ao preencher |

## Regras de preenchimento

- **Sem valores monetários individuais e sem dado pessoal.** Esta
  lista de discovery registra acordos, prestações e vencimentos; valores
  de saldo e de prestação entram na carga da P1.2/P2.10, não aqui, e
  nunca com dado pessoal real.
- **Sem matrícula real:** código substituto, correspondência só no
  piloto (ver
  [roteiro de anonimização](../seguranca/roteiro-anonimizacao-piloto.md)).
- Critério de "ativo": registrar o critério usado no legado (não
  presumir): _em branco_.
- Parcelamentos encerrados, cancelados ou quitados NÃO entram na
  lista; se o legado não distingue, registrar a limitação aqui:
  _em branco_.
- A soma das prestações a vencer por acordo tem de bater com o total
  do resumo; divergência explicada, não ajustada.

## Pendências da P0.30

- [ ] Piloto escolhido e acesso ao legado autorizado.
- [ ] Lista levantada do legado real, com data de referência e fonte.
- [ ] Aprovação do piloto registrada no campo do cabeçalho.
