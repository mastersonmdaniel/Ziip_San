# ADR-0044 — O projeto não tem prazos: datas são indicação de sequência

- **Status:** Aceito, 28/10/2026
- **Decisor:** dono do produto (diretriz de 21/10/2026, formalizada por cartão em 28/10/2026)
- **Afeta:** plano canônico (cabeçalho, §2.4, §5 e §8), ADR-0001, ADR-0023, ADR-0033, ADR-0035; achado COE-14 (23a)

## Contexto

O plano nasceu com datas de fase e marcos (P0 a P4, homologação local, trilha de editais, lançamento) e passou a
tratar algumas delas como prazo, inclusive marcos de decisão do dono e datas de editais. Em 21/10/2026 o dono do
produto esclareceu que a pesquisa de editais é suplemento de planejamento, não atendimento de edital, e que o projeto
não está sujeito a prazos (plano v2.16). A diretriz não tinha ADR, e partes do plano continuaram com linguagem de prazo
(23a, COE-14). O ADR-0035 já retirou do plano a gestão de equipe e de capacidade.

## Decisão

1. **Sem prazos de projeto.** Datas de fase, de marco e de lançamento indicam sequência e ordem de grandeza; não são
   compromisso, não geram cobrança de pendência por data e não justificam reduzir escopo ou qualidade.
2. **A ordem vem da dependência.** O que define quando uma tarefa entra é a dependência declarada e a decisão do dono,
   não o calendário.
3. **Datas de norma são fatos.** Vigências, prazos legais e regulatórios (por exemplo, obrigatoriedade da NFAg-e,
   prazo de adequação ao Pix Automático, verificação das normas de referência da ANA) continuam registrados como datas
   de norma, porque condicionam o produto quando ele estiver em operação; não são prazos do projeto.
4. **Linguagem do plano.** O plano não usa "prazo", "atraso", "folga" ou "data-limite" para o próprio projeto; usa
   "sequência", "marco" e "data de referência". Pendências do dono se descrevem pelo que falta decidir, sem data.
5. **Tamanho das tarefas.** P, M e G continuam como indicação de esforço, sem conversão em equipe nem em calendário
   (ADR-0035).

## Consequências

### Positivas

- O plano deixa de sinalizar compromisso externo que não existe.
- As decisões passam a ser tomadas pela dependência e pelo mérito, não pela pressão de data.

### Negativas

- Sem prazos, a priorização depende de o dono escolher a ordem quando houver concorrência entre frentes.

## Alternativas consideradas

- Manter datas como meta de calendário (situação até a v2.15): substituída pela diretriz de 21/10/2026.
- Retirar todas as datas do plano: preterida; as datas de referência ajudam a ler a sequência e o porte.

## Tarefas derivadas

- Plano v3.0: revisão do cabeçalho, das seções 2.4, 5 e 8 e de toda linguagem de prazo; marcos como datas de
  referência; pendências do dono sem data.
