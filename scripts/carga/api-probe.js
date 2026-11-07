// P2.9 -- Equivalente k6 da sonda de p95 (paridade com a ferramenta citada
// na P0.23: "k6 ou Gatling"). Uso opcional: o caminho padrao do pacote e o
// medir-api.py (sem dependencia). Rode em paralelo ao faturamento:
//   k6 run -e API_BASE_URL=http://127.0.0.1:8090 -e DURACAO=2h scripts/carga/api-probe.js
// O relatorio do k6 (http_req_duration p(95)) entra no registro junto do
// resumo-api.json; os dois medem a mesma coisa, use UM deles por execucao.
//
// Veredito do p95, IGUAL ao da sonda Python (medir-api.py):
//   - P95_MAX_MS definido      -> threshold p(95) < P95_MAX_MS;
//   - P95_BASELINE_MS definido -> threshold p(95) < baseline * 1.15
//     (regra de regressao de +15% do RNF-70, P0.23);
//   - os dois definidos -> os DOIS thresholds valem (reprova em qualquer um);
//   - nenhum -> sem threshold: apenas mede e reporta.
import http from 'k6/http';
import { check } from 'k6';

const thresholds = [];
if (__ENV.P95_MAX_MS) {
  thresholds.push(`p(95)<${__ENV.P95_MAX_MS}`);
}
if (__ENV.P95_BASELINE_MS) {
  thresholds.push(`p(95)<${Number(__ENV.P95_BASELINE_MS) * 1.15}`);
}

export const options = {
  vus: Number(__ENV.VUS || 8),
  duration: __ENV.DURACAO || '2h',
  thresholds: thresholds.length ? { http_req_duration: thresholds } : {},
};

const PATHS = (__ENV.API_PROBE_PATHS || '/actuator/health').split(',');
export default function () {
  const path = PATHS[Math.floor(Math.random() * PATHS.length)];
  const res = http.get(`${__ENV.API_BASE_URL}${path}`, {
    headers: __ENV.API_BEARER_TOKEN
      ? { Authorization: `Bearer ${__ENV.API_BEARER_TOKEN}` } : {},
  });
  check(res, { 'status < 500': (r) => r.status < 500 });
}
