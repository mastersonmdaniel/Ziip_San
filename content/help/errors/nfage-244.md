---
code: "nfage:244"
title: "Falha na descompactação da área de dados"
suggest_ticket: true
---

# Falha na descompactação da área de dados

A SEFAZ não conseguiu descompactar a área de dados da mensagem: a NFAg-e é transmitida compactada (GZip) e o conteúdo recebido não descompacta no padrão esperado. O documento nem chegou a ser lido.

## Causas prováveis

- A mensagem foi corrompida ou truncada na transmissão.
- A compactação foi feita fora do padrão do serviço (formato ou codificação diferentes do esperado).

## O que fazer

- Reenvie o documento: uma nova transmissão costuma resolver quando houve corrupção no caminho.
- Se a falha se repetir, abra um chamado com o horário — a causa tende a ser da camada de transmissão/compactação.
