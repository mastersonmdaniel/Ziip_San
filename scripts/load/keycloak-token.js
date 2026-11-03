/* Cenário reduzido do Keycloak (P0.23) — token endpoint, sem o kit oficial.
 *
 * O plano pede o kit de benchmark oficial do Keycloak (logins, renovações de token e
 * concessões client_credentials). O kit oficial (keycloak/keycloak-benchmark) exige seu
 * próprio build e um Keycloak em modo produção; nesta onda ele fica documentado em
 * keycloak-benchmark.sh e pendente de execução. Este cenário k6 é o substituto reduzido
 * e honesto: mede o token endpoint do Keycloak local, sem se apresentar como o kit.
 *
 * Exige: KC_URL, KC_REALM, KC_CLIENT_ID e KC_CLIENT_SECRET (client_credentials) ou
 * KC_USERNAME/KC_PASSWORD (password grant). Sem as variáveis, o script falha
 * explicitamente em vez de medir o vazio.
 *
 * Uso:
 *   KC_URL=http://127.0.0.1:8081 KC_REALM=ziipsan KC_CLIENT_ID=... KC_CLIENT_SECRET=... \
 *     k6 run --summary-export=/var/tmp/k6-kc.json scripts/load/keycloak-token.js
 */
import http from 'k6/http';
import { check, fail } from 'k6';

const KC_URL = __ENV.KC_URL || '';
const REALM = __ENV.KC_REALM || 'ziipsan';
const CLIENT_ID = __ENV.KC_CLIENT_ID || '';
const CLIENT_SECRET = __ENV.KC_CLIENT_SECRET || '';
const USERNAME = __ENV.KC_USERNAME || '';
const PASSWORD = __ENV.KC_PASSWORD || '';

if (KC_URL === '' || CLIENT_ID === '') {
  fail('KC_URL e KC_CLIENT_ID sao obrigatorios; sem Keycloak local nao ha medicao.');
}

export const options = {
  vus: Number(__ENV.VUS || 5),
  duration: __ENV.DURATION || '60s',
  thresholds: { http_req_failed: ['rate<0.01'] },
};

export default function () {
  const body = CLIENT_SECRET !== ''
    ? { grant_type: 'client_credentials', client_id: CLIENT_ID, client_secret: CLIENT_SECRET }
    : { grant_type: 'password', client_id: CLIENT_ID, username: USERNAME, password: PASSWORD };
  const res = http.post(`${KC_URL}/realms/${REALM}/protocol/openid-connect/token`, body);
  check(res, { 'token 200': (r) => r.status === 200 });
}
