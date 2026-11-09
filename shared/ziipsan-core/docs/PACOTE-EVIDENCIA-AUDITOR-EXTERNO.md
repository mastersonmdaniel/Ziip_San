# Pacote de evidencia e auditor externo (ED-75) — modulo `certification`

Modulo: `certification` (core, `shared/ziipsan-core`), pacote novo
`br.com.ziipsan.core.certification.external`, ao lado dos pacotes
irmaos `quality9001` (ED-69), `env14001` (ED-70), `health45001`
(ED-71), `assetmgmt55001` (ED-72) e `infosec27001` (ED-73); a ED-74
(ISO/IEC 27701) vive no modulo `lgpd`, mas a norma entra aqui pelo
parametro `cert.iso27701.enabled`, como as demais.
Origem: ADR-0041; 21a §8, CQ-14; GES-22, JOR-23. Dependencias:
ED-69..ED-74 e P3.15, todas executadas e na main.

## Fronteira — o que NAO esta aqui

| Conteudo | Dono | Onde |
|---|---|---|
| Ligar/desligar cada norma, escopo declarado, edicao, proposta x aceite | Nucleo P1.32 (ADR-0041) | core `certification`, parametros `cert.*` (core V3) |
| Pacote de evidencia do fornecedor da ISO/IEC 27001 (montagem original) | ED-73 | `SupplierEvidencePack` em `certification.infosec27001` — a ED-75 inclui os MESMOS quatro tipos de artefato como secao do pacote multi-normas, por referencia, sem duplicar a montagem |
| Catalogo de permissoes `recurso:acao` (P0.13) e papeis | ED-06 / P0.13 | `PermissionCatalog` (ziipsan-security) — o acesso externo NAO e permissao de perfil de fabrica: e concessao por tenant com aceite, regida pelos parametros abaixo |
| Conteudo concreto do escopo regulatorio (quais dados e relatorios o regulador le) | ED-97 | sobre este mecanismo (pendencia nomeada) |
| Acesso do orgao a instancia dedicada | LC.15 | fora do escopo da ED-75 (pendencia nomeada) |

## Parametros (nenhum mecanismo paralelo)

- `cert.evidence_pack_personal_data` (core V3, BOOLEAN, classe a,
  TRAVADO em `false`, limite LOCKED; ADR-0041, item 10): o pacote de
  evidencia nunca contem dado pessoal.
- `cert.external_auditor_access` (core V3, BOOLEAN, classe b, padrao
  `false`; ADR-0041, item 10): o papel de auditor externo original.
- `reg.external_access` (BOOLEAN, classe b, padrao `false`): nomeado
  pelo aceite da ED-75 para o mecanismo GENERALIZADO (auditor +
  regulador/titular). A chave nao existe no catalogo do core V3; o
  SQL pendente desta tarefa a registra em
  `public.parameter_definitions`, no padrao do precedente da ED-06.
  Classe b = fora do padrao de fabrica exige aceite formal
  registrado, com proponente diferente do aceitante.

## O que este pacote entrega

1. **Pacote de evidencia por norma ligada** (`EvidencePack` +
   `EvidenceItem`): qualquer subconjunto das seis normas
   (`CertificationStandard`, pelos parametros reais
   `cert.<norma>.enabled`/`.edition`); item de norma desligada e
   recusado no construtor canonico; manifesto canonico com SHA-256
   verificavel (desenho da ED-73); secao de evidencia do Ziip como
   fornecedor (`SupplierEvidenceItem`: baseline da P3.8/PLT-08,
   trilha da P1.7/PLT-19, documentos da P3.9 por referencia — os 14
   valores abertos da P3.9 nao sao copiados — e incidentes da P3.17),
   que se declara incompleta quando falta tipo, nunca finge
   completude.
2. **Sem dado pessoal (classe a, travado):** cada item declara a
   classe do dado — `AGGREGATE`, `MASKED` ou `PSEUDONYMIZED`; a
   classe `PERSONAL` e recusada na construcao do item e nao existe
   no CHECK do SQL.
3. **Observador externo generalizado** (`ExternalProfile` +
   `ExternalScope`): `CERTIFICATION_AUDITOR` le somente o pacote de
   evidencia das normas ligadas; `REGULATOR_SERVICE_OWNER` (agencia
   reguladora, prefeitura) le dados e relatorios regulatorios e o
   minimo de dado pessoal — sem pacote de evidencia ISO e sem dado
   pessoal alem do minimo. Dado pessoal alem do minimo nao e legivel
   por nenhum perfil externo.
4. **Concessao com validade** (`ExternalAccessGrant`): desligada por
   padrao (retrato do parametro na concessao), somente leitura,
   expiracao OBRIGATORIA (sem `expiresAt` nao se constroi; nenhuma
   duracao padrao foi inventada — a spec nao fixa prazo), proponente
   diferente do aceitante (classe b, com aceite) e sujeito externo
   por referencia pseudonimizada (sem campo de dado pessoal).
   Regulador com escopo de evidencia declarado e recusado por
   construcao; auditor sem escopo de evidencia tambem.
5. **Porta unica de leitura** (`ExternalAccessService`): confere
   concessao ativa (parametro + validade), matriz de escopo e
   escopo fino por norma (norma do pacote dentro da concessao E
   ligada no instante da leitura); escrita e recusada sempre
   (`attemptWrite`); CADA leitura — concedida ou negada — gera um
   `ExternalReadLogEntry` (quem, perfil, recurso, referencia,
   quando, desfecho e motivo da negacao).

## Persistencia

SQL em `src/main/resources/db/pendente/ED-75__certification_evidence_external_access.sql`
(6 tabelas `cert_ext_*`, RLS via `register_tenant_table`, FKs
compostas inline, sem DELETE) + o registro do parametro
`reg.external_access` no catalogo global. Sem numero Flyway: na
integracao, proximo numero livre da sequencia global da P3.12
(V100+), com atualizacao da lista de publicadas no mesmo pouso.
