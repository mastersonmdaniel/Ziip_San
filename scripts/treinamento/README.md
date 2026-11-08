# Scripts de treinamento — ED-11

## `validar-ambiente.py`

Valida o manifesto de um tenant de pré-treinamento contra a mesma
régua do produto (`PreTrainingTenant`, módulo da plataforma): origem
massa sintética ou clone mascarado da ED-09 com finalidade `TRAINING`,
marca de sintético, varredura da P0.45 em zero, fiscal de produção
desligado e, no clone, o portão da ED-09 (RIPD anterior e viável).
Massa sintética com bloco de clone associado é recusada (massa
sintética não tem clone), e a avaliação do RIPD precisa informar o
avaliador (`assessedBy`, campo não-nulo do record
`ReidentificationAssessment` da ED-09) — o campo consta do schema do
manifesto e é conferido pelo validador.

```bash
python3 validar-ambiente.py manifesto.json
# {"pronto": true, "bloqueadores": []}   (exit 0)
# {"pronto": false, "bloqueadores": [...]} (exit 2)
```

O formato do manifesto está em
`docs/treinamento/MANIFESTO-PRE-TREINAMENTO.template.json` e o schema,
em `docs/treinamento/manifesto-pre-treinamento.schema.json`.

Provisionar o tenant (criar schema, gerar massa, executar o clone e
rodar a varredura no banco) é da integração — este script só valida o
manifesto do ambiente já provisionado, como o validador de manifesto
da GL.1. Nenhum tenant é criado por ele.

## Testes

```bash
python3 -m unittest discover -s testes
```
