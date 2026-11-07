# ED-04 — Requisitos mínimos do pacote de reversão

Publicados para os dois ambientes de homologação da ED-04 (ADR-0024). O pacote de
reversão contém, pelo item 3 do ADR-0024: a imagem da aplicação, PostgreSQL 17 com
PostGIS 3.5, Keycloak (26.8.0 no ambiente real), cofre próprio e armazenamento de
objetos compatível com S3. A imagem da aplicação é única, com os processos `api` e
`migrate` selecionados por `command: api` / `command: migrate` no compose — não há
processo `worker` no ambiente da P0.27.

> **Natureza dos números:** CPU, memória e disco abaixo são a **base de ensaio da
> ED-04**, proposta para a homologação única e sujeita a ajuste com o resultado
> medido do ensaio (tempo de subida e de importação registrados no runbook). Não são
> requisito de edital nem compromisso comercial.
>
> **Versões medidas:** Docker Compose v2 é requisito explícito (ver tabelas). As
> versões exatas medidas de Docker, Compose e kernel no servidor do ensaio **não**
> são presumidas aqui: são registradas no **Registro do Ensaio** do runbook, a
> partir da saída dos comandos de pré-voo (ver `ROTEIRO-DE-ENSAIO.md`).

> **Layout do pacote:** o pacote é distribuído como a árvore `reversao/` +
> `infra/` (`postgres/init`, `keycloak/realms`, `vault/config.hcl`) do tag da
> versão, preservando a estrutura relativa entre as duas árvores. Copiar só a
> `reversao/` sem a `infra/` deixa o pacote incompleto: é do init que nascem as
> roles do banco, é de `keycloak/realms` que o realm é importado e é de
> `vault/config.hcl` que o cofre lê a sua configuração.

## Pilha do pacote (os dois ambientes)

| Componente | Versão / forma |
|---|---|
| Banco | PostgreSQL 17 com PostGIS 3.5 (serviço `postgres-primary`) |
| Identidade | Keycloak 26.8.0 (serviço `keycloak`), com banco próprio no PostgreSQL e importação do realm de `infra/keycloak/realms` |
| Cofre | Cofre próprio (serviço `vault`), só no pacote de reversão — exceção ao ADR-0010 com origem no ADR-0024 (item 4) |
| Objetos | Armazenamento de objetos compatível com S3 (serviço `minio`, SeaweedFS S3), só no pacote de reversão — exceção ao ADR-0010 com origem no ADR-0024 (item 4) |
| Aplicação | Imagem única; processos `api` e `migrate` selecionados por `command: api` / `command: migrate` (serviços `api` e `migrate`) |

## Ambiente 1 — Servidor Linux limpo

| Item | Mínimo (base de ensaio) |
|---|---|
| Sistema | Linux 64 bits capaz de rodar Docker Engine (distribuição a critério do órgão) |
| Kernel | **Kernel Linux 5 ou superior** — exigência registrada no ADR-0024 a partir dos editais (SAMAE, SG-004/SG-019) |
| Contêineres | Docker Engine + **Docker Compose v2** (requisito explícito; versão exata medida registrada no Registro do Ensaio) |
| CPU | 4 vCPU |
| Memória | 16 GB RAM |
| Disco | 100 GB livres em SSD (banco, imagens e exportação do tenant) |
| Rede | Saída para puxar as imagens na instalação; depois da instalação, o pacote opera sem dependência da AWS nem do SaaS |
| Acesso | Usuário com permissão de operar o Docker; portas do pacote, por padrão, só em `127.0.0.1` (a exposição da `api` à rede do órgão é decisão registrada no ensaio) |
| Dados | Somente a exportação do tenant (manifesto + hashes, formato da P3.16); nenhum dado de outro tenant |

## Ambiente 2 — Windows Server 2022 ou superior, com Docker no WSL2 (homologação única)

| Item | Mínimo (base de ensaio) |
|---|---|
| Sistema | Windows Server 2022 ou superior |
| Virtualização | WSL2 habilitado; **virtualização aninhada** disponível quando o servidor é uma VM — é o risco técnico registrado do pacote (ADR-0024): sem ela, o WSL2 não sobe e o ensaio neste ambiente não é válido |
| Contêineres | Docker rodando **dentro do WSL2** (contêineres Linux), com **Docker Compose v2** (requisito explícito; versão exata medida registrada no Registro do Ensaio); os comandos do pacote são os mesmos do ambiente Linux, executados no shell do WSL2 |
| CPU | 4 vCPU (na VM e reservados ao WSL2) |
| Memória | 16 GB RAM (limite do WSL2 igual ou maior, se o servidor tiver outros papéis) |
| Disco | 100 GB livres em SSD |
| Rede | Idem ao ambiente Linux |
| Regra de homologação | Homologação **única** neste ambiente (aceite da ED-04). Nova homologação só se um cliente adotar Windows na reversão (mitigação registrada no plano) |

## Fora destes requisitos (não pertence à ED-04)

- Instância dedicada na **AWS** (conta separada, mesma IaC/imagem/pipeline) e acesso
  administrativo do órgão, somente leitura, com MFA e auditoria: **LC.15**.
- Perfil externo do regulador e do titular, somente leitura: **ED-75** (GES-22).
