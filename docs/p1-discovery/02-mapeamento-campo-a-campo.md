# Template 02 — Mapeamento campo a campo: legado → Ziip SAN (P1.1)

> **Aprovação do piloto: ⏳ PENDENTE — aguarda a decisão P0.30.**
> Aprovador: _em branco (preencher só após a P0.30)_ · Data: _em branco_ ·
> Evidência: _em branco_.
>
> Este template está EM BRANCO. A linha de exemplo está marcada
> `SINTÉTICO` e é fictícia. Origem: P1.1,
> [PLANO-v3.0.md](../plano-v3/PLANO-v3.0.md) (linha 627). Base da carga
> da P1.2 (importador declarativo versionado) e insumo do mapeamento à
> unidade consumidora da P1.3 (incluindo o CNAE — ver template 07).

## Mapeamento (uma linha por campo)

| Campo do legado | Tabela/arquivo do legado | Campo de destino no Ziip SAN | Transformação | Validação | Observações |
|---|---|---|---|---|---|
| _em branco_ | _em branco_ | _em branco_ | _em branco_ | _em branco_ | _em branco_ |
| `campo_exemplo` — SINTÉTICO | `TABELA_EXEMPLO` — SINTÉTICO | `consumer_units.registration_number` — SINTÉTICO (exemplo de destino, não mapeamento real) | nenhuma — SINTÉTICO | formato — SINTÉTICO | Linha de exemplo; apagar ao preencher com o legado real |

## Regras de preenchimento

- **Transformação tem de ser explícita e reversível onde for preciso.**
  "Igual" não é transformação: registrar formato, normalização (ex.:
  maiúsculas, remoção de máscara), conversão de tipo e regra de
  valores ausentes.
- **Sem dado pessoal real e sem matrícula real** — código substituto
  no lugar da matrícula, correspondência só no piloto (ver
  [roteiro de anonimização](../seguranca/roteiro-anonimizacao-piloto.md)).
- Todo campo do dicionário (template 01) aparece aqui ou na lista de
  campos sem correspondência do template 01, sem exceção.
- Campos de destino que o plano já nomeia (ex.: `registration_number`
  e `check_digit` no lugar de matrícula/DV, `municipality_ibge`,
  atividade econômica/CNAE na unidade — P1.3) devem ser usados com
  esses nomes; divergência de nome deve ser registrada, não corrigida
  em silêncio.

## Campos de destino sem origem no legado (a decidir na P1.2/P1.3)

| Campo de destino no Ziip SAN | Como será preenchido na carga | Decisão (após P0.30) |
|---|---|---|
| _em branco_ | _em branco_ | _em branco_ |

## Pendências da P0.30

- [ ] Piloto escolhido; dicionário (template 01) aprovado pelo piloto.
- [ ] Mapeamento preenchido a partir do legado real.
- [ ] Aprovação do piloto registrada no campo do cabeçalho.
