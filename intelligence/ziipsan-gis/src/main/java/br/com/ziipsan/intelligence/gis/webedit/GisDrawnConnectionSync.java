package br.com.ziipsan.intelligence.gis.webedit;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ligação nova desenhada vira ligação no cadastro, sincronizada
 * (SG-866, TR-1053): quando um item de pacote aplicado cria uma
 * {@code gis_connection} (ramal desenhado no editor), o cadastro
 * comercial (ED-13, {@code service_connections} da P2.23) ganha a
 * ligação correspondente e os dois lados guardam a referência um
 * do outro. A sincronização é por estado explícito e idempotente:
 * a chave é o id da feição GIS — reaplicar o mesmo pacote não
 * duplica a ligação.
 *
 * <p>Regras puras aqui; a escrita cruzada (módulo registry) é da
 * integração por porta ({@link DrawnConnectionPort}), no padrão
 * das portas dos pacotes vizinhos — o GIS não escreve direto na
 * tabela do cadastro comercial.
 */
public final class GisDrawnConnectionSync {

    public static final Set<String> SYNC_STATUSES = Set.of(
            "PENDING", "SYNCED", "CONFLICT", "RETIRED");

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PENDING", Set.of("SYNCED", "CONFLICT"),
            "SYNCED", Set.of("CONFLICT", "RETIRED"),
            "CONFLICT", Set.of("SYNCED", "RETIRED"),
            "RETIRED", Set.of());

    private GisDrawnConnectionSync() {
    }

    /** Porta de saída para o cadastro comercial (ED-13). */
    public interface DrawnConnectionPort {
        /** Cria (ou devolve a existente) ligação do ramal desenhado. */
        UUID ensureServiceConnection(UUID tenantId, UUID gisConnectionId,
                                     UUID consumerUnitId, String domainKey);

        /** Marca a ligação do cadastro como retirada (ramal retirado). */
        void retireServiceConnection(UUID tenantId, UUID serviceConnectionId);
    }

    /** Só ramal aplicado (não rascunho) sincroniza. */
    public static boolean shouldSync(String changeSetStatus,
                                     String featureTable, String operation) {
        return "APPLIED".equals(changeSetStatus)
                && "gis_connection".equals(featureTable)
                && ("INSERT".equals(operation) || "UPDATE".equals(operation));
    }

    /** Chave de idempotência: a própria feição GIS. */
    public static String idempotencyKey(UUID gisConnectionId) {
        if (gisConnectionId == null) {
            throw new IllegalArgumentException("feicao GIS ausente");
        }
        return "gis-connection:" + gisConnectionId;
    }

    public static boolean canTransition(String from, String to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void ensureTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    "transicao de sincronizacao invalida: " + from + " -> " + to);
        }
    }

    /** Domínio do ramal desenhado decide o tipo da ligação. */
    public static String serviceKindForDomain(String domainKey) {
        return switch (domainKey == null ? "" : domainKey) {
            case "WATER" -> "WATER";
            case "SEWER" -> "SEWER";
            default -> throw new IllegalArgumentException(
                    "dominio sem ligacao cadastral: " + domainKey);
        };
    }
}
