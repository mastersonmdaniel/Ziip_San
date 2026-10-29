# ADR-0045 — Motor de OS no núcleo da plataforma; tipos e alvos de saneamento no pacote

- **Status:** Aceito, 28/10/2026
- **Decisor:** dono do produto (cartão de 28/10/2026, decisão pendente PD-02 da estrutura da v3.0)
- **Complementa:** ADR-0037 (plataforma com pacotes de vertical), que não dizia de que lado fica a OS; corrige a tabela
  de PLATAFORMA-E-VERTICAIS §2, que punha "OS" entre os módulos do pacote de vertical
- **Afeta:** ADR-0039 (unidade consumidora como raiz e alteração por OS), ADR-0041 (Certificações de qualidade),
  ADR-0032 (material da OS); plano v3.0: P0.47, P1.13, P1.31, P1.32, P1.35, P1.36, P2.25, P3.4, ED-20, ED-34, ED-36;
  mapa Flyway (`V9_1`); achados COE-10 e COE-16 (23a)

## Contexto

O ADR-0037 separou o núcleo genérico da plataforma dos pacotes de vertical, com teste de fronteira: o núcleo não
depende de classe, tabela nem texto de pacote. O PLATAFORMA-E-VERTICAIS §2 listou "OS e almoxarifado de saneamento"
entre os módulos do pacote `sanitation`.

Desde então, a OS ganhou papel transversal. Pelo ADR-0039, toda solicitação ou alteração de cliente ou de unidade
consumidora gera OS, de campo ou interna. O catálogo de alvos da v2.19 deu à OS alvos tipados, de unidade consumidora a
componente de equipamento. O núcleo do módulo Certificações de qualidade (P1.32) depende da P1.31 (OS), e a P2.25 liga
ocorrência e ação ao protocolo e à OS, enquanto a própria P1.32 exige teste de que o núcleo não depende de pacote. Com
a OS no pacote, o núcleo de certificações dependeria do pacote, e o teste de fronteira teria de falhar ou de ser
contornado (23a, COE-16).

## Decisão

1. **Motor de OS no núcleo.** Pertencem ao núcleo da plataforma o modelo da OS, a máquina de estados, a OS interna e a
   de campo, a OS de lote e a OS inversa, os efeitos aplicados uma vez no encerramento, a aprovação da OS interna por
   parâmetro (ADR-0039, item 8), o vínculo com o protocolo de atendimento (P1.36), o histórico, as observações internas
   e o contrato de alvo tipado. O contrato de alvo é genérico: tipo de alvo, referência à entidade e validação de
   existência por um provedor registrado para aquele tipo; a validação fica habilitada quando a entidade existe.
2. **Tipos e alvos de saneamento no pacote `sanitation`.** O pacote declara os tipos de serviço de saneamento (ligação
   nova, corte, religação, troca e aferição de hidrômetro, vazamento, alteração cadastral e os demais do catálogo), os
   efeitos específicos sobre as entidades do pacote e os tipos de alvo de saneamento (unidade consumidora, ligação,
   hidrômetro, trecho e ponto de rede, instalação operacional, equipamento, componente, edificação, frota, ocorrência de
   rede, zona, local sem ativo), com os provedores de validação de cada um. Corte e religação (P3.4), preço e lançamento
   do serviço (ED-34), programação e execução no backoffice (ED-36) e execução offline (ED-20) são do pacote e usam o
   motor.
3. **Registro pelo manifesto.** O pacote registra tipos de serviço, tipos de alvo e efeitos pelo manifesto
   `vertical.json` e pelas interfaces de efeito e de validação do núcleo; o núcleo não conhece nenhum tipo de
   saneamento por nome. Outros módulos que criam entidades alvo, como o cadastro de ativos (P1.35) e a ocorrência de rede
   (ED-92), registram os seus tipos de alvo pelo mesmo contrato, no lado da fronteira que a tabela única de módulos de
   PLATAFORMA-E-VERTICAIS definir (P0.47).
4. **Migrations.** As tabelas do motor ficam na reserva `V9_1` do mapa Flyway, sem mudar o número, no módulo do núcleo
   definido na P0.47; os tipos de serviço de saneamento são carga do pacote, não colunas nem valores fixos do motor.
5. **Testes.** O teste de fronteira (P0.47, estendido pela P1.13) recusa dependência do motor para o pacote e termo de
   saneamento no código e nos textos do motor, pela lista de exceções versionada do ADR-0037. Um teste de contrato com o
   pacote de exemplo (`example-vertical`, só em teste) cria tipo de serviço, tipo de alvo e efeito sem nenhum termo de
   saneamento e percorre o ciclo da OS até o encerramento.
6. **Certificações sobre o motor.** O núcleo de Certificações de qualidade (P1.32, P2.25) liga ocorrência, ação e
   evidência à OS pelo motor do núcleo, sem depender do pacote.

## Consequências

### Positivas

- A fronteira do ADR-0037 continua verdadeira: núcleo de certificações e motor de OS ficam do mesmo lado.
- Uma segunda vertical aproveita OS, protocolo, lote, aprovação e histórico sem reescrever o motor.
- A divisão de responsabilidades da OS entre P1.31, P3.4, ED-34, ED-36 e ED-20 (COE-10) passa a ter regra de
  arquitetura, não só de plano.

### Negativas

- O motor precisa de nomes e textos genéricos, e o pacote precisa implementar efeito e validação por interface, com mais
  disciplina na P1.31.
- O manifesto `vertical.json` ganha seções de tipos de serviço e de alvo, que entram no teste de contrato do manifesto.

## Alternativas consideradas

- **OS inteira no pacote `sanitation`**, como estava em PLATAFORMA-E-VERTICAIS §2: rejeitada; o núcleo de certificações
  dependeria do pacote, contra o teste de fronteira.
- **OS inteira no núcleo, com os tipos de saneamento:** rejeitada; poria termos e regras de saneamento no núcleo, contra
  o item 5 do ADR-0037.
- **Ajuste do ADR-0037 por linha datada:** preterida pela regra 2 do índice de ADRs (PD-03), porque a decisão muda a
  substância da fronteira.

## Tarefas derivadas

- P0.47: tabela única de módulos com o motor de OS no núcleo; seções de tipos de serviço e de alvo no contrato do
  manifesto.
- P1.31: motor no núcleo, com contrato de alvo genérico; tipos e alvos de saneamento registrados pelo pacote.
- P1.13: teste de fronteira e de termos sobre o motor.
- P1.32 e P2.25: vínculo com a OS pelo motor do núcleo.
- PLATAFORMA-E-VERTICAIS §2 e ARQUITETURA-ALVO: OS no núcleo, com tipos e alvos no pacote (COE-16).
