# Checklist do gate de treinamento — jornada de implantação (ED-11, PLT-21)

Checklist operacional do passo `TRAINING` da jornada de implantação do
console da P1.6. Cada item corresponde a um bloqueador nomeado de
`TrainingGate`/`PreTrainingTenant`/`TrainingCompletion`/`ItEnablement`:
o passo só é marcado `DONE` em `platform.onboarding_step` com todos os
itens verdes, e o motivo de qualquer item vermelho é o texto do
bloqueador, não uma anotação livre.

## Ambiente

- [ ] Tenant de pré-treinamento provisionado e registrado
  (`platform.pretraining_tenant`), com manifesto validado por
  `scripts/treinamento/validar-ambiente.py`.
- [ ] Origem é massa sintética **ou** clone mascarado da ED-09 com
  finalidade `TRAINING`.
- [ ] Se clone: portão da ED-09 liberado (origem produção, sem clone de
  clone, avaliação de reidentificação do RIPD anterior ao pedido e
  viável).
- [ ] Marca de sintético presente; varredura da P0.45 com **0**
  violações.
- [ ] Emissão fiscal de produção desligada no tenant de
  pré-treinamento.

## Escopo e planos

- [ ] Escopo de treinamento da implantação declarado (conjunto de
  módulos requeridos) e não vazio.
- [ ] Módulo `TI_CLIENTE` (capacitação da TI) dentro do escopo.
- [ ] Todo módulo do escopo com plano válido: total de exatamente
  **40 h** (`TrainingPlan.validate` sem achados).

## Execução

- [ ] Para cada módulo do escopo: todas as unidades do plano
  entregues, horas entregues ≥ 40 h, pelo menos um participante
  inscrito e todos os inscritos concluídos (registro em
  `REGISTRO-CONCLUSAO.template.csv` / `platform.training_module_completion`).
- [ ] Capacitação da TI: tópicos obrigatórios entregues; tópicos com
  prática exercitados no tenant de pré-treinamento; tópico
  `INSTANCIA_DEDICADA` entregue se o contrato exige instância
  dedicada (LC.15).

## Fechamento

- [ ] `TrainingGate.assess` sem bloqueadores para a implantação.
- [ ] Documentação das customizações do tenant completa ou com a
  declaração explícita de inexistência (ver
  `CUSTOMIZACOES-DO-CLIENTE.md`) — item irmão do aceite da ED-11,
  conferido no fechamento da implantação (não é bloqueador deste
  gate, que é o do treinamento).
- [ ] Passo `TRAINING` gravado como `DONE` pelo console da P1.6
  somente depois dos itens acima (fiação da integração).

> Pendência declarada: na ED-09 o passo `TRAINING` é obrigatório mas
> não está em `GATE_STEP_KEYS` de `ImplantationPlan`. A promoção do
> passo a gate nesse conjunto — ou a consulta obrigatória a
> `TrainingGate` pelo console antes do `DONE` — é o ajuste de
> integração nomeado no relatório da ED-11:
> **[A DEFINIR NA INTEGRAÇÃO: promoção do passo TRAINING a gate em
> `ImplantationPlan.GATE_STEP_KEYS` (ED-09) ou consulta obrigatória do
> console a `TrainingGate` antes de concluir o passo]**.
