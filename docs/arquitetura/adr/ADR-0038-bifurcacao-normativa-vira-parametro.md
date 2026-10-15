# ADR-0038 — Toda bifurcação normativa vira parâmetro do tenant

- **Status:** Aceito, 14/10/2026
- **Decisor:** dono do produto (diretriz e cartões de 14/10/2026)
- **Afeta:** ADR-0002 (parâmetros do tenant); ADR-0015 (classes e limites legais); `docs/arquitetura/PARAMETROS-DO-TENANT.md`
  §1 e catálogo; levantamentos `docs/levantamento/gsan/20a` a `20g`; plano v2.14

## Contexto

A análise do GSAN e das normas (documentos 20a a 20g) encontrou muitos pontos em que a lei, as normas das agências ou a
prática legítima das prestadoras admitem mais de um caminho: juros por dia com teto da agência ou por mês pro rata,
supressão a pedido com ou sem débito, esgoto "fora de uso" automático ou só por OS, compensação por corte indevido com
fórmulas diferentes entre ARIS, AGIR e ARESC, negativação ou protesto por ente público, contagem de prazos em dias
corridos ou úteis. Cada um virava uma pergunta ao dono do produto, como se o produto tivesse de escolher uma regra por
todos os clientes.

O dono do produto decidiu que não: o produto oferece os caminhos e o cliente escolhe, na implantação, a regra que vai
usar.

## Decisão

1. **Bifurcação vira configuração.** Sempre que lei, norma de agência reguladora ou prática legítima admitir caminhos
   alternativos (A, B ou C), ou quando normas divergirem entre si, o produto implementa todos os caminhos e oferece a
   escolha como parâmetro do tenant, com vigência, versão gravada no resultado e aceite, conforme o ADR-0002.
2. **Padrão de fábrica: o caminho mais protetivo ao usuário.** Na dúvida entre caminhos, o padrão é o de menor risco
   legal para o prestador e maior proteção ao usuário (por exemplo, juros por dia com o teto da agência, supressão a
   pedido permitida mantendo o débito em cobrança, negativação de ente público desligada). Escolher outro caminho segue
   a classe do parâmetro (ADR-0015): norma citada, aprovação dupla e aceite formal quando for classe b.
3. **Lei federal que proíbe expressamente continua travada.** Onde lei federal veda ou fixa limite (por exemplo, as
   vedações de corte por inadimplência da Lei 14.015/2020, o piso da tarifa social da Lei 14.898/2024, o histórico de
   120 meses), o caminho mais permissivo de uma norma local não vira opção: o parâmetro é classe a ou c. Confirmado pelo
   dono do produto em 14/10/2026, mantendo a decisão de 12/10/2026 do ADR-0015, item 3.
4. **Perfis por agência pré-carregados.** Os caminhos de cada agência levantada (ARIS, AGIR, ARESC e as do documento
   08) entram como perfil regulatório (G1) que preenche os parâmetros na implantação; o cliente revisa e aceita o
   conjunto. Perfil é sugestão marcada com a fonte (PARAMETROS-DO-TENANT §1, princípio 4).
5. **O que não é bifurcação continua sendo decisão de produto.** Escolhas de modelagem, de fase ou de escopo que não
   correspondam a regras alternativas admitidas por norma ou prática seguem como pergunta ao dono do produto, por
   cartão.
6. **Rastreabilidade.** Cada parâmetro criado por esta regra cita no catálogo as normas ou práticas que originam os
   caminhos e o documento de levantamento de onde saiu.

## Consequências

### Positivas

- O produto atende agências e prestadoras com regras diferentes sem código por cliente, ao contrário do GSAN, que
  resolve a diferença com classes por empresa (20a, 20e).
- As decisões do dono deixam de travar o desenho a cada divergência normativa.
- A escolha do cliente fica registrada, com norma citada e responsável, o que protege o Ziip SAN em fiscalização.

### Negativas

- Mais caminhos para implementar e testar: cada opção precisa de teste de aceite próprio.
- Catálogo de parâmetros maior, o que exige boa implantação (perfis pré-carregados e simulação antes de ativar).
- Combinações de opções podem ser incoerentes; o validador de coerência do ADR-0002 precisa cobri-las.

## Alternativas consideradas

- Uma regra única de produto por tema, decidida pelo dono: rejeitada pelo dono do produto.
- Configurar também o que a lei federal proíbe, com aceite formal: rejeitada pelo dono do produto em 14/10/2026.
- Padrão de fábrica pelo perfil da agência do tenant: preterida; o perfil entra como sugestão sobre o padrão mais
  protetivo.

## Tarefas derivadas

- PARAMETROS-DO-TENANT: princípio 10 e seção de parâmetros de bifurcação (v2.14), com as chaves dos levantamentos 20a e
  20f; as de 20b, 20c e 20d entram na consolidação desses documentos.
- P0.19: `parameter_definitions` comporta parâmetro enumerado com opções e norma de origem por opção.
- Cada tarefa que implementa um parâmetro de bifurcação tem um teste de aceite por opção.
