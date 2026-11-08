# Totem em modo quiosque, senhas e painel de chamada — ED-40 (contrato de integração)

## Modo quiosque (portal da ED-38 no hardware do cliente)
- O portal roda no terminal do cliente com `portal.kiosk_enabled=true` (parâmetro do
  tenant, classe b, fábrica desligado). Desligado, o terminal não emite senha
  (`KioskTerminal` recusa) e a rota do quiosque não é exposta.
- Terminal é cadastrado por dispositivo e unidade de atendimento
  (`sac_kiosk_terminals`): o totem só emite senha da SUA unidade.
- Ações do quiosque (lista fechada): emitir senha, ajuda de acessibilidade e ver o
  painel. NENHUMA ação autenticada do portal (faturas, protocolos, dados pessoais)
  existe no quiosque — não há sessão de cliente no terminal público.
- Emissão: o cidadão escolhe o tipo de serviço e, quando for o caso, o perfil de
  atendimento prioritário da P1.3 (CAD-81 — categorias da Lei 10.048/2000,
  acompanhante, formato acessível e canal Libras/TDD/videoatendimento). A senha
  impressa/exibida traz só o código (ex.: `P003` prioritária, `N012` comum) e a
  hora de emissão.

## Fila e chamada (SG-625, SG-626)
- Ordenação: prioridade legal primeiro, depois ordem de emissão — mesma regra da
  fila da ED-35 (`ServiceQueue`). Empate: sequencial da senha.
- SG-625 (tela de atendimento): o atendente chama a próxima senha para o seu
  guichê; a chamada grava `called_at` e `desk_id` na senha. Início do atendimento
  grava `service_started_at`; encerramento, ausência (chamada sem comparecimento)
  e cancelamento fecham o ciclo sem reescrever instante anterior.
- SG-626 (painel de chamada em navegador): rota pública da unidade,
  `GET /painel-chamada?unidade=` — exibe a chamada atual e o histórico recente
  (código, guichê, hora). NENHUM nome ou dado pessoal vai ao painel.

## Relatório de espera (RAS-26)
- `GET /atendimento/relatorio-espera?de=&ate=` (backoffice): por unidade de
  atendimento e por dia — limite aplicado, senhas iniciadas, dentro/fora do
  limite, % dentro, espera média e máxima, e senhas ainda não iniciadas
  (contadas à parte, nunca dentro de dentro/fora).
- Limites: parâmetros `sac.queue_wait_limit_business_day_minutes` (fábrica 15) e
  `sac.queue_wait_limit_holiday_adjacent_minutes` (fábrica 30), valores de fábrica
  da Lei estadual SC 12.465/2002. Espera = emissão → início do atendimento.
- Dia de limite ampliado: véspera e dia seguinte a feriado PROLONGADO (sequência
  de dias não úteis ≥ 3 que contém o feriado), pelo calendário de feriados do
  tenant (dado parametrizável entregue à fiação; nenhuma lista no código).

## Fora desta entrega / integração
- Frontend do quiosque e do painel (telas) e a fiação JPA/sequência por
  unidade/dia: integração, como nas ED-35/ED-38 (núcleo provado, telas por rota).
- Impressora térmica do totem: hardware do cliente; o núcleo entrega o código e
  os instantes, a impressão é da camada de apresentação.
