# RB-08 — Fila fiscal acima do limite do RNF-08 (Central, P2.11)

- **Regra:** `central.fiscal.queue_over_limit` (`fiscal.queue.pending > limiar do RNF-08`, TENANT, WARNING, owner: platform)
- **Sinal:** documentos aguardando desfecho (RESERVED, SIGNED, TRANSMITTED, PENDING_RECONCILIATION) acima do limiar configurado em `central.billing_fiscal.thresholds`.

## Procedimento

1. Na aba **Fiscal** da Central, abrir o tenant do alerta e ver a composição da fila por estado e a idade do documento mais antigo.
2. TRANSMITTED/PENDING_RECONCILIATION antigos sugerem SEFAZ sem retorno conclusivo: conferir o RB-04 (SEFAZ indisponível) antes de qualquer reenvio.
3. RESERVED/SIGNED antigos sugerem parada interna de assinatura/transmissão: escalar à operação fiscal da Ziip com tenant, contagens e idade.
4. A operação SOBRE os documentos (reenvio, inutilização, cancelamento) é do tenant, na tela da P2.33 — a Central só observa e escala; não operar documento pela Central.

## Não fazer

- Não baixar o limiar na configuração para silenciar o alerta.
- Não tratar documento REJECTED como fila: rejeitado é terminal (a correção é documento novo, P2.6) e a fila dele é a do tenant (P2.33).
