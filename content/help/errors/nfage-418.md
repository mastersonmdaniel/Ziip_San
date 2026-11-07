---
code: "nfage:418"
title: "Site de autorização inválido"
suggest_ticket: true
---

# Site de autorização inválido

O site de autorização indicado na chave de acesso difere do site que recebeu o documento (ou não existe para este autorizador). A rejeição aparece em consultas e eventos quando a chave aponta para outro site de autorização.

## Causas prováveis

- A chave foi montada com o número de site de outro ambiente autorizador.
- A consulta/evento foi dirigido ao site errado para aquela chave.

## O que fazer

- Confira a chave e dirija a consulta ou o evento ao autorizador/site que autorizou o documento.
- Se a chave veio de importação, confira o site de autorização dela antes de operar sobre o documento.
