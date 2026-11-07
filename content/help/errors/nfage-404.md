---
code: "nfage:404"
title: "Prefixo de namespace não permitido"
suggest_ticket: true
---

# Prefixo de namespace não permitido

O XML usa prefixo de namespace, o que o leiaute da NFAg-e não permite: os elementos devem estar no namespace padrão do projeto, sem prefixos. A SEFAZ rejeita na validação da área de dados.

## Causas prováveis

- O XML foi montado por componente que prefixa os elementos do namespace.
- O documento foi transformado/editado fora do sistema antes do envio.

## O que fazer

- Reenvie o documento pela fila do sistema, sem transformação intermediária do XML.
- Persistindo, abra um chamado: é falha de montagem do XML.
