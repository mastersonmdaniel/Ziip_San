# MINUTA — Termo de Homologação Local do Piloto (P3.9)

> **MINUTA — NÃO ASSINAR. Aprovação do piloto pendente (piloto ainda não escolhido — P0.30). Assinatura só na LC.9, antes do primeiro dado real. Ressalva para este termo: a menção à LC.9 vale para o Contrato SaaS e o DPA; este termo é assinado com o piloto escolhido, na homologação local.**
>
> Estado desta minuta: **PRODUZIDA COM APROVAÇÃO PENDENTE** (P3.9). Fontes:
> ADR-0033 (homologação local do piloto; anonimização na origem; rede do
> piloto como geometria; AWS `sa-east-1` só no lançamento) e aceite da P3.9
> ("termo da homologação local assinado, sem transferência de dado
> pessoal"). Este termo **não** é o Contrato SaaS nem o DPA: a assinatura do
> contrato e do DPA é só na **LC.9**, antes do primeiro dado real.

## 1. Partes

- **Piloto (prestadora):** [A DEFINIR — piloto ainda não escolhido (P0.30);
  razão social, CNPJ, endereço e representante não constam de nenhuma
  fonte].
- **Ziip SAN:** [A DEFINIR — qualificação completa na contratação, como na
  MINUTA-CONTRATO-SAAS.md, Cláusula 1ª].

## 2. Objeto

Homologação **local** do Ziip SAN com o piloto: dois ou três ciclos de
faturamento paralelo, no ambiente local do Ziip, **com dados sintéticos ou
anonimizados do piloto**, comparados conta a conta com o sistema do piloto,
mais o ensaio de POC. **Não há**, nesta etapa, corte do legado, emissão
fiscal real nem contrato de operação (ADR-0033, item 4).

## 3. Sem transferência de dado pessoal — anonimização na origem

1. **Nenhum dado pessoal real entra na base local**, em nenhum perfil, até o
   lançamento: CPF, nome, endereço, telefone, e-mail ou qualquer outro dado
   pessoal real **não** são transferidos ao Ziip neste termo (ADR-0033,
   item 5).
2. Dado do piloto só entra **anonimizado na origem**: a anonimização é feita
   **pelo piloto**, ou por **rotina entregue pelo Ziip e executada no
   ambiente do piloto**, **antes** da entrega.
3. A chave de comparação conta a conta é um **código substituto**; a
   **correspondência com a matrícula fica só com o piloto**.
4. A base local é conferida por **teste de varredura** que recusa documento
   sem a marca de dado sintético (ADR-0033, item 5).

## 4. Geometria da rede do piloto (regra de leitura do ADR-0033)

1. **Entram reais** as **redes, peças e estruturas** do piloto (tubulações,
   nós, válvulas, hidrantes, PV, elevatórias, reservatórios, ETA, ETE e
   demais ativos), que **não são dado pessoal**, mediante **autorização
   neste termo** — o termo de cooperação da homologação local (P0.30) — com
   **acesso restrito**; elas **não** vão para o ambiente de demonstração, que
   continua só com massa sintética.
2. **Entram sintéticos ou anonimizados na origem** as **ligações, ramais,
   cavaletes e imóveis**, cuja posição aponta o imóvel e, por ele, o
   titular: código substituto no lugar da matrícula e coordenada deslocada
   dentro da quadra, sem coincidir com o lote de origem, ou agregada no
   centroide da quadra, com o **método registrado por camada** no lote de
   importação; lote com camada pessoal sem o método registrado é
   **recusado**.

## 5. Ambiente e limites

1. A homologação roda no **ambiente local** (Docker Compose na máquina de
   desenvolvimento, com PostgreSQL/PostGIS, Keycloak, emulador compatível
   com S3 e cofre local — ADR-0033, itens 1 e 3). A infraestrutura na **AWS
   `sa-east-1` só é criada no lançamento comercial** (ADR-0033, item 2).
2. **Produção real** com cliente, dado pessoal real, emissão fiscal em
   produção e atestado **só acontecem no lançamento**, junto com a AWS
   (ADR-0033, item 5). A homologação local com dados anonimizados **não é
   execução do objeto** e **não gera atestado** (ADR-0033, seção "Atestados
   e licitações").
3. Fiscal: a homologação em produção restrita/homologação da SEFAZ
   (`tpAmb=2`) a partir do ambiente local não é emissão real;
   `ziipsan.fiscal.production-allowed` fica **falsa** em todo perfil local
   (ADR-0033, item 6). A exceção do certificado digital da própria Ziip/TGP
   nessa homologação fiscal segue o ajuste v2.8/v2.9 do ADR-0033 e **não**
   envolve certificado nem dado de cliente.

## 6. Vigência deste termo e destino dos dados da homologação

[A DEFINIR — prazo deste termo e o destino/expurgo dos dados anonimizados e
da geometria ao fim da homologação não constam das fontes além das regras
dos §§3–4; definir com o piloto na assinatura.]

## 7. Assinatura

Assinatura: [A DEFINIR — piloto não escolhido (P0.30); este termo é
assinado com o piloto escolhido, e o Contrato SaaS e o DPA continuam com
assinatura só na LC.9].
