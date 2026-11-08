# Pacote ISO 9001 (ED-69) — gestão da qualidade

Módulo: `certification` (core, `shared/ziipsan-core`), pacote novo
`br.com.ziipsan.core.certification.quality9001`.
Origem: ADR-0041; 21a §6.1, CQ-08. Depende de P2.25, P3.31 e da curadoria
ED-76 para o conteúdo do mapa de cláusulas.

> **Módulo confirmado:** o aceite da ED-69 fixa o pacote no módulo
> `certification` — diferente da ED-74 (ISO/IEC 27701), cujo pacote vive
> no módulo `lgpd` por ser o acréscimo da LGPD. Não há ambiguidade aqui:
> a pendência "certification × lgpd" era da 27701 e não se transfere
> para a 9001.

## Fronteira — o que NÃO está aqui

| Conteúdo | Dono | Onde |
|---|---|---|
| Ligar/desligar norma, escopo declarado, edição, proposta × aceite, mapa congelado, vínculo genérico de evidência | Núcleo P1.32 (ADR-0041) | core `certification`, `V21_1`, parâmetros `cert.*` (core V3) |
| Ocorrência única (não conformidade, incidente, melhoria), documento controlado, ação | P2.25 | `V21_2` (`cert_occurrence`, `cert_document`, `cert_action`) |
| Riscos formais (critério, escore), objetivos, auditoria interna, competência, fornecedor, requisitos legais | P3.31 | `certification/governance`, `V21_3` |
| Indicadores e metas (medição tem uma só fonte) | P3.41 (JOR-19) | registro único de indicadores |
| Título/resumo de cláusula em palavras próprias | Curadoria ED-76 sobre textos comprados (P1.28) | `platform.cert_clause` (V9_8), fonte `docs/normas/` |

## O que este pacote entrega

1. **Ativação como régua** (`Iso9001Activation`): reflete os parâmetros
   reais `cert.iso9001.enabled` (classe d, desligado por padrão) e
   `cert.iso9001.edition` (ALLOWED_VALUES `["2015","2026"]`, core V3) e o
   escopo `cert.scope_statement`. Edição-alvo 2026; 2015 só em transição.
   Com a norma ligada, proposta e aceite são de pessoas diferentes
   (recusa se `proposedBy == acceptedBy`).
2. **Mapa de processos** (`ProcessMapEntry`): por processo — responsável,
   entradas, saídas, indicador (código do registro da P3.41) e risco
   (descrição declarada; o registro formal é o da P3.31). Persistência:
   `cert_q9001_processes`.
3. **Calibração de instrumentos** (`InstrumentCalibration`): validade
   inclusiva e **bloqueio** — `requireUsableOn(data)` recusa medição com
   instrumento vencido. Persistência: `cert_q9001_calibrations`
   (histórico por registro novo, sem UPDATE de validade).
4. **Saída não conforme** (`NonconformingOutput`): classificação
   (processo, descrição do desvio, disposição decidida) ligada pelo
   `occurrenceId` à ocorrência `NONCONFORMITY` da P2.25 — FK composta no
   SQL pendente. Nenhum segundo registro de não conformidade é criado.
5. **Mapa de cláusulas como autoavaliação** (`QualityClauseAssessment`):
   por referência (número), título em palavras próprias, situação
   declarada e evidências ligadas por tipo + id. Conforme exige
   evidência; não-aplicável exige justificativa; toda saída carrega o
   aviso de autoavaliação (o módulo não certifica). **Conteúdo do mapa
   (quais cláusulas de cada edição): pendente da curadoria** —
   `docs/normas/mapa-clausulas.json` está vazio no repo e esta tarefa
   não inventa cláusula de norma protegida.
6. **Checklist de prontidão por edição** (`ReadinessChecklist`): itens
   por cláusula com pergunta em palavras próprias e situação
   (pronto/pendente/não-aplicável justificado), percentual de prontidão
   e `compareEditions` — a comparação de cláusulas na troca de edição
   (incluídos, removidos, alterados, inalterados), na mesma régua da
   ED-76.

## Desligar a norma

Nenhuma operação de exclusão existe no pacote nem no SQL pendente:
desligar barra o nascimento de registros novos (porta `requireEnabled`
em todas as fábricas) e preserva os existentes; a ocorrência da P2.25
continua existindo como registro livre, como a V21_2 desenha.

## Persistência

SQL em `src/main/resources/db/pendente/ED-69__certification_quality_iso9001.sql`:
o plano reserva a faixa `V21_x` para os pacotes por norma (ED-69 a
ED-77) **sem número fechado**; pelo precedente ED-74/ED-17/ED-14, nenhum
número foi inventado. Na integração, o arquivo recebe o próximo
`V21_x` livre e é movido para `db/migration/tenant/`.
