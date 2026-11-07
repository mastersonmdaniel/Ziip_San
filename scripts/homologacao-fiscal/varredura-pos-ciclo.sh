#!/usr/bin/env bash
# P3.7 — Varredura (padrao P0.45) depois do fim do ciclo: procura o certificado
# da Ziip/TGP nos lugares onde ele NUNCA pode estar/restar.
#
# Uso:  ZIIPSAN_THUMBPRINT_ZIIP="<thumbprint lido do cofre>" \
#       ./varredura-pos-ciclo.sh /raiz/do/repo /pasta/de/backups /area/separada
#
# DETECCAO (correcao D1 da revisao): num XML real o certificado viaja no
# elemento X509Certificate como DER em BASE64 — o thumbprint (hex do hash do
# DER) NUNCA aparece como texto no arquivo, e uma busca textual pelo
# thumbprint declarava "limpa" uma raiz contendo o certificado. Este script
# aplica o MESMO algoritmo da classe VarreduraHomologacao: extrai cada
# X509Certificate, decodifica base64->DER (python3) e compara o SHA-1 e o
# SHA-256 calculados do DER com o thumbprint esperado. A busca textual pelo
# thumbprint permanece apenas como fallback fechado (marcadores/registros):
# ela so adiciona recusas, nunca substitui a deteccao por hash.
#
# Sai com codigo 1 se encontrar qualquer ocorrencia. A decisao formal (recusar)
# e do teste VarreduraHomologacaoTest no CI; este script e a varredura
# operacional sobre o disco do ambiente local no fechamento do ciclo.
set -euo pipefail

THUMB="${ZIIPSAN_THUMBPRINT_ZIIP:?defina ZIIPSAN_THUMBPRINT_ZIIP (lido do cofre local, nunca gravado em arquivo do repo)}"
[ "$#" -ge 1 ] || { echo "uso: varredura-pos-ciclo.sh <raiz> [raiz...]" >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "ERRO: python3 e obrigatorio para a varredura (extracao base64->DER do X509Certificate)" >&2; exit 2; }

FALHOU=0
for RAIZ in "$@"; do
  [ -e "$RAIZ" ] || { echo "AVISO: raiz inexistente ignorada: $RAIZ" >&2; continue; }
  while IFS= read -r -d '' ARQ; do
    if ! ZIIPSAN_THUMBPRINT_ZIIP="$THUMB" python3 - "$ARQ" <<'PYEOF'
import base64
import hashlib
import os
import re
import sys

caminho = sys.argv[1]
esperado = re.sub(r"[\s:]+", "", os.environ.get("ZIIPSAN_THUMBPRINT_ZIIP", "")).lower()
if not esperado:
    sys.exit(2)
try:
    with open(caminho, "rb") as f:
        bruto = f.read()
except OSError:
    sys.exit(0)
texto = bruto.decode("utf-8", errors="ignore")

def hashes_do_der(b64):
    limpo = re.sub(r"\s+", "", b64)
    der = None
    for decodificar in (base64.b64decode, base64.urlsafe_b64decode):
        try:
            der = decodificar(limpo)
            break
        except Exception:
            der = None
    if not der:
        return ()
    return (hashlib.sha1(der).hexdigest(), hashlib.sha256(der).hexdigest())

# 1) Deteccao real: X509Certificate em base64 -> DER -> thumbprint calculado.
for conteudo in re.findall(
        r"<(?:[A-Za-z0-9_.-]+:)?X509Certificate(?:\s[^>]*)?>(.*?)</(?:[A-Za-z0-9_.-]+:)?X509Certificate\s*>",
        texto, re.DOTALL):
    limpo = re.sub(r"\s+", "", conteudo)
    if limpo.lower() == esperado:
        sys.exit(1)  # marcador textual dentro do elemento
    if esperado in hashes_do_der(limpo):
        sys.exit(1)  # certificado real: o hash do DER confere

# 2) Fallback fechado: o thumbprint como texto em qualquer ponto do arquivo.
if esperado in re.sub(r"[\s:]+", "", texto).lower():
    sys.exit(1)
sys.exit(0)
PYEOF
    then
      echo "VIOLACAO: arquivo com o certificado da Ziip/TGP encontrado: $ARQ (raiz: $RAIZ)" >&2
      FALHOU=1
    fi
  done < <(find "$RAIZ" -type f -not -path '*/.git/*' -print0 2>/dev/null)
done
if [ "$FALHOU" -eq 1 ]; then
  echo "Varredura RECUSOU o fechamento: ver violacoes acima." >&2
  exit 1
fi
echo "Varredura limpa: nenhum arquivo com o certificado da Ziip/TGP nas raizes varridas (extracao X509Certificate base64->DER conferida)."
