# Error tracking de produção — LC.7

Produtor: sessão `92031a19-4d6d-4174-92b7-164897d85503` (papel: Produtor).

Escopo: o aceite da LC.7 pede **error tracking** gerenciado em
produção. A P3.11 tratou só do ambiente local e remeteu expressamente
o de produção a esta tarefa ("Nenhuma ferramenta externa é contratada
nesta tarefa; a escolha do serviço gerenciado fica para a LC.7/LC.2" —
`error-tracking-local.md` da P3.11).

## 1. Serviço — [A DEFINIR] (pendência P-LC7-02)

Nenhuma fonte local nomeia o serviço gerenciado de error tracking.
Ele é **parâmetro de implantação**, não escolha deste pacote:

- `ERROR_TRACKING_DSN`: `[A DEFINIR — referência de segredo (LC.4), sem valor]`
- `ERROR_TRACKING_ENVIRONMENT`: `producao`
- Provedor: `[A DEFINIR]` — critério mínimo de seleção (a registrar na
  decisão): residência de dados compatível com o inventário da LC.2
  (RNF-44, Brasil), retenção configurável e expurgo, e captura sem
  dado pessoal por padrão (ver §3).

## 2. O que é capturado (a partir das bases existentes)

- **Entrada primária:** exceções não tratadas e erros 5xx da API — a
  mesma entrada que a P3.11 propõe para o SLI `api.availability`
  (derivação de `http.server.requests`, P0.8).
- **Correlação obrigatória:** cada evento carrega `X-Request-ID` e o
  `trace_id` do OpenTelemetry (coletor deste pacote), para ir do erro
  ao trace e ao log JSON (bases da P0.8). `tenant_id` só nos termos da
  P0.8 (traces e resumos agregados; nunca como rótulo irrestrito).
- **Agrupamento:** pelo serviço + tipo de exceção + ponto de captura
  (fingerprint padrão do serviço escolhido); exceção não tratada sem
  alerta associado é registrada como lacuna na revisão pós-incidente
  (regra herdada da P3.11).

## 3. Privacidade (herdada, não afrouxada)

- Antes do envio, remover: corpos de requisição/resposta com dado
  pessoal, credenciais, tokens e cabeçalhos de autorização. O que a
  fonte local fixa é o princípio (P0.8: telemetria com privacidade;
  logs com `X-Request-ID`, sem segredo); a lista campo a campo do
  serviço escolhido é `[A DEFINIR]` na ativação, junto da seleção do
  provedor (P-LC7-02) — nenhum campo sensível é enviado "para
  depuração".
- Eventos de error tracking **não** alimentam a página pública de
  status: ela publica só o resumo pós-incidente aprovado (P3.11/P3.18).

## 4. Ligação com alerta e plantão

- Erro novo em produção (primeira ocorrência) e regressão de erro
  conhecido geram notificação ao plantão (estrutura em
  `docs/operacao/plantao-producao.md`); o canal e a ferramenta de
  notificação são `[A DEFINIR]` (P-LC7-03).
- Severidade segue a ligação alerta→runbook
  (`docs/operacao/alertas-runbooks-producao.md`): erro 5xx sustentado
  é tratado pelo RB-01; os demais runbooks conforme o componente.

## 5. Ativação (pendente de AWS/provedor)

A ativação real — criar o projeto no serviço, injetar a DSN pelo
cofre e provar a chegada de um erro de teste — exige a conta AWS e a
escolha do provedor: **não executada neste bloco**, pendências
P-LC7-01/P-LC7-02. Nenhum evento de teste é alegado.
