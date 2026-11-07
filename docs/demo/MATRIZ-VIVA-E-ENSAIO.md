# Matriz viva, resultado por item e relatório de cobertura (P3.23)

## Papéis (quem é dono do quê)

- **Roteiros e matriz viva são da P1.25**: o roteiro da VISAN (81 itens,
  162 pontos, com a forma de demonstração de cada um) e o do SAMAE
  (lista crítica, com as seções 2.4 e 2.5), cada item ligado a
  requisito, ED, tarefa e fase; a matriz viva, no formato do
  consolidado 18 §7, é conferida no CI por
  `scripts/check-matriz-viva.py`. Esta tarefa **não** reescreve item
  nenhum dos dois roteiros.
- **A P3.23 é dona do ensaio e do registro**: executar os roteiros no
  ambiente de demonstração, cronometrar o preparo, gravar o resultado
  **por item** e aplicar o critério do P3. O registro por item é a
  ponte: ele usa a mesma chave da matriz viva (o item de roteiro) e as
  mesmas ligações (requisito, tarefa, teste), de modo que o resultado
  do ensaio **atualiza a matriz viva** — que, pela regra do plano, é
  atualizada ao fim de cada tarefa que toca requisito de edital, a cada
  ensaio e a cada edital novo.

## Formato do resultado por item

Uma linha por item executado, no formato de
`resultados-ensaio.template.csv`:

| Coluna | Sentido |
|---|---|
| `item` | Identificador do item no roteiro (a chave da matriz viva) |
| `roteiro` | `VISAN` ou `SAMAE` |
| `secao` | Seção do roteiro (no SAMAE, `2.4`/`2.5` são as do critério) |
| `pontos_maximos` | Pontos do item no roteiro (a soma VISAN precisa ser 162) |
| `pontos_obtidos` | Pontos obtidos: igual ao máximo se `APROVADO`, 0 se `FALHOU` (pontuação parcial não é aprovação — formato recusado) |
| `resultado` | `APROVADO`, `FALHOU`, `NAO_EXECUTADO` ou `BLOQUEADO` |
| `requisito` | Requisito ligado ao item (matriz viva) |
| `tarefa` | Tarefa ligada ao item |
| `teste` | Teste que prova o requisito (o ID do requisito vai no teste, regra do plano) |
| `evidencia` | Registro da demonstração (ata, captura, log — referência, não o conteúdo) |
| `duracao_seg` | Duração medida da demonstração do item |

## A régua (avaliar-ensaio.py e PocEnsaioCriterion — mesma regra, dois lados)

1. **VISAN**: somados os pontos obtidos dos itens aprovados. Aprovado o
   critério se **≥ 50** — e só se a soma dos pontos máximos do arquivo
   for exatamente **162**; divergência é `INCONCLUSIVO_ESTRUTURA`
   (o arquivo não é o roteiro da P1.25).
2. **SAMAE 2.4/2.5 sem falha**: qualquer `FALHOU` reprova; qualquer
   item dessas seções `NAO_EXECUTADO`/`BLOQUEADO` (ou a ausência das
   seções) deixa o critério `NAO_COMPROVADO` — "sem falha" não se
   declara sobre o que não foi executado.
3. **Tempo de preparo**: início e fim do ensaio convertidos em **dias
   úteis decorridos** (segunda a sexta, menos os feriados declarados
   em arquivo — nenhum feriado é embutido) e comparados com os dois
   orçamentos dos editais de referência, **5 e 7 dias úteis**. O
   veredito global exige caber no maior; o resultado registra os dois.
4. **Cobertura**: todo item executado precisa de requisito, tarefa e
   teste preenchidos; um item sem a ligação completa torna a cobertura
   `INCOMPLETA` e o ensaio não aprova.

Veredito global `APROVADO` exige os quatro. Qualquer outra combinação
sai `REPROVADO_OU_INCOMPLETO`, com o veredito de cada perna no
`resultado-ensaio.json` — nunca um "quase" agregado.

## Relatório de cobertura (anexado ao aceite)

Gerado do mesmo registro por item (`cobertura-poc.csv`, colunas
**requisito, tarefa, teste, item de roteiro**, roteiro, seção,
resultado, pontos e evidência) e resumido no
`RELATORIO-COBERTURA-POC.template.md` preenchido a cada ensaio. É esse
relatório — não uma declaração — que se anexa ao aceite da P3.23 a cada
ensaio executado. Enquanto nenhum ensaio real tiver rodado, o relatório
anexado é o **template** e o aceite registra o ensaio como pendente de
execução (ver o relatório da entrega).
