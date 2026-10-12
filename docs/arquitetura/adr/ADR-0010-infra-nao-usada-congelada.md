# ADR-0010 — Infraestrutura não usada congelada

- **Status:** Aceito, 12/10/2026 (execução no P0; nesta data, só o registro)
- **Decisor:** dono do produto
- **Afeta:** revisão 17, §4.3 (compose expõe serviços não usados), achado O2 (Terraform), §7 (lista de congelamento) e
  §8.5

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
