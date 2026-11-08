# Pacote ISO/IEC 27701 (ED-74) — o que a norma pede além da LGPD

Módulo: `compliance/ziipsan-lgpd`, pacote novo `br.com.ziipsan.compliance.lgpd.privacyiso`.
Origem: ADR-0041; 21a §6.6, CQ-13. Depende de P2.25, P3.10, P1.15 e P3.40 (e da
curadoria ED-76 para o conteúdo do mapa).

## Fronteira (JOR-15) — o que NÃO está aqui

| Conteúdo | Dono | Onde |
|---|---|---|
| Direitos do titular, registro de operações (ROPA), canal no portal | P3.40 — obrigação de todo tenant, sem pacote nem flag | `privacy/` + `V23_1` |
| Incidente com dado pessoal, modelo e registro de RIPD | P3.10 | `incident/` |
| Ligar/desligar norma, escopo declarado, edição, aceite, mapa congelado, vínculo de evidência genérico | Núcleo P1.32 (ADR-0041) | core `certification`, `V21_1`, parâmetros `cert.*` (core V3) |
| Título/resumo de cláusula em palavras próprias | Curadoria ED-76 sobre textos comprados (P1.28) | `platform.cert_clause` (V9_8), fonte `docs/normas/` |

## O que este pacote entrega

1. **Ativação como régua** (`Iso27701Activation`): reflete os parâmetros reais
   `cert.iso27701.enabled` (classe d, desligado por padrão) e
   `cert.iso27701.edition` (ALLOWED_VALUES `["2019","2025"]`, core V3).
   Edição-alvo 2025; 2019 só em transição (declarar o contrário é recusado).
   Com a norma ligada, escopo declarado **e** escopo do SGPI são obrigatórios.
2. **Papel por tratamento** (`ProcessingRoleAssignment`): controlador ou
   operador por código de registro de operações da P3.40 — o papel decide a
   audiência dos controles aplicáveis. Persistência: `lgpd_processing_roles`.
3. **Escopo do SGPI** (`lgpd_pims_scopes` no SQL pendente): um escopo ativo
   por tenant, ligado à ativação do núcleo por FK condicional (padrão V23_1).
4. **Mapa de cláusulas e controles como autoavaliação**
   (`PrivacyClauseAssessment`): por referência (número), audiência
   (controlador/operador/comum), situação declarada e evidências ligadas por
   tipo + id aos registros da P3.40 (ROPA, pedido do titular) e da P3.10
   (RIPD, incidente). Conforme exige evidência; não-aplicável exige
   justificativa; toda saída carrega o aviso de autoavaliação (ADR-0041, item 8).
   **Conteúdo do mapa (quais cláusulas/controles da edição de 2025): pendente
   da curadoria** — `docs/normas/mapa-clausulas.json` está vazio no repo e
   esta tarefa não inventa cláusula de norma protegida.
5. **Privacidade desde a concepção** (`PrivacyByDesignCheck`): mudança com
   tratamento novo/alterado exige a referência do RIPD (P3.10); sem
   tratamento novo/alterado, exige justificativa. O banco repete as travas.
6. **Mapa de dados** (`PrivacyDataMap`): colunas da classificação DAD-29
   (P0.11) × registros da P3.40; coluna sem registro = pendência do SGPI
   (a mesma régua da cobertura da P3.40, como visão de gestão).

## Desligar a norma

Nenhuma operação de exclusão existe no pacote nem no SQL pendente:
desligar barra o nascimento de registros novos (porta `requireEnabled`) e
preserva os existentes; a P3.40 não é tocada (teste dedicado prova que a
privacidade do titular continua ativa com `cert.iso27701.enabled=false`).

## Persistência

SQL em `src/main/resources/db/pendente/ED-74__lgpd_privacy_iso27701.sql`:
o plano reserva a faixa `V23_x` para a ED-74 **sem número fechado**; pelo
precedente ED-17/ED-14, nenhum número foi inventado. Na integração, o
arquivo recebe o próximo `V23_x` livre e é movido para `db/migration/tenant/`.
