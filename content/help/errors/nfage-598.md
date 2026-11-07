---
code: "nfage:598"
title: "Namespace diferente do padrão da NFAg"
suggest_ticket: true
---

# Namespace diferente do padrão da NFAg

O XML usa um namespace diverso do namespace padrão da NFAg (http://www.portalfiscal.inf.br/nfag). Namespace diferente faz a SEFAZ não reconhecer a mensagem como NFAg-e.

## Causas prováveis

- O documento foi montado com o namespace de outro documento fiscal (por exemplo, o da NF-e).
- Houve transformação do XML fora do sistema antes do envio.

## O que fazer

- Reenvie o documento pela fila do sistema, que usa o namespace padrão da NFAg.
- Persistindo, abra um chamado: é falha de montagem do XML.
