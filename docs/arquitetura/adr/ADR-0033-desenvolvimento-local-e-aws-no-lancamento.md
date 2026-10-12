# ADR-0033 — Desenvolvimento local até o lançamento comercial; AWS no lançamento

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto (cartões de 12/10/2026)
- **Afeta:** ADR-0001 (marco do piloto e fase final); ADR-0010 (cofre local e emulador de S3); ADR-0011 (momento da
  implantação na AWS); ADR-0012 (vigência das metas de disponibilidade e continuidade); ADR-0013 (ambientes do P0);
  ADR-0023 (atestados e licitações); ADR-0024 (IaC e instância dedicada); ADR-0025 (nível Setor Público); ADR-0029
  (marco de contratação e capacidade); ADR-0016, ADR-0018, ADR-0022, ADR-0027 e ADR-0028 (remissões ao go-live, pela
  regra de leitura do item 7); `docs/arquitetura/ARQUITETURA-ALVO.md` (§7.3); `REQUISITOS-NAO-FUNCIONAIS.md`;
  `FISCAL-PORTE-JAVA.md` (§3.6, §3.8 e §6); `CENTRAL-ZIIP.md` (§11); plano v2.7 (P0.26 a P0.29, P0.45, GL.1 a GL.4,
  fase LC, §2.2, §2.4, §5 a §8); consolidado 18
- **Ajuste de 12/10/2026 (v2.8), antes de qualquer implementação:** resolvidas as duas pendências deste ADR. (a)
  **Momento do lançamento:** escolhida a opção A da seção "Atestados e licitações": o lançamento comercial começa
  depois de todas as fases, inclusive a trilha de editais (ondas A, B e C), a partir de outubro de 2028; primeira
  produção por volta de fevereiro de 2029; primeiro atestado entre maio e julho de 2029; licitações com POC a partir do
  3º trimestre de 2029. A meta de disputar no 2º semestre de 2028 deixa de valer, e as opções B e C passam a
  alternativas rejeitadas; a consequência negativa "com a opção A, a meta de 2028 do ADR-0029 cai" deixa de ser
  hipótese; o efeito na capacidade da trilha está no ADR-0029 (ajuste v2.8). (b) **Exceção explícita ao item 5**
  (certificado do item 6): a produção restrita da NFS-e Nacional e a homologação da SEFAZ (`tpAmb=2`), a partir do
  ambiente local e sem emissão real, usam o e-CNPJ da própria Ziip/TGP, e nenhum certificado de cliente entra no
  ambiente local; o CPF contido nesse certificado é do responsável pela empresa, que consente por escrito, e não é
  dado de cliente nem de consumidor; o certificado (arquivo e senha) fica no cofre local, fora do banco, e cada acesso
  a ele é registrado (quem, quando e para quê); o XML assinado leva o certificado público, com o mesmo CPF, e os XML de
  homologação gravados no ambiente local ficam abrangidos pela mesma exceção e pelo mesmo consentimento; fora dessa
  exceção, a regra do item 5 e o teste de varredura da base local continuam valendo sem mudança; certificado de cliente
  só na produção, a partir do lançamento (plano LC.4). Se a homologação da NFAg-e aceita o CNPJ da Ziip/TGP como
  emitente de teste do modelo 75 fica a confirmar no manual e no credenciamento (plano §2.4). As tarefas derivadas
  "Escolha do momento do lançamento" e "Certificado digital da homologação fiscal no ambiente local" ficam cumpridas;
  decisão do dono do produto.

## Contexto

O plano v2.6 previa, no P0, homologação e produção na AWS `sa-east-1` com IaC, RDS com PITR, R1 sobre o KMS e Keycloak
de produção (P0.26 a P0.29), e um go-live do piloto em junho e julho de 2027, com dois ou três ciclos de faturamento
paralelo com dado real, corte do legado e emissão fiscal ligada (GL.1 a GL.4). O primeiro atestado viria desse piloto,
no fim de 2027, e a primeira licitação com POC, no 2º semestre de 2028 (ADR-0023, ADR-0029).

Manter infraestrutura gerenciada no ar desde o P0 custa, na estimativa do ADR-0011, na ordem de US$ 2 mil por mês, sem
receita até o primeiro contrato em produção, e obriga a operar backup, cofre e plantão de um ambiente que, até o
piloto, só recebe dado sintético. O dono do produto decidiu concentrar a implantação na nuvem no lançamento comercial e
manter todo o desenvolvimento, inclusive a homologação com o piloto, na máquina de desenvolvimento.

## Decisão

1. **Desenvolvimento local até o lançamento.** Todo o desenvolvimento, a homologação e o ambiente de demonstração rodam
   localmente, em Docker Compose na máquina de desenvolvimento, com PostgreSQL 17 e PostGIS 3.5 (ADR-0013), Keycloak,
   emulador compatível com a API S3 e cofre local, até que todas as fases estejam concluídas e o produto esteja pronto
   para venda. O CI continua no GitHub Actions, sem dado de cliente.
2. **AWS no lançamento comercial.** O provedor e os serviços do ADR-0011 (AWS `sa-east-1`: RDS for PostgreSQL 17
   Multi-AZ com PITR e réplica de leitura, KMS, Secrets Manager, S3 com Object Lock, CloudFront e AWS WAF,
   observabilidade gerenciada) não mudam. Muda o momento: a infraestrutura na AWS só é criada no lançamento comercial, nova fase do plano
   depois do P4 (fase LC), quando não houver mais desenvolvimento de fase pendente. Antes da implantação definitiva, um
   ensaio de implantação na AWS com dado sintético, em ambiente destruído ao fim, compara o comportamento local com o da
   nuvem (plano LC.1).
3. **Código pronto para a AWS por interfaces.** Armazenamento de objetos, chaves (KEK e DEK por tenant,
   `TenantKeyProvider`), segredos, fila (outbox do ADR-0014) e envio de e-mail ficam atrás de interfaces, com
   implementação local agora e implementação AWS no lançamento. Cada interface tem suíte de teste de contrato, que roda
   no CI contra a implementação local e, no lançamento, contra a da AWS. Módulos de domínio não dependem de SDK de
   provedor (teste de fronteira).

   | Interface | Implementação local (até o lançamento) | Implementação AWS (no lançamento) |
   |---|---|---|
   | Armazenamento de objetos | emulador compatível com S3, com retenção imutável se o emulador a suportar; senão, implementação local que recusa sobrescrita e exclusão | S3 com Object Lock |
   | Chaves (`TenantKeyProvider`) | KEK no cofre local, DEK por tenant guardada cifrada; rotação e destruição da DEK (crypto-shredding) ensaiadas | AWS KMS |
   | Segredos | cofre local, fora do modo dev, com armazenamento persistente; nenhum segredo no repositório | AWS Secrets Manager |
   | Fila | outbox no PostgreSQL (ADR-0014) | a mesma outbox, no RDS |
   | E-mail | implementação que grava a mensagem para inspeção, sem envio externo | serviço de e-mail definido na IaC (Amazon SES ou equivalente) |
   | Backup e PITR | arquivamento de WAL e backup em volume local; PITR ensaiado com tempo registrado | backup automático e PITR do RDS; cópia de snapshots em outra conta |

4. **Homologação local do piloto no lugar do go-live.** O marco de junho e julho de 2027 deixa de ser entrada em
   produção e passa a ser a homologação local do piloto: dois ou três ciclos de faturamento paralelo, no ambiente local,
   com dados sintéticos ou anonimizados do cliente, comparados conta a conta com o sistema dele, mais o ensaio de POC.
   Não há corte do legado, emissão fiscal real nem contrato de operação nessa etapa.
5. **Nenhum dado pessoal real na base local.** CPF, nome, endereço, telefone, e-mail ou qualquer outro dado pessoal real
   não entram na base local, em nenhum perfil, até o lançamento. Dado do piloto só entra anonimizado na origem: a
   anonimização é feita pelo piloto, ou por rotina entregue pelo Ziip e executada no ambiente do piloto, antes da
   entrega; a chave de comparação conta a conta é um código substituto, e a correspondência com a matrícula fica só com
   o piloto. A base local é conferida por teste de varredura que recusa documento sem a marca de dado sintético. Produção
   real com cliente, dado pessoal real, emissão fiscal em produção e atestado só acontecem no lançamento, junto com a
   AWS.
6. **Fiscal.** A homologação na produção restrita da NFS-e Nacional e na homologação da SEFAZ (`tpAmb=2`) continua
   possível a partir do ambiente local, sem emissão real. `ziipsan.fiscal.production-allowed` fica falsa em todo perfil
   local; `tpAmb=1` só existe na produção da AWS, a partir do corte do lançamento, com a aprovação dupla do
   `FISCAL-PORTE-JAVA.md` §3.6. O certificado digital usado nessa homologação é pendência do dono do produto (plano
   §2.4), porque o e-CNPJ traz o CPF do responsável.
7. **Regra de leitura dos documentos anteriores.** Nos ADRs e documentos escritos até a v2.6 do plano, "go-live" (do
   piloto) lê-se como "homologação local do piloto" quando trata do faturamento paralelo e do ensaio, e como "primeira
   produção, no lançamento comercial" quando trata de dado real, corte do legado, emissão fiscal em produção, contrato de
   operação ou atestado. "Homologação" e "produção" na AWS leem-se como ambientes criados no lançamento; até lá,
   homologação é a homologação local.

## O que este ADR substitui e o que ajusta

| ADR ou documento | O que muda | Forma |
|---|---|---|
| ADR-0001 | marco "go-live piloto" passa a "homologação local do piloto"; nova fase final "lançamento comercial" depois do P4; P0 sem infraestrutura na AWS | linha de ajuste (v2.7) |
| ADR-0010 | cofre local e emulador de S3 deixam de estar congelados e entram como exceção, só no ambiente local, até o lançamento | linha de ajuste (v2.7) e tabela "Exceções" |
| ADR-0011 | provedor e serviços mantidos; IaC, RDS com PITR, R1 sobre o KMS, Secrets Manager, S3 com Object Lock, borda e observabilidade gerenciada passam do P0 ao lançamento; no P0, cofre e backup locais | linha de ajuste (v2.7) |
| ADR-0012 | metas de disponibilidade e continuidade, pentest antes da produção, residência e plantão valem a partir do lançamento; desempenho continua medido nas fases, no ambiente local | linha de ajuste (v2.7) |
| ADR-0013 | PostgreSQL 17 no P0 em todo o ambiente local; conferência na hospedagem documental no P0 e prática no ensaio do lançamento | linha de ajuste (v2.7) |
| ADR-0023 | primeiro atestado só depois do lançamento e da primeira operação real; ambiente de demonstração local até o lançamento; impacto na meta de licitações de 2028 (seção "Atestados e licitações") | linha de ajuste (v2.7) |
| ADR-0024 | IaC parametrizada e instância dedicada só a partir do lançamento; o ambiente local serve de base ao pacote de reversão | linha de ajuste (v2.7) |
| ADR-0025 | nível Setor Público contratável só a partir do lançamento; plantão acionado a partir da primeira produção | linha de ajuste (v2.7) |
| ADR-0029 | "go-live do piloto (M10)" lê-se "homologação local do piloto"; efeito do lançamento sobre a capacidade da trilha recalculado com a escolha do plano §2.4 | linha de ajuste (v2.7) |
| ADR-0016, ADR-0018, ADR-0022, ADR-0027, ADR-0028 | remissões a "go-live" | regra de leitura do item 7, sem linha de ajuste |
| Plano | v2.7: P0.26 a P0.29 em versão local, nova P0.45 (interfaces), GL.1 a GL.4 redefinidas, fase LC (LC.1 a LC.14), caminho crítico, marcos, riscos, resumo, §2.2, §2.4 e §8 | revisão do plano |
| ARQUITETURA-ALVO, RNF, FISCAL-PORTE-JAVA, CENTRAL-ZIIP, consolidado 18 | ambiente local como alvo até o lançamento; vigência das metas; emissão fiscal e contrato no lançamento; atestado | revisão dos documentos |

## Atestados e licitações

O ADR-0023 (item 3) prevê atestado emitido depois dos ciclos em produção. Homologação local com dados anonimizados não
é execução do objeto: não gera atestado, e o edital de Videira pede ainda ligações em operação e remessa ao e-Sfinge
(consolidado 18, §4). O primeiro atestado passa a depender do lançamento e de uma primeira operação real, com paralelo
real, corte e ciclos em produção. A POC das licitações também exige o sistema em nuvem (consolidado 18, §3.1 e §3.2), o
que só existe depois do lançamento.

Intervalo estimado entre o início do lançamento e o primeiro atestado, com incerteza de ±30%: cerca de 8 semanas de
ensaio, infraestrutura, pentest e contrato (LC.1 a LC.9); carga real e um ou dois ciclos de paralelo real (cerca de 2
meses); corte; atestado de 3 a 5 meses depois do corte, intervalo já admitido no plano anterior (corte em julho,
atestado no fim de 2027). Total: de 7 a 9 meses.

| Opção | Início do lançamento | Primeira produção (corte) | Primeiro atestado | Primeira licitação com POC (M13) |
|---|---|---|---|---|
| **A. Leitura literal do item 2:** lançamento depois de todo o P4, inclusive a trilha de editais (onda C concluída no 3º trimestre de 2028) | outubro de 2028 | por volta de fevereiro de 2029 | maio a julho de 2029 | a partir do 3º trimestre de 2029 |
| **B. Alternativa proposta:** lançamento logo depois das tarefas originais do P4 (17/12/2027), com o piloto como cliente inicial em produção e a trilha de editais seguindo depois do lançamento, como evolução do produto em produção | janeiro de 2028 | por volta de maio de 2028 | agosto a outubro de 2028 | 2º semestre de 2028, no limite (editais do tipo SAMAE) |
| **C. Intermediária:** lançamento na prontidão de POC (M12, 30/06/2028), com a onda C depois do lançamento | julho de 2028 | por volta de novembro de 2028 | fevereiro a abril de 2029 | a partir do 2º trimestre de 2029 |

A opção A cumpre a decisão ao pé da letra e adia a meta do ADR-0029 em cerca de um ano. A opção B é a única que mantém
licitações em 2028, mas contraria o item 2 ("quando não houver mais desenvolvimento de fase pendente"), porque a trilha
continua depois do lançamento; traz custo de nuvem a partir de janeiro de 2028, operação de produção e plantão
concorrentes com a trilha e cerca de 15 semanas-pessoa de lançamento (plano, fase LC) no mesmo período da trilha, o que
absorve quase toda a folga da contratação de 5 desenvolvedores (ADR-0029). A opção C fica entre as duas. Editais do tipo
VISAN (dois atestados, 8.500 ligações, e-Sfinge em operação) ficam para 2029 em qualquer opção.

A escolha não se deduz da decisão: fica como pendência do dono do produto no plano §2.4, com prazo em 13/11/2026,
porque o termo de intenção do piloto (P0.30) precisa dizer quando a produção começa. Até a escolha, o plano registra as
datas da opção A e mostra as das opções B e C ao lado.

## Consequências

### Positivas

- Sem custo de nuvem até o lançamento (na ordem de US$ 2 mil por mês no dimensionamento inicial do ADR-0011), nem
  operação de backup, cofre e plantão de ambiente sem cliente.
- Nenhum dado pessoal real fora do controlador antes de haver contrato, DPA e infraestrutura de produção.
- O ambiente local em contêineres (PostgreSQL, Keycloak, armazenamento compatível com S3 e cofre) é a base do pacote de
  reversão do ADR-0024, que passa a ser exercitado todo dia.
- As interfaces com teste de contrato tornam verificável a neutralidade de provedor que o ADR-0011 (item 4) já exigia.

### Negativas

- Primeiro atestado e primeira licitação com POC atrasam (seção anterior); com a opção A, a meta de 2028 do ADR-0029
  cai.
- Diferenças entre o ambiente local e a AWS (RDS Multi-AZ, KMS, Object Lock do S3, rede, TLS, IAM, latência) só
  aparecem no lançamento. Mitigação: testes de contrato das interfaces no CI e ensaio de implantação na AWS (LC.1)
  antes da implantação definitiva.
- O lançamento concentra infraestrutura, pentest, contrato, carga real, paralelo real e corte num só período; o risco
  que o plano distribuía pelo P0 e pelo go-live passa a ficar no fim.
- Metas de desempenho medidas no hardware local não provam o comportamento na nuvem; servem de linha de base e são
  medidas de novo no ensaio (LC.1).
- O paralelo da homologação local usa dado anonimizado: divergências que dependem de dado real (cadastro incompleto,
  endereço de entrega, documento inválido) só aparecem no paralelo real do lançamento, que por isso continua no plano
  (LC.11).
- O piloto só terá produção no lançamento; o termo de intenção (P0.30) precisa dizer isso, com risco de o cliente não
  aceitar o prazo.
- A máquina de desenvolvimento passa a ser o único ambiente de homologação e demonstração. Não guarda dado real, mas a
  perda dela interrompe ensaios; mitigação: ambiente recriável do zero por script a partir do GitHub, com tempo medido.

### O que muda no código

- Interfaces de armazenamento de objetos, segredos e envio de e-mail, além do `TenantKeyProvider` e da outbox já
  previstos, com implementação local e suíte de contrato; teste de fronteira contra SDK de provedor em módulo de
  domínio.
- Compose com perfis do ambiente local completo (cofre local e emulador de S3, além de PostgreSQL e Keycloak),
  homologação local e demonstração local, portas em 127.0.0.1.
- Teste de varredura da base local contra dado pessoal real.
- Implementações AWS (KMS, Secrets Manager, S3, e-mail) e IaC escritas na fase LC.

### O que muda no roadmap

- Plano v2.7: P0.26 a P0.29 em versão local; nova P0.45; GL.1 a GL.4 redefinidas como homologação local; nova fase
  "Lançamento comercial" (LC.1 a LC.14) depois do P4. Pentest externo (P3.8), assinatura do contrato e do DPA (P3.9),
  publicação da página de status (P3.18) e importação dos XML reais (P2.19) passam, na parte que exige produção, à fase
  LC.
- Novos marcos M14 (início do lançamento), M15 (primeira produção) e M16 (primeiro atestado); M13 condicionado à
  escolha do plano §2.4.

## Alternativas consideradas

- **Manter a AWS desde o P0 (plano v2.6).** Rejeitada pelo dono do produto: custo e operação de nuvem por mais de um
  ano sem cliente em produção.
- **Homologação do piloto com dado real na máquina local.** Rejeitada: dado pessoal de terceiros em ambiente sem os
  controles de produção (cofre gerenciado, backup no Brasil com expiração, monitoramento, pentest).
- **Hospedagem intermediária de menor custo (VPS) até o lançamento.** Rejeitada: o ADR-0011 já a descartou para
  produção por falta de certificações; como homologação, acrescenta custo sem acrescentar o que falta ao ambiente
  local, que é o comportamento dos serviços da AWS.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Interfaces de infraestrutura com implementação local, suíte de contrato e teste de varredura da base local (P0.45) | P0 |
| Ambiente local completo, homologação e demonstração locais (P0.27); backup e PITR locais e R1 sobre o cofre local (P0.28); Keycloak em modo de produção local (P0.29) | P0 |
| Exceção no ADR-0010 para o cofre local e o emulador de S3 | P0, registrada nesta data |
| Roteiro de anonimização na origem entregue ao piloto, com código substituto no lugar da matrícula | P0 (com a P0.30), antes da P1.2 |
| Escolha do momento do lançamento (opções A, B ou C) | até 13/11/2026 (plano §2.4) |
| Certificado digital da homologação fiscal no ambiente local | até 27/11/2026 (plano §2.4) |
| Homologação local do piloto (GL.1 a GL.4) | jun–jul/2027 |
| Ensaio de implantação na AWS, IaC, RDS, KMS e Secrets Manager, S3, Keycloak, observabilidade, pentest, contrato, carga e paralelo reais, corte e primeiro atestado (LC.1 a LC.14) | Lançamento comercial |
