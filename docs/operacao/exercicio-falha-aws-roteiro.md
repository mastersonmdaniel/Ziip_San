# Exercício de falha provocada na AWS — roteiro (LC.7 / PLT-23)

Produtor: sessão `92031a19-4d6d-4174-92b7-164897d85503` (papel: Produtor).

> **NÃO EXECUTADO neste bloco.** Sem conta AWS (regra dura). Roteiro
> pronto para a primeira janela de produção controlada após o
> lançamento. Nenhum resultado é alegado. Pendência **P-LC7-08**.
>
> Base: aceite da LC.7 — "exercício com falha provocada na AWS
> conduzido pelo runbook e revisão pós-incidente registrada" — e o
> programa de exercícios da P3.11, que prevê os mesmos cenários "em
> produção controlada" depois do lançamento.

## 1. Cenário da primeira execução (escolha deste roteiro)

**Fila parada (coletor parado)** — é o único cenário com **alerta
executável distinguível** no catálogo vigente
(`central.collector.stale`, CRITICAL, RB-02). Executar primeiro um
cenário sem regra executável não provaria disparo de alerta (limite
declarado pela P3.11 e mantido na LC.7). Os demais cenários do
programa da P3.11 (réplica, PSP, SEFAZ, certificado, falha genérica)
entram nas janelas seguintes, cada um com a ressalva registrada:
sem regra executável, o exercício prova o **runbook**, não o alerta.

Para o Keycloak, o exercício de derrubada de réplica tem roteiro
próprio na LC.6 (`teste-derrubada-replica.md`) e pode dividir a mesma
janela, com registro separado.

## 2. Pré-condições

1. Produção na AWS ativa (LC.2/LC.3/LC.4), coletor OTel exportando
   (P-LC7-01 resolvida) e regra `central.collector.stale` avaliada no
   serviço gerenciado.
2. Plantão ativo (P-LC7-03 resolvida) e janela combinada fora do pico,
   com comunicador da página de status designado.
3. Registro aberto (tabela do §5) e revisão pós-incidente criada
   (modelo em `revisao-pos-incidente-aws.md`), ainda vazia.

## 3. Procedimento (conduzido pelo RB-02)

1. Anotar a linha de base: idade da última coleta
   (`platform.collector.last_run_age_s`) e estado da página de status.
2. **Provocar:** parar o coletor/worker de produção de forma
   controlada e reversível (parar o serviço; nunca apagar mensagens
   PENDING/DEAD — proibição expressa do RB-02).
3. Aguardar o alerta `central.collector.stale` (limiar > 900 s):
   registrar horário do disparo, canal e quem foi acionado.
4. **Conduzir pelo RB-02, sem atalhos:** conferir `last_run_at`
   (ausência = nunca coletou), idade da PENDING mais antiga e DEAD,
   reiniciar o coletor, confirmar retomada e resolução do alerta.
5. Conferir a página de status: o componente afetado deve ter
   refletido o fato (sonda/incidente) e voltado após a mitigação.
6. Fechar o exercício no registro (§5) e concluir a revisão
   pós-incidente com o bloco "Resumo publicável" — publicar o resumo
   na página **só** se o exercício teve impacto visível ao usuário;
   exercício sem impacto não gera resumo público (declarar isso na
   revisão).

## 4. Evidência esperada

- Horários UTC de: provocação, disparo do alerta, acionamento,
  mitigação e resolução.
- Id do evento de alerta no serviço gerenciado e trechos de log com
  `X-Request-ID` (sem segredos nem dado pessoal).
- Estado da página de status durante e depois (capturas ou o JSON de
  `GET /api/v1/public/status` dos dois momentos).

## 5. Registro (preencher na execução — nenhuma linha pré-preenchida)

| Data | Cenário | Ambiente | Alerta disparou? (id) | Tempo até alerta | Tempo até mitigação | Runbook bastou? | Lacunas e correções |
|---|---|---|---|---|---|---|---|
| _(a preencher na 1ª execução)_ | fila parada (coletor) | produção AWS | | | | | |

## 6. Critério de aprovação do exercício

- **Aprovado:** alerta executável disparou, plantão foi acionado, o
  RB-02 bastou para mitigar, a página refletiu o fato e a revisão
  pós-incidente foi registrada.
- **Reprovado (com valor):** qualquer elo que falhe vira correção
  registrada (runbook, regra, fiação ou plantão) no mesmo ciclo —
  exercício sem registro não aconteceu (regra da P3.11).
