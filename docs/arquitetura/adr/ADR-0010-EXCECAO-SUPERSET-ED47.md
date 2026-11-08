# ADR-0010 — Exceção do Superset (ED-47): registro e conformidade

> **Verificação feita nesta tarefa:** a seção **Exceções** do ADR-0010
> (`docs/arquitetura/adr/ADR-0010-infra-nao-usada-congelada.md`, texto
> conferido no espelho local da ED-04) **já contém** a linha do Apache
> Superset, incluída no ajuste de 12/10/2026 (editais) do próprio
> ADR-0010. Ou seja: o item do aceite da ED-47 "exceção no ADR-0010
> registrada" **já estava cumprido antes desta entrega** — este arquivo
> não finge um registro novo; ele fixa a linha existente e prova a
> conformidade da implementação com a Forma dela, item a item.

## Linha existente na tabela de Exceções do ADR-0010 (transcrição)

| Componente | Caso concreto | Forma | Substituição | Origem |
|---|---|---|---|---|
| Apache Superset (licença Apache 2.0) | Consultas livres, modelo semântico e cubos exigidos pelos editais (`18g` §2.5), que o PostgreSQL e os relatórios nativos não atendem | Contêiner próprio, a partir do P4, lendo só a réplica de leitura; SSO pelo Keycloak; uma role de banco por tenant com restrição pela identidade da conexão; modo síncrono, metadados no PostgreSQL; Redis e Celery não entram sem nova exceção | Nenhuma prevista; relatórios e painéis padrão continuam nativos | ADR-0027 |

## Conformidade da entrega da ED-47 com a Forma

| Forma do ADR-0010 | Como a entrega cumpre |
|---|---|
| Contêiner próprio | `infra/superset/docker-compose.superset.yml`, perfil `bi`, porta só em `127.0.0.1:8088` |
| Lendo só a réplica de leitura | Uma database por tenant apontando à réplica; `ReplicaOnlyGuard` reprova qualquer fonte fora da réplica ou no endpoint do primário (teste confere cada fonte) |
| SSO pelo Keycloak | `superset_config.py` (OIDC, realm `ziipsan`) + `SupersetSsoConfig` validado no módulo |
| Uma role de banco por tenant, restrição pela identidade da conexão | `TenantBiRole`/`BiRoleProvisioningSql`: role derivada do tenant, tenant resolvido por `current_user` no mapeamento — não pela GUC, que a sessão pode trocar |
| Modo síncrono | `SQLLAB_ASYNC = False`; nenhum serviço Redis/Celery no compose |
| Metadados no PostgreSQL | Banco de metadados PostgreSQL dedicado do perfil (`superset-db`), sem dado de tenant |
| Redis e Celery não entram sem nova exceção | Ausentes da entrega; alertas/relatórios agendados do Superset desligados (`ALERT_REPORTS: False`) — o agendamento do produto é o da P3.22 |

## Notas

1. **"A partir do P4":** a Forma autoriza o componente a partir do P4.
   A ED-47 é tarefa da trilha de editais e entrega código, configuração
   e testes; a **ativação** do perfil `bi` em ambiente compartilhado
   segue o marco do ADR (P4/LC), não esta entrega.
2. **Nenhuma linha nova é necessária.** Se a integração encontrar o
   ADR-0010 do repo divergente deste espelho, a linha a inserir é
   exatamente a transcrita acima, com Origem = ADR-0027.
3. **Licença:** Apache 2.0, já registrada na linha; a conferência no
   SBOM é da P0.40.
