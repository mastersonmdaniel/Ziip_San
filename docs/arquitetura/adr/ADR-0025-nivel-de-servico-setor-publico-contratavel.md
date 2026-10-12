# ADR-0025 — Nível de serviço Setor Público contratável

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0012 (metas, item 4: alterar meta exige novo ADR); ADR-0015 (item 5, eliminação ao fim do contrato);
  ADR-0019 (contrato e política de SLA); ADR-0020 (prioridades e SLA de suporte); ADR-0023 (equipe no P4);
  `docs/arquitetura/REQUISITOS-NAO-FUNCIONAIS.md` (RNF-10, RNF-42, RNF-54 e o novo RNF-71);
  `docs/arquitetura/AJUDA-SUPORTE-SOBRE.md` (`sla_policy`)
- **Base:** `docs/levantamento/editais/18h-aderencia-nao-funcionais-servicos-habilitacao.md` §3.1, §3.2, §5 (E7, E9)
  e §6 (decisão 6)
- **Ajuste de 12/10/2026 (editais, v2.5), antes de qualquer implementação:** item 3 passa a ter equipe pelo ADR-0029
  (4 a 5 desenvolvedores a partir de agosto de 2027), com escala de plantão para a S1 ativa até 31/01/2028 (ED-05);
  continua vedado assinar contrato neste nível antes da escala ativa; o padrão do produto também passa a usar as
  severidades S1 a S5 (ADR-0020, ajuste v2.5), e a linha "Prioridades P1–P4" do Contexto fica como registro do padrão
  anterior; decisão do dono do produto.

## Contexto

As metas do produto (ADR-0012, RNF) divergem dos editais analisados em pontos verificáveis (`18h` §3.1):

| Tema | Padrão do produto | Videira (18a/18b) | SAMAE São Ludgero (18c) |
|---|---|---|---|
| Disponibilidade | 99,5% backoffice (RNF-10); 99,9% portal (RNF-11) | 99% em horário comercial (TR-1089) | 99,9% geral, com desconto de 5% por faixa (SG-1313) |
| Suporte | Prioridades P1–P4, prazo pela política do contrato, sem número (ADR-0020) | Cinco severidades: solução em 2, 4, 6, 16 e 32 h úteis | Cinco severidades: 2, 4, 16, 72 e 160 h; aderência de 95, 95, 90, 80 e 70%; multa de 10, 10, 5, 5 e 5% |
| Exportação no fim | 15 dias, formato aberto (RNF-42) | 10 dias úteis, CSV, JSON ou XML, com dicionário de dados | Vedada a retenção após o fim |
| Pós-contrato | Carência de 30 dias; eliminação em até 60 dias (75 com contestação) (ADR-0015, RNF-42) | Consulta pelo órgão por 120 dias, um usuário | — |
| Relatórios e arquivos | Sem meta | Até 2 min | Até 5 min |
| Monitoramento | Burn rate (RNF-53), plantão a definir (RNF-54) | Proativo e automatizado | 24/7 proativo (SG-1312) |

Elevar essas metas para todos os clientes custaria redundância e plantão que o piloto não paga (ADR-0012, alternativa
rejeitada de 99,95%). Recusar as cláusulas excluiria o produto dos editais.

## Decisão

1. **O padrão do produto não muda:** 99,5% no backoffice, 99,9% no portal e as demais metas do ADR-0012 e do RNF
   continuam valendo para todo contrato que não escolha outro nível.
2. **Nível "Setor Público" contratável por cliente**, registrado no contrato (ADR-0019) e na política de SLA
   (`support.sla_policy`, ADR-0020), com estas metas:
   - **Disponibilidade de 99,9%** ao mês para o backoffice e o portal. A exclusão da janela de manutenção (RNF-12) do
     cálculo segue o contrato.
   - **Cinco severidades** (S1 a S5), classificadas por impacto × criticidade, cada uma com prazo de primeira resposta,
     **prazo de solução**, meta de aderência e multa fixados no contrato. Referências: severidade 1 com solução em
     2 horas úteis; demais conforme o edital (tabela do Contexto). O relatório mensal mostra a aderência por
     severidade e o cálculo da multa.
   - **Exportação de dados em até 10 dias úteis**, em CSV, JSON ou XML, com dicionário de dados, manifesto e hash.
   - **Consulta somente leitura aos dados por até 120 dias após o fim do contrato**, pelo prazo e pelo número de
     usuários fixados no contrato. Quando o contrato exigir prazo maior que o padrão de 30 + 15 + 30 dias, a carência,
     a resposta a contestação e a eliminação passam a seguir o contrato: a carência de 30 dias começa no fim do período
     de consulta, e os demais prazos do ADR-0015 (item 5) correm a partir dela. Contrato que vede retenção após o fim
     (SAMAE) mantém o padrão.
   - **Relatórios e arquivos gerados sob demanda em até 2 ou 5 minutos**, conforme o contrato (RNF-71).
3. **Plantão:** o plantão 24x7 para a severidade 1 e o monitoramento 24/7 com resposta humana dependem da decisão de
   equipe do P4 (ADR-0023, item 4). Enquanto ela não for tomada, o prazo de 2 horas da severidade 1 não tem equipe
   que o sustente, o que precisa ser considerado antes de assinar contrato nesse nível.
4. **A arquitetura não muda:** standby síncrono, três réplicas da `api` e Keycloak duplicado (`ARQUITETURA-ALVO.md` §1)
   já comportam 99,9%; o nível acrescenta medição, relatório e operação.
5. Este ADR é o "novo ADR" exigido pelo item 4 do ADR-0012 para as metas acima; os ajustes estão registrados no
   ADR-0012, no ADR-0015, no ADR-0020 e no `REQUISITOS-NAO-FUNCIONAIS.md`.

## Consequências

### Positivas

- O produto pode disputar editais com metas de setor público sem impor custo de redundância e plantão a todo cliente.
- A política de SLA do ADR-0020 já é por contrato; o nível novo é configuração, não outro sistema.
- O relatório de aderência por severidade dá ao órgão a prova para a medição mensal e ao Ziip a prova de cumprimento.

### Negativas

- Prazo de solução de 2 horas exige equipe técnica disponível; o plano atual supõe uma pessoa com agentes (`18h` §6,
  decisão 5), o que não sustenta esse prazo antes da decisão do P4.
- 99,9% ao mês admite cerca de 43 minutos fora do ar; uma única implantação malsucedida pode consumir o mês inteiro.
  RNF-13 (implantação sem indisponibilidade) e o error budget do ADR-0012 passam a ser condição do nível.
- A consulta pós-contrato prolonga a guarda de dado pessoal, até 120 dias além do padrão; o DPA precisa prevê-la e o
  certificado de descarte registra a data efetiva.
- Dois níveis de serviço a medir e a reportar.

### O que muda no código

- `support.sla_policy` com cinco severidades, prazo de solução, meta de aderência e regra de multa; matriz impacto ×
  criticidade na abertura do chamado.
- Relatório mensal de disponibilidade e de aderência por severidade, por contrato.
- Rotina de fim de contrato com período de consulta somente leitura configurável, antes da carência do ADR-0015.
- Exportação com dicionário de dados gerado do schema.

### O que muda no roadmap

- P2: política de SLA com cinco severidades (amplia a P2.15).
- P3: exportação em 10 dias úteis com dicionário; período de consulta pós-contrato no procedimento de eliminação.
- P4: decisão de equipe e plantão (ADR-0023).

## Alternativas consideradas

- **Manter 99,5%, quatro prioridades e eliminação em 60 dias, recusando cláusulas mais exigentes.** Rejeitada: exclui o
  produto de editais como o do SAMAE (99,9%) e o de Videira (120 dias).
- **Adotar os valores dos editais como padrão para todos.** Rejeitada: custo de plantão e de operação incompatível com
  o piloto e com clientes que não os exigem.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| `sla_policy` com cinco severidades, prazo de solução, aderência e multa; matriz impacto × criticidade | P2 (amplia a P2.15) |
| RNF-71 medido na carga de referência para o catálogo padrão de relatórios (ADR-0027) | P3 |
| Exportação em até 10 dias úteis com dicionário de dados, manifesto e hash | P3 |
| Período de consulta somente leitura pós-contrato antes da carência, previsto no DPA e no certificado de descarte | P3 |
| Relatório mensal de disponibilidade e de aderência por severidade, com cálculo de multa | P3 |
| Decisão de equipe e plantão 24x7 para a severidade 1 | P4 |
