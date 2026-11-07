---
code: "nfage:236"
title: "Chave de acesso inválida"
suggest_ticket: false
---

# Chave de acesso inválida

A chave de acesso informada é inválida para a SEFAZ. A validação da chave (regra I04 do MOC) aponta o motivo: CNPJ zerado ou inválido, ano fora do intervalo aceito, mês inválido, modelo diferente de 75, número zerado, tipo de emissão inválido, site de autorização inválido, UF inválida ou dígito verificador (DV) inválido.

## Causas prováveis

- A chave foi digitada ou importada com erro em algum campo, inclusive no dígito verificador.
- A chave é de outro documento (outro modelo, outra UF ou outro emitente).

## O que fazer

- Confira a chave posição a posição com o documento original, sem redigitá-la de memória.
- Use a chave devolvida pelo sistema na emissão; se o DV não confere, a chave foi montada fora do padrão e deve ser regenerada.
