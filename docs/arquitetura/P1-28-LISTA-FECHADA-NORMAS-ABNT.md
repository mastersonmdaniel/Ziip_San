# P1.28-prep — Lista fechada das normas ABNT para a curadoria do catálogo do GIS

- **Origem:** plano v3.0, P1.28; ADR-0034, decisão 2 e ajuste v2.11, item 6;
  GIS-CADASTRO-TECNICO §4.1 ("Normas compradas (v2.11)").
- **Status: COMPRA PENDENTE — NÃO COMPRADA.** A linha P1.28 exige "custo estimado
  e aprovado pelo dono antes da compra", e essa aprovação **não existe**. Nada
  abaixo foi comprado, e nenhum arquivo de norma entra no repositório (licença
  de uso da ABNT: arquivos fora do repositório). Este documento traz **só
  referência, número e edição** — nunca texto de norma.
- **Consequência (fallback do próprio plano, aplicado na P1.26):** norma não
  comprada deixa os seus itens marcados como **valor de referência a validar**.
  Todos os itens do modelo semeados ou migrados na V13_1/V13_2 nascem com
  `reference_status = REFERENCE_TO_VALIDATE` e nenhum é apresentado como
  conferido. Quando a compra for aprovada e feita, a P1.27 passa os itens
  conferidos para `STANDARD`, com norma e edição — nunca antes.
- **Edição vigente:** conferida onde for publicamente verificável. "Projeto"
  (catálogo consolidado `docs/normas`) significa que a fonte do projeto indica
  a edição como confirmada no catálogo consolidado; "a confirmar no ABNT
  Catálogo antes da compra" significa que a edição final só se fecha no ato da
  compra, no ABNT Catálogo, como o §4.1 exige. A coluna "Fonte da edição" diz
  de onde veio cada ano — nenhum ano foi inventado.

## Lista fechada (33 itens)

Oito do núcleo da P1.28 mais as demais citadas no §4.1 do projeto. A série
NBR 5667 conta como três partes (5667-1, 5667-2 e 5667-3) e a série NBR 10004
como duas (10004-1 e 10004-2).

| # | Norma | Edição a comprar | Fonte da edição | Uso no catálogo (§4) |
|---|---|---|---|---|
| 1 | NBR 12586 | 1992 | catálogo consolidado do projeto + referência pública (NBR 12586/92) | cadastro de sistema de abastecimento de água |
| 2 | NBR 12587 | 1992 | catálogo consolidado do projeto + referência pública (NBR 12587/92) | cadastro de sistema de esgotamento sanitário |
| 3 | NBR 12218 | 2017 | catálogo consolidado do projeto + 2ª edição 03/05/2017 (referência pública) | projeto de rede de distribuição de água |
| 4 | NBR 9649 | 1986 | projeto (§4.3) + catálogo consolidado do projeto | projeto de redes coletoras de esgoto |
| 5 | NBR 5647-1 | 2025 | projeto (§4.2.1) | PVC-U PBA |
| 6 | NBR 7665 | 2025 | projeto (§4.2.1) | PVC-M DEFOFO |
| 7 | NBR 7675 | 2026 | projeto (§4.1, §4.2.1: edição de 08/2026) | ferro fundido dúctil |
| 8 | NBR 15561 | 2024 | projeto (§4.2.1) | PE 80 e PE 100 |
| 9 | NBR 17287 | 2026 | catálogo consolidado do projeto | operação das redes de distribuição de água |
| 10 | NBR 15750 | 2024 | projeto (§4.2.1) | PVC-O |
| 11 | NBR 15802 | 2010 (projeto) | projeto (§4.2.1: "projeto NBR 15802:2010") | PE — norma em projeto, não publicada como NBR vigente; comprar só se o ABNT Catálogo a listar como adquirível, senão registrar como indisponível |
| 12 | NBR 5667-1 | 2006 (conf. 04/2024) | projeto (§4.2.3) | hidrante de coluna |
| 13 | NBR 5667-2 | 2006 (conf. 04/2024) | projeto (§4.2.3) | hidrante subterrâneo |
| 14 | NBR 5667-3 | 2006 (conf. 04/2024) | projeto (§4.2.3) | hidrante com obturação própria |
| 15 | NBR 12208 | 2020 | projeto (§4.3) | elevatória de esgoto |
| 16 | NBR 12209 | 2011 | projeto (§4.3) | ETE |
| 17 | NBR 12212 | 2017 | catálogo consolidado do projeto (o projeto §4.2.3 dizia "edição a confirmar"; o catálogo a fecha em 2017 — confirmar no ABNT Catálogo antes da compra) | poço tubular |
| 18 | NBR 12213 | 1992 | projeto (§4.2.3) | captação superficial |
| 19 | NBR 12214 | 2020 | projeto (§4.2.3) | estação elevatória de água |
| 20 | NBR 12216 | 1992 | projeto (§4.2.3) | ETA |
| 21 | NBR 12217 | 1994 | catálogo consolidado do projeto | reservatório de distribuição |
| 22 | NBR 12244 | 2006 | projeto (§4.2.3) + catálogo consolidado do projeto | construção de poço tubular |
| 23 | NBR 7362 | 2025 | projeto (§4.3) | PVC parede maciça (esgoto) |
| 24 | NBR 7367 | 1988 | projeto (§4.3) | assentamento (esgoto PVC) |
| 25 | NBR 14486 | 2000 | projeto (§4.3) | redes em PVC (esgoto) |
| 26 | NBR ISO 21138-1 | 2021 | projeto (§4.3) | PVC/PE de parede estruturada |
| 27 | NBR ISO 21138-3 | 2021 | projeto (§4.3) | PVC/PE de parede estruturada |
| 28 | NBR 8890 | 2020 | projeto (§4.3, §4.4) | concreto (esgoto e drenagem) |
| 29 | NBR 9648 | 1986 | catálogo consolidado do projeto | estudo de concepção de esgoto |
| 30 | NBR 10160 | 2026 | projeto (§4.3, §4.4) | tampões e grelhas (classe de carga) |
| 31 | NBR 17076 | 2024 | projeto (§4.3) | ETE de menor porte |
| 32 | NBR 10004-1 | 2024 | projeto (§4.5, série de 2024) | classificação de resíduos, parte 1 |
| 33 | NBR 10004-2 | 2024 | projeto (§4.5, série de 2024) | classificação de resíduos, parte 2 |

## Fora desta lista fechada (não inventar)

- **Textos ISO e normas do setor usados na curadoria da ED-76** (P1.28): a
  linha os inclui na compra, mas a ED-76 é outra frente — a lista desses
  textos não é feita aqui e não pode ser adivinhada a partir do §4 do GIS.
  Pendência para a frente da ED-76: entregar a sua lista antes da compra.
- **Perfis Corsan/Aegea (Anexo A, rev. 03/2024) e Saneago (IN 00.0229, rev. 04
  de 11/03/2024)**: são perfis de importação/exportação (§4.1), não normas
  ABNT a comprar — não entram nesta lista.
- **NBR de desenho técnico** citadas de passagem no projeto (§12, pranchas):
  não fazem parte do §4.1 e não entram na lista fechada.

## Custo estimado: NÃO APURÁVEL sem comprar

O ABNT Catálogo não publica preço sem consulta de compra (licença por
usuário/organização, preço sob consulta). Nenhum valor é estimado aqui para
não inventar número: **a estimativa depende de cotação do dono no ABNT
Catálogo** (33 itens acima, mais a lista da ED-76 quando entregue). Pendência
do dono, em ordem:

1. aprovar a lista fechada acima (e a lista da ED-76, quando entregue);
2. solicitar a cotação no ABNT Catálogo;
3. aprovar o custo cotado — só então a compra é feita e registrada no diário
   de bordo, com edição e data, como a P1.28 exige.

## O que a P1.26 já fez com esta pendência

- Itens migrados e semeados: todos `REFERENCE_TO_VALIDATE`, sem `standard_ref`
  de norma não comprada.
- Parâmetros `gis.snap_tolerance_m` e `gis.import_snap_tolerance_m`: limites
  registrados como valor de referência a validar (o §3.10 já os marcava assim).
- Nada neste commit apresenta qualquer valor do modelo como conferido em
  norma: conferir exige a compra acima + a curadoria da P1.27.
