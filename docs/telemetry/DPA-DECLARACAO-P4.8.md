# Declaração para o DPA — telemetria de produto (P4.8 / PLT-42)

> Esta é a DECLARAÇÃO do tratamento feita pela P4.8. O registro formal
> no DPA é da **LC.9** ("DPA registra a telemetria de produto agregada
> (ver P4.8)"), fora desta tarefa — nada aqui finge o registro feito.

- **Tratamento declarado:** telemetria de uso do produto (adoção de
  funcionalidades, tempo até o primeiro ciclo, abandono de assistentes,
  tarefas mais repetidas), controlada pelo parâmetro
  `telemetry.product_usage` (padrão `AGGREGATED`; `OFF` desliga).
- **Forma:** somente AGREGADO por tenant e por papel.
- **O que NÃO é coletado:** identificador de pessoa, gravação de sessão
  e conteúdo digitado — recusados na ingestão
  (`TelemetryPrivacyGuard`, código `telemetry-forbidden-field`) e
  ausentes por construção do tipo do evento.
- **Usos declarados:** aba Adoção da Central Ziip, painel de uso do
  tenant e priorização do changelog.
- **Papel das partes (para a LC.9 registrar):** conforme ADR-0002/
  ADR-0019, Ziip SAN como operador e o tenant como controlador — a
  confirmação e a assinatura são da LC.9.
