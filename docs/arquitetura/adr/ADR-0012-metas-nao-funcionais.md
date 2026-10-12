# ADR-0012 — Metas não funcionais como critério de aceite

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** revisão 17 (achado O4, §6); `docs/arquitetura/REQUISITOS-NAO-FUNCIONAIS.md`
- **Ajuste de 12/10/2026 (editais), antes de qualquer implementação:** as metas deste ADR continuam o padrão do
  produto; o ADR-0025, como novo ADR exigido pelo item 4, cria o nível "Setor Público" contratável por cliente (99,9% de
  disponibilidade, cinco severidades com prazo de solução, exportação em 10 dias úteis, consulta pós-contrato de até
  120 dias, relatórios em 2 ou 5 minutos); o item 1 passa a abranger RNF-01 a RNF-71, com o novo RNF-71 (tempo de
  relatório e arquivo); decisão do dono do produto.

## Contexto

O projeto não tinha metas de desempenho, disponibilidade ou recuperação, nem teste de carga (O4). Sem metas, não há
como dimensionar a infraestrutura, escolher a hospedagem (ADR-0011) nem aceitar uma fase.

## Decisão

1. Adotar as metas propostas na revisão 17 (§6), detalhadas em `REQUISITOS-NAO-FUNCIONAIS.md` (RNF-01 a RNF-70).
2. Cada RNF passa a ser critério de aceite a partir da fase indicada no documento.
3. Política de error budget: esgotado o orçamento mensal de um SLO, novas funcionalidades param até a correção.
4. Alterar uma meta exige novo ADR.

## Consequências

- Hospedagem, dimensionamento e desenho do lote passam a ser avaliados contra números.
- RPO de 15 minutos exige arquivamento contínuo de WAL; 99,9% no portal exige ao menos duas zonas de disponibilidade
  ou equivalente.
- O CI ganha carga noturna, que custa tempo de máquina e manutenção de massa sintética.

## Alternativas consideradas

- **Definir metas só no go-live.** Rejeitada: o desenho do banco, do lote e da hospedagem depende delas agora.
- **99,95% e RPO de 5 minutos desde o início.** Rejeitada: custo de redundância incompatível com o piloto; fica como
  meta de longo prazo.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Instrumentar métricas e traces (RNF-50, RNF-51) | P0 |
| Primeiro ensaio de restauração com tempo medido (RNF-14 a RNF-16) | P0 |
| Massa sintética de 300 mil ligações e teste com 300 schemas | P1 |
| Carga de referência noturna no CI (RNF-70) e SLOs com burn rate (RNF-53) | P2 |
| Página de status, plantão e SLA no contrato | P3 |
