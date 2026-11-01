# Arquivo (P0.7)

Estes arquivos NÃO fazem parte do ambiente ativo e NÃO são usados por
`docker-compose.yml` nem por nenhum script atual:

- `kubernetes/` — manifests Kubernetes antigos (ADR-0010: fora do escopo do
  ambiente local; o deploy em nuvem fica para o lançamento comercial).
- `terraform/` — Terraform antigo (ADR-0010; região `us-east-1` contra
  `sa-east-1` do ADR-0011, e outputs de segredos — outro motivo para não
  usá-lo como está).

P0.7 os tirou de `infra/kubernetes/` e `infra/terraform/` para cá, sem
apagá-los do histórico do Git, para consulta. Valores literais de segredos
que estavam nesses arquivos (ex.: "changeme") foram substituídos por
`REDACTED-ARQUIVADO` nesta cópia — o conteúdo original permanece no
histórico do Git, mas esta cópia não contém segredo utilizável.

Divergência registrada (P0.7 × ADR-0010, Vault): o ADR-0010 mandava 6
serviços para `profiles:` (incluindo o Vault); a P0.7 manda remover 5
serviços e deixar só o SeaweedFS (serviço `minio`) em perfil. O critério
de aceite é a P0.7: o Vault foi REMOVIDO do compose, não perfilado. O ADR
não foi alterado sem decisão do dono.

Para reativar qualquer coisa daqui, é preciso decisão do dono e revisão
— não basta copiar de volta.
