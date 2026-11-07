# ED-17 — Teste de carga: 100 mil ligações em leitura horária

## O que o aceite pede

Teste de carga com **100 mil ligações em leitura horária** e **meta de
desempenho registrada**; extensão de série temporal só por decisão do dono,
se o PostgreSQL puro não atender (ADR-0010, item 3).

## Meta registrada

- **Volume:** um ciclo horário completo de 100 mil ligações = 100 mil
  amostras horárias por hora, sustentado hora após hora.
- **Janela:** o lote horário de 100 mil amostras é ingerido, deduplicado e
  avaliado (alarmes) **dentro da própria hora** — derivado da cadência
  horária do aceite: se a ingestão de uma hora não fecha dentro dela, a
  fila cresce sem bound. Este é o critério registrado nesta entrega; o
  número fino de vazão (amostras/s) é medido na primeira rodada real e
  registrado no diário antes de qualquer decisão de extensão.
- **Integridade sob carga:** nenhuma amostra aceita se perde; reenvio do
  mesmo lote devolve 100% de duplicadas; a consulta da curva de um ponto
  num intervalo usa o índice por ponto e tempo com poda de partição
  (plano de execução conferido, no padrão da P2.3).
- **Decisão de extensão:** se o PostgreSQL puro (partição mensal da P2.3)
  não fechar a janela, a saída é decisão do dono por ADR — nunca adoção
  silenciosa de extensão de série temporal.

## Roteiro (PC do dono, com Docker — a CI real roda lá)

1. Subir o ambiente da camada (banco de integração) e aplicar as migrations
   incluindo a da ED-17 já numerada na sequência global.
2. Gerar a massa: 100 mil pontos de medição sintéticos (sem dado pessoal
   real), 1 canal cada, com base legal registrada.
3. Gerar 24 lotes horários de 100 mil amostras (2,4 milhões de amostras) e
   ingerir cada lote pela rota de amostras horárias com credencial de
   integração de teste, medindo o tempo de cada lote.
4. Reenviar o último lote inteiro: esperado 0 aceitas e 100 mil duplicadas.
5. Conferir a curva de 10 pontos sorteados nas 24 horas e o
   `EXPLAIN ANALYZE` da consulta por ponto e intervalo (poda de partição
   visível no plano).
6. Registrar tempos por lote, contadores e planos no diário de bordo da
   camada.

## O que foi executado nesta entrega

- `TelemetryScaleTest` (teste automatizado, no pacote da entrega): monta
  100 mil amostras horárias em memória e roda a detecção de alarmes e a
  regra de consumo contínuo sobre o volume inteiro, conferindo os
  resultados — prova de que as regras não têm custo quadrático escondido
  nem perdem amostra.
- **O roteiro acima NÃO foi executado nesta VM** (sem Java, Maven e
  Docker): a medição de vazão contra o PostgreSQL particionado fica para a
  rodada de CI da camada no PC do dono, como nas demais tarefas do
  projeto. Nenhum número de desempenho é afirmado sem essa medição.
