# ADR-0046 — Ingestão de supervisório somente leitura, por REST e arquivo, sem broker

- **Status:** Aceito, 29/10/2026 (proposto em 28/10/2026, decisão PD-04 da estrutura da v3.0; aceite do dono por cartão de 29/10/2026)
- **Decisor:** dono do produto
- **Exceção controlada a:** ADR-0010 (infraestrutura não usada congelada), dentro do ADR-0028, item j (telemetria por REST
  e arquivo; MQTT só sob demanda)
- **Afeta:** ED-17 (telemetria e supervisório), ED-87 (operação de estações), ED-93 (sala de situação), ED-85 e ED-86
  (perdas e pressão), P3.8 e LC.8 (pentest); achados OPE-30 (22b) e COE-20 (23a)

## Contexto

O ADR-0028, item j, decidiu telemetria por REST e arquivo, com MQTT só sob demanda, e o ADR-0010 registrou nas exceções
a ingestão de telemetria por REST e arquivo, sem componente novo, deixando MQTT e OPC congelados até ADR de exceção com
cliente pagante. O código atual ainda tem um cliente MQTT (Paho) no módulo de qualidade, que a P0.25 remove.

O 22b (OPE-30) mostrou que os prestadores com supervisório (SCADA) guardam as medições num historiador, na rede
operacional, e que o padrão de mercado para levar esses dados a um sistema de gestão é a coleta unidirecional por um
agente de saída do lado do cliente, sem porta de entrada na rede operacional, coerente com o modelo de zonas e conduítes
da série ISA/IEC 62443 (partes 3-2, 3-3, 4-1 e 4-2). Saneamento é infraestrutura crítica (Decreto 9.573/2018 e
Decreto 10.569/2020, segundo o 22b). Sem contrato fixado, a primeira implantação com supervisório pediria um broker ou
uma leitura direta do historiador, contra o ADR-0010.

## Decisão (proposta)

1. **Somente leitura e unidirecional.** O Ziip SAN nunca escreve em CLP, RTU, IHM nem historiador e não envia comando à
   rede operacional. Os dados só entram.
2. **Agente de saída do lado do cliente.** A coleta no historiador (por OPC UA, MQTT com Sparkplug B, SQL somente
   leitura ou CSV agendado) é feita por um agente fora da infraestrutura do Ziip SAN, que abre só conexões HTTPS de
   saída, guarda localmente o que não conseguiu enviar e reenvia quando o enlace volta. Quem fornece o agente (cliente,
   integrador do supervisório ou agente de referência publicado pelo Ziip) é definido na ED-17.
3. **Entrada pelo que já existe.** O agente entrega por REST (endpoint na `api`) ou por arquivo (importação no
   `worker`), as mesmas formas da exceção de telemetria do ADR-0010. Nenhum broker, servidor OPC ou conector MQTT entra
   na infraestrutura do Ziip SAN.
4. **Contrato do envelope.** Esquema versionado do envelope, com tenant, agente, ponto de medição, instante e valor;
   assinatura do envelope e credencial por agente, emitida pelo tenant e revogável; idempotência por amostra (ponto e
   instante); limite de taxa por tenant e por agente; rejeição registrada com motivo. Teste de contrato por versão do
   envelope.
5. **Persistência.** Tabela particionada no PostgreSQL, sem auditoria por linha, com retenção por parâmetro do tenant,
   como na ED-17; alarmes e paradas como registros próprios; consumo pela ED-87, pela ED-93 e pelo módulo de perdas.
6. **Exceção controlada.** O que continua congelado não muda: broker, MQTT, OPC e leitura direta do historiador pelo Ziip
   SAN só entram por novo ADR, com cliente pagante (ADR-0028, item j).
7. **Registro no ADR-0010.** Aceito este ADR, a seção "Exceções" do ADR-0010 ganha a linha "Ingestão de supervisório
   somente leitura (sem componente novo)", com origem neste ADR.

## Consequências

### Positivas

- A primeira implantação com supervisório não exige broker nem exceção nova.
- A rede operacional do cliente não recebe conexão de entrada nem comando do Ziip SAN.
- O mesmo caminho de ingestão serve a telemetria de grandes consumidores e ao supervisório.

### Negativas

- Depende de um agente do lado do cliente, que precisa ser instalado, mantido e monitorado fora do produto.
- A latência é a do envio do agente; controle em tempo real fica fora do escopo.

## Alternativas consideradas

- **Broker MQTT na infraestrutura do Ziip SAN:** fora do ADR-0010 e do ADR-0028, item j; só com cliente pagante e ADR.
- **Leitura direta do historiador pelo Ziip SAN (OPC UA ou SQL):** exige conexão de entrada na rede operacional do
  cliente; rejeitada pelo risco de infraestrutura crítica.
- **Só registro manual de paradas e diário de operação:** continua disponível (ED-87), mas não atende quem tem
  supervisório.

## Tarefas derivadas

- ED-17: endpoint, importação de arquivo, contrato do envelope, credencial por agente e teste de contrato; definição do
  fornecedor do agente.
- ED-87, ED-93, ED-85 e ED-86: leitura dos dados ingeridos.
- ADR-0010: linha na seção "Exceções" depois do aceite.
- P3.8 e LC.8: caminho de ingestão no escopo do pentest.
