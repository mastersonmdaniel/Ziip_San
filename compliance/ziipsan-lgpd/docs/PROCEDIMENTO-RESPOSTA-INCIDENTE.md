# Procedimento de resposta a incidente com dado pessoal — P3.10 (PLT-34)

Papéis: o **Ziip SAN é o operador**; o **tenant é o controlador**. Este procedimento é o
artefato operacional da P3.10; os prazos de código estão em
`br.com.ziipsan.compliance.lgpd.incident` (`IncidentClock`, `LgpdIncidentParameters`,
`AnpdDeadlines`).

## 1. Conhecimento e abertura (T0)

1. Qualquer canal que trouxer a suspeita (Central, suporte, alerta de acesso anômalo do
   ADR-0008/RNF-41, suboperador) registra o **instante do conhecimento pelo Ziip SAN**.
2. Abre-se a **ocorrência da P2.25** (tipo incidente) e o registro do incidente nasce ligado
   a ela (`occurrenceId`) — incidente e RIPD compartilham esse vínculo.
3. O **relógio do incidente inicia no conhecimento** (`IncidentClock.start`), com o valor
   vigente de `lgpd.operator_notice_hours` do tenant.
4. O **escopo** é descrito pelas colunas classificadas afetadas e suas categorias da
   classificação por coluna da P0.11 (DAD-29: `ID, CT, LOC, FIN, SOC, SENS`) — nunca por
   texto livre.

## 2. Contenção e avaliação

Contenção imediata (revogar acesso, isolar serviço, preservar evidências e o
`personal_data_access_log`), avaliação de risco aos titulares (natureza dos dados,
categorias DAD-29, volume, reversibilidade) e decisão fundamentada sobre a relevância do
incidente para fins de comunicação (Res. CD/ANPD 15/2024).

## 3. Aviso ao controlador (operador → tenant)

- O Ziip SAN informa o tenant **sem demora injustificada e dentro do prazo contratual**:
  conhecimento + `lgpd.operator_notice_hours` (classe c, **só reduz**; teto fixado no
  contrato/DPA da P3.9 — a Res. 15/2024 não fixa esse número e a ANPD recomenda fixá-lo
  em contrato).
- O aviso usa o conteúdo mínimo do modelo ANPD (seção 5), para o controlador não perder
  o próprio prazo.
- **Alertas na Central às 24 h e às 48 h** do conhecimento enquanto o aviso não estiver
  registrado (`IncidentClock.dueCentralAlerts`); vencido o prazo sem aviso, o incidente
  fica marcado como aviso em atraso.

## 4. Comunicação do controlador (tenant → ANPD e titulares)

Responsabilidade do controlador, com os modelos desta entrega preenchidos pelo operador:

- **3 dias úteis** do conhecimento pelo controlador para comunicar à ANPD e aos titulares
  afetados (Res. CD/ANPD 15/2024), pelos modelos `MODELO-COMUNICACAO-ANPD.md` e
  `MODELO-AVISO-TITULARES.md`;
- **complementação em 20 dias úteis** quando nem toda a informação estiver disponível no
  prazo inicial (`AnpdDeadlines`).

## 5. Registro, retenção e vínculos

- Registro do incidente guardado por **5 anos** (`ret.incident_record_years`), com trilha
  de quem acessou o registro.
- Vínculo com o **registro de operações de tratamento**: campo `processingRecordRef`
  declarado e serializado desde já; o preenchimento efetivo é da **P3.40** (tarefa
  posterior, não implementada aqui).
- Encerramento só com verificação de eficácia na ocorrência da P2.25, quando a norma
  ativa a exigir.

## 6. Fora de escopo

Privacidade do rastreamento do executor e das fotos de campo: **ED-20**.
