---
code: "nfage:215"
title: "Falha no schema XML"
suggest_ticket: true
---

# Falha no schema XML

O XML enviado não passou na validação do schema (a estrutura oficial) da NFAg-e: algum elemento está fora da ordem, do formato ou do conteúdo que o leiaute vigente define. A SEFAZ rejeita nessa etapa, antes de qualquer regra de negócio — o documento não foi processado nem gerou efeito.

## Causas prováveis

- Um campo foi montado fora do padrão do leiaute (ordem das tags, formato de data/valor ou valor fora do domínio permitido).
- O documento foi gerado numa versão de schema diferente da vigente no autorizador.

## O que fazer

- Não corrija o documento à mão: reenvie pela fila do sistema, que monta o XML no leiaute vigente.
- Se a rejeição se repetir em documentos diferentes, abra um chamado com a chave e o horário — o problema tende a ser de montagem/schema, não do dado da conta.
