# Template 04 — Checklist: optantes de débito automático e leiaute bancário (P1.1)

> **Aprovação do piloto: ⏳ PENDENTE — aguarda a decisão P0.30.**
> Aprovador: _em branco (preencher só após a P0.30)_ · Data: _em branco_ ·
> Evidência: _em branco_.
>
> Este template está EM BRANCO. Origem: P1.1,
> [PLANO-v3.0.md](../plano-v3/PLANO-v3.0.md) (linha 627): optantes de
> débito automático e leiaute bancário conferidos no legado contra o
> levantamento da P0.30, com a lista das autorizações a migrar (base da
> P3.24, se houver optantes). A P0.30 também levanta, antes, a
> existência e a quantidade de optantes e o leiaute em uso — esse
> levantamento prévio é, ele próprio, pendente da escolha do piloto.

## Checklist de conferência

- [ ] Quantidade de optantes levantada na P0.30: _em branco (P0.30
      pendente)_.
- [ ] Quantidade de optantes conferida no legado: _em branco_.
- [ ] Divergência entre P0.30 e legado explicada (se houver):
      _em branco_.
- [ ] Leiaute bancário em uso identificado (padrão e versão):
      _em branco_. Referência do plano para autorizações legadas:
      FEBRABAN 150 (registros B, C, E, F) — ver P3.24 e ED-27.
- [ ] Banco(s) do pagador e banco do recebedor por optante levantados:
      _em branco_. (A P3.24 só admite optantes cujo banco do pagador
      seja o mesmo do recebedor; a condição é levantada por optante.)
- [ ] Autorizações vigentes x canceladas x vencidas separadas:
      _em branco_.
- [ ] Lista das autorizações a migrar produzida (base da P3.24, se
      houver optantes): _em branco — ver seção abaixo_.
- [ ] Se NÃO houver optantes: registrado aqui e no diário, para que a
      P3.24 seja cancelada com registro (parte FEBRABAN 150 fica na
      ED-27), conforme o plano. ☐ Sem optantes (confirmado no legado)
      ☐ Com optantes (lista abaixo).

## Lista das autorizações a migrar (só códigos substitutos, nunca matrícula real)

| Código substituto da unidade | Banco do pagador | Banco do recebedor | Situação da autorização | Data da autorização | Observações |
|---|---|---|---|---|---|
| _em branco_ | _em branco_ | _em branco_ | _em branco_ | _em branco_ | _em branco_ |
| `SUBST-EXEMPLO-001` — SINTÉTICO | _exemplo_ — SINTÉTICO | _exemplo_ — SINTÉTICO | vigente — SINTÉTICO | _exemplo_ — SINTÉTICO | Linha de exemplo; apagar ao preencher |

## Regras de preenchimento

- **Sem dado pessoal real e sem matrícula real.** A correspondência
  código substituto ↔ matrícula real fica SÓ no piloto (ver
  [roteiro de anonimização](../seguranca/roteiro-anonimizacao-piloto.md)).
- Nenhuma autorização é migrada por este template: ele só levanta e
  confere. A migração é da P3.24, e só se houver optantes e se o
  parecer jurídico da RAS-06 mantiver o objeto (ver P3.24).
- Arquivos de teste do paralelo local (GL.2) nunca são enviados ao
  banco.

## Pendências da P0.30

- [ ] Piloto escolhido; levantamento P0.30 de optantes e leiaute.
- [ ] Conferência no legado e lista de autorizações a migrar.
- [ ] Aprovação do piloto registrada no campo do cabeçalho.
