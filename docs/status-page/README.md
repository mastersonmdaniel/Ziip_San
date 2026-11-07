# Página pública de status — P3.18

Artefatos desta tarefa:

- **Dados:** core `V10_4` — `support.status_component`, `status_incident`,
  `status_update`, `maintenance_window` (+ junção
  `status_incident_component`).
- **Lógica:** pacote `br.com.ziipsan.platform.statuspage` em
  `platform/ziipsan-platform` — o status é **derivado** de sonda,
  incidentes e janelas a cada leitura (`PublicStatusService`); não há
  flag manual de status.
- **Endpoint público:** `GET /api/v1/public/status`
  (`PublicStatusController`), JSON somente via classe `Json` do core.
- **Página:** `index.html` neste diretório — estática, só exibe o JSON
  do endpoint (inclusive admite "não foi possível carregar" em vez de
  afirmar saúde quando o endpoint não responde).

## Teste no ambiente local

1. Aplicar as migrations core (o ambiente local da P0.27 já roda o
   `CoreMigrationRunner` com `outOfOrder`); a `V10_4` cria as tabelas
   vazias — nenhum componente é semeado.
2. Cadastrar um componente com `probe_key` e exercitar a derivação pelos
   testes unitários (`StatusDerivationTest`, `MaintenanceWindowTest`,
   `PostIncidentSummaryTest`) e, com a fiação da integração, gravando
   `last_probe_outcome = 'DOWN'`: a leitura seguinte do endpoint deve
   mostrar o componente `OUTAGE` sem nenhuma edição de status.
3. Publicar uma janela com menos de 72 h de antecedência: o serviço
   recusa e o `CHECK` da `V10_4` recusa o INSERT direto equivalente.
4. Resolver um incidente, publicar o bloco "Resumo publicável" da
   revisão pós-incidente (modelo da P3.11) via
   `PostIncidentSummary.publish` e conferir o resumo em
   `publishedSummaries` no JSON e na página.

## O que NÃO é desta tarefa

- **Hospedagem separada da produção: LC.7.** Aqui a página é artefato
  estático + endpoint; a publicação em hospedagem separada, as sondas
  sintéticas em produção e o coletor que alimenta `last_probe_*` são da
  LC.7 (e a fiação JDBC/coletor local, da integração da camada).
- **Divulgação responsável de vulnerabilidade e Trust Center: P3.38**
  (PLT-09, deixado pela própria spec da P3.18). A P3.38 reutiliza esta
  mesma hospedagem separada quando ela existir.
- **Assinatura por e-mail das atualizações da página: P4.8.**
