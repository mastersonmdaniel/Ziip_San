# 22 — Modelos jurídicos: pedido de esclarecimento e impugnação

> **MODELOS (templates). Não são aconselhamento jurídico.** Todo pedido deve
> ser revisado por advogado antes de protocolar, e os campos marcados
> `PENDENTE` devem ser preenchidos com os dados do edital concreto. Nada aqui
> substitui a leitura do edital, do termo de referência e da lei vigente na
> data do protocolo.

Regra de base (ADR-0023, item 5; consolidado 18 §6): **o produto segue a lei**,
com as travas do ADR-0015, e não oferece configuração que afrouxe a lei para
atender a um edital. Os modelos abaixo servem para pedir esclarecimento ou
impugnar o item **antes da sessão**, nos termos da Lei 14.133/2021, art. 164;
na POC, demonstra-se a regra legal configurada e cita-se a norma.

## Quando usar cada modelo

| Item do consolidado 18 §6 | Instrumento indicado no §6 | Modelo |
|---|---|---|
| L1 — TR-286 (perda da tarifa social por faturas em atraso) | esclarecimento ou impugnação | A ou B |
| L2 — TR-415, SG-398 (ordens de corte em dias e horários livres) | esclarecimento | A |
| L3 — TR-494 (multas do TCE/SC transferidas à contratada) | esclarecimento ou impugnação | A ou B |
| L4 — TR-339, SG-327 (débito em conta no regime antigo) | (tratamento no produto: Pix Automático; FEBRABAN 150 só para legadas) | A, se o edital exigir o regime antigo |
| L5 — ED-019 e mínimo de 8.500 ligações (atestado) | impugnação possível em editais futuros, sem contar com o resultado | B |
| L6 — ETP-005 (vedação a software livre; registro no INPI) | esclarecimento quando a exigência aparecer | A |
| L7 — TR 4.2.5, SG-005 ("orientada a objetos, interpretada") | esclarecimento | A |
| L8 — TR 27.4, A-05 (subcontratação vedada × nuvem de terceiro exigida) | esclarecimento antes da sessão | A |
| L9 — art. 93, A-16 (titularidade das customizações) | cláusula-padrão de licença (ED-02, ED-10) | A, se o edital exigir cessão |
| L10 — TR-030 (consulta por 120 dias após o contrato) | (tratamento no nível Setor Público; DPA e certificado de descarte) | A, se o edital exigir além do contrato |
| L11 — TR-570, SG-546, POC-20 (fatura por e-mail) | (link autenticado por padrão; PDF anexo só por opt-in) | A, se o edital exigir anexo |
| L12 — SG-708, SG-709, SG-711 (vínculo só por nome e CPF/CNPJ) | esclarecimento | A |
| L13 — TR-097, TR-099, TR-104 (acesso da TI do órgão a ambiente e logs) | (só em instância dedicada, somente leitura, MFA e auditoria) | A, se o edital exigir acesso amplo |
| L14 — SG-822 (aderência do software à ISO/IEC 17025) | esclarecimento sobre o que se exige do software | A |
| L15 — SAMAE 2.26 (Anexo XX da Portaria de Consolidação 5/2017) | (catálogo normativo pela redação vigente) | A |
| L16 — TR-144, "BPM" (validação por "API do governo") | esclarecimento | A |
| L17 — SG-402 a SG-411, SG-424, SG-425 (negativação e protesto "automáticos") | (carta de aviso prévio antes da inclusão; baixa e teto travados) | A, se o edital exigir automatismo sem aviso |
| L18 — multa moratória de 10% por dia "sobre o valor do exame"; suspensão "com a Prefeitura de Peruíbe" | esclarecimento antes da sessão (erro material de modelo) | A |
| L19 — VISAN 24.3.7, 24.3.13 (resíduos e drenagem no GIS fora do objeto) | esclarecimento | A |
| L20 — contrato do SAMAE 12.1.6, TR 4.4 (transferência internacional de dados) | (hospedagem e backups em `sa-east-1`; provedor de modelo fora do Brasil só com RIPD e DPA) | A, se o edital exigir transferência |

## Modelo A — Pedido de esclarecimento (Lei 14.133/2021, art. 164)

```
À Comissão de Contratação — [ÓRGÃO: PENDENTE]
Pregão Eletrônico nº [PENDENTE] — Processo nº [PENDENTE]

[LICITANTE: PENDENTE], pessoa jurídica inscrita no CNPJ nº [PENDENTE],
vem, tempestivamente, requerer ESCLARECIMENTO sobre o(s) item(ns) abaixo,
nos termos do art. 164 da Lei nº 14.133/2021 e do item [do edital: PENDENTE]
do instrumento convocatório.

1. ITEM QUESTIONADO
   [Item do edital: PENDENTE — ex.: TR-286 / TR-415 / SG-398 / TR-494]
   Texto do edital: "[transcrever fielmente: PENDENTE]"

2. DÚVIDA / DIVERGÊNCIA APONTADA
   [Escolher e adaptar um dos blocos do § "Blocos de fundamentação" abaixo;
   nada além do bloco correspondente deve ser alegado sem revisão.]

3. ESCLARECIMENTO REQUERIDO
   Confirma a Administração que [o item será interpretado conforme a norma
   citada no item 2 / o item será retificado / o item não será exigido na
   forma escrita: PENDENTE — escolher uma]?

4. CONDUTA DA LICITANTE NA POC
   Independentemente da resposta, a licitante demonstrará na prova de
   conceito a regra legal configurada no produto e citará a norma, conforme
   o ADR-0023 (item 5). [Manter este parágrafo apenas se a demonstração
   legal for o caso; senão, suprimir: PENDENTE.]

Local e data: [PENDENTE]
Assinatura e identificação do representante legal: [PENDENTE]
Protocolo: [PENDENTE — preencher após protocolar; lançar o número na matriz
viva, campo legal_note, do requisito correspondente.]
```

## Modelo B — Impugnação (Lei 14.133/2021, art. 164)

```
À Comissão de Contratação — [ÓRGÃO: PENDENTE]
Pregão Eletrônico nº [PENDENTE] — Processo nº [PENDENTE]

[LICITANTE: PENDENTE], pessoa jurídica inscrita no CNPJ nº [PENDENTE],
vem, tempestivamente, IMPUGNAR o(s) item(ns) abaixo, nos termos do art. 164
da Lei nº 14.133/2021 e do item [do edital: PENDENTE] do instrumento
convocatório, requerendo a retificação do edital ou a exclusão da exigência.

1. ITEM IMPUGNADO
   [Item do edital: PENDENTE — ex.: TR-286 / TR-494]
   Texto do edital: "[transcrever fielmente: PENDENTE]"

2. FUNDAMENTAÇÃO
   [Escolher e adaptar um dos blocos do § "Blocos de fundamentação" abaixo.]

3. PEDIDO
   a) a retificação do item [PENDENTE] para [redação proposta: PENDENTE]; ou,
   subsidiariamente, a exclusão da exigência;
   b) a republicação do edital com reabertura de prazo, se a alteração
   afetar a formulação das propostas (art. 55, §1º, da Lei nº 14.133/2021).
   [A alínea (b) só se aplica se a alteração afetar as propostas: PENDENTE.]

4. RESSALVA
   A impugnação não suspende os prazos do certame salvo decisão motivada da
   Administração. A licitante acompanha a decisão no prazo do edital e, sem
   decisão favorável, demonstra na POC a regra legal e registra a recusa
   para recurso, conforme o consolidado 18 §8.2 ("Impugnação ou esclarecimento
   sem efeito").

Local e data: [PENDENTE]
Assinatura e identificação do representante legal: [PENDENTE]
Protocolo: [PENDENTE — preencher após protocolar; lançar o número na matriz
viva, campo legal_note, do requisito correspondente.]
```

## Blocos de fundamentação (um por item — mínimo exigido pela P1.25)

### TR-286 — Perda da tarifa social por faturas em atraso (L1)

> O item prevê a perda de benefício/tarifa (ex.: tarifa social) em função de
> quantidade de faturas em atraso. A Lei nº 14.898/2024 veda condicionar a
> tarifa social à adimplência. O produto trava essa conduta (piso da tarifa
> social, ADR-0015) e não oferece configuração que a afrouxe. Requer-se
> esclarecimento ou a retificação do item, para que a perda do benefício só
> ocorra após aviso na fatura por no mínimo três meses, na forma da lei
> (situação `LOSS_NOTICE` e texto do aviso pelo motor de mensagens legais;
> ver P2.32 e a tarefa da tarifa social, P1.5).

### TR-415 e SG-398 — Geração de ordens de corte em dias e horários livres (L2)

> O item admite gerar ordens de corte em qualquer dia e horário. A Lei nº
> 14.015/2020 veda o corte em sexta-feira, fim de semana, feriado e véspera.
> O produto trava as vedações (ADR-0015, G9) e a ED-29 recusa geração fora
> da janela legal. Requer-se esclarecimento confirmando que só serão geradas
> ordens dentro da janela legal.

### TR-494 — Multas do TCE/SC transferidas à contratada (L3)

> O item transfere à contratada as multas do TCE/SC por atraso ou erro na
> remessa ao e-Sfinge. A responsabilidade pela remessa é do jurisdicionado.
> O produto gera e valida a remessa; o envio é do cliente (ADR-0028, e).
> Requer-se esclarecimento ou a retificação do item, para que a
> responsabilidade acompanhe quem envia.

### Demais itens do §6

Para L4–L20, usar a coluna "Norma" e a coluna "Tratamento" do consolidado
18 §6 como única fonte da fundamentação, sem acrescentar norma ou tese nova:
transcrever a norma citada no §6, o tratamento do produto indicado lá e o
pedido correspondente (esclarecimento, nos casos em que o §6 o indica).
Qualquer tese fora do §6 exige revisão jurídica própria — PENDENTE, caso a
caso, e não coberta por este modelo.

## Pendências (tudo que depende de edital concreto ou de decisão)

- Edital, órgão, processo, licitante, representante, datas e protocolos:
  PENDENTE (só existem no certame concreto).
- Revisão por advogado antes de protocolar: PENDENTE em todos os usos.
- Protocolo efetivo: nenhum pedido foi protocolado nesta entrega; os campos
  `legal_note` da matriz viva seguem sem número de protocolo até o primeiro
  protocolo real.
- Decisão da comissão e eventual retificação do edital: PENDENTE (a
  comissão pode manter o texto; nesse caso, vale a ressalva do Modelo B,
  item 4).

## Fontes

- Consolidado 18 §6 (tabela L1–L20: item, norma, tratamento); ADR-0023
  (item 5); ADR-0015 (travas legais); Lei 14.133/2021, art. 164 (prazo e
  forma do pedido, conforme o edital concreto).
