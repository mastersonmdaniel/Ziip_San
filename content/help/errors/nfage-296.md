---
code: "nfage:296"
title: "Erro no acesso à LCR do certificado de assinatura"
suggest_ticket: true
---

# Erro no acesso à LCR do certificado de assinatura

A SEFAZ não conseguiu acessar a LCR (lista de certificados revogados) do certificado de assinatura — falta o endereço da lista no certificado ou houve erro no acesso. Sem confirmar que o certificado não foi revogado, a assinatura não é aceita.

## Causas prováveis

- O certificado não traz o endereço da LCR (CRL Distribution Point).
- Indisponibilidade temporária da SEFAZ ou da autoridade certificadora.

## O que fazer

- Aguarde alguns minutos e reenvie, sem trocar o documento.
- Se o erro for imediato e constante, pode ser o certificado sem endereço de LCR: fale com a autoridade certificadora e abra um chamado.
