# ED-66 — Campo GIS no app Capacitor (GIS-15): desenho, fiação e pendências

Fonte do desenho: `GIS-CADASTRO-TECNICO.md` §8 (Campo), §3.8 (metadados
de captura), §3.9 (referências/altimetria), §6.1/§6.3/§6.4 (pacotes,
concorrência, as-built) e ADR-0034 decisões 3 e 5. Base do app: ED-18
(Capacitor, dispositivo, sync) e ED-20 (OS de campo, privacidade
OPE-11). Cadastro/pacotes: ED-53/GIS-04 (`gis_change_set` tipo FIELD).

## O que vive onde

| Peça | Onde | Papel |
|---|---|---|
| `campo/src/gis/gnss.ts` | app | Fix GNSS (porta do receptor), tipo de solução, PDOP, portão do as-built (pré-conferência offline) |
| `campo/src/gis/fieldPackage.ts` | app | Pacote offline por área: validade, camada pessoal (`gis.view-personal`), meta por classe |
| `campo/src/gis/gisDelta.ts` | app | Deltas: `client_change_id` UUIDv7, versão de base, diff por atributo |
| `campo/src/gis/fieldPhoto.ts` | app | Foto: posição, azimute, hash, varredura, desfoque (ED-20) |
| `campo/src/gis/sketch.ts` | app | Croqui vetorial com amarrações (alinhamento predial/meio-fio) |
| `campo/src/gis/gisDeltaSync.ts` | app | Adaptador de payload sobre o SyncEngine da ED-18 (não é motor novo) |
| `gis/field/GisFieldRules` | servidor | Espelho definitivo das regras (as-built, foto, camada pessoal) |
| `gis/field/FieldDeltaApplier` | servidor | Idempotência por `client_change_id` + conflito por atributo |
| `gis/field/AltitudeConversion` | servidor | hgeoHNOR2020 por porta: H = h − antena − N; sem grade, recusa |
| `db/pendente/ED-66__gis_field.sql` | servidor | `gis_field_package`, recibo de delta, fila de conflito, extensão da foto, croqui |

## Fiação na integração (portas → donos)

| Porta/peça | Dono real |
|---|---|
| `GnssSource` (TS) / plugin Bluetooth NMEA | plugin nativo novo do app (P-ED66-01) — receptor RTK externo; correção NTRIP pelo celular é do plugin/sistema |
| Motor de sync, diretivas BLOCK/RETIRE, wipe, dispositivo | ED-18 (`SyncEngine`, `FieldDeviceService`, core V11) — bloqueio remoto já é o BLOCK/RETIRED de lá; nada duplicado aqui |
| Desfoque de rostos | `field.photo_blur_faces` + `FaceBlurPort` da ED-20 (mesma regra, mesmo contrato de ausência D-3); a flag `facesBlurred` é DERIVADA invocando a porta em `registerFieldPhoto` (TS) / `GisFieldRules.capturePhoto` (Java) — nunca autodeclarada (correção m1) |
| Varredura da foto | pipeline de anexos da P1.20 (ClamAV) — a foto nasce QUARANTINE e só CLEARED sustenta cadastro |
| Pacote de edição dos deltas | `gis_change_set` tipo `FIELD`, canal `FIELD` (V13_9) + revisão por outra pessoa (§6.2) — delta aplicado ≠ rede alterada: a rede muda na aprovação do pacote |
| OS que altera a rede | ED-20 abre a feição; a alteração vira item em pacote `SERVICE_ORDER` (§8) — fronteira da ED-20/ED-36, não desta entrega |
| Grade geoidal | hgeoHNOR2020 (IBGE) carregada no servidor (P-ED66-04); o núcleo só conhece a porta |
| Resolução de conflito | tela web da fila `gis_field_conflict` (a revisão de pacotes da GIS-04 é a base; tela específica é integração) |

## Privacidade (ED-20, OPE-11) — como se aplica aqui

- A posição da FOTO é a do fix da coleta (um ponto, com a feição), não
  uma trilha do executor: nenhum ponto de `field_track_points` nasce
  deste pacote e a governança da trilha (só na jornada, aviso ao
  empregado, acesso só da chefia, retenção) continua sendo a da ED-20.
- Foto de campo segue `field.photo_blur_faces` com o contrato de
  ausência da ED-20 (D-3): parâmetro ausente no pacote/servidor =
  recusa nomeada, nunca `false` presumido.
- Camadas pessoais (imóvel/ligação) só descem no pacote de quem tem
  `gis.view-personal` (§8/§6.6), conferido na montagem (servidor) e na
  abertura (aparelho).

## Massa de ensaio

Todas as coordenadas, ondulações, hashes e feições dos testes são
SINTÉTICOS de bancada, declarados nos próprios testes. Nenhum dado de
rede, tenant ou pessoa real foi usado ou inventado.
