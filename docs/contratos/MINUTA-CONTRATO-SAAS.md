# MINUTA — Contrato SaaS Ziip SAN (P3.9)

> **MINUTA — NÃO ASSINAR. Aprovação do piloto pendente (piloto ainda não escolhido — P0.30). Assinatura só na LC.9, antes do primeiro dado real.**
>
> Estado desta minuta: **PRODUZIDA COM APROVAÇÃO PENDENTE** (P3.9). Nenhuma
> cláusula, prazo, valor ou percentual foi inventado: tudo o que as fontes
> (ADR-0002, ADR-0019, ADR-0020, ADR-0033, aceite da P3.9, pacotes ED-05,
> P3.10, P3.15 e P3.16) não fixam aparece como marcador **[A DEFINIR]** e
> consta do `RELATORIO-P3.9.md`. Esta minuta encaixa no arcabouço do nível
> Setor Público da ED-05 (mesma pasta lógica `docs/contratos/`, mesmas
> remissões aos Anexos I–IV daquele pacote).

## Cláusula 1ª — Qualificação das partes

- **CONTRATANTE (cliente/tenant):** [A DEFINIR — razão social, CNPJ,
  endereço e representante legal não constam de nenhuma fonte; preencher na
  contratação].
- **CONTRATADA (Ziip SAN):** [A DEFINIR — razão social, CNPJ, endereço e
  representante legal não constam de nenhuma fonte; preencher na
  contratação].
- Número do contrato, processo licitatório e modalidade, quando aplicáveis,
  são registrados na Central Ziip no ato da contratação (ADR-0019, item 1).

## Cláusula 2ª — Objeto e licença controlada pelo contrato

1. O objeto é o uso do Ziip SAN como serviço (SaaS), nos termos do contrato
   registrado na Central: número, processo licitatório e modalidade,
   contratante (CNPJ), vigência, aditivos (prazo, valor, objeto), índice e
   data-base do reajuste, empenhos com saldo, contatos institucionais do
   contratante e a política de SLA de suporte com o calendário (referência a
   `support.sla_policy`) — ADR-0019, item 1.
2. **Licença controlada:** plano, módulos e cotas (ligações ativas, usuários,
   armazenamento) derivam do contrato vigente e alimentam
   `tenant_subscription` e as flags de plano (ADR-0019, item 2).
3. As faturas são emitidas por competência ou medição prevista no contrato,
   pelo financeiro; a NFS-e da venda sai pelo motor fiscal do produto, com o
   Ziip como prestador em contexto fiscal próprio da plataforma. Pagamentos
   são informados (data, valor, ordem bancária) e conciliados contra as
   faturas. Não há gateway de cartão (ADR-0019, itens 3 e 4).
4. Índice de reajuste e data-base: [A DEFINIR — o ADR-0019 exige o registro
   do índice e da data-base no contrato, mas nenhuma fonte fixa qual índice].

## Cláusula 3ª — Parâmetros do tenant e responsabilidade do controlador

1. As decisões regulatórias e de negócio que variam por prestador (agência
   reguladora, tarifa social, convênios, multa e juros, retenção, corte —
   decisões G1, G2, G4, G5, G6 e G9) são **parâmetros do tenant**, com
   definição, limites legais validados pelo sistema, vigência e **aceite
   formal registrado** de quem aprovou (ADR-0002, itens 1 e 2).
2. Sem valor aceito e vigente, a operação que depende do parâmetro falha com
   erro explícito, e o tenant não passa a produção sem o conjunto obrigatório
   aceito (ADR-0002, item 3).
3. O contrato aloca ao **cliente** a responsabilidade pelos valores aceitos
   nos parâmetros (ADR-0002, item 5).

## Cláusula 4ª — Cláusula de aviso

1. Contrato vencido, cota excedida ou fatura em atraso geram **alerta e aviso
   ao tenant** — nunca bloqueio automático (ADR-0019, item 5).
2. Alertas previstos: contrato a vencer em **180, 90 e 30 dias**; saldo de
   empenho **abaixo de duas faturas**; cota em **80% e 100%**; fatura vencida;
   pagamento sem fatura correspondente (ADR-0019, item 7).
3. Toda restrição por inadimplência exige **aviso prévio por escrito** ao
   contratante, no prazo desta cláusula: [A DEFINIR — nenhuma fonte fixa o
   número de dias do aviso prévio; decisão do dono/piloto na contratação].
4. A forma escrita do aviso e o canal de envio: [A DEFINIR — não consta das
   fontes além da exigência de ser por escrito (ADR-0019, item 5)].

## Cláusula 5ª — Restrição somente manual (sem bloqueio automático)

Redação integral conforme o ADR-0019, item 5:

1. **Nenhum bloqueio automático.** Não existe, neste contrato, mecanismo de
   bloqueio, suspensão ou restrição automática por vencimento de contrato,
   excesso de cota ou atraso de fatura.
2. **Restrição por inadimplência é somente manual** e exige, cumulativamente:
   a. **aviso prévio por escrito** no prazo da Cláusula 4ª, item 3;
   b. execução por **uma pessoa de plataforma com MFA e step-up**
      (reautenticação no ato), que pode ser o financeiro
      (`PLATFORM_FINANCE`) ou o dono (`PLATFORM_OWNER`);
   c. **justificativa registrada**, com **referência à fatura vencida e ao
      aviso** prévio;
   d. **auditoria** completa do ato.
   Sem fatura vencida e aviso registrados, a restrição é recusada.
3. **Efeito limitado:** a restrição limita-se ao backoffice em **somente
   leitura**. **Continuam operando:** o portal do consumidor, o Pix, os
   retornos bancários e a emissão de 2ª via (`CENTRAL-ZIIP.md` §6.2, citado
   pelo ADR-0019, item 5).
4. A restrição do SaaS **não alcança o serviço essencial** do cliente, e
   nenhuma configuração do tenant ou do contrato afrouxa as vedações de
   corte por inadimplência do consumidor (ADR-0015, item 3, citado pelo
   ADR-0019, item 5). A restrição observa avaliação jurídica (Lei
   14.133/2021, citada pelo ADR-0019, item 5).

## Cláusula 6ª — Reajuste

O reajuste é calculado pela Central como **proposta** e só vale depois de
registrado o **apostilamento ou aditivo** correspondente (ADR-0019, item 6).
Nenhum reajuste é aplicado automaticamente.

## Cláusula 7ª — Suporte e SLA

O suporte observa a **MINUTA-SLA-SUPORTE.md** (política de SLA por contrato,
severidades S1–S5, protocolo `SUP-AAAA-NNNNNN`), que é parte integrante
deste contrato por remissão, e o DPA (Cláusula 8ª).

## Cláusula 8ª — Proteção de dados

O tratamento de dados pessoais rege-se pela **MINUTA-DPA.md**, parte
integrante deste contrato: Ziip SAN **operador**, cliente **controlador**
(ADR-0002, item 5). A lista de suboperadores e as regras de transferência
internacional estão na **MINUTA-SUBOPERADORES-TRANSFERENCIA.md**.

## Cláusula 9ª — Saída, exportação e eliminação

1. Encerrado o contrato, por qualquer motivo, aplica-se o regime de saída da
   P3.16, incorporado por remissão (tabela normativa no Anexo III da ED-05):
   - **Exportação completa** em CSV, JSON ou XML, com manifesto, hash e
     dicionário de dados: até **10 dias úteis** no nível Setor Público; até
     **15 dias** no nível padrão.
   - **Consulta somente leitura pós-contrato** (nível Setor Público): pelo
     prazo do contrato, **no máximo 120 dias**, antes da carência, com
     escrita recusada.
   - **Carência de 30 dias** contados do fim do contrato (no Setor Público,
     do fim da consulta somente leitura); durante a carência, download da
     exportação e contestação são registrados.
   - **Resposta a contestação** na carência: até **15 dias**, com alerta na
     Central antes do vencimento.
   - **Certificado de eliminação:** até **30 dias** após o fim da carência ou
     da resposta à última contestação (o que ocorrer depois), com **máximo
     de 75 dias com contestação e 60 dias sem contestação**, contados do
     **início da carência** (âncora: no nível padrão, o fim do contrato; no
     Setor Público, o fim da consulta somente leitura).
2. A eliminação é **agendada** somente por `PLATFORM_OWNER`, com MFA, é
   **cancelável durante a carência** e tem a execução **recusada antes do fim
   da carência**; contestação registrada e ordem judicial registrada
   **suspendem** a execução. O método de eliminação é o **crypto-shredding**
   (destruição da chave do tenant) — P3.16.
3. O certificado de eliminação contém hashes, expiração de backups e de
   cópias imutáveis e a **exceção dos chamados** prevista no DPA (retenção
   pela vigência mais 5 anos — MINUTA-DPA.md).
4. Para tenant de natureza pública, a eliminação usa a **tabela de
   temporalidade** e a **listagem de eliminação** da P3.19 (RAS-18) e é
   recusada sem elas; o conteúdo da tabela é do tenant, aprovado por ele
   (P3.16/P3.19 via Anexo III da ED-05).

## Cláusula 10ª — Risco residual de desastre regional (declaração)

O contrato **declara** o risco residual: a arquitetura cobre falha de zona,
ransomware e erro humano, **não** a perda da região inteira; não há backup
nem réplica fora de `sa-east-1` (LC.3; RNF-44 — conforme Anexo/§2.9 do
arcabouço da ED-05). Esta declaração é parte do objeto contratado.

## Cláusula 11ª — Vigência, rescisão e penalidades gerais

[A DEFINIR — prazo de vigência, hipóteses e prazos de rescisão, multa
rescisória e penalidades gerais **não constam de nenhuma fonte** (fatos
verificados pelo coordenador, item (h) da delegação); decisão do dono/piloto
na contratação. Nenhum percentual geral foi redigido: o único percentual
estrutural das fontes é o da multa por chamado estourado dentro da política
de SLA — ver MINUTA-SLA-SUPORTE.md.]

## Cláusula 12ª — Foro e disposições gerais

[A DEFINIR — foro e demais disposições gerais **não constam de nenhuma
fonte**; decisão do dono/piloto na contratação.]

## Assinaturas

Assinatura somente na **LC.9**, antes do primeiro dado real, após aprovação
da minuta pelo piloto. Campos de assinatura: [A DEFINIR — piloto não
escolhido (P0.30)].
