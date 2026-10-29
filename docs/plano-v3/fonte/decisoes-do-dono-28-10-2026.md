# Decisões do dono do produto para a v3.0 do plano (cartões de 28/10/2026)

1. **Reescrever o plano numa v3.0 consolidada.** Cada tarefa reescrita com os ajustes incorporados ao aceite, uma
   tarefa dona por achado, ondas corrigidas, sobreposições resolvidas e pacotes pendentes fora das ondas. O histórico
   das versões fica preservado no git e num anexo.
2. **Aprovadas todas as recomendações dos relatórios 23a a 23e**, e em especial:
   - perfis de acesso de fábrica por função (atendente, faturista, leiturista, almoxarife, ouvidor, encarregado de
     dados, regulador e outros), cada um com menu e página inicial (JOR-01);
   - LGPD do titular como obrigação de todo tenant: direitos do titular, registro de operações de tratamento e canal no
     portal saem do pacote voluntário ISO/IEC 27701 (ED-74) e viram tarefa de fase (JOR-15);
   - limpeza do código antes da renomeação para ziipsan (P0.33), conforme o relatório 23c;
   - hierarquia de ativos antecipada para o P1, junto do núcleo de OS (COE-05).
3. **ADR que permite login gov.br e pagamento sem conta no portal**, alterando o ADR-0022 (novo ADR-0043).
4. **ADR formalizando que o projeto não tem prazos** (diretriz de 21/10/2026): datas de fase e marco são indicação de
   sequência; o plano não usa linguagem de prazo (novo ADR-0044).
5. **Não reestimar agora**: as tarefas sem tamanho seguem "a estimar"; o tamanho continua só como indicação de esforço.
6. **Nome na conta só do usuário titular, como parâmetro**: a fatura sai em nome do usuário titular (NR 11, art. 33,
   §2º), com opção do tenant de exibir também o proprietário; padrão mais protetivo.
7. **Protocolo sempre antes da OS**: toda solicitação externa nasce como protocolo e gera a OS; alteração interna do
   backoffice gera OS sem protocolo (ADR-0039, item 7).

Diretrizes que continuam valendo: bifurcação normativa vira parâmetro com padrão mais protetivo e lei federal que
proíbe fica travada (ADR-0038); unidade consumidora como raiz e toda alteração por OS (ADR-0039); sem prazos de
projeto; sem gestão de equipe ou capacidade; desenvolvimento local até o lançamento comercial e AWS depois (ADR-0033);
decisões do dono não se alteram sem cartão.

## Decisões pendentes da estrutura v3.0 (cartão de 28/10/2026: aceitas todas as recomendações)

- PD-01: o grupo B2 da onda B fica fora da prontidão de POC (ED-01 depende da onda A, do grupo B1 e do P4); B2 entra
  só no critério da LC.1.
- PD-02: motor de OS no núcleo da plataforma; tipos e alvos de saneamento no pacote `sanitation`; registrar em ADR.
- PD-03: regra 2 do índice de ADRs: linha de ajuste só para esclarecimento; mudança de substância por ADR novo que
  substitui.
- PD-04: ADR proposto, antes da onda A, para ingestão de supervisório somente leitura por REST e arquivo, sem broker
  (exceção controlada ao ADR-0010).
- PD-05: P4.6 vai para a seção de pacotes sob demanda e deixa de contar no critério da LC.1.
- PD-06: atividade econômica (CNAE) na unidade consumidora.
- PD-07: caixa próprio do prestador como item condicional da ED-26, ligado por parâmetro e confirmado na P1.1.
- PD-08: pacote de validação do sistema por versão para a ISO/IEC 17025 (ED-51) pendente até haver cliente que o exija.
- PD-09: ED-68, ED-102 e ED-103 seguem como pacotes pendentes.
- PD-10: pareceres jurídicos: primeiro o da RAS-06 (Pix Automático e débito automático), que decide o escopo da P3.24
  e da ED-27.
