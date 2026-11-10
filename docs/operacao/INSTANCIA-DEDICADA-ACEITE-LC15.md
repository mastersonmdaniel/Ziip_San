# Mapa de aceite — LC.15 "Instância dedicada na AWS"

> Pacote LC.15 — Produtor, sessão `b3a03bf9-b502-478f-a071-9590cb482d99`.
> Fonte única: registo LC.15 em
> `docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json`
> (título "Instância dedicada na AWS", origem `18h-E4; ADR-0024; ADR-0011`,
> deps ED-04 e LC.2, size "a estimar"), lido na íntegra antes da produção.
> Autorização: decisão 52 (execução integral já); decisão 41 (atos em AWS
> real na contratação).

| # | Cláusula do aceite | Evidência produzida | Estado |
|---|---|---|---|
| A1 | Instância executada para o cliente cujo contrato a exija; fora do caminho crítico da LC.13 | Decisão 52 substituiu o gatilho pela execução integral do pacote; ativação viva continua condicionada a contrato (roteiro, PC-1). A LC.15 não toca a LC.13 | ✔ pacote; ativação = pendência P-LC15-02 |
| A2 | Pilha própria: `api`, `worker`, Keycloak e banco | api = módulo `platform` da LC.2 (em `main.tf`); worker + Keycloak = `stack.tf` (mesmo cluster; worker com a mesma imagem/modo `worker` da P0.5; Keycloak LC.6, 2 réplicas); banco = `database.tf` (módulo LC.3) | ✔ código (harness §pilha) |
| A3 | Conta AWS separada, em `sa-east-1` | Provider com `assume_role` para a conta dedicada + `dedicated_account_id` validado + backend/state próprios dessa conta + papel OIDC próprio no pipeline (`AWS_IAC_ROLE_ARN_DEDICADO`); região fixa nos módulos. Registo no catálogo: core `V13_1` (`dedicated_aws_account_id` em `public.tenant_catalog`) — a parte "conta da instância" que a core `V13` já publicada reservou expressamente à LC.15, com teste de guardas no padrão da V13 | ✔ código; conta real = pendência P-LC15-01 |
| A4 | Criada pela MESMA IaC da LC.2 | O ambiente compõe os 5 módulos partilhados da LC.2 sem os alterar, e compõe os módulos LC.3/LC.4 já na main pelos caminhos deles | ✔ código (harness §paridade) |
| A5 | Mesma imagem e mesmo pipeline de atualização; sem bifurcação de código nem de versão (ADR-0024) | `container_image` é uma só variável para api e worker; Keycloak com a imagem da LC.6 (`quay.io/keycloak/keycloak:26.8.0`, valor vivo na main); pipeline = o `deploy-infra.yml` da LC.2 estendido com o ambiente `dedicado` (sem workflow novo) | ✔ código (harness §paridade) |
| A6 | Banco exclusivo do cliente no MODO PADRÃO de schema por tenant, SEM depender da P4.6 | RDS exclusivo (LC.3) + `tenant_db_mode = "schema-per-tenant"` com validação que recusa qualquer outro modo + `check` em `main.tf`; zero referências a modos/`isolation_mode` da P4.6 na pasta | ✔ código (harness §banco) |
| A7 | TR-104: só na dedicada, só leitura de configuração/monitoramento, MFA e auditoria, nunca em compartilhada | `access.tf` + política JSON canónica + negação explícita de escrita + CloudTrail; papel do realm em `keycloak/`; ausência verificada nos envs compartilhados vivos | ✔ código + validação estrutural local; ver A8 |
| A8 | Teste: escrita pelo perfil do órgão recusada e registrada | Metade estrutural executada localmente (política sem escrita; Deny explícito; auditoria declarada). Metade viva (recusa real + evento CloudTrail) roteirizada em `INSTANCIA-DEDICADA-TR104-LC15.md` §3.2 | ◐ local ✔; vivo = pendência P-LC15-03 (decisão 41) |
| A9 | Backup, chaves e segredos nos mesmos padrões da produção; sem backup nem réplica fora de `sa-east-1` (LC.3/LC.4) | Mesmos módulos LC.3 (PITR + cofre isolado na mesma região) e LC.4 (KMS com rotação, segredos vazios por referência); saída `inventory_residency_br` com `backups_outside_region="none"`/`replication_outside_region="none"`; nenhuma `replication_configuration` na pasta | ✔ código (harness §residência) |
| A10 | Pacote de reversão fica na ED-04 (COE-04) | Declarado fora de âmbito no relatório e no roteiro (passo 8); nada de reversão foi produzido nem prometido aqui | ✔ fronteira respeitada |
| A11 | Preço fica na ED-10 | Declarado fora de âmbito; nenhum preço, custo ou estimativa comercial neste pacote | ✔ fronteira respeitada |

## Tensão registada (não resolvida em silêncio)

O `PLANO-v3.0` (§"alinhar a LC.15 ao ADR-0024", conforme o brief do
coordenador) descreve a instância dedicada como **extensão do modo de
banco dedicado da P4.6**. O acceptance canónico da LC.15 manda o
contrário operacional: banco exclusivo **no modo padrão de schema por
tenant, sem depender da P4.6**. **Segue-se o acceptance** (fonte única
declarada): a P4.6 não é dependência, pré-condição nem modo desta
instância. A tensão fica registada para o revisor/coordenador tratarem
a redação do plano, se o entenderem — esta produção não edita o plano.
