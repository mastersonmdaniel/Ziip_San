#!/bin/sh
# P3.11 — falha provocada no AMBIENTE LOCAL (P0.27). Nunca usar contra
# produção: o script recusa qualquer alvo que não seja localhost/127.0.0.1.
#
# GUARDA DO ALVO (correção pós-revisão P-3): a validação ancora no HOST
# real da URL — a autoridade é extraída, userinfo é recusado e o host
# precisa ser EXATAMENTE "127.0.0.1" ou "localhost" (delimitado por ":",
# "/", "?" , "#" ou fim da URL), com porta numérica quando presente.
# A versão anterior casava por prefixo e ACEITAVA alvos externos
# disfarçados. Casos de bypass que esta guarda RECUSA (provados por
# `--verificar-guarda`, ver abaixo):
#   http://localhost.evil.com          http://127.0.0.1.evil.com
#   http://127.0.0.1@evil.com/x        http://localhost@evil.com
#   http://usuario:senha@127.0.0.1     http://127.0.0.1:abc
#   http://192.168.0.10                https://127.0.0.1 (esquema https
#                                      é recusado: fail-closed, só http)
# Casos aceitos: http://127.0.0.1, http://127.0.0.1:<porta>,
# http://localhost, http://localhost:<porta>, com caminho opcional.
#
# ALVO SEM PADRÃO (correção pós-revisão, §5 da revisão): não há mais URL
# padrão. O `local-env.md` (P0.27) NÃO documenta a porta da API no perfil
# `homolog` e cita `http://localhost:8080` como o emissor (`iss`) do
# Keycloak local — não como a aplicação. Definir `ZIIPSAN_LOCAL_URL`
# explicitamente com a base real da API no Compose local é obrigatório.
#
# ENDPOINT DE CAOS — PENDÊNCIA EXPLÍCITA DE INTEGRAÇÃO COM A P0.27:
# `local-env.md` não documenta NENHUM endpoint de caos, e os endpoints
# actuator documentados (P0.8) são AUTENTICADOS (este script não envia
# credencial). O caminho abaixo é a INTERFACE ESPERADA, não um endpoint
# existente: até a P0.27 expô-lo (com a autenticação definida), a chamada
# falha com erro explícito — comportamento intencional, nunca sucesso.
set -eu

CHAOS_PATH="${ZIIPSAN_CHAOS_PATH:-/actuator/local-chaos/fail-once}"

# Devolve 0 somente se o HOST real da URL for exatamente 127.0.0.1 ou
# localhost, sem userinfo, com esquema http e porta numérica (se houver).
is_local_target() {
  url="$1"
  case "$url" in
    http://*) ;;
    *) return 1 ;;
  esac
  rest="${url#http://}"
  authority="${rest%%/*}"
  authority="${authority%%\?*}"
  authority="${authority%%#*}"
  case "$authority" in
    ""|*@*) return 1 ;;
  esac
  host="$authority"
  case "$authority" in
    *:*)
      host="${authority%%:*}"
      port="${authority#*:}"
      case "$port" in
        ""|*[!0-9]*|*:*) return 1 ;;
      esac
      ;;
  esac
  host_lc="$(printf '%s' "$host" | tr 'A-Z' 'a-z')"
  [ "$host_lc" = "127.0.0.1" ] || [ "$host_lc" = "localhost" ]
}

# Autoteste da guarda (não chama a rede): cada caso documentado acima é
# conferido; qualquer divergência é erro explícito.
if [ "${1:-}" = "--verificar-guarda" ]; then
  falhas=0
  espera_aceite() {
    if is_local_target "$1"; then echo "OK  aceito (esperado): $1";
    else echo "FALHA deveria aceitar: $1" >&2; falhas=1; fi
  }
  espera_recusa() {
    if is_local_target "$1"; then echo "FALHA deveria recusar: $1" >&2; falhas=1;
    else echo "OK  recusado (esperado): $1"; fi
  }
  espera_aceite "http://127.0.0.1"
  espera_aceite "http://127.0.0.1:8080"
  espera_aceite "http://127.0.0.1:9000/algum/caminho"
  espera_aceite "http://localhost"
  espera_aceite "http://localhost:9000"
  espera_recusa "http://localhost.evil.com"
  espera_recusa "http://127.0.0.1.evil.com"
  espera_recusa "http://127.0.0.1@evil.com/x"
  espera_recusa "http://localhost@evil.com"
  espera_recusa "http://usuario:senha@127.0.0.1"
  espera_recusa "http://127.0.0.1:abc"
  espera_recusa "http://127.0.0.1:"
  espera_recusa "http://192.168.0.10"
  espera_recusa "http://example.com"
  espera_recusa "https://127.0.0.1"
  if [ "$falhas" -ne 0 ]; then echo "ERRO: guarda do alvo falhou no autoteste." >&2; exit 1; fi
  echo "Guarda do alvo: todos os casos conferem."
  exit 0
fi

if [ -z "${ZIIPSAN_LOCAL_URL:-}" ]; then
  echo "RECUSADO: ZIIPSAN_LOCAL_URL não definida." >&2
  echo "Motivo: o local-env.md (P0.27) não fixa a porta da API no perfil homolog;" >&2
  echo "http://localhost:8080 é o emissor do Keycloak local, não a aplicação." >&2
  echo "Defina ZIIPSAN_LOCAL_URL com a base local real da API (ex.: http://127.0.0.1:<porta-do-Compose>)." >&2
  exit 1
fi
BASE_URL="${ZIIPSAN_LOCAL_URL%/}"
if ! is_local_target "$BASE_URL"; then
  echo "RECUSADO: alvo não é local (host deve ser exatamente 127.0.0.1 ou localhost, sem userinfo): $BASE_URL" >&2
  exit 1
fi

echo "Provocando falha local em $BASE_URL (interface esperada de caos — pendência de integração com a P0.27)..."
# Se o endpoint não existir neste ambiente (estado atual documentado) ou
# exigir autenticação, isso é erro explícito (não sucesso).
curl -fsS -X POST "$BASE_URL$CHAOS_PATH" || {
  echo "ERRO: endpoint de caos não respondeu — ele NÃO consta do local-env.md (P0.27) e os actuator" >&2
  echo "documentados são autenticados; trate como pendência de integração com a P0.27, não como falha provocada." >&2
  exit 1; }
echo "Falha provocada. Confira o alerta na Central e siga o runbook RB-01."
