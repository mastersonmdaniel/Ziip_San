---
code: "nfage:297"
title: "Assinatura difere do calculado"
suggest_ticket: true
---

# Assinatura difere do calculado

O valor da assinatura (SignatureValue) difere do valor calculado pela SEFAZ sobre o documento recebido: o conteúdo assinado não é exatamente o conteúdo transmitido. Em geral, algo no XML mudou depois da assinatura.

## Causas prováveis

- Um campo do documento foi alterado depois da assinatura (edição manual ou remontagem).
- Caracteres ou espaços foram modificados na transmissão, quebrando a integridade do XML assinado.

## O que fazer

- Nunca edite o documento depois de assinado: corrija os dados antes, assine de novo e reenvie.
- Se ninguém editou o documento, abra um chamado — pode ser falha de montagem/transmissão.
