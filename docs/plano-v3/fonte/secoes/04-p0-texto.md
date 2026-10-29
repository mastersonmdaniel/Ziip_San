### P0 — Plataforma operável · 13/10–27/11/2026

Objetivo: ambiente fora do Drive, código renomeado para `ziipsan`, artefato implantável com versão gerada, marca e
temas, altos e médios de segurança, parâmetros do tenant, ambiente local completo (homologação e demonstração locais,
cofre local e emulador de S3), interfaces de infraestrutura com teste de contrato, PITR local e R1 sobre o cofre local
(ADR-0033); kit de habilitação iniciado. A infraestrutura na AWS `sa-east-1` (ADR-0011) fica para o lançamento
comercial (fase LC).

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|

P0.33 vem logo depois da P0.1: as demais tarefas de código do P0 dependem dela (dependência implícita, não repetida
na coluna). Com o ADR-0033 (v2.7), a P0.26 fica reduzida à tabela de custo e às conferências documentais, e a P0.27, a
P0.28, a P0.29 e a P0.45 entregam o ambiente local; a infraestrutura na AWS vai para a fase LC. A regra pura do R3 pode
começar no P0, em paralelo.

**Códigos de problema na P0.33** (decisão do dono do produto, 12/10/2026, §2.4). Lista dos códigos emitidos hoje,
conferida em `ApiException.java`, `GlobalExceptionHandler.java`, `ProblemResponses.java`, `JwtTenantFilter.java`,
`SecurityConfig.java` e nos serviços do cadastro, mais o código previsto na P0.43. Os códigos de campo e de aviso
(`errors[].code`, `warnings[].code`, como `REQUIRED` e `DOCUMENT_IN_USE`) já estão em inglês e não mudam. Código novo
nasce em inglês, em minúsculas com hífen, e entra no catálogo de erros explicados (P1.22).

| Atual | Novo | Status |
|---|---|---|
| `nao-autenticado` | `unauthenticated` | 401 |
| `acesso-negado` | `access-denied` | 403 |
| `mfa_obrigatorio` (previsto) | `mfa-required` | 403 |
| `tenant-ausente`, `tenant-inativo`, `tenant-divergente` | `tenant-missing`, `tenant-inactive`, `tenant-mismatch` | 403 |
| `nao-encontrado` | `not-found` | 404 |
| `exclusao-indisponivel` | `deletion-unavailable` | 405 |
| `versao-desatualizada` | `stale-version` | 409 |
| `conflito-de-dados` | `data-conflict` | 409 |
| `registro-excluido`, `registro-ja-excluido` | `record-deleted`, `record-already-deleted` | 409 |
| `cliente-com-vinculo-vigente`, `cliente-sem-documento` | `customer-has-active-link`, `customer-without-document` | 409 |
| `busca-na-url` | `search-in-url` | 400 |
| `filtro-indisponivel`, `filtro-invalido` | `filter-unavailable`, `invalid-filter` | 400 |
| `ordenacao-invalida`, `pagina-invalida`, `inscricao-invalida` | `invalid-sort`, `invalid-page`, `invalid-inscription` | 400 |
| `validacao` | `validation` | 422 |
| `confirmacao-necessaria` | `confirmation-required` | 422 |
| `erro-interno` | `internal-error` | 500 |

