# MINUTA — Termos de Uso da Plataforma Ziip SAN (P3.9)

> **MINUTA — NÃO ASSINAR. Aprovação do piloto pendente (piloto ainda não escolhido — P0.30). Assinatura só na LC.9, antes do primeiro dado real.**
>
> Estado desta minuta: **PRODUZIDA COM APROVAÇÃO PENDENTE** (P3.9). Fontes:
> ADR-0002, ADR-0019 e ADR-0020. Esta minuta contém **somente o que as fontes
> sustentam** (licença por contrato, suporte por protocolo, papéis); o
> restante é estrutura com **[A DEFINIR]**, registrada no
> `RELATORIO-P3.9.md`. Os Termos de Uso são anteriores ao primeiro contrato
> (ADR-0002, tarefas derivadas) e se subordinam ao Contrato SaaS e ao DPA:
> em caso de divergência, valem o Contrato e o DPA.

## 1. Natureza e vínculo com o contrato

1. O uso da plataforma Ziip SAN decorre de **licença controlada pelo
   contrato**: plano, módulos e cotas (ligações ativas, usuários,
   armazenamento) derivam do contrato vigente do tenant (ADR-0019, itens 1
   e 2). Não há contratação por autosserviço, trial ou gateway nestes
   termos.
2. Estes termos não criam direitos além do contrato vigente; encerrado o
   contrato, o uso observa o regime de saída do Contrato SaaS (Cláusula 9ª)
   e do DPA.

## 2. Papéis

1. O **tenant é o controlador** dos dados pessoais que trata na plataforma e
   nomeia seu encarregado; o **Ziip SAN é o operador** (ADR-0002, item 5;
   DPA, Cláusula 1ª).
2. Os usuários do tenant atuam sob responsabilidade do tenant; os papéis e
   permissões internos do tenant são os do produto: [A DEFINIR — o catálogo
   de papéis do tenant não é objeto das fontes desta minuta].

## 3. Uso aceitável

1. O tenant usa a plataforma dentro do plano, dos módulos e das cotas do
   contrato (ADR-0019, item 2). Cota excedida gera **alerta** (80% e 100% —
   ADR-0019, item 7), **não** bloqueio automático (Contrato SaaS, Cláusula
   5ª).
2. Demais regras de uso aceitável (vedações de conduta, conteúdo e
   integrações): [A DEFINIR — não constam das fontes desta minuta].

## 4. Suporte por protocolo

1. O suporte é prestado por **chamado com protocolo `SUP-AAAA-NNNNNN`**, com
   conversa encadeada, estados com pausa do relógio, escalonamento e CSAT
   (ADR-0020, itens 1 e 3).
2. Os prazos são os da **política de SLA do contrato** (MINUTA-SLA-SUPORTE);
   severidades **S1 a S5** (ADR-0020; ADR-0025).
3. Anexos de chamado são validados pelo conteúdo e ficam em **quarentena até
   a varredura** antivírus (ClamAV, junto do `worker`), guardados sob prefixo
   e chave do tenant (ADR-0020, item 4). As notificações por e-mail levam
   protocolo, estado e link, **sem conteúdo nem anexos** (ADR-0020, item 5).
4. **Acesso assistido:** somente com consentimento do administrador do
   tenant, vinculado a chamado, por no máximo **72 horas**, revogável a
   qualquer momento, com auditoria — e a exceção de emergência do DPA
   (ADR-0020, item 6 e seção de exceção; DPA, Cláusula 7ª).
5. Há **página pública de status** em hospedagem separada, alimentada pelas
   sondas e pelos incidentes da Central (ADR-0020, item 7).

## 5. Parâmetros e responsabilidade do tenant

As decisões regulatórias configuradas como **parâmetros do tenant** exigem
**aceite formal registrado**; sem valor aceito e vigente, a operação
dependente falha com erro explícito (ADR-0002, itens 1 a 3). A
responsabilidade pelos valores aceitos é do cliente (ADR-0002, item 5).

## 6. Disponibilidade destes termos e alterações

[A DEFINIR — forma de publicação, versionamento e aviso de alteração destes
termos não constam das fontes desta minuta.]

## 7. Disposições finais

[A DEFINIR — disposições finais destes termos não constam das fontes; em
caso de conflito, prevalecem o Contrato SaaS e o DPA (§ introdutório).]
