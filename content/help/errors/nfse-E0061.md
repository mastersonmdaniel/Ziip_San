---
code: "nfse:E0061"
title: "Substituição de MEI/ME-EPP não pode mudar tomador, competência nem valor"
suggest_ticket: false
---

# Substituição de MEI/ME-EPP não pode mudar tomador, competência nem valor

Na substituição de NFS-e de emitente MEI ou ME/EPP do Simples Nacional, os campos identificação do tomador, data de competência e valor do serviço não podem ser alterados em relação à nota original. A substituta tentou mudar um deles.

## Causas prováveis

- A substituição corrigiu justamente o tomador, a competência ou o valor — campos travados para MEI e ME/EPP.
- O regime do emitente (opSimpNac) faz a regra mais restritiva se aplicar.

## O que fazer

- Se a correção é nesses campos, cancele a nota (se o prazo permitir) e emita uma nova com os dados certos.
- Na substituição, limite a correção aos demais campos permitidos.
