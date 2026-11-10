# Ambiente `dedicado` — instância dedicada (LC.15)

> Pacote LC.15 — Produtor, sessão `b3a03bf9-b502-478f-a071-9590cb482d99`.
> Autorização: decisão 52 do dono (09/11/2026) — a LC.15 executa-se
> integralmente já, mesmo sem contrato de cliente. Atos em AWS real
> executam-se na contratação (decisão 41). Cliente concreto: **[A DEFINIR]**.

Esta pasta é a forma completa do ambiente que a LC.2 entregou como modelo.
Para ativar para um cliente futuro: seguir
`docs/operacao/INSTANCIA-DEDICADA-ATIVACAO-LC15.md` (copiar para
`envs/dedicado-<tenant>`, preencher a conta AWS separada e aplicar pelo
mesmo pipeline).

## Composição

| Ficheiro | Conteúdo |
|---|---|
| `main.tf` | Provider com `assume_role` para a **conta AWS separada** do cliente; módulos partilhados da LC.2 (`foundation`, `platform` = api, `edge`, `security`, `messaging`); guarda de ativação (tenant + conta + modo do banco) |
| `stack.tf` | **Worker** (mesma imagem da release, modo `worker` do entrypoint P0.5) e **Keycloak** (imagem e parâmetros da LC.6, 2 réplicas) sobre o mesmo cluster ECS |
| `database.tf` | **Banco exclusivo**: módulo RDS/PITR da LC.3 + módulo KMS/Secrets da LC.4, já na main — padrões da produção, tudo em `sa-east-1` |
| `access.tf` | **TR-104**: papel IAM só-leitura da TI do órgão (MFA + ExternalId), negação explícita de escrita e CloudTrail de auditoria da conta |
| `policies/orgao-ti-readonly-tr104.json` | Lista canónica das ações de leitura do TR-104 (fonte única carregada por `access.tf`) |
| `keycloak/orgao-ti-readonly-role.json` | Papel do realm dedicado para a TI do órgão (lado aplicação) |
| `variables.tf` / `outputs.tf` | Parâmetros e inventário de residência (RNF-44): sem backup nem réplica fora de `sa-east-1` |

## Regras que este ambiente carrega

- **Mesma IaC, mesma imagem, mesmo pipeline** que a produção (ADR-0024):
  nenhum módulo partilhado foi alterado; `container_image` é a mesma
  variável da api e do worker; o Keycloak usa a imagem da LC.6.
- **Banco no modo padrão**: `tenant_db_mode = "schema-per-tenant"`
  (validado). Esta instância **não depende da P4.6**.
- **TR-104 só aqui**: o papel do órgão não existe em nenhum ambiente
  compartilhado; a tentativa de escrita é recusada (omissão + `Deny`
  explícito) e registada no CloudTrail desta conta.
- **Sem segredo no state**; valores sem fonte ficam `[A DEFINIR]`.
