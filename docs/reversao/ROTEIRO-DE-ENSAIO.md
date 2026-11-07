# ED-04 — Roteiro de ensaio do pacote de reversão

> **Onde este roteiro roda:** na infraestrutura do dono — um servidor Linux limpo
> e um Windows Server 2022 ou superior com Docker no WSL2. **Ele não roda nesta
> VM de desenvolvimento** (sem Docker) e nenhum ensaio é dado como executado
> sem o Registro do Ensaio preenchido (template no runbook).
>
> **O que só se fecha com estes ensaios:** os itens **3, 4, 6, 7 e 8** do
> acceptance da ED-04 — o pacote sobe em Linux limpo (3); sobe em Windows
> Server 2022+/WSL2 (4); nos dois ambientes, carrega a exportação do tenant
> sintético (6); nos dois ambientes, passa na fumaça (7); e o runbook é
> exercitado do início ao fim, com registro (8). Sem os dois ensaios, com os
> dois registros, esses itens **não** se fecham.
>
> **Dados do ensaio:** tenant **sintético** apenas. Nenhum dado real, nenhuma
> credencial do SaaS e nenhuma credencial neste documento.

Documentos de apoio: `REQUISITOS-MINIMOS.md` (pilha e mínimos dos dois
ambientes) e `RUNBOOK-MIGRACAO-REVERSAO.md` (passos, critérios e template do
Registro do Ensaio). Este roteiro é a sequência executável daquele runbook.

## 1. Pré-voo — por ambiente

Rodar **antes** de qualquer subida, no ambiente do ensaio, e guardar a saída:
ela alimenta os campos de versões medidas do Registro do Ensaio.

### 1.1. Comum aos dois ambientes

Antes dos comandos: estar no diretório do pacote (`cd reversao`, com
`reversao/` + `infra/` copiados) e criar o `.env.reversao` preenchido —
é o Passo 1 da seção 4, antecipado porque a conferência de serviços abaixo
já o usa.

```bash
docker --version
docker compose version
docker compose -f docker-compose.reversao.yml --env-file .env.reversao config --services
```

Resultado esperado: Docker responde; **Docker Compose v2** responde; a lista
de serviços do pacote é `postgres-primary`, `minio`, `keycloak`, `vault`,
`migrate` e `api` (sem `worker`).

- **Aceite da etapa:** os dois comandos de versão respondem e a lista de
  serviços confere.
- **Abortar:** Docker ausente, Compose que não seja v2, ou serviço divergente
  da lista acima — registrar o motivo e não seguir.

### 1.2. Linux limpo

```bash
uname -r
```

Resultado esperado: kernel **5 ou superior** (exigência do ADR-0024, dos
editais SAMAE SG-004/SG-019).

- **Aceite da etapa:** `uname -r` mostra kernel ≥ 5.
- **Abortar:** kernel abaixo de 5 — o servidor não atende aos requisitos
  mínimos; registrar e não seguir.

### 1.3. Windows Server 2022+ / WSL2

Conferir, no Windows, antes de abrir o shell do WSL2:

1. Versão do sistema: Windows Server **2022 ou superior**.
2. **WSL2 ativo** e distribuição Linux padrão do WSL2 abrindo normalmente.
3. **Virtualização aninhada** habilitada, quando o servidor for uma VM.

Todos os comandos deste roteiro rodam **dentro do shell do WSL2**, e o
pré-voo comum (1.1) e o `uname -r` (1.2) são repetidos dentro dele.

- **Aceite da etapa:** WSL2 ativo, shell Linux abre, Docker e Compose v2
  respondem dentro do WSL2 e `uname -r` mostra kernel ≥ 5.
- **Abortar:** WSL2 indisponível ou virtualização aninhada indisponível —
  critério de abortar do runbook; registrar e não seguir.

## 2. Pull das imagens — tags exatas do pacote

Puxar as imagens **com as tags exatas do pacote**, sem `latest`:

```bash
cd reversao
docker compose -f docker-compose.reversao.yml --env-file .env.reversao pull
```

Tags conferidas nesta etapa, no mínimo:

| Serviço | Imagem / tag exata |
|---|---|
| `postgres-primary` | `postgis/postgis:17-3.5` (PostgreSQL 17 com PostGIS 3.5) |
| `keycloak` | `quay.io/keycloak/keycloak:26.8.0` |
| `api` / `migrate` | A tag fixa da imagem única definida no `.env.reversao` do pacote |
| `vault` / `minio` | As tags fixas definidas no compose/`.env.reversao` do pacote |

Resultado esperado: pull de todas as imagens concluído, sem troca de tag.

- **Aceite da etapa:** todas as imagens do pacote presentes localmente, nas
  tags exatas.
- **Abortar:** imagem `postgis/postgis:17-3.5` indisponível para pull no
  servidor de destino — critério de abortar do runbook — ou qualquer imagem só
  disponível com outra tag/`latest`; registrar e não seguir.

## 3. Obter a exportação do tenant sintético

Gerar (na origem do ensaio) ou receber a exportação do tenant sintético **no
formato que o `importar-tenant.sh` espera**:

```text
<diretorio-da-exportacao>/
├── manifesto.json
├── SHA256SUMS
└── banco/
    ├── tenant.sql        (ou tenant.dump)
    └── catalogo.sql
```

- `manifesto.json` — manifesto da exportação (tenant, schema, versão, data,
  contagens por tabela).
- `SHA256SUMS` — hashes de todos os arquivos da exportação.
- `banco/tenant.sql` ou `banco/tenant.dump` — dump do schema do tenant
  sintético.
- `banco/catalogo.sql` — só a linha desse tenant em `public.tenant_catalog`,
  sem segredos e sem linhas de outros tenants.

Conferência na chegada:

```bash
cd <diretorio-da-exportacao>
sha256sum -c SHA256SUMS
```

Resultado esperado: `sha256sum -c` verde para todos os arquivos, e tenant
sintético confirmado no manifesto.

- **Aceite da etapa:** hashes verdes, layout completo e tenant sintético.
- **Abortar:** hash divergente, manifesto incompatível com o conteúdo,
  arquivo do layout ausente ou qualquer sinal de dado de **outro** tenant ou
  de dado real — critérios de abortar do runbook; registrar e não seguir.

**Nota sobre objetos:** a carga dos objetos do tenant no armazenamento
compatível com S3 segue o formato de exportação quando confirmado pelas
P3.16/P3.33. Hoje o importador cobre o banco; este roteiro não presume formato
de objetos — declarar no Registro do Ensaio o que foi carregado.

## 4. Sequência do ensaio — comandos numerados

Executar na ordem, **sem pular passo**, no diretório do pacote (no Windows:
dentro do shell do WSL2, com o pacote copiado como `reversao/` + `infra/`,
preservando a estrutura relativa — ver passo 2 do runbook). Anotar início e
fim de cada passo para o Registro do Ensaio.

### Passo 1 — Preencher o `.env.reversao`

(O arquivo é criado já no pré-voo, item 1.1, porque a conferência de
serviços o usa; este passo é a conferência do preenchimento. Se ele ainda
não existir, criar agora:)

```bash
cd reversao
cp .env.reversao.example .env.reversao
# preencher .env.reversao fora do repositório: tag fixa da imagem,
# credenciais locais do pacote e schema do tenant sintético
```

Resultado esperado: `.env.reversao` preenchido, fora do controle de versão,
sem nenhuma credencial do SaaS.

- **Abortar:** qualquer credencial real/do SaaS no arquivo, ou tag sem versão
  fixa (`latest`).

### Passo 2 — Subir o pacote

```bash
./subir.sh
```

Resultado esperado: `postgres-primary`, `keycloak`, `vault` e `minio` no ar;
`migrate` (`command: migrate`) conclui as migrations e sai com sucesso; `api`
(`command: api`) no ar, com `ziipsan.fiscal.production-allowed=false`. Não há
serviço `worker`.

- **Aceite da etapa:** subida completa, `migrate` concluído sem erro.
- **Abortar:** migration falhando no `migrate` (o pacote não sobe com
  migration parcial) ou serviço da pilha fora do ar — registrar e não seguir.

### Passo 3 — Importar a exportação do tenant sintético

```bash
./importar-tenant.sh <diretorio-da-exportacao>
```

Resultado esperado: manifesto e hashes conferidos pelo script, dump
restaurado, `catalogo.sql` aplicado e tenant registrado com
`environment = 'REVERSAO'`, mantido o `isolation_mode` do catálogo.

- **Aceite da etapa:** importação concluída pelo script, sem divergência de
  hash e com as contagens do manifesto conferidas no destino.
- **Abortar:** hash divergente, manifesto incompatível, ou schema do tenant
  já existente no destino (o pacote nunca sobrescreve em silêncio) —
  registrar e não seguir.

### Passo 4 — Rodar os testes de fumaça

```bash
./testes-fumaca.sh
```

Resultado esperado: **todos os 7 itens** do script verdes — (1) banco
responde; (2) tenant no catálogo com modo de isolamento válido e ambiente
`REVERSAO`; (3) `GET /api/v1/about` responde 200; (4) realm do Keycloak
responde 200; (5) cofre próprio responde; (6) armazenamento S3 responde;
(7) flag fiscal de produção desligada. Item pulado não conta como passado.

- **Aceite da etapa:** fumaça verde, item a item, com a saída guardada.
- **Abortar:** qualquer item falho — registrar o item e não declarar o ensaio
  aprovado.

### Passo 5 — Encerrar

```bash
./parar.sh
```

Resultado esperado: pacote parado, com o volume do banco preservado, salvo
decisão registrada em contrário no Registro do Ensaio.

- **Aceite da etapa:** encerramento limpo e decisão (parar ou manter no ar)
  registrada.

## 5. Critérios de aceite e de abortar do ensaio (resumo)

**Aceite (espelha o runbook):**

- Subida completa no ambiente, com tempos medidos registrados.
- Importação do tenant sintético sem divergência de hash e com contagens do
  manifesto conferidas no destino.
- Testes de fumaça verdes, sem item pulado.
- Registro do Ensaio preenchido e assinado por operador e conferente.

**Abortar — qualquer um interrompe e registra o motivo (espelha o runbook):**

- Hash divergente na exportação, ou manifesto incompatível com o conteúdo.
- Schema do tenant já existente no destino.
- WSL2/virtualização aninhada indisponível no ambiente Windows.
- Migration falhando no `migrate`.
- Qualquer sinal de dado de **outro** tenant na exportação ou no destino.
- Imagem `postgis/postgis:17-3.5` indisponível para pull no servidor de
  destino.

Abortado o ensaio, o destino é descartado (volume removido de forma
registrada) e a origem não é tocada em nenhum passo.

## 6. Encerramento — um Registro do Ensaio por ambiente

1. Preencher **um Registro do Ensaio por ambiente** (Linux limpo e Windows
   Server 2022+/WSL2), usando o template ao final do
   `RUNBOOK-MIGRACAO-REVERSAO.md` — incluindo a seleção de processo
   (`command:`) conferida, as versões medidas (Docker/Compose/kernel/PostGIS)
   do pré-voo e o campo de cofre e S3 no ar.
2. **Anexar as saídas** de `./subir.sh`, `./importar-tenant.sh` e
   `./testes-fumaca.sh`, e as saídas do pré-voo (versões e `uname -r`).
3. Assinatura de operador e conferente no registro. Só com os dois registros
   completos os itens 3, 4, 6, 7 e 8 do acceptance da ED-04 se fecham.
