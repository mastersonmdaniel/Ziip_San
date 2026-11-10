# TR-104 — Acesso da TI do órgão à instância dedicada (LC.15)

> Pacote LC.15 — Produtor, sessão `b3a03bf9-b502-478f-a071-9590cb482d99`.

## 1. A regra do aceite

Acesso administrativo da TI do órgão (TR-104) concedido **só na
instância dedicada**, **somente leitura** sobre configuração e
monitoramento, **com MFA e auditoria**, **nunca em infraestrutura
compartilhada**. Teste do aceite: **tentativa de escrita pelo perfil do
órgão recusada e registrada**.

## 2. O modelo produzido

| Camada | Artefato | O que garante |
|---|---|---|
| AWS — identidade | `infra/aws/envs/dedicado/access.tf`: papel `orgao-ti-readonly` | Assunção só pelos principais do órgão nomeados na ativação (`orgao_ti_principal_arns`; vazio = ninguém), com `ExternalId`, `aws:MultiFactorAuthPresent=true` e MFA com ≤ 12 h (`MultiFactorAuthAge`); sessão ≤ 1 h |
| AWS — permissões | `infra/aws/envs/dedicado/policies/orgao-ti-readonly-tr104.json` (carregado por `access.tf` — fonte única) | Só ações de leitura (Get/List/Describe/Filter) de configuração e monitoramento (CloudWatch/Logs, ECS, RDS, ALB, EC2-describe, CloudFront/WAF get-list, Config, GuardDuty, Inspector, Backup, SNS/SES get-list, STS). **Sem** `GetSecretValue` e **sem** `kms:Decrypt`: de segredos e chaves, só metadados |
| AWS — negação | Política `orgao-ti-deny-write` anexada ao mesmo papel (`access.tf`) | `Deny` explícito de qualquer ação fora da lista de leitura (`NotAction`): a escrita é recusada mesmo se outra política a concedesse por engano |
| Aplicação | `infra/aws/envs/dedicado/keycloak/orgao-ti-readonly-role.json` | Papel do realm dedicado `orgao-ti-readonly`: `view-configuration`, `view-monitoring`, `view-health`, `view-audit-events`; negações explícitas de gestão/escrita; atribuição condicionada a OTP (MFA) |
| Auditoria | CloudTrail `dedicated_audit` + bucket S3 com KMS/versionamento + log group (`access.tf`) | Regista **todas** as chamadas na conta, incluindo as recusadas (`AccessDenied`), com validação de integridade dos ficheiros; tudo em `sa-east-1`, sem replicação para fora |
| Escopo | Esta pasta só existe no ambiente dedicado | Nenhum ambiente compartilhado (homologação/produção/demonstração) contém o papel, a política ou o realm role — verificado estruturalmente pelo harness da LC.15 contra os ficheiros vivos da main |

## 3. Teste do aceite — duas metades

### 3.1 Metade executável localmente (EXECUTADA nesta produção)
Validação estrutural da política, no harness
(`trabalho/validar_lc15.py`, log integral em `harness-logs/`):
- todas as ações concedidas no JSON canónico têm verbo de leitura;
- nenhuma ação proibida presente (`GetSecretValue`, `Decrypt`, qualquer
  `*` curinga de escrita, verbos Create/Put/Delete/Update/Modify/etc.);
- a lista do `NotAction` da política de negação cobre/casa com a lista
  de leitura concedida;
- o trust exige MFA; o CloudTrail de auditoria está declarado;
- o papel TR-104 **não aparece** em nenhum ficheiro dos ambientes
  compartilhados vivos na main (prova de "nunca em infraestrutura
  compartilhada", ao nível de código).
Placar: ver `RELATORIO-LC.15.md` (lido do log integral do harness).

### 3.2 Metade viva (PENDÊNCIA NOMEADA — execução na contratação, decisão 41)
Contra a AWS real, com a instância ativada (roteiro de ativação, passo 5):
1. Assumir o papel `orgao-ti-readonly` com MFA a partir de um principal
   do órgão; confirmar leitura (ex.: `aws ecs describe-services`,
   `aws cloudwatch get-metric-data`) — deve **permitir**.
2. Tentar uma escrita com o mesmo perfil (ex.: `aws ecs update-service`
   com contagem inalterada, ou `aws rds modify-db-instance` sem efeito) —
   deve **recusar** com `AccessDenied`.
3. Localizar o evento da recusa no CloudTrail da conta dedicada e
   arquivar o JSON do evento como evidência do aceite.
4. Tentar assumir o papel **sem** MFA — deve **recusar** no trust.
Estado: **não executado** — não há conta AWS nem cliente (pendência
P-LC15-01/P-LC15-02 do relatório). Não declarar como executado sem o
registo do passo 3.

## 4. Limites declarados

- A política concede leitura com `Resource = "*"` **dentro desta conta**
  — aceitável por desenho: a conta é exclusiva do cliente e só contém
  esta instância. Se um dia a conta vier a conter outro sistema, o
  escopo deve ser restringido por ARN antes da ativação desse sistema.
- O lado Keycloak depende do realm real da LC.6 (a LC.6 regista o realm
  vigente como pendência dela); o ficheiro desta entrega é o papel a
  importar, não um realm completo.
