# ED-85 — Modulo de perdas operacional: DMC, vazao minima noturna e balanco por DMC

Modulo `intelligence/ziipsan-losses`, pacote novo
`br.com.ziipsan.intelligence.losses.dmc`: setorizacao (DMC vivo sobre
`dma_district` + zona DMA de `gis_zone`, fonte unica), macromedidores
vinculados aos pontos de medicao da ED-17, vazao minima noturna e
liquida (`loss.night_window` padrao 02:00–04:00 do aceite;
`loss.legit_night_use_l_per_conn_h` sem padrao), balanco hidrico
mensal por DMC e por setor com grau de confianca, ILI com dados reais
e fila de prioridade, volume autorizado nao faturado por OS
operacional (`loss.unbilled_estimate_method`), exclusao de exportacao
em bloco por contrato, ajuste micromedido JOR-34 com metodo gravado e
anexo de perdas reais das ocorrencias da ED-92 (JOR-33). O balanco
publicado e a entrada real lida pela ED-78 (NR 15) e pelas variaveis
de balanco do SINISA (ED-48). Parametros `loss.*` sao do tenant:
meta, uso legitimo e metodo nao tem valor de fabrica nesta entrega.
