---
code: "nfage:243"
title: "XML malformado"
suggest_ticket: true
---

# XML malformado

O XML da mensagem está malformado: a SEFAZ não conseguiu nem interpretar a estrutura do arquivo enviado (caractere inválido, tag não fechada ou conteúdo fora de lugar). Nada do documento foi processado.

## Causas prováveis

- O arquivo foi truncado ou corrompido no envio.
- Há caractere especial não escapado no conteúdo do XML.

## O que fazer

- Reenvie o documento pela fila do sistema, sem editar o XML à mão.
- Se persistir em vários documentos, abra um chamado com o horário do envio — é falha de montagem/transmissão, não da conta.
