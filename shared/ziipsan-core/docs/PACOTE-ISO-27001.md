# Pacote ISO/IEC 27001 (ED-73) — segurança da informação (SGSI)

Módulo: `certification` (core, `shared/ziipsan-core`), pacote novo
`br.com.ziipsan.core.certification.infosec27001`.
Origem: ADR-0041; 21a §6.5, CQ-12. Depende de P2.25, ED-06, P3.8, P3.9 e
P3.17 — todas executadas; consumo por referência/porta, sem duplicar
nenhuma.

> **Módulo:** o aceite da ED-73 fixa o pacote no módulo `certification`
> (ADR-0041), o mesmo dos pacotes irmãos ED-69 (ISO 9001, em
> `certification.quality9001` no core) — reutiliza-se o desenho deles
> (régua de ativação, autoavaliação com aviso fixo, SQL pendente sem
> número, testes ligada/desligada). A ED-74 (ISO/IEC 27701) vive no módulo
> `lgpd` por ser o acréscimo da LGPD; não é o caso da 27001.

## Fronteira — o que NÃO está aqui

| Conteúdo | Dono | Onde |
|---|---|---|
| Ligar/desligar norma, escopo declarado, edição, proposta × aceite, mapa congelado, vínculo genérico de evidência | Núcleo P1.32 (ADR-0041) | core `certification`, `V21_1`, parâmetros `cert.*` (core V3) |
| Ocorrência única, documento controlado, ação | P2.25 | `V21_2` (`cert_occurrence`, `cert_document`, `cert_action`) |
| Catálogo de permissões e recertificação por usuário | ED-06 | `PermissionCatalog` (security) e `RecertificationService` (platform) |
| Linha de base de segurança (ASVS 5.0, API Security Top 10, LLM Top 10) | P3.8 (PLT-08) | `platform/securityreview` |
| Exportação verificável da trilha de auditoria | P1.7 (PLT-19) | trilha de auditoria |
| Minutas e documentos contratuais | P3.9 | `docs/contratos/` (6 minutas) |
| Incidentes de segurança | P3.17 | registros de incidente/acesso |
| Título/resumo de cláusula em palavras próprias | Curadoria ED-76 sobre textos comprados (P1.28) | `platform.cert_clause` (V9_8), fonte `docs/normas/` |

## Ponto crítico — Anexo A e a decisão do dono

O aceite manda: **identificadores e títulos do Anexo A reproduzidos só
depois de decisão do dono sobre licença ou reescrita**. Essa decisão NÃO
existe. Logo, este pacote entrega o MECANISMO completo e NÃO reproduz
nenhum dos 93 títulos/identificadores literais:

- a SoA identifica cada controle por **chave de referência** declarada
  pelo tenant/curadoria e exige exatamente 93 linhas de chaves únicas;
- o título de cada linha nasce **`[A DEFINIR]`** (marcador
  `AnnexAControlEntry.TITLE_PENDING`, coluna `title_pending` no SQL) e a
  exportação conta e declara quantos títulos seguem pendentes;
- pendência nomeada: **"títulos do Anexo A aguardam decisão do dono
  (licença × reescrita)"**.

## O que este pacote entrega

1. **Ativação como régua** (`Iso27001Activation`): reflete os parâmetros
   reais `cert.iso27001.enabled` (classe d, desligado por padrão) e
   `cert.iso27001.edition` (ALLOWED_VALUES `["2022"]`, padrão `"2022"`,
   core V3) e o escopo `cert.scope_statement`. Edição-alvo única:
   ISO/IEC 27001:2022 com a Emenda 1:2024. Com a norma ligada, proposta e
   aceite são de pessoas diferentes.
2. **Declaração de Aplicabilidade** (`AnnexAControlEntry` +
   `StatementOfApplicability`): 93 controles por referência, cada um com
   aplicabilidade, justificativa (obrigatória sempre), estado, evidência,
   responsável e última revisão; exportação JSON determinística;
   **exclusão justificada permitida** (NOT_APPLICABLE com justificativa);
   aplicável IMPLEMENTED exige evidência.
3. **Matriz de responsabilidade compartilhada**
   (`SharedResponsibilityEntry`): por controle — SUPPLIER, CLIENT ou
   SHARED; controle marcado como implementado pelo fornecedor EXIGE a
   referência da evidência do fornecedor.
4. **Campanha de recertificação por papel e escopo**
   (`AccessRecertificationCampaign`): nasce do retrato do catálogo da
   ED-06 lido pela porta `PermissionCatalogPort` (a fiação injeta o
   adaptador sobre o `PermissionCatalog` real; o catálogo não é
   duplicado e a recertificação por usuário da ED-06 não é refeita);
   decisão do gestor (KEEP/REVOKE) registrada por item; item sem decisão
   NÃO é mantido por omissão.
5. **Pacote de evidência do fornecedor** (`SupplierEvidencePack`):
   agrega POR REFERÊNCIA + SHA-256 os artefatos do aceite — baseline da
   P3.8 (PLT-08), exportação da trilha da P1.7 (PLT-19), documentos da
   P3.9 (referência à minuta; os 14 valores abertos da P3.9 não são
   copiados nem inventados) e incidentes da P3.17. Manifesto canônico com
   hash próprio verificável; tipo ausente ⇒ pacote se declara incompleto.
6. **Mapa de cláusulas como autoavaliação** (`IsmsClauseAssessment`):
   por referência, título em palavras próprias, situação e evidências,
   com o aviso fixo de autoavaliação (o módulo não certifica). Conteúdo
   do mapa pendente da curadoria ED-76, como nos pacotes irmãos.

## Desligar a norma

Nenhuma operação de exclusão existe no pacote nem no SQL pendente:
desligar barra o nascimento de registros novos (porta `requireEnabled`
em todas as fábricas E nos construtores canônicos dos registros, que
exigem a ativação como componente — correção D1 da revisão cruzada) e
preserva os existentes — o teste de aceite
`Iso27001AcceptanceOnOffTest` prova os dois lados.

## Persistência

SQL em `src/main/resources/db/pendente/ED-73__certification_infosec_iso27001.sql`
(9 tabelas `cert_i27001_*`, RLS via `register_tenant_table`, sem DELETE):
pela regra vigente vale só a sequência global da P3.12 (V100+; último
usado V101) — nenhum número foi inventado. Na integração/pouso, o arquivo
recebe o próximo número global livre, atualiza-se
`migration-policy/published-tenant-migrations.txt` e ele é movido para
`db/migration/tenant/`.
