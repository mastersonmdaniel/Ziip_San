---
code: "nfage:252"
title: "Ambiente diverge do ambiente de recebimento"
suggest_ticket: false
---

# Ambiente diverge do ambiente de recebimento

O ambiente informado no documento (produção ou homologação) diverge do ambiente do serviço que o recebeu. Documento de homologação não é aceito em produção, e vice-versa.

## Causas prováveis

- O documento foi emitido em homologação e enviado ao serviço de produção (ou o contrário).
- A configuração de ambiente do tenant está diferente do certificado/endpoint usado.

## O que fazer

- Confira o ambiente configurado para o tenant e reenvie ao serviço do mesmo ambiente.
- Documento de teste deve ser refeito em produção: homologação não tem valor fiscal.
