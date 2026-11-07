---
code: "nfage:283"
title: "Erro na cadeia do certificado transmissor"
suggest_ticket: true
---

# Erro na cadeia do certificado transmissor

A cadeia de certificação do certificado transmissor não pôde ser validada pela SEFAZ: a autoridade certificadora emissora não está cadastrada, está revogada ou o certificado não foi assinado por ela. A conexão é recusada.

## Causas prováveis

- A cadeia de certificados (raiz e intermediárias) está incompleta ou desatualizada no ambiente emissor.
- O certificado foi emitido por autoridade não reconhecida no cadastro da SEFAZ.

## O que fazer

- Reinstale o certificado com a cadeia completa e atualizada da autoridade certificadora.
- Persistindo, abra um chamado: pode ser cadastro da AC junto à SEFAZ.
