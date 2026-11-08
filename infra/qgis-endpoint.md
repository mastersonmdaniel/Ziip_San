# ED-55 / GIS-10 — Endpoint dedicado do QGIS (§7.1 item 5)

Configuração de referência (a aplicar pela rotina de implantação no
ambiente; nenhum host/porta real é fixado aqui):

- Listener PostgreSQL dedicado para as roles `qgis_*`, FORA do
  PgBouncer em modo transaction da api.
- TLS obrigatório com `sslmode=verify-full` no cliente (o
  `pg_service.conf` gerado por `GisQgzProjectFactory` já sai assim).
- Acesso somente por VPN do cliente ou CIDR de origem declarado
  (`GisQgisRules.ensureEndpointPolicy` recusa qualquer outra forma).
- `pg_hba.conf`: apenas `hostssl` para as roles `qgis_*`, método de
  autenticação do ambiente (senha com validade do core V12; IAM no
  RDS a partir do lançamento, §7.1 item 1).
- Auditoria: log de conexões e comandos das roles `qgis_*`
  (pgAudit quando disponível no RDS — conferir na LC.1, §7.1 item 6);
  histórico de escrita é o dos pacotes (gatilhos da GIS-04).
- Até o lançamento, o endpoint existe só no ambiente local (ADR-0033).
