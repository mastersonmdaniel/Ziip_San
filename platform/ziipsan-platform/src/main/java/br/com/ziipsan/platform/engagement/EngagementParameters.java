package br.com.ziipsan.platform.engagement;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta dos parametros {@code eng.*} da ED-101 (metas de consumo, comparacao
 * com pares e gamificacao), no padrao das portas de parametros do projeto
 * (P3.34/ED-38/ED-41): o adaptador le os valores vigentes do catalogo do
 * tenant (P0.19); esta tarefa NAO presume fabrica onde o aceite nao a da.
 *
 * <ul>
 *   <li>{@code eng.peer_report_enabled} — interruptor do modulo de
 *       engajamento por tenant (aceite: "opcional por tenant"). Ausencia =
 *       desligado: sem meta, sem relatorio e sem gamificacao. Tratado como
 *       o interruptor de toda a ED-101 porque e a unica chave de tenant que
 *       o aceite nomeia para a opcionalidade (decisao de execucao
 *       D-ED101-01, registrada no relatorio);</li>
 *   <li>{@code eng.peer_group_min_size} — tamanho minimo do grupo de pares
 *       desta tarefa (chave do aceite). REUTILIZA o piso de agregacao da
 *       ED-38 (O9-3): classe c, piso {@code PeerComparison.MIN_UNITS_FLOOR}
 *       (5), so amplia — valor abaixo do piso e recusado, nunca aplicado,
 *       e o minimo efetivo nunca e menor que o da ED-38
 *       ({@code bill.peer_min_units});</li>
 *   <li>{@code eng.gamification_milestones} — <b>chave NOVA/proposta</b>:
 *       marcos da gamificacao definidos pelo tenant, no formato proposto de
 *       {@link GamificationMilestones}. SEM valor de fabrica: ausente, a
 *       gamificacao nao concede marco nenhum (nunca se inventa ponto,
 *       limiar ou regra);</li>
 *   <li>{@code bill.invoice_template} — modelo de fatura aprovado pela
 *       agencia/ERI (chave do aceite; registrada no catalogo core V3 como
 *       tipo VERSION, classe d, sem fabrica — "modelo de fatura aprovado
 *       pela ERI", Lei 11.445/2007, art. 39, p.u., com o bloco do
 *       fornecedor travado pelo CDC, art. 42-A; aprovacao por conteudo
 *       na P2.32). O adaptador resolve a versao vigente do modelo (a
 *       entidade de modelos da P2.5) e devolve o TEXTO dela. Ausente =
 *       nenhuma mensagem impressa e gerada.</li>
 * </ul>
 *
 * As chaves {@code bill.peer_comparison} e {@code bill.peer_min_units} NAO
 * sao redefinidas aqui: sao da ED-38 e consumidas pela porta
 * {@code SelfCareParameters} da propria ED-38 (ver
 * {@link PeerReportService}).
 */
public interface EngagementParameters {

    /** Valor de {@code eng.peer_report_enabled}; ausencia = desligado. */
    boolean peerReportEnabled(UUID tenantId);

    /** Valor de {@code eng.peer_group_min_size}, se houver vigente. */
    Optional<Integer> peerGroupMinSize(UUID tenantId);

    /** Valor cru de {@code eng.gamification_milestones}, se houver vigente. */
    Optional<String> gamificationMilestonesRaw(UUID tenantId);

    /** Texto vigente do modelo {@code bill.invoice_template}, se houver. */
    Optional<String> invoiceTemplate(UUID tenantId);
}
