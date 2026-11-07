# Registro de resultado — P2.9, carga com 300 mil ligações

> **TEMPLATE — preencher com a execução real no PC do dono.**
> Nenhum campo abaixo tem valor: resultado inventado aqui invalidaria o
> aceite. O `executar-carga.sh` gera `resultado.json` com estes mesmos
> campos; transcreva dele e anexe os artefatos citados.

- **Data/hora da execução:** _preencher_ (America/Sao_Paulo)
- **Ambiente:** homologação local (P0.27), commit: _preencher_
- **Tenant da carga (descartável):** _preencher_
- **Massa:** sintética declarada, semente `ziipsan-carga-p29-v1`,
  manifesto SHA-256: _preencher_ (`massa/manifesto.json`)

## Volume

| Métrica | Alvo | Medido |
|---|---|---|
| Ligações semeadas | 300.000 | _preencher_ |
| Leituras na referência | 300.000 | _preencher_ |
| Consumos na referência | 300.000 | _preencher_ |
| Contas concluídas | 100.000 | _preencher_ |
| Contas com erro (PROCESSING_ERROR) | 0 | _preencher_ |
| Soma total das contas | — | _preencher_ |

## Duração do faturamento

- Início/fim: _preencher_ — **duração: _preencher_ s** (limite do aceite:
  7.200 s) — dentro do limite? _sim/não_

## p95 da API (medido em paralelo)

- Amostras: _preencher_ — p50 _preencher_ ms, **p95 _preencher_ ms**,
  p99 _preencher_ ms, erros _preencher_
- Teto aplicado: `P95_MAX_MS` _preencher_ / linha de base P0.23
  `P95_BASELINE_MS` _preencher_ (limite de regressão +15%, RNF-70)
- Veredito p95: _preencher_ (artefato: `api/resumo-api.json` +
  `api/amostras-api.csv`)

## Keycloak — realm do portal × capacidade da P0.23 (PLT-28)

- Limites transcritos da P0.23 (`carga-p0.23.env`): _preencher_ — nenhum
  deles é conferido pela sonda deste aparato (ver abaixo)
- p95 da sonda de **discovery** do portal sob a carga: _preencher_ ms;
  erros: _preencher_
- Veredito: **MEDIÇÃO PARCIAL** — a sonda mede latência de discovery; os
  limites da P0.23 são de login/vazão/erro do cenário de benchmark,
  grandezas diferentes. A PLT-28 só aprova com o cenário que os limites
  descrevem (artefato: `keycloak.json`)

## Uso de recursos

- Picos por container (CPU/memória) durante a janela: _preencher a partir
  de `resultado.json` → `recursos.picos_por_container`_ (CSV bruto:
  `recursos.csv`, anexar)

## Veredito final da P2.9

- [ ] 100 mil contas em < 2 h
- [ ] p95 dentro do teto/linha de base
- [ ] Realm do portal dentro da capacidade da P0.23
- **Resultado:** _APROVADO / REPROVADO — preencher após a execução_
