# MINUTA — Acordo de Tratamento de Dados Pessoais (DPA) (P3.9)

> **MINUTA — NÃO ASSINAR. Aprovação do piloto pendente (piloto ainda não escolhido — P0.30). Assinatura só na LC.9, antes do primeiro dado real.**
>
> Estado desta minuta: **PRODUZIDA COM APROVAÇÃO PENDENTE** (P3.9). Anexo do
> Contrato SaaS (MINUTA-CONTRATO-SAAS.md). Fontes: ADR-0002, ADR-0019,
> ADR-0020, ADR-0033, aceite da P3.9 e pacote P3.10 (procedimento de resposta
> a incidente, modelo de RIPD, `LgpdIncidentParameters`). O que as fontes não
> fixam aparece como **[A DEFINIR]** e consta do `RELATORIO-P3.9.md`.

## Cláusula 1ª — Papéis

1. **Ziip SAN é o OPERADOR.** Trata dados pessoais em nome do controlador,
   conforme as instruções documentadas deste e do Contrato SaaS.
2. **O cliente (tenant) é o CONTROLADOR** e **nomeia seu próprio
   encarregado** de dados (ADR-0002, item 5). Identificação e contato do
   encarregado do controlador: [A DEFINIR — dado do cliente, a registrar na
   contratação; nenhuma fonte o contém].
3. O controlador responde pelas decisões que lhe cabem como parâmetros do
   tenant (bases, retenções e valores das decisões G — ADR-0002): os valores
   aceitos são de responsabilidade do cliente, com aceite formal registrado
   (quem aprovou, quando, versão do conjunto e ato que embasa, quando
   houver).

## Cláusula 2ª — Obrigações do operador (Ziip SAN)

O operador obriga-se a:

1. tratar dados pessoais somente conforme o Contrato, este DPA e as
   instruções documentadas do controlador;
2. manter o registro do incidente de segurança por **5 anos**
   (`ret.incident_record_years` — parâmetro fixo da P3.10, outro valor é
   recusado pela validação);
3. comunicar incidente ao controlador na forma da Cláusula 4ª e entregar ao
   controlador os modelos de comunicação à ANPD e de aviso aos titulares
   preenchidos com o que apurou (P3.10);
4. manter a lista de suboperadores atualizada e avisar mudanças na forma da
   MINUTA-SUBOPERADORES-TRANSFERENCIA.md;
5. restringir o acesso a dados pessoais às hipóteses deste DPA (acesso
   assistido com consentimento e exceção de emergência — Cláusula 7ª);
6. na saída, exportar, manter a consulta somente leitura quando cabível e
   eliminar com certificado, observada a exceção da Cláusula 6ª (Contrato
   SaaS, Cláusula 9ª; P3.16).

## Cláusula 3ª — Obrigações do controlador (cliente)

O controlador obriga-se a:

1. nomear e manter identificado o seu encarregado (Cláusula 1ª, item 2);
2. definir e aceitar formalmente os parâmetros do tenant que lhe cabem,
   inclusive retenções e bases de tratamento (ADR-0002);
3. cumprir os **seus** prazos perante a ANPD e os titulares (Cláusula 5ª);
4. na homologação local, entregar dados **somente anonimizados na origem**
   (Cláusula 9ª e MINUTA-TERMO-HOMOLOGACAO-LOCAL.md; ADR-0033);
5. no regime de saída de tenant público, aprovar a tabela de temporalidade
   e a listagem de eliminação (P3.19/RAS-18).

## Cláusula 4ª — Comunicação de incidente do operador ao controlador

1. O operador comunica ao controlador qualquer incidente de segurança com
   dados pessoais **sem demora injustificada** e, no máximo, dentro do prazo
   contratual desta cláusula.
2. A Resolução CD/ANPD nº 15/2024 **não fixa número** para o aviso do
   operador ao controlador, e a ANPD recomenda fixá-lo em contrato (aceite
   da P3.9). Este DPA fixa, portanto, o **teto contratual** do parâmetro
   `lgpd.operator_notice_hours`: **[A DEFINIR — número de horas do teto;
   nenhuma fonte fixa este número; decisão do dono/piloto na contratação.
   Sem teto registrado, a validação da P3.10 RECUSA configurar o
   parâmetro.]**
3. **Mecanismo do parâmetro (fixado):** `lgpd.operator_notice_hours` é
   parâmetro de **classe c, só reduz** (P3.10, `LgpdIncidentParameters`):
   - o valor configurado pelo tenant pode **encurtar** o prazo, nunca
     alongá-lo além do teto do item 2 nem acima do valor já aceito;
   - valor menor que 1 hora é recusado; valor acima do teto ou acima do
     valor aceito anterior é recusado.
4. O relógio do incidente começa no **conhecimento pelo operador**, com
   **alertas na Central às 24 h e às 48 h** enquanto o aviso ao controlador
   não estiver registrado; vencido o prazo sem aviso, o incidente é marcado
   como aviso em atraso (P3.10).
5. O aviso do operador usa o conteúdo mínimo do modelo de comunicação à
   ANPD da P3.10, para o controlador não perder o próprio prazo, e o
   incidente fica ligado à **ocorrência da P2.25** e, quando aplicável, ao
   RIPD (Cláusula 10ª).

## Cláusula 5ª — Prazos do controlador perante a ANPD e os titulares

Responsabilidade do controlador, com os modelos entregues pelo operador
(Res. CD/ANPD nº 15/2024, via P3.10):

1. comunicação à ANPD e aos titulares afetados em **3 dias úteis** do
   conhecimento pelo controlador;
2. **complementação em 20 dias úteis** quando nem toda a informação estiver
   disponível no prazo inicial.

## Cláusula 6ª — Retenção de chamados e exceção no certificado de descarte

1. **Retenção:** os chamados de suporte e seus anexos são retidos pela
   **vigência do contrato mais 5 anos** (ADR-0020, decisão do dono de
   12/10/2026), com minimização: o formulário orienta a não anexar dado
   pessoal desnecessário; texto com CPF, CNPJ, e-mail ou telefone marca
   `contains_personal_data`; o acesso é restrito ao solicitante, ao
   administrador do tenant e aos agentes, com registro.
2. **Exceção no certificado de descarte (redação prevista neste DPA, como
   exige o aceite da P3.9):** a eliminação de fim de contrato do tenant
   **não alcança** os chamados e anexos retidos pelo item 1. O **certificado de
   eliminação/descarte** emitido na saída (Contrato SaaS, Cláusula 9ª)
   **registra expressamente esta exceção**, identificando: (a) a categoria
   retida (chamados e anexos); (b) o fundamento (esta cláusula e o
   ADR-0020); (c) a **data prevista de eliminação** (fim da vigência mais 5
   anos); e (d) a eliminação com contagem ao fim desse prazo.

## Cláusula 7ª — Acesso assistido e exceção de acesso de emergência

1. **Regra — acesso assistido com consentimento** (ADR-0020, item 6): o
   agente de suporte usa a **própria identidade, com MFA**, somente com
   **consentimento do administrador do tenant vinculado a um chamado**, com
   escopo definido, **prazo máximo de 72 horas**, **revogação a qualquer
   momento** e **auditoria de cada requisição**. Não há impersonação.
2. **Exceção — acesso de emergência (PREVISTA neste DPA, como exige o
   ADR-0020):** a única exceção ao consentimento, sob condições
   **cumulativas**:
   a. **somente em incidente de segurança declarado**, registrado conforme
      o plano de resposta a incidente (P3.10) **antes** da abertura da
      sessão; sem incidente aberto, o pedido é negado e auditado;
   b. **somente `PLATFORM_OWNER`**, com reautenticação e segundo fator no
      momento de abrir a sessão (**step-up**); o MFA do login anterior não
      basta;
   c. **somente leitura**, com as vedações do acesso assistido (revelar
      documento, exportar); qualquer escrita é recusada;
   d. **aviso ao tenant em até 24 horas** da abertura, ao administrador e ao
      encarregado, pela outbox, com motivo, período e escopo; a sessão
      aparece ao tenant na mesma lista do acesso assistido;
   e. **registro completo:** toda requisição no `audit_log` do tenant e em
      `platform.audit_event`, com o id do incidente; a sessão, o motivo e o
      que foi consultado entram no registro do incidente; a sessão termina
      com o encerramento do incidente ou antes.
3. O acesso de emergência usa a mesma tabela de sessões do acesso assistido
   (tipo `EMERGENCY`, com a referência ao incidente no lugar do
   consentimento) e **não** autoriza a role da Central a ler tabelas de
   tenant (ADR-0018, item 5, citado pelo ADR-0020).

## Cláusula 8ª — Telemetria de produto agregada

A telemetria de produto é **agregada**: **sem identificador de pessoa, sem
gravação de sessão e sem conteúdo digitado** (aceite da P3.9, via Anexo II
da ED-05, que remete a P4.8/LC.9). Nenhum dado fora dessas três vedações é
coletado a título de telemetria.

## Cláusula 9ª — Homologação local sem dado pessoal real

Até o lançamento, nenhum dado pessoal real entra na base local: dado do
piloto só entra **anonimizado na origem** (anonimização feita pelo piloto ou
por rotina entregue pelo Ziip e executada no ambiente do piloto), com código
substituto como chave de comparação e a correspondência com a matrícula
somente com o piloto (ADR-0033, itens 4 e 5). O termo dessa homologação é a
MINUTA-TERMO-HOMOLOGACAO-LOCAL.md.

## Cláusula 10ª — Suboperadores, transferência internacional e RIPD

1. Suboperadores, aviso de mudança, cláusulas-padrão da ANPD e a **trava de
   aprovação** regem-se pela **MINUTA-SUBOPERADORES-TRANSFERENCIA.md**,
   parte integrante deste DPA.
2. Cada transferência internacional é registrada no **RIPD pelo modelo da
   P3.10** (`MODELO-RIPD.md`), que identifica módulo, tratamentos,
   categorias de dado pelas colunas classificadas da P0.11 (DAD-29),
   compartilhamento e suboperadores, transferência internacional, retenção,
   riscos e medidas, com vínculo à ocorrência da P2.25 e, quando entregue,
   ao registro de operações da P3.40.

## Cláusula 11ª — Assinatura

Assinatura somente na **LC.9**, antes do primeiro dado real, após aprovação
da minuta pelo piloto.
