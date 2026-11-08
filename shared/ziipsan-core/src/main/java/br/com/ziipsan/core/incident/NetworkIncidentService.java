package br.com.ziipsan.core.incident;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servico puro da ocorrencia (ED-92) sobre portas — a fiacao
 * (JDBC/Spring) implementa as portas na integracao, como nas ondas
 * anteriores:
 * <ul>
 *   <li>{@link CodePort}: codigo unico por tenant (sequencia do
 *       banco; formato e do tenant/banco, nunca presumido aqui);</li>
 *   <li>{@link AffectedAreaPort}: area afetada CALCULADA pelo
 *       isolamento da ED-54 — devolve o retrato do inicio;</li>
 *   <li>{@link StorePort}: persistencia por tenant.</li>
 * </ul>
 * Inclui: subtipo programada da ED-35, intake do relato anonimo da
 * ED-38 (JOR-14) e o verificador de existencia do alvo
 * {@code NETWORK_INCIDENT} da P1.31 ({@link #targetExists} +
 * {@link NetworkIncidentTargetRegistration}).
 */
public final class NetworkIncidentService {

    public interface CodePort { String nextCode(UUID tenantId); }
    public interface AffectedAreaPort {
        NetworkIncident.AffectedSnapshot computeAtStart(UUID tenantId, UUID arcIdOrNull, UUID nodeIdOrNull);
    }
    public interface StorePort {
        void save(NetworkIncident incident);
        Optional<NetworkIncident> find(UUID tenantId, UUID incidentId);
        List<NetworkIncident> list(UUID tenantId);
    }

    private final CodePort codes;
    private final AffectedAreaPort areaOrNull;
    private final StorePort store;

    public NetworkIncidentService(CodePort codes, AffectedAreaPort areaOrNull, StorePort store) {
        if (codes == null || store == null) throw new IllegalArgumentException("portas de codigo e persistencia obrigatorias");
        this.codes = codes; this.areaOrNull = areaOrNull; this.store = store;
    }

    public NetworkIncident open(UUID tenantId, NetworkIncident.Type type, NetworkIncident.Origin origin,
                                LocalDateTime startedAt, UUID arcIdOrNull, UUID nodeIdOrNull) {
        NetworkIncident.AffectedSnapshot snap = areaOrNull == null
                ? NetworkIncident.AffectedSnapshot.empty()
                : areaOrNull.computeAtStart(tenantId, arcIdOrNull, nodeIdOrNull);
        NetworkIncident i = new NetworkIncident(UUID.randomUUID(), tenantId, codes.nextCode(tenantId),
                type, false, origin, startedAt, snap, null);
        store.save(i);
        return i;
    }

    /** Subtipo: interrupcao programada da ED-35 vira ocorrencia planned/PLANNED vinculada. */
    public NetworkIncident openPlanned(UUID tenantId, UUID plannedOutageId, LocalDateTime scheduledStart,
                                       NetworkIncident.AffectedSnapshot snapshotFromIsolation) {
        NetworkIncident i = new NetworkIncident(UUID.randomUUID(), tenantId, codes.nextCode(tenantId),
                NetworkIncident.Type.INTERRUPTION, true, NetworkIncident.Origin.PLANNED,
                scheduledStart, snapshotFromIsolation, plannedOutageId);
        store.save(i);
        return i;
    }

    /**
     * Relato sem login do portal (ED-38, JOR-14): o adaptador da
     * {@code NetworkOccurrencePort} da ED-38 chama aqui quando o
     * relato NAO foi agrupado. Mapeamento categoria->tipo DECLARADO
     * como decisao de execucao (a ED-38 nomeia as categorias; o tipo
     * e desta entidade): LEAK->RUPTURE, NO_WATER/LOW_PRESSURE->
     * INTERRUPTION, SEWAGE->OVERFLOW; WATER_QUALITY/OTHER nao viram
     * ocorrencia de rede automaticamente (ficam so no protocolo).
     */
    public Optional<NetworkIncident> fromAnonymousReport(UUID tenantId, String category, LocalDateTime now) {
        NetworkIncident.Type type = switch (category == null ? "" : category) {
            case "LEAK" -> NetworkIncident.Type.RUPTURE;
            case "NO_WATER", "LOW_PRESSURE" -> NetworkIncident.Type.INTERRUPTION;
            case "SEWAGE" -> NetworkIncident.Type.OVERFLOW;
            default -> null;
        };
        if (type == null) return Optional.empty();
        return Optional.of(open(tenantId, type, NetworkIncident.Origin.COMPLAINTS, now, null, null));
    }

    /**
     * Encerramento: a validacao da causa contra o catalogo vigente do
     * tenant vive na ENTIDADE ({@code NetworkIncident.close(data,
     * catalogo)} — correcao m2); o servico so repassa o catalogo.
     * Catalogo ausente/vazio continua erro explicito
     * ({@code MissingParameterException} via {@code OutageParameters}).
     */
    public void close(UUID tenantId, UUID incidentId, List<String> causeCatalogRaw,
                      NetworkIncident.ClosureData data) {
        NetworkIncident i = store.find(tenantId, incidentId)
                .orElseThrow(() -> new IllegalArgumentException("ocorrencia inexistente no tenant: " + incidentId));
        i.close(data, causeCatalogRaw);
        store.save(i);
    }

    /** Verificador de existencia do alvo NETWORK_INCIDENT (P1.31): existe = linha do tenant. */
    public boolean targetExists(UUID tenantId, UUID incidentId) {
        return store.find(tenantId, incidentId).isPresent();
    }
}
