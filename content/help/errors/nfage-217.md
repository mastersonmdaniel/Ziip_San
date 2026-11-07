---
code: "nfage:217"
title: "Documento não consta na SEFAZ"
suggest_ticket: false
---

# Documento não consta na SEFAZ

A SEFAZ não encontrou o documento na sua base. Consultas, cancelamentos e referências a esse documento não são aceitos enquanto ele não constar.

## Causas prováveis

- O documento nunca foi autorizado (foi rejeitado ou ficou só no sistema).
- A chave consultada está errada.

## O que fazer

- Confira a chave e o estado do documento na fila de documentos fiscais.
- Se o documento nunca foi autorizado, emita-o novamente pela fila, em vez de referenciá-lo.
