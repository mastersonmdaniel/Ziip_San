# ED-04 — Runbook de migração / reversão de um tenant

Pacote de reversão em contêineres Linux (ADR-0024), construído sobre o ambiente local
da P0.27. Este runbook é exercitado do início ao fim no ensaio da ED-04 — **uma vez em
servidor Linux limpo e uma vez em Windows Server 2022+ com Docker no WSL2** — e cada
execução preenche o **Registro do Ensaio** ao final deste documento. A sequência
executável dos ensaios está em `docs/reversao/ROTEIRO-DE-ENSAIO.md`.

**Escopo e separação (COE-04):** este runbook cobre só a reversão do tenant para o
pacote local. A instância dedicada na AWS e o acesso administrativo do órgão (somente
leitura, MFA, auditoria) são da **LC.15**; o perfil externo do regulador e do titular
(somente leitura) é da **ED-75**. Nada disso é executado nem prometido aqui.

**Exceção de infraestrutura:** o cofre próprio e o armazenamento de objetos
compatível com S3 entram só neste pacote de reversão, registrados como exceção ao
ADR-0010 com origem no ADR-0024 (item 4) — ver o adendo
`docs/arquitetura/adr/ADR-0010-EXCECAO-REVERSAO-ED04.md`, com as linhas para a tabela
da seção Exceções do ADR-0010.

## Pré-condições

1. Exportação do tenant de origem disponível e íntegra (passo 1), **sintética no
   ensaio** — nenhum dado real é usado na homologação da ED-04.
2. Servidor de destino conforme `docs/reversao/REQUISITOS-MINIMOS.md`.
3. `.env.reversao` preenchido fora do repositório (modelo:
   `reversao/.env.reversao.example`), com a tag fixa da imagem da aplicação e as
   credenciais locais do pacote. Nenhuma credencial do SaaS é reutilizada.
4. Janela registrada: a reversão é operação assistida, com operador e conferente
   (duas pessoas) identificados no registro do ensaio.

## Passo 1 — Exportar o tenant na origem

Fonte da exportação: a cópia lógica diária do schema do tenant (mecanismo da P3.33,
que também alimenta a exportação de fim de contrato da P3.16), no formato da P3.16
(CSV, JSON ou XML para os dados tabulares, com manifesto, hash e dicionário de dados).

1. Gerar o dump do schema do tenant: `pg_dump -n <schema_name>` (precedente da
   P3.33), gravado como `banco/tenant.sql` (ou formato custom como
   `banco/tenant.dump`).
2. Gerar `banco/catalogo.sql` com a linha do tenant em `public.tenant_catalog`
   (somente a linha desse tenant, sem segredos e sem linhas de outros tenants).
3. Gerar `manifesto.json` (tenant, schema, versão do produto, data, contagens por
   tabela) e `SHA256SUMS` de todos os arquivos da exportação.
4. Conferir: `sha256sum -c SHA256SUMS` verde na origem antes de transferir.

## Passo 2 — Preparar o servidor de destino

1. Conferir os requisitos mínimos (no Linux: kernel 5 ou superior; no Windows:
   WSL2 ativo e virtualização aninhada; todos os comandos seguintes rodam no
   shell do WSL2).
2. Copiar o pacote do tag da versão, preservando a estrutura relativa: a pasta
   `reversao/` **e** a árvore `infra/` (`postgres/init`, `keycloak/realms`,
   `vault/config.hcl`). Sem a `infra/`, o pacote está incompleto — o init do
   PostgreSQL, a importação do realm do Keycloak e a configuração do cofre
   vêm dela.
3. Criar `.env.reversao` a partir do exemplo (`reversao/.env.reversao.example`)
   e preenchê-lo fora do repositório.
4. Seleção de processo da imagem única: é por `command:` no compose — o serviço
   `api` usa `command: api` e o serviço `migrate` usa `command: migrate`, como
   no compose real do ambiente da P0.27. Não há processo `worker` nesse
   ambiente. Registrar a conferência no Registro do Ensaio.

## Passo 3 — Subir o pacote

```bash
cd reversao
./subir.sh
```

Sobe `postgres-primary` (PostgreSQL 17 com PostGIS 3.5, com o init de
`infra/postgres/init`), `keycloak` (26.8.0, com importação do realm de
`infra/keycloak/realms`), `vault` (cofre próprio) e `minio` (armazenamento de
objetos compatível com S3, SeaweedFS S3), roda o serviço `migrate`
(`command: migrate`, que aplica as migrations de core e as de tenant) e sobe o
serviço `api` (`command: api`) com `ziipsan.fiscal.production-allowed=false`
(regra da P0.27 para ambientes locais). Não há serviço `worker` no pacote.

**Objetos no S3:** a carga dos objetos do tenant no armazenamento compatível
com S3 segue o formato de exportação quando ele for confirmado pelas tarefas
P3.16/P3.33. Hoje o importador do pacote cobre o banco (passo 4); nenhum formato
de carga de objetos é presumido nem inventado aqui — o que for carregado, e em
que formato, é declarado no Registro do Ensaio.

## Passo 4 — Importar a exportação do tenant

```bash
./importar-tenant.sh <diretorio-da-exportacao>
```

O script confere manifesto e hashes, **aborta se o schema do tenant já existir**,
restaura o dump, aplica `catalogo.sql` e registra `environment = 'REVERSAO'` na
linha do tenant, mantendo o `isolation_mode` que veio do catálogo
(core V7: `SCHEMA`, `SHARED` ou `DEDICATED`).

## Passo 5 — Testes de fumaça

```bash
./testes-fumaca.sh
```

Itens (os 7 do script): banco responde; tenant presente no catálogo com modo de
isolamento válido e ambiente `REVERSAO`; `GET /api/v1/about` responde 200; realm
do Keycloak responde 200; cofre próprio responde; armazenamento S3 responde;
flag fiscal de produção desligada. **Todos** devem passar; item pulado não conta
como passado.

## Passo 6 — Encerrar o ensaio / entregar ao tenant

1. `./parar.sh` (preserva o volume do banco) ou manter o pacote no ar, conforme a
   decisão registrada.
2. Preencher o Registro do Ensaio abaixo e anexar as saídas de `subir.sh`,
   `importar-tenant.sh` e `testes-fumaca.sh`.

## Critérios de aceite do ensaio

- Subida completa nos **dois** ambientes, com tempos medidos registrados.
- Importação do tenant sintético sem divergência de hash e com contagens do
  manifesto conferidas no destino.
- Testes de fumaça verdes nos dois ambientes, sem item pulado.
- Registro do ensaio preenchido e assinado por operador e conferente.

## Critérios de abortar (qualquer um interrompe e registra o motivo)

- Hash divergente na exportação, ou manifesto incompatível com o conteúdo.
- Schema do tenant já existente no destino (o pacote nunca sobrescreve em silêncio).
- WSL2/virtualização aninhada indisponível no ambiente Windows.
- Migration falhando no `migrate` (o pacote não sobe com migration parcial).
- Qualquer sinal de dado de **outro** tenant na exportação ou no destino.
- Imagem `postgis/postgis:17-3.5` indisponível para pull no servidor de destino.

Abortado o ensaio, o destino é descartado (volume removido de forma registrada) e a
origem **não é tocada** em nenhum passo deste runbook: a reversão é sempre uma cópia
nova no destino, nunca uma alteração da origem.

## Registro do Ensaio (template — um por ambiente)

| Campo | Valor |
|---|---|
| Ambiente (Linux limpo / Windows Server 2022+ WSL2) | |
| Servidor (identificação, sem credencial) | |
| Versões: imagem Ziip (tag) / Keycloak | |
| Seleção de processo (`command:`) conferida (`command: api` / `command: migrate`) | sim / não |
| Versões medidas: Docker / Compose / kernel / PostGIS | |
| Cofre (`vault`) e S3 (`minio`) no ar | sim / não |
| Tenant sintético (schema_name) e isolation_mode | |
| Início / fim da subida (tempo medido) | |
| Início / fim da importação (tempo medido) | |
| Hashes conferidos (SHA256SUMS) | sim / não |
| Testes de fumaça: resultado item a item | |
| Divergências e decisões durante o ensaio | |
| Critério de abortar acionado? | não / sim — qual |
| Operador (nome e papel) | |
| Conferente (nome e papel) | |
| Data | |
