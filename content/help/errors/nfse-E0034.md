---
code: "nfse:E0034"
title: "Chave de nota rejeitada indevida"
suggest_ticket: false
---

# Chave de nota rejeitada indevida

A chave de acesso de NFS-e rejeitada só pode ser informada quando quem emite é o tomador ou o intermediário e o motivo da emissão é a rejeição anterior. Fora desse caso, o campo deve ficar vazio.

## Causas prováveis

- A chave da nota rejeitada foi preenchida numa emissão do prestador.
- O motivo da emissão não é o de rejeição.

## O que fazer

- Remova a chave referenciada ou ajuste emitente e motivo para o caso de reemissão por rejeição.
- Na emissão comum do prestador, emita a DPS nova sem referência à rejeitada.
