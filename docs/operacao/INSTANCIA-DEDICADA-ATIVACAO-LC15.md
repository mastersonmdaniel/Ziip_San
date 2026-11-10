# Roteiro de ativação da instância dedicada — LC.15

> Pacote LC.15 — Produtor, sessão `b3a03bf9-b502-478f-a071-9590cb482d99`.
> Autorização da produção deste pacote: decisão 52 do dono (09/11/2026) —
> execução integral já, mesmo sem contrato. **Este roteiro executa-se na
> contratação** (decisão 41: provas/atos em AWS real na contratação).
> Cliente concreto: **[A DEFINIR]** — não existe cliente, nome, CNPJ nem
> conta neste pacote; nada aqui foi preenchido com dado presumido.

## 0. Aceite que este roteiro cumpre (LC.15, fonte única)

"instância dedicada executada só para o cliente cujo contrato a exija,
fora do caminho crítico da LC.13; pilha própria (`api`, `worker`,
Keycloak e banco) em conta AWS separada, em `sa-east-1`, criada pela
mesma IaC da LC.2, com a mesma imagem e o mesmo pipeline de atualização,
sem bifurcação de código nem de versão (ADR-0024); banco exclusivo do
cliente no modo padrão de schema por tenant, sem depender do pacote sob
demanda da P4.6; acesso administrativo da TI do órgão (TR-104) concedido
só na instância dedicada, somente leitura sobre configuração e
monitoramento, com MFA e auditoria, nunca em infraestrutura
compartilhada (teste: tentativa de escrita pelo perfil do órgão recusada
e registrada); backup, chaves e segredos nos mesmos padrões da produção,
sem backup nem réplica fora de `sa-east-1` (ver LC.3 e LC.4); o pacote de
reversão fica na ED-04 (COE-04)"

Nota sobre o gatilho: a decisão 52 substituiu o gatilho "só para o
cliente cujo contrato a exija" pela execução integral imediata **do
pacote**; a ativação viva continua a exigir um cliente/contrato real —
é esse o passo 1.

## 1. Pré-condições (portões — nenhum se salta)

| # | Pré-condição | Fonte/estado |
|---|---|---|
| PC-1 | Contrato com o cliente que exija a instância dedicada, assinado | Ato futuro — **[A DEFINIR]** |
| PC-2 | LC.2 pousada (IaC base + pipeline) | Na main (verificado no clone local nesta produção) |
| PC-3 | Módulos LC.3 (RDS/PITR) e LC.4 (KMS/Secrets) na main | Na main: `infra/terraform/modules/rds-pitr` e `infra/terraform/modules/kms-secrets` |
| PC-4 | Configuração do Keycloak de produção (LC.6) na main | Na main: `infra/keycloak/producao/` |
| PC-5 | Release vigente da produção identificada por digest (a MESMA imagem servirá aqui) | **[A DEFINIR]** na ativação — registar o digest no ato |
| PC-6 | Pacote LC.15 pousado (esta entrega) após revisão/confirmação/portão | Esteira — fora desta sessão de produção |

## 2. Passo a passo

### Passo 1 — Conta AWS separada do cliente
1. Criar a conta AWS do cliente dedicado (conta nova, na mesma
   organização se aplicável — **[A DEFINIR]** o desenho de organização;
   o aceite exige conta separada, não fixa a ferramenta de criação).
2. Região de trabalho: `sa-east-1` (única; os módulos recusam outra).
3. Criar o papel IaC dentro dessa conta para o pipeline OIDC e registar
   o ARN em `vars.AWS_IAC_ROLE_ARN_DEDICADO` do repositório.
4. Bootstrap do state (manual, uma vez, registado — mesmo procedimento
   da LC.2): bucket `ziipsan-tfstate-<conta>` em `sa-east-1` **nesta
   conta**, com versionamento, criptografia KMS, bloqueio de acesso
   público e Object Lock conforme a LC.5. Preencher `backend.hcl` a
   partir de `backend.hcl.example`.

### Passo 2 — Instanciar o ambiente
1. Copiar `infra/aws/envs/dedicado/` para `infra/aws/envs/dedicado-<tenant>`
   (padrão fixado pela LC.2) — sem alterar módulos partilhados.
2. Preencher `terraform.tfvars` a partir de `terraform.tfvars.example`:
   `tenant_slug` do cliente, `dedicated_account_id` (12 dígitos) e
   `dedicated_account_role_arn` da conta do passo 1, CIDR, domínio,
   dimensionamento aprovado, `container_image` = **digest da mesma
   release que corre em produção** (PC-5), valores LC.3/LC.4 do banco.
   `tenant_db_mode` fica `schema-per-tenant` — qualquer outro valor é
   recusado pela validação.
3. Confirmar a guarda: o `check` em `main.tf` falha o plano se faltarem
   tenant/conta ou se o modo do banco divergir do padrão.

### Passo 3 — Plano e aplicação pelo pipeline
1. Despachar `.github/workflows/deploy-infra.yml` com
   `environment=dedicado`, `action=plan`; conferir o plano (conta de
   destino = conta dedicada; região = `sa-east-1`).
2. Despachar com `action=apply`: o job `apply-dedicado` só corre atrás
   do Environment `dedicado` (aprovação manual) e assume o papel da
   conta dedicada.
3. Registar: hash do plano aplicado, digest da imagem, saídas
   `dedicated_stack` e `inventory_residency_br` (conferir
   `backups_outside_region = "none"` e `replication_outside_region = "none"`).

### Passo 4 — Pilha: verificação pós-apply
1. Serviços ECS no cluster: `api` (módulo platform), `worker` e
   `keycloak` (2 réplicas — LC.6) saudáveis.
2. Criar, no RDS exclusivo, os bancos/roles separados como na produção:
   banco da aplicação e banco `keycloak` (role própria) — procedimento
   de provisionamento da aplicação; a senha do Keycloak vai para o
   Secrets Manager (LC.4) e o ARN entra em `keycloak_db_secret_arn`;
   reaplicar para o serviço do Keycloak a consumir por referência.
3. Provisionar o tenant do cliente na aplicação **no modo padrão
   schema-por-tenant** (provisionamento normal da plataforma) — nenhum
   procedimento da P4.6 é invocado.
4. Registar a instância no catálogo: preencher
   `public.tenant_catalog.dedicated_aws_account_id` (core `V13_1`,
   desta entrega — a parte "conta da instância" que a core `V13`
   reservou à LC.15) com o ID da conta do passo 1, na linha do tenant.
4. Testes de fumaça da instância (login no Keycloak dedicado, API,
   worker a processar) — registar evidências.

### Passo 5 — TR-104 (TI do órgão)
1. Nomear com o órgão: principais autorizados e `ExternalId`;
   preencher `orgao_ti_principal_arns` e `orgao_ti_external_id`; reaplicar.
2. Criar o grupo `orgao-ti` no realm dedicado e importar o papel de
   `keycloak/orgao-ti-readonly-role.json`; exigir OTP (MFA) no fluxo do
   grupo — sem MFA, o trust IAM recusa a assunção e o papel do realm
   não é atribuído.
3. Executar o **teste vivo do aceite** (roteiro em
   `INSTANCIA-DEDICADA-TR104-LC15.md` §3): tentativa de escrita pelo
   perfil do órgão → recusada → registo no CloudTrail desta conta.
   Guardar o evento como evidência. **Sem este registo, a LC.15 não se
   declara ativada.**
4. Confirmar por contraste: o papel `orgao-ti-readonly` **não existe**
   em nenhum ambiente compartilhado (homologação/produção/demonstração).

### Passo 6 — Backup, chaves e residência
1. Conferir o plano de backup (LC.3) ativo sobre o RDS exclusivo e o
   cofre isolado **na mesma região**; conferir rotação automática da
   chave da plataforma (LC.4).
2. Conferir a saída `inventory_residency_br`: única exceção fora da
   região = metadados do CloudFront (certificado/WAF, exigência do
   serviço, sem dados) — a mesma exceção declarada da LC.2.

### Passo 7 — Atualizações futuras (sem bifurcação)
Toda a atualização desta instância é uma nova release da MESMA imagem,
promovida pelo MESMO pipeline (`deploy-infra.yml`, `environment=dedicado`),
na mesma cadência da produção. Não existe ramo, fork nem versão própria
da instância dedicada (ADR-0024).

### Passo 8 — Reversão
O pacote de reversão é da **ED-04 (COE-04)** — fora do âmbito da LC.15.
Em caso de desativação, seguir o pacote da ED-04; esta LC limita-se a
não o contradizer (a instância é um ambiente isolado: desligar os
serviços e a conta não toca a infraestrutura compartilhada).

## 3. Registo da ativação (preencher no ato — nasce em branco)

| Campo | Valor |
|---|---|
| Cliente / contrato | [A DEFINIR] |
| Conta AWS dedicada (ID) | [A DEFINIR] |
| Digest da imagem aplicada | [A DEFINIR] |
| Data/responsável do apply | [A DEFINIR] |
| Evidência do teste TR-104 (evento CloudTrail) | [A DEFINIR] |
| Conferência de residência (saída anexada) | [A DEFINIR] |
