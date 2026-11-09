# IaC da LC.2 — homologação, produção e demonstração na AWS `sa-east-1`

> Pacote LC.2 — Produtor, sessão `764b25ad-2ccf-40e5-afbf-986d42be0213`.
> Ferramenta: Terraform — **decisão de execução declarada** (o plano v3.0
> não fixa a ferramenta da LC.2; ver `RELATORIO-LC.2.md`, §1). Diretório
> **novo**: o `infra/terraform` antigo foi arquivado em `infra/_arquivado/`
> (ADR-0010) e **não** é reaproveitado (ADR-0011: "IaC nova em diretório
> próprio").

## Estrutura

```
infra/aws/
  modules/
    foundation/   VPC, sub-redes, endpoints regionais, tags de residência
    edge/         CloudFront + WAF, TLS 1.3, HSTS, ACM automático,
                  domínio próprio do tenant (P2.27) e anti-tomada
    platform/     ALB (TLS 1.3), ECS Fargate (imagem única), ECR
    security/     TR-091 GuardDuty, TR-092 Inspector, TR-096 AWS Config
    messaging/    SES por tenant (P3.5/PLT-10), SNS de eventos, base SMS/custo
  envs/
    homologacao/  ambiente "shared"
    producao/     ambiente "shared" (apply só com aprovação manual)
    demonstracao/ ambiente "demo" (massa sintética da P3.23)
    dedicado/     MODELO da LC.15 — copiar para dedicado-<tenant>
```

Um ambiente novo — demonstração ou instância dedicada (LC.15) — é **mais
uma pasta em `envs/`** com os mesmos módulos, a mesma imagem e o mesmo
pipeline: é essa a parametrização que o aceite da LC.2 exige. A instância
dedicada difere só por: `environment_kind = "dedicated"`, `tenant_slug`
preenchido, conta AWS separada (backend e papel do pipeline próprios) e
pilha completa ligada (api, worker, Keycloak e banco são das LC.6/LC.3
sobre as saídas destes módulos).

## Regras que o código carrega

- **Região fixa `sa-east-1`** (ADR-0011, RNF-44): validação de variável
  recusa outra região; sem réplica nem backup fora da região. Única
  exceção declarada: certificado do viewer do CloudFront e WAF de escopo
  CLOUDFRONT em `us-east-1`, exigência do serviço, só metadados públicos
  (ver `outputs.tf` → `inventory_residency_br` de cada ambiente).
- **Sem segredo no state**: nenhum recurso recebe valor de segredo;
  segredos vivem no Secrets Manager (LC.4) e são referenciados por ARN.
  O backend S3 usa criptografia, versionamento e lock (ver
  `backend.hcl.example` de cada ambiente).
- **Bootstrap do state (uma vez por conta, manual e registrado)**: o
  bucket do state não pode ser criado pela própria IaC que o usa.
  Procedimento: criar manualmente (console/CLI do dono da conta) o bucket
  `ziipsan-tfstate-<conta>` em `sa-east-1` com versionamento, criptografia
  KMS, bloqueio de acesso público e Object Lock conforme a LC.5; registrar
  data/responsável no diário; só então preencher `backend.hcl`. Bucket de
  state **não** é recurso desta IaC — declaração expressa.
- **Tamanhos sem fonte = `[A DEFINIR]`**: CPU/memória/réplicas, CIDR,
  domínio real, limite do WAF e imagem da release vivem no `terraform.tfvars`
  de cada ambiente (modelo em `terraform.tfvars.example`). Nenhum valor foi
  presumido nesta entrega.
- **Deploy só pelo pipeline** (`.github/workflows/deploy-infra.yml`):
  produção com aprovação manual (Environment `producao`); rollback pelo
  mesmo pipeline (procedimento em `docs/operacao/ROLLBACK.md`).

## Domínio próprio do tenant (P2.27) — passos de cada lado

> Seção acrescentada na correção (estágio 3, sessão
> `b36fd7a8-91ec-4322-9e7e-8bfe051efd54`; defeito R-2 da revisão):
> o modelo da P2.27 põe o TXT de verificação e o CNAME de apontamento
> **no DNS do tenant**. Esta IaC não cria nenhum desses registos.

**No DNS do tenant (responsabilidade do tenant, a pedido da plataforma):**

1. Publicar o TXT `_ziipsan-verify.<domínio>` com o valor de verificação
   do tenant (a aplicação gera/confere este valor — P2.27,
   `platform.tenant_domain`; os dados por domínio saem na saída
   `tenant_dns_publication` do módulo `edge`).
2. Publicar os CNAMEs de validação do certificado ACM relativos ao seu
   domínio (saída `acm_validation_records` do módulo `edge`): sem eles,
   o ACM não emite o certificado com o domínio do tenant.
3. Publicar o CNAME do domínio (ou do subdomínio contratado) apontado
   para a saída `distribution_domain` do ambiente (domínio da
   distribuição CloudFront) — é este CNAME, do lado do tenant, que
   aponta o domínio para a borda.

**Nesta IaC (módulo `edge`):**

4. Só com a posse provada na aplicação (`possession_proven = true` no
   item de `tenant_domains`) é que o domínio entra nos SANs do
   certificado e nos aliases da distribuição — sem posse provada, a
   borda não serve o domínio (proteção contra tomada de subdomínio).
5. Os registos de validação do ACM criados nesta zona são só os do
   domínio raiz da plataforma; nenhum registo é criado em domínio de
   tenant.

**Encerramento do contrato:**

6. Remover o item de `tenant_domains` e aplicar: o domínio sai dos SANs
   e dos aliases no mesmo apply e a distribuição deixa de o servir.
7. Confirmar com o tenant a remoção do CNAME e do TXT no DNS dele —
   esses registos são dele e esta IaC não os pode apagar; um CNAME
   residual apontaria para uma distribuição que já não tem esse alias.

## Fronteiras com as tarefas vizinhas

| Tarefa | O que é dela (não duplicado aqui) |
|---|---|
| LC.1 | Ensaio de implantação com dado sintético que exercita esta IaC |
| LC.3 | RDS PostgreSQL 17 Multi-AZ, PITR, cofre de backup |
| LC.4 | KMS, Secrets Manager, rotação e papéis finos |
| LC.5 | S3 com Object Lock (incl. disciplina do bucket de state/Config) |
| LC.6 | Keycloak de produção sobre o cluster daqui |
| LC.7 | Observabilidade gerenciada e página de status |
| LC.15 | Executar a instância dedicada (esta entrega só parametriza) |
| P3.23 | Massa sintética e ensaio de POC que povoam `demonstracao` |

## Estado desta entrega

Código produzido e validado **localmente, sem AWS** (harness estrutural;
ver `RELATORIO-LC.2.md`). Nada foi aplicado: conta AWS, `plan`/`apply`
reais, exercício de rollback e criação do ambiente de demonstração são
pendências externas nomeadas no relatório.
