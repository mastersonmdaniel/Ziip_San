---
code: "nfage:280"
title: "Certificado transmissor inválido"
suggest_ticket: true
---

# Certificado transmissor inválido

O certificado digital usado na transmissão (conexão TLS com a SEFAZ) é inválido: inexistente na mensagem, de versão diferente da exigida ou sem o uso de autenticação de cliente. Sem certificado transmissor válido, a SEFAZ nem recebe o documento.

## Causas prováveis

- O certificado selecionado para transmissão não é um e-CNPJ/e-CPF ICP-Brasil válido.
- O certificado instalado está corrompido ou sem a cadeia completa.

## O que fazer

- Selecione um certificado válido do emitente para a transmissão e reinstale a cadeia, se preciso.
- Se o certificado está regular e a rejeição persiste, abra um chamado com o horário.
