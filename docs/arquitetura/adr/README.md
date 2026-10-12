# Registros de decisão de arquitetura (ADR)

Decisões de arquitetura, de produto e de processo do Ziip SAN (código interno: SIGSAN). Cada ADR registra o contexto,
a decisão, as consequências, as alternativas rejeitadas e as tarefas derivadas, com a fase do roadmap (ADR-0001) em que
cada tarefa entra.

## Regras

1. Decisão nova, alterada ou revogada vira ADR. Não se decide em conversa, diário ou comentário de código sem ADR
   correspondente.
2. ADR não se apaga nem se reescreve. Quando a decisão muda, cria-se um ADR novo que o substitui; o antigo passa a
   "Substituído por ADR-NNNN" e só essa linha de status é editada.
3. Numeração sequencial de quatro dígitos, sem reaproveitar números. Nome do arquivo: `ADR-NNNN-titulo-curto.md`.
4. Status possíveis: Proposto (pendente de decisão), Aceito, Substituído, Rejeitado.
5. Documentos de plano e de levantamento (`docs/PLANO-EXECUCAO-PDCA-R0-R9.md`, `docs/levantamento/`) citam o ADR em vez
   de repetir a decisão. Em caso de divergência, vale o ADR mais recente.

## Índice

| ADR | Título | Status | Data |
|---|---|---|---|
| [0001](ADR-0001-roadmap-p0-p4.md) | Roadmap P0–P4 com marco de go-live piloto no lugar do R1–R9 | Aceito | 12/10/2026 |
| [0002](ADR-0002-decisoes-g-como-parametros-do-tenant.md) | Decisões G1, G2, G4, G5, G6 e G9 como parâmetros do tenant com aceite formal | Aceito | 12/10/2026 |
| [0003](ADR-0003-motor-fiscal-portado-para-java.md) | Motor fiscal do Ziip Cloud portado para Java; NFAg-e e NFS-e construídas sobre ele (G3) | Aceito | 12/10/2026 |
| [0004](ADR-0004-sinisa-e-sinir-assistidos.md) | SINISA e SINIR em modo assistido, sem Web Service nem credencial de terceiros (G7, G8) | Aceito | 12/10/2026 |
| [0005](ADR-0005-isolamento-hibrido-por-porte.md) | Isolamento híbrido de tenants por porte, com shard no catálogo | Aceito | 12/10/2026 |
| [0006](ADR-0006-bff-com-cookie-httponly.md) | BFF com cookie HttpOnly no lugar do token no navegador | Aceito | 12/10/2026 |
| [0007](ADR-0007-mfa-configuravel-por-tenant.md) | MFA configurável por tenant; obrigatório na administração da plataforma | Aceito | 12/10/2026 |
| [0008](ADR-0008-mascara-de-documento-configuravel.md) | Máscara de CPF/CNPJ configurável com teto validado; busca por documento controlada | Aceito | 12/10/2026 |
| [0009](ADR-0009-push-automatico-e-dev-fora-do-drive.md) | Push automático para o GitHub e desenvolvimento fora do Google Drive | Aceito | 12/10/2026 |
| [0010](ADR-0010-infra-nao-usada-congelada.md) | Infraestrutura não usada congelada em perfis opcionais e arquivo | Aceito | 12/10/2026 |
| [0011](ADR-0011-hospedagem-a-decidir.md) | Hospedagem de produção | Proposto | 12/10/2026 |
| [0012](ADR-0012-metas-nao-funcionais.md) | Metas não funcionais como critério de aceite | Aceito | 12/10/2026 |
| [0013](ADR-0013-postgresql-17-no-p0.md) | PostgreSQL 17 e PostGIS 3.5 no P0 | Aceito | 12/10/2026 |
| [0014](ADR-0014-outbox-no-postgresql.md) | Trabalho assíncrono por outbox no PostgreSQL | Aceito | 12/10/2026 |
| [0015](ADR-0015-limites-legais-dos-parametros.md) | Limites legais dos parâmetros do tenant: classes, multa, corte, tarifa social e retenção | Aceito | 12/10/2026 |
| [0016](ADR-0016-rebrand-e-renomeacao-ziipsan.md) | Marca do produto em pacote único, marca do tenant e renomeação `sigsan` → `ziipsan` | Aceito | 12/10/2026 |
| [0017](ADR-0017-temas-claro-escuro-azul-e-verde-agua.md) | Temas claro, escuro e do sistema, com padrões Azul Água e Verde Água | Aceito | 12/10/2026 |
| [0018](ADR-0018-central-ziip-propria.md) | Central Ziip própria no Ziip SAN, com o console da plataforma | Aceito | 12/10/2026 |
| [0019](ADR-0019-cobranca-saas-por-contrato.md) | Cobrança do SaaS por contrato e licença controlada | Aceito | 12/10/2026 |
| [0020](ADR-0020-ajuda-e-suporte-com-protocolo.md) | Ajuda e Suporte com protocolo, SLA por contrato e acesso assistido com consentimento | Aceito | 12/10/2026 |
| [0021](ADR-0021-sobre-versao-e-changelog.md) | Sobre, versão gerada no build e changelog do cliente versionado | Aceito | 12/10/2026 |
| [0022](ADR-0022-portais-historico-e-documentos.md) | Histórico e documentos no portal do consumidor e na área do tenant | Aceito | 12/10/2026 |

## Referências

- `docs/levantamento/17-REVISAO-ESTRUTURA-SAAS-2026-10-12.md` — revisão de estrutura SaaS (achados X, S, D, O, P, F).
- `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` — plano canônico: decisões (§2), mapa Flyway (§3) e fases P0–P4 (§4).
