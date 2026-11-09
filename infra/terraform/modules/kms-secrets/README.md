# Modulo `kms-secrets` (LC.4)
Produtor: sessao ca6d61bf-f7f3-42dd-a535-196ff96c1069 (papel: Produtor). Caminho de repo declarado:
`infra/terraform/modules/kms-secrets/`. A DEK por tenant e gerada pelo
KMS (`GenerateDataKey` contra a chave do tenant) na aplicacao que
implementa o `TenantKeyProviderContract` (P0.28) — a suite verde sobre
o KMS real e prova AWS (roteiro LC4-T1), nao executada neste bloco.
