---
code: "nfage:293"
title: "Erro na cadeia do certificado de assinatura"
suggest_ticket: true
---

# Erro na cadeia do certificado de assinatura

A cadeia de certificação do certificado de assinatura não pôde ser validada: autoridade emissora não cadastrada na SEFAZ, certificado da AC revogado ou assinatura da AC não confere.

## Causas prováveis

- A cadeia (raiz e intermediárias) do certificado de assinatura está incompleta ou desatualizada.
- O certificado veio de autoridade não reconhecida pela SEFAZ.

## O que fazer

- Reinstale o certificado de assinatura com a cadeia completa e atualizada.
- Persistindo, abra um chamado: pode ser cadastro da AC junto à SEFAZ.
