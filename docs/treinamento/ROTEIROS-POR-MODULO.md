# Roteiros por módulo — material de treinamento (ED-11)

Um roteiro por módulo do catálogo. Cada roteiro segue as cinco fases do
plano-padrão (8 h + 12 h + 12 h + 4 h + 4 h = 40 h) e só treina funções
que as tarefas de origem constroem — a coluna "base" é a tarefa cujo
aceite descreve a função. As fases `OPERACAO_GUIADA` e
`PRATICA_SUPERVISIONADA` acontecem sempre no tenant de pré-treinamento.
Os exercícios são enunciados de operação com resultado esperado
observável no sistema; nenhum exercício usa dado real.

## `CADASTRO` — Cadastro e unidade consumidora (base: P1.3, P1.2, P1.31, P1.41, P1.40)

- **Fundamentos:** unidade consumidora como raiz (P1.31); a regra de que
  toda inclusão/alteração de cliente e de unidade — tela, API ou CSV —
  gera OS interna e o efeito se aplica no encerramento (P1.3/P1.31).
- **Guiada:** cadastrar cliente pela tela com rascunho e `validate-step`;
  acompanhar a OS interna gerada até o efeito.
- **Prática:** carga por CSV pelo importador genérico (P1.40); importação
  pelo framework declarativo com ensaio sem gravar e conciliação por
  setor/rota/categoria/saldo (P1.2); unidade sem usuário vigente na
  situação da P1.41.
- **Exceções:** documento inválido no perfil de qualidade do staging
  (P1.2); reimportação sem duplicar (P1.2); busca por documento com
  papel, registro e cota (P0.11, no módulo de privacidade).

## `MEDICAO_CONTA` — Medição, ciclo e conta (base: P2.5, P2.7, P2.20, P2.29)

- **Fundamentos:** medição, ciclo e conta da P2.5; CBS/IBS com vigência
  no cálculo (P2.4, lido pela conta).
- **Guiada:** registrar leitura e percorrer o ciclo até a conta;
  leitura terceirizada da P2.7 no mesmo fluxo.
- **Prática:** retificação e manutenção de conta pelo backoffice
  (P2.20); simulação de impacto de reajuste/revisão tarifária (P2.29)
  sem aplicar.
- **Exceções:** conta em situação que o backoffice recusa retificar;
  parâmetro sem valor aceito e vigente bloqueando a operação (P0.19).

## `ARRECADACAO` — Arrecadação (base: P3.1, P3.2, P3.29, P2.5)

- **Fundamentos:** retorno e conciliação (P3.1); Pix Cobrança e boleto
  híbrido (P3.2).
- **Guiada:** baixar um retorno e conciliar; emitir cobrança Pix.
- **Prática:** pagamento não identificado na fila da P3.29 e a sua
  destinação; devolução de pagamento (P3.29).
- **Exceções:** retorno divergente da conciliação; débito automático
  legado (P3.24) só entra neste roteiro para o tenant em que a P0.30
  encontrou optantes — fora dele, o tópico é declarado não aplicável,
  não treinado como se existisse.

## `FISCAL` — Fiscal (base: P1.8, P2.6, P2.13, P3.6, P2.33, P3.7)

- **Fundamentos:** porte fiscal e cofre de certificado (P1.8); NFAg-e
  modelo 75 (P2.6) e NFS-e (P2.13, P3.6).
- **Guiada:** emitir documento no ambiente de homologação fiscal
  (P3.7) — nunca no tenant de pré-treinamento com fiscal de produção,
  que nele é desligado por guarda.
- **Prática:** fila de rejeições fiscais: tratar, inutilizar e reenviar
  (P2.33); rejeição do catálogo de erros (P2.16).
- **Exceções:** certificado vencido no cofre; documento de outro
  emitente recusado na importação de anteriores (P2.19).

## `PORTAL_ATENDIMENTO` — Portal e atendimento (base: P3.3, P3.20, P1.36, P3.36, P3.35)

- **Fundamentos:** protocolo de atendimento como origem de toda
  solicitação externa (P1.36); portal com 2ª via, Pix e protocolo
  (P3.3) e vínculo/histórico de 120 meses (P3.20).
- **Guiada:** abrir solicitação pelo protocolo e acompanhar no portal;
  central de notificações e preferências (P3.36).
- **Prática:** conferir a autenticidade pública de uma fatura e os
  canais oficiais (P3.35) como o atendente orienta o consumidor.
- **Exceções:** solicitação sem protocolo; consumidor sem vínculo.

## `OS_CAMPO` — Ordens de serviço e campo (base: P1.31, P1.35, P3.4, P2.28)

- **Fundamentos:** núcleo de OS (P1.31); ativos e a hierarquia de seis
  níveis (P1.35); caixa unificada de pendências (P2.28).
- **Guiada:** programar e executar uma OS de cada tipo treinado; corte
  e religação com as regras e os bloqueios da P3.4.
- **Prática:** OS gerada por alteração cadastral (fronteira P1.3/P1.31);
  delegação temporária de pendência (P2.28).
- **Exceções:** corte bloqueado pela regra (P3.4); OS sem executor
  habilitado (competência da P3.31 alimenta a verificação, OPE-14).

## `GIS_CADASTRO_TECNICO` — GIS (base: P1.26, P1.27, P3.25, P3.26, P3.27, P2.21–P2.23)

- **Fundamentos:** modelo de dados do núcleo do GIS (P1.26); catálogo
  de fábrica de água e esgoto (P1.27).
- **Guiada:** consultar o mapa web (P3.25); editar em pacote com
  revisão por outra pessoa (P2.22).
- **Prática:** importar cadastro técnico DWG via DXF, DXF, SHP e GPKG
  (P3.26); vínculo feição ↔ imóvel ↔ ligação ↔ ramal (P2.23);
  temáticos e OS no mapa (P3.27).
- **Exceções:** violação de regra de conectividade/topologia (P2.21);
  pacote de edição sem revisor distinto do editor (P2.22).

## `PERDAS_QUALIDADE` — Perdas e qualidade (base: P1.32, P3.28, P3.27)

- **Fundamentos:** núcleo do módulo de certificações de qualidade
  (P1.32); temáticos de precisão (P3.27).
- **Guiada:** registrar ocorrência de vazamento oculto e aferição de
  hidrômetro (P3.28) até a OS correspondente.
- **Prática:** análise de um temático de perdas sobre o cadastro do
  tenant de pré-treinamento.
- **Exceções:** ocorrência sem vínculo com unidade/ligação.

## `PLATAFORMA_IMPLANTACAO` — Plataforma e implantação (base: P1.6, P0.19, P0.13, P1.37, ED-09)

- **Fundamentos:** console da plataforma e a jornada de implantação
  com os seus gates (P1.6/PLT-21); metodologia da ED-09.
- **Guiada:** propor, aprovar (pessoa diferente) e aceitar uma mudança
  de parâmetro com vigência futura e alerta de risco (P1.6/P0.19);
  ler o painel da jornada e a porcentagem concluída.
- **Prática:** atribuir perfis de fábrica (P1.37) e conferir o menu
  resultante por perfil; exportar/importar pacote de parâmetros
  aceitos entre ambientes (ED-09).
- **Exceções:** produção bloqueada por passo obrigatório pendente;
  proponente igual ao aprovador recusado (P0.19).

## `PRIVACIDADE_SEGURANCA` — Privacidade e segurança (base: P3.40, P3.10, P0.11, P0.15, P3.17, P0.13)

- **Fundamentos:** direitos do titular e registro de operações de
  tratamento (P3.40); máscara com teto e leituras registradas (P0.11).
- **Guiada:** atender um direito do titular pelo canal do portal
  (P3.40); política de MFA do tenant no login (P0.15).
- **Prática:** busca por documento com a permissão própria, dentro da
  cota, conferindo o registro da busca (P0.11); acesso assistido com
  consentimento (P3.17) — sem impersonação (ADR-0020).
- **Exceções:** busca sem permissão (403) e cota excedida (429 com
  alerta), como o aceite da P0.11 prescreve; fluxo de incidente e RIPD
  (P3.10) em nível de procedimento, sem dado de incidente real.

## `TI_CLIENTE` — Capacitação da TI do cliente

Roteiro próprio em `CAPACITACAO-TI.md` (tópicos, práticas e régua de
conteúdo em `ItEnablement`). As 40 h do módulo seguem as mesmas cinco
fases; as práticas dos tópicos `handsOn` ocupam as fases guiada e
supervisionada.
