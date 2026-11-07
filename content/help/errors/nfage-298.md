---
code: "nfage:298"
title: "Assinatura fora do padrão do Projeto"
suggest_ticket: true
---

# Assinatura fora do padrão do Projeto

A assinatura difere do padrão do Projeto NFAg-e: falta a referência ao atributo Id assinado ou faltam os algoritmos de transformação previstos (C14N e Enveloped). A assinatura existe, mas não está no formato que o leiaute exige.

## Causas prováveis

- A assinatura foi gerada por componente fora do padrão XML-DSig adotado pelo projeto.
- A referência (Reference URI) ao Id do documento não foi incluída na assinatura.

## O que fazer

- Reenvie o documento pela fila do sistema, que assina no padrão vigente.
- Se persistir, abra um chamado: é falha do componente de assinatura, não do dado da conta.
