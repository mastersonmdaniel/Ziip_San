# ED-07 — Monitoramento e alertas para o cliente: operação e ensaio do item 2.5 do SAMAE

## O que o tenant configura
1. Escolhe, entre os eventos do catálogo único da P1.38, quais geram alerta,
   indicando a versão declarada do evento e o usuário responsável.
2. Para cada escolha, decide se a entrega também sai por webhook (P2.26),
   o que exige um endpoint de saída cadastrado para aquele evento.
3. Indica o responsável do tenant para falhas de lote (P2.1).

Tentativa de configurar evento fora do catálogo, ou em versão não
declarada, é recusada na hora — nada é gravado.

## O que acontece quando o evento ocorre
- Nasce um alerta ao responsável, entregue na central de avisos (P3.36).
- Se a regra pede webhook e há endpoint configurado, a entrega sai assinada
  (HMAC-SHA-256 com carimbo de tempo), com tentativas e recuo exponencial
  com jitter; falhas consecutivas até o limiar da política desativam o
  endpoint automaticamente e o estado fica registrado.
- O alerta aparece na seção de alertas da página inicial por perfil (P3.39).
- A reentrega da mesma mensagem da outbox não duplica alerta nem entrega.

## Falha de lote
Execução de lote (P2.1) encerrada em FAILED notifica o responsável do
tenant pela porta tipada de falha de lote (BatchFailureNotificationPort).
A falha de lote NÃO é evento do catálogo da P1.38, e a central da P3.36 só
ingere evento do catálogo — por isso ela não entra na central sob um nome
inventado. Quando a integração declarar o evento de falha de lote no
vertical do pacote batch, o mesmo aviso entra na central pela ingestão
real (P336NotificationCenterAdapter.deliverBatchFailureWithDeclaredEvent).
Se nenhum responsável estiver configurado, o desfecho registrado é "sem
responsável" — o sistema não inventa destinatário.

## Painel de uso e desempenho do tenant
Por período: execuções de lote (total, concluídas, falhadas, interrompidas,
duração média e taxa de sucesso), alertas gerados e entregas de webhook
(total, sucesso, falha, taxa e endpoints desativados). Seção cuja fonte
ainda não foi ligada aparece como indisponível — nunca como zero.

## Ensaio do item 2.5 do SAMAE (alertas ao cliente) — roteiro
Pré-condição: ensaio com tenant sintético, sem dado real.
1. Configurar alerta para um evento do catálogo e disparar o evento:
   o responsável recebe o aviso na central; com webhook configurado, o
   endpoint de ensaio recebe a carga assinada (conferir com o
   WebhookSignatureVerifier: assinatura HMAC válida E carimbo dentro da
   tolerância — assinatura válida com carimbo velho é repetição recusada).
2. Tentar configurar um nome de evento inexistente: o sistema recusa.
3. Derrubar o endpoint de ensaio e disparar eventos até o limiar da
   política: o endpoint é desativado automaticamente e o painel mostra a
   desativação.
4. Provocar a falha de uma execução de lote: o responsável do tenant é
   notificado.
5. Abrir a página inicial com o perfil do responsável: o alerta consta na
   seção de alertas do tenant.
Resultado esperado: os cinco passos sem falha. O ensaio real (ambiente do
dono, CI total) é a prova do item — este pacote entrega o mecanismo e os
testes unitários; a execução do ensaio fica registrada como pendência no
RELATORIO-ED-07 até rodar na integração.
