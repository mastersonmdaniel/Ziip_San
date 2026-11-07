---
code: "nfage:599"
title: "Caracteres de edição na mensagem"
suggest_ticket: true
---

# Caracteres de edição na mensagem

Não é permitida a presença de caracteres de edição no início ou no fim da mensagem, nem entre as tags: espaços, quebras de linha ou caracteres de formatação fora do conteúdo quebram a leitura da mensagem pela SEFAZ.

## Causas prováveis

- O XML foi formatado (indentado) ou editado fora do sistema depois da montagem.
- O arquivo passou por editor de texto antes do envio.

## O que fazer

- Reenvie o documento pela fila do sistema, sem abrir nem salvar o XML em editor.
- Se o arquivo veio de importação, importe o original sem edição.
