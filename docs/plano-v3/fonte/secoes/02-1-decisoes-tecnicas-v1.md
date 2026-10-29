### 2.1 Técnicas da v1

**=** mantida; **Δ** alterada.

| # | Decisão | Situação |
|---|---|---|
| D1 | `.git` fora do Drive (`--separate-git-dir`) | Δ todo o desenvolvimento sai do Drive no P0 (ADR-0009) |
| D2 | Push só com autorização | Δ **revogada pelo ADR-0009**: push automático com testes verdes |
| D3 | Autor: nome e e-mail do usuário | = |
| D4 | Spring Boot 4.1.1 (suporte OSS até 31/07/2027) | = linha seguinte antes de 31/07/2027 (P3.14) |
| D5/D6 | Realm único, claim `tenant_id` (UUID) | Δ mais gestão de usuários que fixa o `tenant_id` (17:S3); consumidores do portal em realm próprio, `ziipsan-portal` (ADR-0022) |
| D7 | Keycloak 26, banco próprio | = modo de produção na P0.29 (local) e na LC.6 (AWS) |
| D8 | API na 8090 | = |
| D9 | Cifra de dev por HKDF; só dado sintético até o R1 | Δ `dev-env` só em lista fechada de perfis (17:M2); até o lançamento, só dado sintético ou anonimizado na origem, com R1 sobre o cofre local (ADR-0033) |
| D10 | Migrations por job separado | Δ a JVM da API não recebe credencial de migração (17:S2) |
| D11 | Rascunhos e `validate-step` no R2 | = (P1.3) |
| D12 | ADMIN, MANAGER, OPERATOR, VIEWER, `registry.reveal-document` | Δ novos: busca por documento, aprovação de parâmetros, plataforma |
| D13 | `oidc-client-ts`, token em memória | Δ **substituída pelo ADR-0006** (BFF); hoje o token está em `sessionStorage` |
| G10a | `outOfOrder(true)` + teste "tenant antigo alcança o atual" | Δ só até o fim do P3; depois, versões sempre crescentes (17:D1) |
| S1–S3 (v1) | `DocumentNumber` no core; CPF mascarado no PDF do MTR; `drivers` cifrado | = implementadas; máscara segue o ADR-0008 |

