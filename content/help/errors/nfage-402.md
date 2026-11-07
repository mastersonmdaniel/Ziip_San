---
code: "nfage:402"
title: "XML com codificação diferente de UTF-8"
suggest_ticket: true
---

# XML com codificação diferente de UTF-8

O XML da área de dados usa codificação diferente de UTF-8, que é a única aceita pelo serviço. A SEFAZ rejeita antes de interpretar o conteúdo.

## Causas prováveis

- O arquivo foi gerado ou convertido em outra codificação (por exemplo, ISO-8859-1) por sistema externo.
- A declaração de codificação do XML diverge do conteúdo real.

## O que fazer

- Reenvie o documento pelo sistema, que gera o XML em UTF-8.
- Se o XML veio de importação, importe de novo a partir do arquivo original e abra um chamado se a codificação vier errada de novo.
