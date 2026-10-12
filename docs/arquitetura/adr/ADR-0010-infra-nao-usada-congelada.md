# ADR-0010 — Infraestrutura não usada congelada

- **Status:** Aceito, 12/10/2026 (execução no P0; nesta data, só o registro)
- **Decisor:** dono do produto
- **Afeta:** revisão 17, §4.3 (compose expõe serviços não usados), achado O2 (Terraform), §7 (lista de congelamento) e
  §8.5
- **Ajuste de 12/10/2026 (editais), antes de qualquer implementação:** hospedagem decidida (ADR-0011, AWS
  `sa-east-1`), o que tira o perfil `vault` do caminho de produção; seção "Exceções" ampliada com o Apache Superset
  (ADR-0027), o ODA File Converter como ferramenta de implantação e a ingestão de telemetria por REST e arquivo, sem
  broker (ADR-0028); decisão do dono do produto.
- **Ajuste de 12/10/2026 (v2.7), antes de qualquer implementação:** com o desenvolvimento local até o lançamento
  comercial (ADR-0033), o cofre local e um emulador compatível com S3 deixam de estar congelados e passam a componentes
  do ambiente local, em perfil próprio do compose, com portas em 127.0.0.1, credenciais fora do repositório e o cofre
  fora do modo dev; ficam registrados na seção "Exceções"; na produção, a partir do lançamento, valem KMS, Secrets
  Manager e S3 (ADR-0011); a consequência negativa sobre o Vault dev "enquanto a hospedagem não for decidida" e a
  tarefa "perfil `vault` documentado para o desenvolvimento do R1" leem-se com o cofre local como provedor do R1 até o
  lançamento; o congelamento "até o go-live" (O que muda no roadmap) vale até o lançamento; decisão do dono do produto.
- **Ajuste de 12/10/2026 (GIS, v2.10), antes de qualquer implementação:** com o GIS do ADR-0034, a seção "Exceções"
  passa a registrar a extensão pgRouting no PostgreSQL (validação em lote da rede e rotas de coleta sobre logradouros),
  sem processo novo, e o endpoint de banco próprio para o QGIS, com TLS e auditoria, fora do PgBouncer da `api`, sem
  componente novo; a linha do ODA File Converter passa a valer desde a P3.26 (núcleo do GIS, antes do P4) e não só na
  trilha. Continuam congelados GeoServer, QGIS Server, Martin e pg_tileserv: os tiles saem da `api`, e servidor de
  mapas (Martin, como plano B de desempenho, ou WMS servidor exigido por edital) só entra por nova linha nesta seção;
  decisão do dono do produto.

## Contexto

O `docker-compose.yml` sobe, além do PostgreSQL e do Keycloak (os únicos que a aplicação usa), serviços que nenhum
módulo consome: TimescaleDB (linhas 57-66, porta 5434), Redis sem senha (83-89, porta 6379), Kafka (97-114, portas
9092 e 9093), Zookeeper (126), SeaweedFS no papel de MinIO (147-159, portas 9000, 8888, 9333 e 8081) e Vault em modo
dev com root token fixo e sem TLS (220-227, porta 8200). Esses serviços publicam portas em todas as interfaces
(0.0.0.0), ao contrário do PostgreSQL e do Keycloak, já ligados a 127.0.0.1 (linhas 25 e 207). Nenhum `pom.xml`
depende de cliente Redis, Kafka, MinIO ou Vault.

`infra/kubernetes` (manifestos de Kafka, Redis, MinIO, Loki, Tempo, Pyroscope, Grafana, Prometheus, Tekton) não é
funcional e usa Secrets "changeme" e tags `latest`. `infra/terraform` é inválido, aponta para `us-east-1`
(`infra/terraform/main.tf:22,88`), contrariando a residência no Brasil, e grava senhas, token do Vault e chave SSH como
outputs no state (O2).

Manter isso ativo custa memória e tempo de subida, amplia a superfície de ataque na máquina de desenvolvimento e
sugere ao leitor uma arquitetura que não existe.

## Decisão

1. Kafka, Zookeeper, Redis, MinIO/SeaweedFS, TimescaleDB e Vault dev **saem do `docker compose up` padrão** e passam a
   **perfis opcionais** (`profiles:`), com portas ligadas a **127.0.0.1**.
2. `infra/kubernetes` e `infra/terraform` vão para **`infra/_arquivado/`**, com nota explicando por que foram
   arquivados, o que não serve mais (região, segredos no state) e que não devem ser reativados como estão.
3. **Reativar só com caso concreto**: um requisito que o PostgreSQL (fila por outbox, `SKIP LOCKED`) ou o serviço
   gerenciado da hospedagem escolhida não atenda, registrado em ADR.
4. A execução fica para o P0; nesta data só se registra a decisão.

## Consequências

### Positivas

- Ambiente de desenvolvimento menor e mais rápido, sem serviços expostos na rede local.
- A arquitetura visível no repositório passa a corresponder à que roda.
- Elimina o risco de alguém aplicar o Terraform atual e criar recursos fora do Brasil com segredos no state.

### Negativas

- Quem precisar de um desses serviços para experimento tem de ligar o perfil explicitamente.
- O Vault dev continua necessário para desenvolver o R1 enquanto a hospedagem não for decidida (ADR-0011); fica num
  perfil próprio e documentado, e não no padrão.
- O conteúdo arquivado envelhece; se reativado depois, precisa ser revisado do zero.

### O que muda no código

- `docker-compose.yml`: `profiles:` nos seis serviços e portas `127.0.0.1:...`; padrão sobe só PostgreSQL e Keycloak.
- `scripts/dev-up.sh` e `README.md`: perfis disponíveis e quando usar.
- `infra/kubernetes` e `infra/terraform` movidos para `infra/_arquivado/` com `LEIA-ME`. A pasta `infra/ansible`, hoje
  vazia, pode ser removida na mesma tarefa.
- Os blocos já comentados no compose (GeoServer, QGIS Server, Grafana, Prometheus, Loki, Tempo, Pyroscope, Gitea,
  Penpot, GlitchTip) seguem a mesma regra: sair do arquivo ativo para o arquivo morto.

### O que muda no roadmap

- Confirma a lista de congelamento da revisão 17 §7 até o go-live. A IaC nova nasce no P0, depois da hospedagem
  (ADR-0011).

## Alternativas consideradas

- **Remover tudo do repositório.** Rejeitada: perde a referência de configuração já pesquisada (por exemplo, Patroni e
  Keycloak no k8s) sem ganho em relação ao arquivamento.
- **Manter como está.** Rejeitada: serviços sem senha expostos em 0.0.0.0 e Terraform perigoso no caminho principal.
- **Corrigir os manifestos e o Terraform agora.** Rejeitada: o desenho depende da hospedagem, ainda não decidida.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Perfis opcionais e portas em 127.0.0.1 no compose; padrão só com PostgreSQL e Keycloak | P0 |
| Perfil `vault` documentado para o desenvolvimento do R1 | P0 |
| Mover `infra/kubernetes` e `infra/terraform` para `infra/_arquivado/` com nota | P0 |
| Retirar do compose os blocos comentados | P0 |

## Exceções

Componente novo só entra pelo item 3 da decisão: caso concreto que o PostgreSQL ou o serviço gerenciado da hospedagem
não atenda, registrado aqui com o ADR de origem.

| Componente | Caso concreto | Forma | Substituição | Origem |
|---|---|---|---|---|
| ClamAV | Arquivos enviados por usuários: anexos de chamados (ADR-0020) e logos de tenant (ADR-0016). O PostgreSQL não faz varredura de malware | Contêiner junto do processo `worker`, sem porta publicada; o arquivo fica em quarentena até a varredura e só então é liberado; infectado permanece bloqueado e gera alerta; atualização de assinaturas monitorada | Serviço equivalente da hospedagem (ADR-0011), atrás da mesma interface de varredura | Decisão do dono do produto, 12/10/2026 |
| Apache Superset (licença Apache 2.0) | Consultas livres, modelo semântico e cubos exigidos pelos editais (`18g` §2.5), que o PostgreSQL e os relatórios nativos não atendem | Contêiner próprio, a partir do P4, lendo só a réplica de leitura; SSO pelo Keycloak; uma role de banco por tenant com restrição pela identidade da conexão; modo síncrono, metadados no PostgreSQL; Redis e Celery não entram sem nova exceção | Nenhuma prevista; relatórios e painéis padrão continuam nativos | ADR-0027 |
| ODA File Converter (gratuito, licença proprietária) | Conversão de DWG atual para DXF na migração do cadastro técnico; o GDAL aberto não lê DWG 2007+ (`18d` §4, decisão 3) | Ferramenta da etapa assistida de implantação, fora da imagem de produção e do `docker compose`; o produto importa só formatos abertos; licença de uso comercial conferida antes do primeiro uso | SDK licenciado da ODA ou conversor equivalente, se a licença mudar | ADR-0028, item b |
| Cofre local (Vault ou OpenBao) | R1 e segredos de execução no ambiente local até o lançamento comercial; KMS e Secrets Manager só existem a partir dele (ADR-0033) | Contêiner em perfil próprio do compose, porta em 127.0.0.1, fora do modo dev (armazenamento persistente, sem root token fixo), credenciais fora do repositório; nunca guarda segredo de produção | AWS KMS e Secrets Manager no lançamento (ADR-0011), atrás das mesmas interfaces | ADR-0033 |
| Emulador compatível com S3 | Armazenamento de objetos no ambiente local até o lançamento, inclusive o teste de retenção imutável do XML autorizado (plano P2.18) | Contêiner em perfil próprio do compose (o SeaweedFS já existente ou equivalente, escolhido na P0.27), porta em 127.0.0.1; só dado sintético ou anonimizado | S3 com Object Lock no lançamento, atrás da mesma interface | ADR-0033 |
| pgRouting (extensão do PostgreSQL, sem processo novo) | Validação de componentes conexos da rede em lote e roteirização da coleta de resíduos sobre o grafo de logradouros (ADR-0034); traçado e isolamento continuam em Java sobre a topologia persistida | Extensão criada no init do banco, como a PostGIS, nunca pela migration de tenant; versão conferida na P0.26 (documental) e na LC.1 (prática) | Rotina própria em Java, se a extensão não estiver disponível | ADR-0034 |
| Endpoint de banco para o QGIS (sem componente novo) | Edição e consulta pelo QGIS com role individual por usuário (ADR-0034, decisão 7); o modo transaction do PgBouncer da `api` não é adequado a sessões longas do QGIS | Listener próprio do PostgreSQL com TLS e verificação do servidor, acesso por VPN ou IP de origem, conexões e comandos auditados; no ambiente local até o lançamento (ADR-0033) | Endpoint dedicado do RDS no lançamento | ADR-0034 |
| Ingestão de telemetria por REST e arquivo (sem componente novo) | Telemetria de grandes consumidores com alarmes (TR-210) e paradas da ETA (SG-810) | Endpoint na `api` e importação no `worker`, em tabela particionada no PostgreSQL; registrada aqui para deixar explícito que não há broker; MQTT e OPC seguem congelados e só entram por ADR de exceção com cliente pagante | — | ADR-0028, item j |
