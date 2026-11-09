# Plantão de produção — definição (LC.7)

Produtor: sessão `92031a19-4d6d-4174-92b7-164897d85503` (papel: Produtor).

Aceite LC.7: "plantão definido". A P3.11 remete o plantão de produção
a esta tarefa; o aceite da P3.11 fixa o substrato (alertas com
responsável e runbook obrigatórios; revisão pós-incidente sem culpa).

> **Limite honesto desta definição:** ela fixa **estrutura, papéis,
> rotação e regras de acionamento**. **Nomes de pessoas, escalas
> reais, telefones e canais pessoais são `[A DEFINIR]`** — ninguém
> inventa escala de pessoas (pendência P-LC7-03).

## 1. Papéis (estrutura)

| Papel | Responsabilidade | Quem |
|---|---|---|
| Plantonista primário | Primeiro acionado dos alertas de página; conduz o runbook | `[A DEFINIR — pessoa da rotação vigente]` |
| Plantonista secundário (backup) | Assume se o primário não responder no prazo de escalonamento ou em indisponibilidade declarada | `[A DEFINIR]` |
| Responsável pelo serviço (`owner`) | Dono do runbook e da revisão pós-incidente do componente; nas regras da P3.11, `platform` | `platform` (papel, da P3.11) |
| Comunicador da página de status | Publica/atualiza incidente e o resumo pós-incidente na página (P3.18) a partir dos fatos | `[A DEFINIR — papel na rotação]` |

## 2. Rotação (estrutura)

- Rotação **semanal** entre os membros elegíveis da equipe de
  plataforma: `[A DEFINIR — lista de elegíveis e ordem]` (P-LC7-03).
  A periodicidade semanal é a estrutura proposta por este pacote e
  fica **a ratificar** na ativação do plantão; nenhuma fonte local
  fixa periodicidade, e nenhuma escala concreta é desenhada aqui.
- Troca de turno só com passagem registrada (incidentes abertos,
  alertas silenciados e por quê, exercícios pendentes).
- Cobertura: o aceite não fixa cobertura 24×7 para todos os alertas,
  e o ADR-0020 registra que o plantão 24×7 da severidade máxima
  depende de decisão de equipe (trilha do produto). Portanto:
  - Alertas de **página** (burn ≥ 6; `CRITICAL` executáveis): acionam
    o plantonista primário a qualquer hora **se e quando** a cobertura
    24×7 for decidida — decisão `[A DEFINIR]` do dono/equipe
    (P-LC7-03). Sem essa decisão, este pacote **não promete** tempo de
    resposta noturno.
  - Alertas de **ticket** (burn ≥ 1 e < 6; `WARNING`): fila do próximo
    dia útil, sem acionamento imediato.

## 3. Acionamento e escalonamento

1. Alerta de página dispara pela ferramenta de plantão
   `[A DEFINIR — ferramenta/canal]` (P-LC7-03), sempre com o
   `runbook_url` da regra (ligação em
   `docs/operacao/alertas-runbooks-producao.md`).
2. Sem confirmação de recebimento pelo primário no prazo
   `[A DEFINIR — minutos]`, escala ao secundário; sem resposta do
   secundário, ao responsável pelo serviço. Os prazos numéricos são
   `[A DEFINIR]`: nenhuma fonte local os fixa.
3. Toda página vira registro: ou exercício (se provocada) ou incidente
   com revisão pós-incidente pelo modelo da P3.11.

## 4. Compensação e limites

- Folga/compensação pós-plantão e remuneração: `[A DEFINIR]` —
  matéria de equipe/contrato, fora das fontes técnicas deste pacote.
- Plantonista nunca executa ação destrutiva fora do runbook sem
  segunda pessoa (regra operacional deste pacote, herdada do espírito
  dos runbooks da P3.11: "sem promoção manual impulsiva", "não apagar
  mensagens à mão").

## 5. Ativação

Plantão "definido" no papel ≠ plantão ativo: a ativação exige as
pessoas nomeadas, a ferramenta configurada com as regras executáveis
e um exercício de acionamento (página de teste) registrado — tudo
pendente de P-LC7-03 e da AWS. Nada disso é alegado como feito.
