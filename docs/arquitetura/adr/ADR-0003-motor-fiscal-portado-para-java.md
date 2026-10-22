# ADR-0003 — Motor fiscal do Ziip Cloud portado para Java (G3)

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` §2.2 (G3) e §5 (R4, "NFAg-e por integrador"); revisão 17, achado P4
- **Estudo de porte:** `docs/arquitetura/FISCAL-PORTE-JAVA.md`

## Contexto

A G3 do plano perguntava se o produto constrói ou integra o emissor da NFAg-e (nota fiscal eletrônica de água e
saneamento, modelo 75), com obrigatoriedade prevista para 01/12/2026, ainda a confirmar no DOU
(`14-VALIDACAO-REGULATORIA-CONSOLIDADA.md`; verificado em 22/10/2026: a data consta do art. 1º, XVI, do Ato Conjunto
RFB/CGIBS nº 4/2026 e do cronograma da Receita Federal, e o documento foi instituído pelo art. 114 do Decreto
12.955/2026 e pelo regulamento do IBS, e não por Ajuste SINIEF). O plano (R4) e a revisão 17 (P4) supunham emissão por integrador
terceiro até 30/04/2027, atrás de uma porta `FiscalAuthorizer` que ainda não existe no código.

O mesmo dono mantém o Ziip Cloud (C#/.NET 8, `G:\Meu Drive\PROJETOS\Ziip Clould\Ziip-cloud-source`), que já tem um
motor fiscal: emissão de NFS-e no Padrão Nacional, cofre de certificado A1/A3 com AES-GCM, assinatura XMLDSig,
cliente mTLS com retry e cálculo de IBS/CBS (o código cita a NT 007/2026; verificado em 22/10/2026: essa NT, da SE/CGNFS-e, aplica o arredondamento
half-even e a tolerância de R$ 0,01 apenas a PIS e Cofins, e o arredondamento de IBS e CBS por documento e tributo
continua a confirmar nas NTs 004, 008 e 009 e no manual da NFS-e) (`src/Nfse.Domain`, `src/Nfse.Application`,
`src/Nfse.Infrastructure` com `Security`, `Xml`, `Sefin`, `Adn`; testes em `tests/Nfse.Tests`).

A NFAg-e é um documento fiscal eletrônico distinto da NFS-e: tem leiaute, schema, autorizador e eventos próprios. O
que se reaproveita do Ziip Cloud é a infraestrutura (certificado, assinatura, transporte, retry) e o cálculo de
IBS/CBS, não o leiaute.

## Decisão

1. O motor fiscal do Ziip Cloud será **portado para Java** e passará a ser um módulo do Ziip SAN, no mesmo monólito
   modular, atrás da porta `FiscalAuthorizer`.
2. A **NFAg-e** será construída sobre esse motor.
3. A **NFS-e** de serviços avulsos (serviços cobrados fora da conta de água, quando o município exigir) também usa o
   motor portado.
4. O cofre de certificado do porte usa o provedor de chaves por tenant do Ziip SAN (R1), com AAD de tenant, e não uma
   chave própria do módulo. O certificado é do prestador; o Ziip SAN guarda e usa a chave privada como operador.
5. O detalhamento técnico (mapeamento de componentes, bibliotecas Java, ordem do porte, lacunas) fica no estudo
   `docs/arquitetura/FISCAL-PORTE-JAVA.md`.

## Consequências

### Positivas

- O produto controla o emissor: sem custo por documento, sem dependência comercial de integrador e sem entregar o
  certificado do cliente a terceiro.
- Parte do caminho crítico já está implementada e testada no Ziip Cloud (assinatura, mTLS, IBS/CBS).
- Uma única pilha (Java) em operação no Ziip SAN, com o mesmo modelo de isolamento, auditoria e cifra do resto do
  sistema.

### Negativas e riscos

- **Duplicação com o Ziip Cloud.** Cada mudança da SEFAZ, da Receita ou do Comitê Gestor do IBS (nova NT, schema,
  regra de validação) precisa ser feita em dois códigos, em duas linguagens. Risco de divergência silenciosa.
  Mitigação:
  - **testes de contrato com o mesmo corpus de XML de referência** nos dois repositórios: para cada caso, entrada,
    XML canônico esperado antes da assinatura e resultado de validação contra o XSD oficial; os dois CIs rodam o
    mesmo corpus. Hoje `tests/Nfse.Tests` do Ziip Cloud não tem arquivos XML de referência; o corpus precisa ser
    criado a partir dos casos existentes;
  - tabela de versões (NT e schema suportados) mantida nos dois produtos e conferida no CI;
  - um único monitor regulatório alimentando os dois backlogs, com a mesma issue aberta nos dois repositórios.
- **Certificado A3 em SaaS.** A3 depende de token ou cartão no equipamento do usuário, o que não funciona em
  processamento no servidor. No Ziip SAN, o suporte a A3 exige assinatura remota (certificado em nuvem ou HSM); até o
  estudo concluir, o caminho de produção é A1.
- **Custódia da chave privada do cliente.** Aumenta o impacto de um vazamento. Exige R1 concluído, rotação,
  crypto-shredding no encerramento e auditoria de cada uso.
- O porte consome capacidade em P1 e P2 que o integrador pouparia.

### O que muda no código

- Novo módulo fiscal (nome e localização definidos no estudo), com a porta `FiscalAuthorizer` e adaptadores NFAg-e e
  NFS-e Padrão Nacional.
- `V4_5` (tributos e fiscal) inclui documentos fiscais, eventos, lotes e a referência ao certificado por tenant.
- Emissão em lote pelo worker (X4), com idempotência por conta e retomada.

### O que muda no roadmap

- "NFAg-e por integrador" sai do R4 e da revisão 17 (P4). Entra o porte em P1 e a NFAg-e em P2.
- R1 passa a ser pré-requisito também do fiscal.

## Alternativas consideradas

- **Serviço fiscal em C# compartilhado entre Ziip Cloud e Ziip SAN.** Rejeitada: coloca uma segunda pilha em
  produção no Ziip SAN, cria dependência de rede e de disponibilidade entre produtos, acopla os ciclos de entrega e
  exige reproduzir fora do Ziip SAN o modelo de isolamento por tenant e a custódia de chaves.
- **Adaptador para integrador fiscal terceiro.** Rejeitada: custo por documento, dependência de fornecedor no caminho
  crítico do faturamento e necessidade de entregar o certificado do cliente a um terceiro.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Concluir o estudo `docs/arquitetura/FISCAL-PORTE-JAVA.md` (componentes, bibliotecas, A3, ordem) | P0 |
| Confirmar no DOU a obrigatoriedade e a data da NFAg-e para o perfil do cliente piloto | P0 |
| Criar o corpus de XML de referência a partir do Ziip Cloud e ligá-lo ao CI dos dois repositórios | P1 |
| Portar cofre de certificado (sobre o R1), XMLDSig, mTLS com retry e IBS/CBS, com os testes de contrato | P1 |
| NFAg-e: leiaute, emissão em lote no ciclo (R4/R4a), eventos e homologação no autorizador | P2 |
| NFS-e de serviços avulsos sobre o motor portado | P3 |
| Homologação com o certificado do cliente piloto e plano de contingência | P3 |
