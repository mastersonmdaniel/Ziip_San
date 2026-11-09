# ADENDO-TETO — Diário C6 (R7-I-m2)

**Data:** 09/11/2026. Corretor do lote R7-B (sessão
`b6cf0c91-9a27-46d5-a359-d4a9ddcaecb9`).

**O que autorizou o pouso vitorioso:** `runner-big.py`, teto
**400.000 bytes**. O lote tem 165.213 bytes — acima do teto de 115.000
do `runner.py` (que parou às 23:02:27 sem tentar a escrita) e abaixo
dos 400.000 do `runner-big.py` (que tentou às 23:02:43, 23:34:15 e
venceu às 03:14:20 → commit `4d88b898`). Detalhe e reconstrução
completa das 4 invocações: adendo datado de 09/11/2026 ao fim de
`cascata-ed/RELATORIO-C6.md`.

**Plano:** segue 1 lote / 1 arquivo / 161 KB — a divisão ordenada pela
PARADA nunca foi executada; o pouso venceu pelo teto maior. Não há
divisão pendente.

**Mensagem corrigida:** a PARADA do `runner-big.py` dizia "~110 KB"
com teto real de 400.000 bytes — texto corrigido em
`cascata-ed/push/runner-big.py` (só texto, sem mudança de
comportamento). `base-ficticia-12k/push/runner-big.py` tem a mesma
mensagem enganosa e **não** foi tocado (pouso consolidado em curso
com ele) — pendência de acompanhamento pós-pouso.

**Regra prospectiva:** toda tentativa de pouso regista runner + teto
no `exec.log`.
