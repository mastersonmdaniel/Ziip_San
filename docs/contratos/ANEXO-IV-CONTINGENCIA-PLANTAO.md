# Anexo IV — Telefone, canal de contingência e plantão S1

> Fonte: aceite da própria **ED-05** (18h-E7, E9; ADR-0025, ajustado na
> v2.7) e sequência da **LC.13**. Este anexo é a **estrutura operacional**
> que o contrato-modelo referencia. Os dados concretos de contato **não
> constam de nenhuma fonte do projeto** e **não foram inventados**.

## 1. Telefone no Brasil e canal de contingência (ED-05)

Exigência do aceite: telefone no Brasil e canal de contingência
**publicados e testados com a ferramenta de chamados fora do ar**.

| Campo | Valor |
|---|---|
| Telefone de suporte no Brasil (DDD + número) | **[A DEFINIR PELA OPERAÇÃO]** — a definir pela operação antes da publicação; nenhum número consta das fontes |
| Canal de contingência (quando a ferramenta de chamados está fora do ar) | **[A DEFINIR PELA OPERAÇÃO]** — canal concreto não consta das fontes |
| Onde publicar | **[A DEFINIR PELA OPERAÇÃO]** (página de status é da P3.18; a decisão de publicação não consta das fontes desta entrega) |
| Teste obrigatório | Simular a **ferramenta de chamados fora do ar** e acionar o canal de contingência de ponta a ponta; registrar evidência do teste (roteiro no §3) |

## 2. Escala de plantão para incidentes S1 (ED-05 / LC.13)

Exigência do aceite: escala de plantão para incidentes **S1** **montada
antes da primeira produção** e **acionada a partir dela** (LC.13).

Estrutura da escala (campos a preencher pela operação — nenhum nome consta
das fontes):

| Campo | Valor |
|---|---|
| Plantonista S1 (nome/contato) por janela | **[A DEFINIR PELA OPERAÇÃO]** |
| Janelas de plantão e rodízio | **[A DEFINIR PELA OPERAÇÃO]** |
| Canal de acionamento do plantão | **[A DEFINIR PELA OPERAÇÃO]** |
| Critério de acionamento | Incidente classificado **S1** na política de SLA do contrato (Anexo I; matriz impacto × criticidade da P2.15) |
| Marco de montagem | **Antes da primeira produção**; acionamento a partir dela (LC.13) |

> Severidade S1 é a única com plantão exigido pelo aceite da ED-05. Plantão
> para S2–S5 **não** consta das fontes: **[A DEFINIR NA P3.9]** se a minuta
> quiser criá-lo — este anexo não o promete.

## 3. Roteiro de ensaio — contingência (Item do aceite ED-05)

Pré-condições: telefone e canal publicados (§1); escala S1 montada (§2).

1. Derrubar/indisponibilizar a ferramenta de chamados no ambiente de ensaio.
2. Abrir uma ocorrência S1 **somente** pelo canal de contingência.
3. Acionar o plantonista da janela vigente a partir da escala.
4. Registrar: horário de abertura, horário do acionamento, quem atendeu,
   evidência (log/print) de cada passo.
5. **Aceite do ensaio:** ocorrência registrada e plantão acionado sem usar
   a ferramenta de chamados. Qualquer desvio = falha registrada, sem
   maquiar o resultado.

**Este ensaio NÃO foi executado nesta entrega** (exige ambiente e pessoas
reais de plantão). Ele é pré-condição de fechamento da ED-05, junto do
ensaio do Item 3 do SAMAE (Anexo I §6).

## 4. Item 2.5 do SAMAE — alertas ao cliente (ED-05 × ED-07)

O aceite da ED-05 exige o item 2.5 do SAMAE **demonstrado em conjunto com
os alertas da ED-07** (monitoramento e alertas para o cliente: evento do
catálogo da P1.38 gera alerta ao responsável, entregue na central de avisos
da P3.36 e, se configurado, por webhook de saída da P2.26 — aceite da
ED-07). A demonstração conjunta depende da ED-07 executada e de ensaio:
**pendente**, registrado no relatório da ED-05. Nenhum resultado de
demonstração é afirmado aqui.
