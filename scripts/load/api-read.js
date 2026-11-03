/* Carga de referência da API (P0.23, RNF-70) — cenário reduzido executável.
 *
 * Escala cheia do plano: tenant sintético de 20 mil ligações (a carga de referência
 * completa, com 300 mil ligações e 300 schemas, é a P2.9). Esta VM não comporta a escala
 * cheia: o cenário é parametrizável (VUS, DURATION) e a escala cheia fica pendente com
 * o harness pronto — nunca rode a escala reduzida e a reporte como escala cheia.
 *
 * Alvos reais (rotas que existem nesta onda):
 *  - GET /actuator/health (sem autenticação; mede infra, não negócio)
 *  - GET /api/v1/registry/consumer-units (autenticado; exige TOKEN com registry:read)
 * Sem TOKEN, só o cenário de health roda — o script avisa e não inventa autenticação.
 *
 * Uso:
 *   BASE_URL=http://127.0.0.1:8080 VUS=10 DURATION=60s TOKEN=<token> \
 *     k6 run --summary-export=/var/tmp/k6-api.json scripts/load/api-read.js
 *   python3 scripts/load/check-regression.py /var/tmp/k6-api.json
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const TOKEN = __ENV.TOKEN || '';

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '60s',
  thresholds: {
    // Sem limiar absoluto aqui: o gate é a regressão de 15% no p95 (RNF-70),
    // aplicada por check-regression.py sobre a baseline versionada.
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  let res = http.get(`${BASE_URL}/actuator/health`);
  check(res, { 'health 200': (r) => r.status === 200 });
  if (TOKEN !== '') {
    const authed = http.get(`${BASE_URL}/api/v1/registry/consumer-units?page=0&size=20`, {
      headers: { Authorization: `Bearer ${TOKEN}` },
    });
    check(authed, { 'registry 200 ou 401/403 explicito': (r) => [200, 401, 403].includes(r.status) });
  }
  sleep(0.2);
}

export function handleSummary(data) {
  const p95 = data.metrics.http_req_duration.values['p(95)'];
  console.log(`P0.23 api-read: p95=${p95}ms (escala reduzida; comparar com baseline via check-regression.py)`);
  return { stdout: JSON.stringify(data, null, 2) };
}
