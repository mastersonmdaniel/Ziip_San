# Error tracking — ambiente local (P3.11)

Escopo desta tarefa: error tracking no ambiente local/homologação local.
O error tracking de PRODUÇÃO (serviço gerenciado, plantão) é da LC.7 e
está fora da P3.11 — não prometer nem configurar aqui.

Local:
- Logs JSON com `X-Request-ID` e traces com `tenant_id` já são da P0.8;
  a investigação local parte deles (ver `docs/operacao/local-env.md`).
- Erros 5xx da API são a entrada **proposta** do SLI `api.availability`
  (derivação a partir da métrica real da P0.8, `http.server.requests` —
  ver `slo-catalog.md`); **não há regra executável de burn** no catálogo
  `alert-rules.json` enquanto a modelagem dessa derivação não for
  decidida (`alert-rules-pendentes.md` §1). O `BurnRateCalculator`
  calcula burn a partir de contagens fornecidas por quem chama; exceção
  não tratada sem alerta associado é registrada como lacuna na revisão
  pós-incidente.
- Nenhuma ferramenta externa é contratada nesta tarefa; a escolha do
  serviço gerenciado fica para a LC.7/LC.2.
