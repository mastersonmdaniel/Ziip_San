# ADR-0004 — SINISA e SINIR em modo assistido (G7, G8)

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` §2.2 (G7, G8), §5 (R6 e trilha GRS)

## Contexto

As decisões G7 (SINISA: envio automatizado, glossário vigente, quem preenche) e G8 (SINIR: Web Service para
terceiros, credencial, lodo de ETA/ETE) dependiam de consulta regulatória e jurídica sem responsável.

Fatos já levantados:

- o MTR só tem valor legal se emitido no SINIR, e a DMR é feita no SINIR (plano §1);
- não há endpoint oficial publicado nem credencial disponível para envio por terceiros. O código já reconhece isso:
  `SinirIntegrationService` não executa chamada HTTP e mantém o MTR como pendente de sincronização
  (`grs/sigsan-mtr/src/main/java/br/com/sigsan/grs/mtr/integration/SinirIntegrationService.java:16-17`, propriedades
  `sinir.enabled` e `sinir.base-url` nas linhas 36-38; estado `SinirSyncStatus` em
  `grs/sigsan-mtr/src/main/java/br/com/sigsan/grs/mtr/domain/WasteMtr.java:152-155`);
- o R6 já previa exportação XLSX/CSV com origem de cada valor (COMPUTED, IMPORTED, MANUAL) e justificativa obrigatória
  para valor manual.

Enviar em nome do prestador exigiria guardar credencial pessoal ou institucional de terceiros em portais do governo,
com responsabilidade sobre cada declaração.

## Decisão

SINISA e SINIR funcionam em **modo assistido**:

1. O sistema calcula os indicadores e os dados declaratórios a partir da base do tenant.
2. O sistema gera o arquivo ou a planilha **no formato oficial vigente** do portal (versão de leiaute e de glossário
   registrada no arquivo e no histórico).
3. O cliente confere e envia no portal do governo, com a própria credencial.
4. O sistema registra a exportação (hash do arquivo, versão do leiaute, usuário, data) e permite importar ou informar
   o retorno do portal (número do MTR emitido, protocolo da DMR, recibo do SINISA, CDF).
5. Não há Web Service com o governo nem guarda de credencial de terceiros.

## Consequências

### Positivas

- Elimina a dependência de credencial e de API inexistente; a entrega não espera definição externa.
- A responsabilidade pela declaração fica com o prestador, que confere e assina no portal.
- Menos superfície de ataque: nenhum segredo de portal governamental no sistema.

### Negativas

- O envio continua manual. Erro de digitação ou de upload fica fora do controle do sistema; mitigação com checagens de
  consistência antes da exportação e conferência do retorno importado.
- Cada mudança de leiaute ou de glossário exige novo exportador versionado.
- Se o governo publicar Web Service no futuro, a automação exige novo ADR.

### O que muda no código

- `SinirIntegrationService` deixa de ser cliente de sincronização e é substituído por exportador do leiaute oficial e
  importador do retorno; saem as propriedades `sinir.enabled` e `sinir.base-url`.
- `SinirSyncStatus` (PENDING, SYNCING, SYNCED) é substituído por estados do fluxo assistido (por exemplo: exportado,
  número informado, conferido). `MtrService` e `MtrServiceTest` acompanham a mudança.
- `V7_x` (SINISA) e `V12_x` (GRS) incluem registro de exportação e de retorno.

### O que muda no roadmap

- G7 e G8 deixam de bloquear R6 e a trilha GRS.
- R6 continua no P4 e a trilha GRS em 2028 (ADR-0001), salvo cliente pagante que antecipe.

## Alternativas consideradas

- **Integração por Web Service com envio automático.** Rejeitada: não há endpoint oficial publicado para terceiros, e
  exigiria guardar credencial do prestador ou de seus responsáveis técnicos.
- **Robô que preenche o portal com a credencial do cliente.** Rejeitada: frágil a qualquer mudança de tela, viola o
  princípio de não guardar credencial de terceiros e pode contrariar os termos de uso do portal.
- **Só relatório interno, sem formato oficial.** Rejeitada: transfere ao cliente a reformatação, que é onde ocorre a
  maior parte dos erros de declaração.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Levantar o leiaute e o glossário vigentes do SINISA e os formatos aceitos pelo SINIR | P1 (paralelo, sem bloquear) |
| Substituir `SinirIntegrationService` e `SinirSyncStatus` pelo fluxo assistido, com testes | P4 (com R6) ou antes, se a trilha GRS for antecipada |
| Exportador SINISA versionado, checagens de consistência, registro de exportação e importação do recibo | P4 (R6) |
| Exportador MTR/DMR e importação de número do MTR e CDF | 2028 (trilha GRS) ou sob demanda |
