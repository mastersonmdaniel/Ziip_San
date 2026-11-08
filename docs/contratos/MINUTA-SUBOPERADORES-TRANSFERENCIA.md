# MINUTA — Suboperadores e Transferência Internacional (P3.9)

> **MINUTA — NÃO ASSINAR. Aprovação do piloto pendente (piloto ainda não escolhido — P0.30). Assinatura só na LC.9, antes do primeiro dado real.**
>
> Estado desta minuta: **PRODUZIDA COM APROVAÇÃO PENDENTE** (P3.9). Anexo
> vivo do DPA (MINUTA-DPA.md, Cláusula 10ª). Fontes: aceite da P3.9
> (categorias de provedores), ADR-0033 (AWS `sa-east-1` no lançamento),
> LGPD arts. 33 a 36 e Resoluções CD/ANPD nº 19/2024 e nº 15/2024 — **citadas
> por remissão a partir das fontes do projeto**; o texto oficial das
> cláusulas-padrão da ANPD **não é reproduzido** nesta minuta (ver §4).

## 1. Lista viva de suboperadores

Esta lista é **viva**: integra o DPA, é mantida atualizada pelo operador e
mudanças seguem o §3. As **categorias** abaixo são as das fontes; **razão
social e país** de cada provedor estrangeiro **não constam das fontes** e
estão como **[A DEFINIR]** — nenhum provedor foi nomeado além dos que o
próprio aceite da P3.9 exemplifica.

| # | Suboperador / categoria | Local do tratamento | Transferência internacional? | Situação |
|---|---|---|---|---|
| 1 | Provedor de nuvem — **AWS, região `sa-east-1`** (serviços do ADR-0011/ADR-0033: RDS, KMS, Secrets Manager, S3, CloudFront, WAF, observabilidade; e-mail por Amazon SES ou equivalente) | **Brasil (`sa-east-1`)** | **Não** — tratamento no Brasil; a infraestrutura só é criada no lançamento (ADR-0033) | Razão social completa: [A DEFINIR] |
| 2 | **Provedor de modelo de linguagem** | Fora do país | **Sim** | Razão social: [A DEFINIR]; país: [A DEFINIR] |
| 3 | **Meta** | Fora do país | **Sim** | Razão social: [A DEFINIR]; país: [A DEFINIR] |
| 4 | **Telegram** | Fora do país | **Sim** | Razão social: [A DEFINIR]; país: [A DEFINIR] |
| 5 | Demais provedores da lista ("e demais da lista" — aceite da P3.9) | [A DEFINIR] | Conforme o país | [A DEFINIR — cada inclusão segue os §§3–5 antes de operar] |

## 2. AWS `sa-east-1` não é transferência internacional

O provedor de nuvem trata os dados na região **`sa-east-1` (Brasil)** e,
portanto, **não** configura transferência internacional para os fins desta
minuta. Não há backup nem réplica fora de `sa-east-1` (risco residual
declarado no Contrato SaaS, Cláusula 10ª). Qualquer mudança de região ou de
país de tratamento do provedor de nuvem passa a ser tratada como mudança de
suboperador (§3) e, se for para fora do país, como transferência
internacional (§§4–5).

## 3. Dever de aviso de mudança ao controlador

1. **Inclusão, substituição ou remoção** de suboperador, e mudança relevante
   de país de tratamento, são comunicadas ao controlador **por escrito**
   (forma escrita como **PROPOSTA REDACIONAL A CONFIRMAR** pelo dono/piloto
   na aprovação — decisão redacional declarada desta minuta, não fato de
   fonte: o aceite da P3.9 exige apenas 'aviso de mudança ao controlador' e
   a ED-05 deixou prazo e forma à P3.9), antes de o novo suboperador tratar
   dados.
2. Prazo do aviso: [A DEFINIR — nenhuma fonte fixa o número de dias de
   antecedência; decisão do dono/piloto na contratação].
3. O aviso identifica o suboperador (razão social, serviço, categorias de
   dado e país) e, sendo estrangeiro, a comprovação do §4.

## 4. Cláusulas-padrão contratuais da ANPD — incorporação POR REMISSÃO

1. Para **todo** suboperador **fora do país** (itens 2 a 5 da lista, quando
   estrangeiros), as **cláusulas-padrão contratuais da ANPD** são
   **incorporadas por remissão** ao instrumento com o suboperador e a este
   DPA, nos termos da **Resolução CD/ANPD nº 19/2024** e dos **arts. 33 a 36
   da LGPD** (aceite da P3.9).
2. **O texto oficial das cláusulas-padrão não é reproduzido nesta minuta.**
   Vale o **texto oficial publicado pela ANPD**, anexado ao instrumento no
   ato da contratação/assinatura. **Ponto de anexação:** [ANEXAR AQUI O
   TEXTO OFICIAL DAS CLÁUSULAS-PADRÃO — RES. CD/ANPD 19/2024 — NO ATO DA
   CONTRATAÇÃO].
3. A incorporação por remissão não dispensa a verificação, provedor a
   provedor, da hipótese de transferência e das garantias exigidas pelos
   arts. 33 a 36 da LGPD.

## 5. TRAVA de aprovação (teste de conferência da P3.9)

**Suboperador estrangeiro sem cláusula-padrão incorporada BLOQUEIA a
aprovação desta minuta e do DPA.** Enquanto qualquer item estrangeiro da
lista do §1 estiver sem a incorporação do §4 comprovada, a minuta **não
pode ser marcada como aprovada** — permanece "produzida com aprovação
pendente". A mesma trava vale para inclusão futura de suboperador
estrangeiro (§3, §3º).

## 6. Registro no RIPD

Cada transferência internacional desta lista é **registrada no RIPD pelo
modelo da P3.10** (`MODELO-RIPD.md`): o RIPD do módulo identifica o
compartilhamento e os suboperadores, a transferência internacional e a
exigência das cláusulas-padrão da Res. CD/ANPD nº 19/2024, com vínculos à
ocorrência da P2.25 e ao registro de operações (P3.40, quando entregue).
Mudança de suboperador é evento de **revisão do RIPD** (modelo da P3.10,
seção "Revisão").
