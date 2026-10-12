# ADR-0015 — Limites legais dos parâmetros do tenant: classes, multa, corte, tarifa social e retenção

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0002 (item 2, limites legais validados); `docs/arquitetura/PARAMETROS-DO-TENANT.md` §2, G2, G5, G6, G9
  e §4
- **Detalhamento dos parâmetros:** `docs/arquitetura/PARAMETROS-DO-TENANT.md`

## Contexto

O ADR-0002 tornou G1, G2, G4, G5, G6 e G9 parâmetros do tenant, com limites legais validados: valor fora do limite é
recusado. O catálogo de parâmetros apontou quatro pontos em que essa regra única não basta.

1. **Multa por atraso.** O teto de 2% do CDC (art. 52 §1º) aplicado a tarifa pública é pergunta jurídica aberta (G5,
   E-02). O código tem duas regras divergentes: no collection, `collection_policies` aceita teto acima de 2% com um
   texto de justificativa (`fine_cap_justification`, CHECK em
   `commercial/sigsan-collection/src/main/resources/db/migration/tenant/V6_1__create_collection_schema.sql:222-224`;
   `FineRule.java:27-31`), sem aceite nem aprovação; no billing, `LateFeePolicy`
   (`commercial/sigsan-billing/src/main/java/br/com/sigsan/commercial/billing/bill/LateFeePolicy.java:6-9`) declara
   que não impõe o teto e admite multa por dia (`FineMode.PER_DAY`).
2. **Corte de fornecimento.** A Lei 14.015/2020 veda iniciar corte por inadimplência em sexta, fim de semana, feriado e
   véspera e exige comunicação em horário comercial (Lei 13.460 arts. 5º XVI e 6º p.u.; Lei 8.987 art. 6º §4º;
   relatórios `08`, `11`, `13`). A lei fala em inadimplemento; os demais motivos (fraude, risco, pedido do usuário)
   ficavam sem regra.
3. **Tarifa social.** A Lei 14.898/2024 e a NR 13 fixam um piso (50% até 15 m³, valor do relatório `08`, a conferir
   no DOU, `14`). O catálogo deixava as faixas abertas, sem validação do piso (`tariff_social_bands`, `V4_1:124-140`).
   O tratamento de NIS e renda exige RIPD (`17` §7), e hoje não há cifra de campo (`10` §1.2 item 4).
4. **Retenção.** Os prazos do módulo LGPD não estão semeados e o motor não elimina (`13` item 8). O catálogo tratava
   prazos como escolha do controlador, inclusive os que têm piso em norma: 120 meses de histórico de leitura e
   faturamento (NR 11 art. 22; `08` E-05, `11` R38) e 6 meses de registros de acesso a aplicações (Marco Civil da
   Internet, art. 15; `10` quadro C, com aplicabilidade ao prestador e ao Ziip em aberto, `10` §(e) pergunta 7). Não
   há rotina de devolução e eliminação ao fim do contrato, nem de descarte em backups (`10` §1.2 item 10).

## Decisão

1. **Classes de parâmetro.** Todo parâmetro do catálogo pertence a uma classe, gravada em `parameter_definitions`:
   - **a — travado pelo sistema:** valor ou procedimento fixo do produto ou da lei; o tenant não altera;
   - **b — configurável com padrão de fábrica:** sair do padrão exige norma citada, aprovação dupla e aceite formal
     registrado; quando previsto, o sistema exibe alerta de risco; havendo limite de fábrica, valor fora dele é
     recusado;
   - **c — só ampliável:** piso fixo da lei ou do Ziip SAN; o tenant só altera no sentido mais protetivo (onde a lei
     fixa teto, só reduz);
   - **d — livre:** escolha do prestador ou da ERI, com validação de tipo e coerência.
   Todas seguem o ADR-0002 (vigência, aprovação dupla, aceite do conjunto). A classificação de cada parâmetro está no
   catálogo.
2. **Multa por atraso (classe b).** Padrão de fábrica 2%, configurável por tenant. Valor acima de 2% exige norma
   citada, aprovação dupla e aceite formal registrado, e o sistema exibe alerta de risco (CDC art. 52 §1º) na
   configuração e no aceite final. A regra fica num só lugar: uma política de encargos, lida de `collection_policies`,
   usada pelo billing e pelo collection.
3. **Corte de fornecimento.**
   - Por inadimplência, as vedações da Lei 14.015/2020 ficam **travadas** (classe a), como já previa o catálogo.
     Confirmado pelo dono do produto em 12/10/2026: nenhuma configuração do tenant afrouxa o corte por inadimplência.
   - Por padrão de fábrica (classe b), as vedações de dia (sexta, fim de semana, feriado e véspera) e a janela de
     horário valem para **todos** os motivos de corte. A única exceção de fábrica é a emergência técnica, com
     justificativa e foto anexadas à OS.
   - O tenant pode excluir das vedações motivo que não seja inadimplência, mediante aceite formal registrado com norma
     citada.
   - A janela de horário (`cut.business_hours`) tem padrão e limites de fábrica; valor fora dos limites é recusado.
4. **Tarifa social (classe c).** O piso federal fica travado no sistema e só é ativado depois de conferido no DOU, com
   a conferência registrada. A agência ou o cliente só concede benefício maior (mais m³, desconto maior, prazo maior),
   nunca menor. NIS e renda são sempre cifrados e cada acesso é registrado, em qualquer configuração (classe a).
5. **Retenção (classe c).** Pisos legais fixos definidos pelo Ziip SAN; o cliente só amplia prazos. Pisos iniciais:
   - 120 meses de histórico de leitura e faturamento (NR 11 art. 22), também quando a ERI não adotou a NR 11;
   - 6 meses de registros de acesso (Marco Civil da Internet, art. 15), independentemente da questão de aplicabilidade;
   - 5 anos de registro de incidente (Res. CD/ANPD 15/2024 art. 10, `10`);
   - 10 anos de guarda após o encerramento do vínculo, a confirmar (prescrição decenal, REsp 1.117.903, tema não
     confirmado, `13`);
   - demais categorias de G6 sem norma no levantamento: piso a definir.
   A eliminação ao fim do contrato e o descarte em backups seguem procedimento do Ziip SAN (classe a), com certificado
   de descarte entregue ao cliente.

## Consequências

### Positivas

- A multa deixa de depender de texto livre: sair do padrão tem prova de quem decidiu e com que norma, e o alerta de
  risco fica registrado no aceite.
- Uma só regra de encargos elimina a divergência entre billing e collection.
- O corte fica protegido por padrão também nos motivos que a lei não alcança, sem impedir o tenant que tenha norma
  própria.
- O piso da tarifa social e os pisos de retenção não podem ser reduzidos por configuração, o que fecha o risco de
  limite permissivo demais apontado no ADR-0002.
- O certificado de descarte dá ao controlador a prova de eliminação exigida no contrato de operador.

### Negativas

- O Ziip SAN passa a responder pelos pisos que fixa. Piso errado para cima obriga a guardar dado além do necessário
  (minimização da LGPD); piso errado para baixo expõe o cliente. Mitigação: cada piso cita a norma e tem teste próprio;
  piso sem norma fica marcado "a definir" até decisão.
- Piso de retenção que vale mesmo sem adesão da ERI à NR 11 pode conflitar com política de minimização do controlador;
  o cliente não consegue reduzir o prazo.
- Multa acima de 2% continua possível. O alerta e o aceite deslocam a prova, não eliminam o risco jurídico do cliente.
- O fluxo de aceite fica mais pesado para os parâmetros de classe b fora do padrão.

### O que muda no código

- `parameter_definitions` com a coluna de classe; `parameter_legal_limits` com piso, teto, limite de fábrica e sentido
  permitido; `parameter_acceptances` com o alerta exibido.
- `LateFeePolicy` (billing) e `SurchargePolicy`/`FineRule` (collection) unificadas numa política só, lida de
  `collection_policies`, com padrão 2% e recusa acima de 2% sem aceite reforçado. O CHECK de `V6_1:222-224` e
  `FineRule.java:27-31` deixam de aceitar justificativa em texto como substituto do aceite. A unificação decide o
  destino da multa por dia (`FineMode.PER_DAY`), que não existe no collection.
- Validação do piso da tarifa social sobre `tariff_social_bands`; bloqueio de ativação até a conferência no DOU.
- Cifra de campo e registro de acesso para NIS e renda.
- Motor de retenção com recusa abaixo do piso; rotina de devolução, eliminação e descarte em backup, com emissão do
  certificado.
- Regra de corte por motivo, com a exceção de emergência técnica exigindo justificativa e foto.

### O que muda no roadmap

- Classes e limites entram no catálogo de limites do P0 (ADR-0002). Os demais itens seguem a fase de cada grupo.

## Alternativas consideradas

- **Teto de 2% travado para a multa.** Rejeitada: a aplicação do CDC a tarifa pública está em aberto (E-02); travar
  impediria cliente com base normativa válida para outro valor.
- **Multa livre, só com justificativa em texto (estado atual do collection).** Rejeitada: não prova quem decidiu nem
  com que norma, e não alerta o risco.
- **Vedações de corte só para inadimplência, como na lei.** Rejeitada como padrão: deixa corte por outros motivos sem
  proteção; mantida como opção do tenant, com aceite.
- **Retenção inteiramente a cargo do controlador.** Rejeitada: permitiria prazo abaixo da norma por configuração.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Classe em `parameter_definitions`, limites com sentido permitido em `parameter_legal_limits`, teste por classe | P0 |
| Política de encargos única (billing e collection), padrão 2%, aceite reforçado acima de 2%, destino de `PER_DAY` | P0 |
| Padrão e limites de fábrica da janela de corte; cota de busca e teto de CNPJ (ADR-0008); teto absoluto da multa, se houver | P0 |
| Alerta de risco CDC no console e no assistente de implantação | P1 |
| Piso da tarifa social travado, com conferência no DOU registrada antes da ativação | P1 |
| Cifra de campo e registro de acesso de NIS e renda | P1 |
| Pisos de retenção sem norma no levantamento (lote CadÚnico, documentos do pedido manual, protocolos, prova de aviso) e confirmação do piso de 10 anos | P1 |
| Motor de retenção com recusa abaixo do piso; versão gravada no expurgo | P2 |
| Regra de corte por motivo, exceção de emergência técnica com justificativa e foto | P3 |
| Procedimento de eliminação ao fim do contrato e descarte em backup, com certificado de descarte, citado no DPA | P3 |
