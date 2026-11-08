# ED-20 — Acompanhamento de campo: governanca e fiacao

## Trilha GPS — LGPD (registro exigido pelo aceite)

- **Finalidade:** acompanhamento da execucao das ordens de servico em campo
  (deslocamento, tempo e paradas da jornada) — `Tracking.PURPOSE`.
- **Base legal:** LGPD, art. 7o, V (execucao de contrato) e IX (interesse
  legitimo) — `Tracking.LEGAL_BASES`.
- **So na jornada:** `field.track_only_in_shift` travado em verdadeiro;
  ponto fora da jornada e recusado na ingestao (`Tracking.ingest`).
- **Aviso ao empregado:** sem o registro do aviso, a trilha nao comeca
  (`Tracking.requireEmployeeNotice`) — e o portao e COMPOSTO na ingestao
  (correcao D-1): `Tracking.ingest(ponto, jornada, trava, aviso)` exige o
  aviso como parametro e recusa (nomeada) sem ele. Nenhum caminho legitimo
  de ingestao sem o aviso existe; `TrackStore.append` so recebe ponto que
  ja passou por `ingest` (ver P-ED20-02 no relatorio).
- **Acesso:** somente chefia (`Tracking.requireSupervisorAccess`), inclusive
  no painel (`GpsPanel.build`).
- **Retencao:** `field.track_retention_days`, so reduz face ao teto da
  plataforma (`FieldOsParameters.validateRetentionDays`); expurgo por
  `Tracking.purgeCutoff`.
- **RIPD do tenant (P3.10):** item `FIELD_GPS_TRACKING`
  (`Tracking.RIPD_ITEM_CODE`) — a fiacao grava este item no RIPD vigente do
  tenant na integracao; o texto do RIPD e da P3.10.

## Fiacao na integracao (portas -> donos)

| Porta (ED-20) | Dono real |
|---|---|
| `EnRouteNotifier` | ED-41 `MultichannelDispatchService.dispatch` (evento "a caminho"; template/categoria sao do tenant na ED-41) |
| `CutFactsPort` | leitura atual de debito/contestacao/aviso/vedacoes (billing + cadastro) |
| `OrderCancellationPort` | motor de OS da P1.31 (cancelamento sem efeito na ligacao) |
| `StockWriteOffPort` | ED-58 (baixa de estoque, ADR-0032) |
| `SacCasePort` | ED-35 (atendimento; pesquisa de satisfacao) |
| `ImporterPort` | P1.40 (importador) |
| `FaceBlurPort` | provedor de imagem do tenant (desfoque de rostos) — INVOCADA por `FieldExecution.registerPhoto`/`startApplyingBlur`/`completeApplyingBlur` (correcao D-2): toda foto registrada com `field.photo_blur_faces` ligado passa pela porta e o retorno vira a flag `facesBlurred` |
| `TrackStore` | persistencia `field_track_points` (SQL pendente) — `append` so de ponto ja ingerido por `Tracking.ingest` (portao do aviso composto, D-1) |

## Contrato de ausencia dos interruptores (correcao D-3)

`FieldOsParameters.trackOnlyInShift()` e `photoBlurFaces()` devolvem
`Optional<Boolean>`, nao `boolean` primitivo: ausencia de valor vigente
(`Optional.empty()`) e DISTINTA de `false` explicito do tenant.

- A implementacao de integracao dessas leituras DEVE devolver vazio
  quando nao houver valor vigente — NUNCA um default `false` por
  ausencia (um default falso desligaria o desfoque silenciosamente).
- Os consumidores resolvem SOMENTE por
  `FieldOsParameters.requireTrackOnlyInShift(Optional)` e
  `requirePhotoBlurFaces(Optional)`, que recusam com motivo nomeado na
  ausencia. E o mesmo desenho da trava de jornada (que ja recusava o
  `false` de `track_only_in_shift`); para o desfoque, o consumidor e
  `FieldExecution`, cujo construtor recebe o `Optional` e recusa nascer
  sem valor explicito.
- `false` explicito de `field.photo_blur_faces` e legitimo: desfoque
  desligado por decisao do tenant, foto registrada sem invocar a porta.

## Painel no mapa (P3.25)

`GpsPanel.Panel` e o dado (trilha + `Tracking.Summary`: distancia, tempo
rastreado, tempo em movimento, paradas) que a camada de publicacao GIS da
P3.25 desenha. Nenhuma tela nova e criada nesta entrega.

## Fora desta entrega (declarado no aceite)

- Qualidade do trabalho de leitura: ED-15. Assistencia por voz: ED-43.
- Solver de deslocamento de equipes (pgRouting/tempo de viagem): BLOQUEADO
  (decisao O8-1; `RoutingGate` da ED-36) — nao implementado.
- `contractorRef` (ED-36) x `supplierId` (P3.31): mapeamento e pendencia
  O9-4 — nenhuma fiacao inventada aqui.
