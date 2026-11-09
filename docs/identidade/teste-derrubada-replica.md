# Teste — Derrubada de uma réplica do Keycloak sem perda de sessão (LC.6)

Produtor: sessão `92031a19-4d6d-4174-92b7-164897d85503` (papel: Produtor).

> **NÃO EXECUTADO neste bloco.** Sem conta AWS (regra dura): este é o
> roteiro pronto para execução na implantação, com a evidência esperada
> definida antes. Nenhum resultado é alegado. Pendência **P-LC6-03**.
> É a reaplicação em AWS do ensaio do RNF-17 da P0.29 ("derrubada de
> uma réplica sem perda de sessão").

## Pré-condições

1. Keycloak de produção implantado com este pacote: 2 réplicas
   saudáveis (`/health/ready` OK nas duas), `start --optimized`,
   TLS ativo, realm com `sslRequired=all` e tema da P0.35.
2. Usuário de teste dedicado do realm de produção-homologação
   (credencial no cofre, LC.4) — nunca usuário real de tenant.
3. Janela combinada e registro aberto (modelo de registro da LC.7 /
   P3.11 pode ser reutilizado).

## Procedimento

1. **Sessão na réplica A.** Autenticar o usuário de teste pelo fluxo
   público (HTTPS) e confirmar, nos logs/métricas, qual réplica
   atendeu o login. Guardar: horário, réplica, `session_state`/id de
   sessão (sem token em texto claro no registro).
2. **Uso contínuo.** Com a mesma sessão, abrir o console/uma aplicação
   cliente do realm e executar uma ação autenticada. Confirmar sessão
   válida.
3. **Derrubada.** Encerrar **uma** réplica pelo orquestrador da
   implantação (a que atendeu o login, se identificável; senão,
   qualquer uma): parar a tarefa/contêiner, sem tocar no banco nem na
   outra réplica. Registrar o horário exato da derrubada.
4. **Continuidade imediata.** Sem novo login, repetir a ação
   autenticada do passo 2 e forçar uma renovação de token
   (`refresh_token`) pelo cliente.
5. **Verificação.** A sessão deve seguir válida: ação autenticada com
   sucesso, renovação de token com sucesso, nenhum redirecionamento
   para a tela de login, nenhum erro de sessão inválida nos logs da
   réplica sobrevivente.
6. **Retorno.** Subir a réplica derrubada, aguardar `/health/ready` e
   a ressincronização do cache (descoberta `jdbc-ping`), e repetir os
   passos 4–5 uma vez.

## Evidência esperada (a coletar na execução)

- Identificação das 2 réplicas e dos horários (login, derrubada,
  verificações, retorno) em UTC.
- Logs das duas réplicas no intervalo (trechos com o id de sessão,
  sem tokens nem segredos).
- Resultado de cada verificação do passo 5 (sucesso/falha por item).
- Estado de saúde antes/depois (`/health/ready` das réplicas).

## Critério de aprovação

- **Aprovado:** sessão do passo 1 atravessa a derrubada sem novo
  login, com ação autenticada e renovação de token bem-sucedidas na
  réplica sobrevivente, e o mesmo após o retorno da réplica.
- **Reprovado:** qualquer exigência de novo login, erro de sessão
  inválida ou perda da sessão em qualquer verificação. Reprovação
  bloqueia o aceite da LC.6 e vira defeito da implantação (investigar
  cache `ispn`/`jdbc-ping` e afinidade do balanceador — a sessão não
  pode depender de afinidade de réplica).

## Registro

Preencher uma linha por execução: data/hora UTC, executor, ambiente,
réplica derrubada, resultado por passo e onde a evidência ficou
guardada. Linha pré-preenchida **não** existe de propósito — execução
não se inventa.
