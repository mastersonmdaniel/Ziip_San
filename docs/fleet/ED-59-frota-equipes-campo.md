# ED-59 — Frota e equipes de campo (ADR-0028 (i); OPE-28)

Spec executada: JSON autoritativo
`fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json`, tarefa
**ED-59 "Frota e equipes de campo"**, G (3), deps ED-36, ED-56, P1.35.
Pacote local, sem push/escrita no GitHub.

## Desenho

Módulo `shared/ziipsan-core`, pacote novo `br.com.ziipsan.core.fleet`
(mesmo módulo da ED-36, dona do motor de programação e do custo da OS;
precedente de pacote novo no core: ED-58 `core.inventory`). Nenhum
arquivo de vizinho é alterado.

| Classe | Papel |
|---|---|
| `FleetRegistry` | Veículo como registro de frota SOBRE o ativo da P1.35 (`assetId`); placa, licenciamento, situação, equipamentos especiais (só os 4 do aceite); verificador de existência do alvo `FLEET_ASSET` da P1.31 |
| `DriverDocuments` | CNH do motorista e licenciamento: VALID/EXPIRING_SOON/EXPIRED com `fleet.docs_alert_days`; multas por veículo e condutor |
| `FuelAndConsumption` | Abastecimentos, consumo médio (método declarado), alerta de desvio (limite do tenant), custo de combustível apropriado à OS |
| `VehicleMaintenance` | Manutenção de veículos como EXTENSÃO da rotina da ED-56: mesmo contrato (gatilho × intervalo amarrados, plano de trabalho com segurança, lead/tolerância dos parâmetros `maint.*` da ED-56, geração só pela porta de OS; finalidades VEHICLE_PREVENTIVE/VEHICLE_CORRECTIVE) |
| `FieldCrewScheduling` | Equipes (composição, motorista, escala/alocação datada), portão de alocação explicável, checklist pré-viagem e o FATO `equipmentAvailable` para a restrição dura `EQUIPMENT_WHEN_ED59` da ED-36 (OPE-33) |
| `FleetCosting` | Custo por veículo (combustível + manutenção + multas) e o valor de frota que preenche o campo `fleet` do `WorkOrderCost` da ED-36 |
| `FleetParameters` | Chaves `fleet.*` sem fábrica (ver relatório §3) |
| `FleetPorts` | `AssetPort` (P1.35) e `WorkOrderPort` (OS de veículo) |
| `FleetCoverage` | Registro OPE-28, OPE-33, OPE-14 (fronteira), ADR-0028 (i), COE-05 |

## Regras duras aplicadas

- **Documento vencido não aloca**: recusa dura no portão (licenciamento
  do veículo e CNH do motorista), sem parâmetro que a desligue;
  `fleet.docs_alert_days` só antecipa o alerta. Teste negativo
  obrigatório no `AllocationGateTest`.
- **Equipamento especial é restrição dura**: tipo de serviço que exige
  hidrojato/vácuo/limpa-fossa/retroescavadeira não é programado sem
  veículo ativo, válido e livre portando o recurso —
  `equipmentAvailable` alimenta o `Candidate.equipmentAvailable` da
  ED-36, filtrado por `EQUIPMENT_WHEN_ED59` em `sched.hard_constraints`.
- **Nenhum dado inventado**: alerta de documentos, itens de checklist e
  limite de desvio são parâmetros do tenant sem fábrica; intervalo de
  manutenção de veículo é do plano do tenant; preços vêm dos
  abastecimentos registrados.
- **Fronteira OPE-14**: habilitação/SST do executor segue a OS da
  P1.31 (portão da ED-36) — aqui só a CNH do motorista.
- **SQL**: `db/pendente/ED-59__*` sem número; toda tabela com
  `UNIQUE (tenant_id, id)` para as FKs compostas (lição da C3).

## Limites declarados

Sem JPA/entidades nem controller nesta entrega (padrão do núcleo da
ED-36/ED-58): retrato + portas + regras puras. A fiação (beans,
repositórios, leitura dos parâmetros, telas, registro do verificador
`FLEET_ASSET` no motor da P1.31 e a passagem do conjunto de restrições
duras ao `SchedulePlanner`) é da integração — pendências no relatório.
