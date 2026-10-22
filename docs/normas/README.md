# Normas e legislação do Ziip SAN

Biblioteca de normas e legislação do setor de saneamento e das áreas que o produto toca. É a base do item "Normas e legislação" do módulo Certificações de qualidade (ADR-0041) e a fonte da carga do catálogo global de normas (core `V9_8`, tarefa P1.34 do plano).

Gerada em 2026-10-22. Contém metadados e resumos em palavras próprias; o texto das normas não é reproduzido, e as pagas (ABNT, ISO) entram só por título, edição e situação.

## Conteúdo

- `catalogo-normas.json`: catálogo consolidado, uma norma por item, com origem, situação, nível de verificação, módulos afetados, tarefas do plano e documentos do projeto que a citam.
- `catalogo/`: leitura por área.
- `partes/`: levantamentos que originaram o catálogo (01 inventário do projeto, 02 legislação federal e do setor, 03 normas técnicas, 04 entidades reguladoras, 05 verificações) e as pendências de cada um.
- `pendencias-consolidadas.md`: o que ainda precisa de confirmação.

## Totais

- **Normas:** 1676.
- **Por verificação:** OFFICIAL_SOURCE 1330, SECONDARY_SOURCE 346.
- **Por situação:** REVOGADA 89, VIGENTE 1232, PARCIALMENTE_REVOGADA 29, VIGENCIA_FUTURA 14, EM_REVISAO 51, A_CONFIRMAR 13, SUBSTITUIDA 90, CONFIRMADA 142, CANCELADA 10, NAO_E_NORMA_EM_VIGOR 6.
- **Precisam de revisão:** 13.

| Área | Arquivo | Normas | A revisar |
|---|---|---|---|
| 01 Legislação federal: leis, leis complementares e decretos | [`01-legislacao-federal-leis-leis-complementares-e-decr.md`](catalogo/01-legislacao-federal-leis-leis-complementares-e-decr.md) | 207 | 0 |
| 02 Agência Nacional de Águas e Saneamento Básico (ANA): normas de referência e resoluções | [`02-agencia-nacional-de-aguas-e-saneamento-basico-ana-.md`](catalogo/02-agencia-nacional-de-aguas-e-saneamento-basico-ana-.md) | 48 | 0 |
| 03 Saúde, meio ambiente, recursos hídricos e política do setor | [`03-saude-meio-ambiente-recursos-hidricos-e-politica-d.md`](catalogo/03-saude-meio-ambiente-recursos-hidricos-e-politica-d.md) | 36 | 2 |
| 04 Metrologia legal (Inmetro) | [`04-metrologia-legal-inmetro.md`](catalogo/04-metrologia-legal-inmetro.md) | 18 | 0 |
| 05 Segurança e saúde no trabalho (MTE) | [`05-seguranca-e-saude-no-trabalho-mte.md`](catalogo/05-seguranca-e-saude-no-trabalho-mte.md) | 23 | 0 |
| 06 Dados pessoais, pagamentos e tributos | [`06-dados-pessoais-pagamentos-e-tributos.md`](catalogo/06-dados-pessoais-pagamentos-e-tributos.md) | 92 | 0 |
| 07 Jurisprudência e súmulas | [`07-jurisprudencia-e-sumulas.md`](catalogo/07-jurisprudencia-e-sumulas.md) | 44 | 0 |
| 08 Entidades reguladoras e normas estaduais: Santa Catarina | [`08-entidades-reguladoras-e-normas-estaduais-santa-cat.md`](catalogo/08-entidades-reguladoras-e-normas-estaduais-santa-cat.md) | 123 | 0 |
| 09 Entidades reguladoras e normas estaduais: Sudeste | [`09-entidades-reguladoras-e-normas-estaduais-sudeste.md`](catalogo/09-entidades-reguladoras-e-normas-estaduais-sudeste.md) | 226 | 2 |
| 10 Entidades reguladoras e normas estaduais: Sul (RS e PR) | [`10-entidades-reguladoras-e-normas-estaduais-sul-rs-e-.md`](catalogo/10-entidades-reguladoras-e-normas-estaduais-sul-rs-e-.md) | 50 | 1 |
| 11 Entidades reguladoras e normas estaduais: Nordeste | [`11-entidades-reguladoras-e-normas-estaduais-nordeste.md`](catalogo/11-entidades-reguladoras-e-normas-estaduais-nordeste.md) | 80 | 2 |
| 12 Entidades reguladoras e normas estaduais: Norte, Centro-Oeste e outras | [`12-entidades-reguladoras-e-normas-estaduais-norte-cen.md`](catalogo/12-entidades-reguladoras-e-normas-estaduais-norte-cen.md) | 101 | 2 |
| 13 Normas técnicas da ABNT (NBR) | [`13-normas-tecnicas-da-abnt-nbr.md`](catalogo/13-normas-tecnicas-da-abnt-nbr.md) | 264 | 0 |
| 14 Normas ISO, ISO/IEC e IEC | [`14-normas-iso-iso-iec-e-iec.md`](catalogo/14-normas-iso-iso-iec-e-iec.md) | 211 | 0 |
| 15 Padrões técnicos, interoperabilidade e outros | [`15-padroes-tecnicos-interoperabilidade-e-outros.md`](catalogo/15-padroes-tecnicos-interoperabilidade-e-outros.md) | 153 | 4 |

## Regras

1. **Classes:** `REGULATORY` (obrigatória por aplicabilidade, não desativável), `VOLUNTARY` (ativável pelo tenant), `REFERENCE` (referência técnica) e `EXPORT_PROFILE` (perfil de exportação de indicadores), conforme o ADR-0041.
2. **Verificação:** `OFFICIAL_SOURCE` quando a norma foi vista em fonte oficial; `SECONDARY_SOURCE` quando só em catálogos e portais de terceiros (caso comum das NBR); `NOT_CONFIRMED` quando só o projeto a cita. Nenhuma norma é descartada por falta de confirmação: ela entra marcada para revisão.
3. **Situação:** a data da conferência é 22/10/2026; norma com vigência futura traz a data no campo de situação.
4. **Sem dado inventado:** número, ano, título e situação vêm de fonte consultada; o que não foi localizado fica em A_CONFIRMAR, com o motivo em `notes`.
5. **Atualização:** o catálogo é regenerado por `scripts/normas/consolidar-catalogo.js` (`node scripts/normas/consolidar-catalogo.js`) a partir de `partes/`; revisões entram como nova parte, sem apagar as anteriores.
6. **Direitos autorais:** só metadados e resumos em palavras próprias.
