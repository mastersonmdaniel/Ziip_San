package br.com.ziipsan.platform.portal.selfcare;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta dos parametros do autoatendimento ampliado (ED-38), no padrao da
 * porta {@code SigningParameters} da P3.34: o adaptador le os valores
 * vigentes do tenant (P0.19); esta tarefa NAO presume fabrica onde o plano
 * nao a da.
 *
 * <ul>
 *   <li>{@code portal.idp_govbr_enabled} — da P3.3 (fabrica false);</li>
 *   <li>{@code portal.idp_govbr_min_level} — SILVER ou GOLD (aceite da
 *       ED-38); ausencia com gov.br ligado = recusa (sem minimo presumido
 *       alem do piso por operacao da P3.3);</li>
 *   <li>{@code portal.min_assurance_by_operation} — da P3.3;</li>
 *   <li>{@code bill.peer_comparison} — DESLIGADO por padrao (aceite);</li>
 *   <li>{@code bill.peer_min_units} — agregado minimo do comparativo, por
 *       analogia a {@code gis.public_aggregation_min_units} (piso 5,
 *       classe c, so amplia): valor abaixo de 5 e recusado em
 *       {@link PeerComparison};</li>
 *   <li>{@code portal.passkey_enabled} — politica da ED-06 (fabrica false
 *       na ED-06); a APLICACAO ao portal e desta tarefa.</li>
 * </ul>
 */
public interface SelfCareParameters {

    boolean govbrEnabled(UUID tenantId);

    /** Valor cru de {@code portal.idp_govbr_min_level} (SILVER|GOLD), se houver. */
    Optional<String> govbrMinLevelRaw(UUID tenantId);

    boolean peerComparisonEnabled(UUID tenantId);

    /** Valor de {@code bill.peer_min_units}, se houver vigente. */
    Optional<Integer> peerMinUnits(UUID tenantId);

    boolean passkeyEnabled(UUID tenantId);
}
