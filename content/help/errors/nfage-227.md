---
code: "nfage:227"
title: "Erro na composição do campo Id"
suggest_ticket: true
---

# Erro na composição do campo Id

Há erro na composição do campo Id do documento: ou falta a literal "NFAG" na frente da chave de acesso, ou a chave que está no Id difere da concatenação dos campos do documento (UF, ano/mês, CNPJ, modelo 75, série, número, tipo de emissão e código numérico). Sem o Id íntegro, a SEFAZ não identifica o documento.

## Causas prováveis

- A chave foi montada fora do sistema, sem a literal NFAG ou com algum campo divergente do documento.
- Um campo da chave (série, número ou código numérico) foi alterado depois da montagem do Id.

## O que fazer

- Não monte nem edite a chave manualmente: use sempre a chave gerada pelo sistema a partir dos dados do documento.
- Se a chave veio de outro sistema, importe o documento pela chave completa e confira se ela corresponde aos dados da conta antes de reenviar.
