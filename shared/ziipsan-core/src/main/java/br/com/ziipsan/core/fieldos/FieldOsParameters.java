package br.com.ziipsan.core.fieldos;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Parametros da ED-20 (OS em campo), SEMPRE lidos do tenant — nenhum valor
 * de negocio e constante nesta classe:
 * <pre>
 *   field.track_only_in_shift            TRAVADO em verdadeiro pelo aceite:
 *                                        qualquer leitura diferente de true
 *                                        e recusada por {@link #requireTrackOnlyInShift}
 *                                        (o rastreamento fora da jornada nao
 *                                        pode ser ligado por configuracao).
 *   field.track_retention_days           retencao da trilha GPS; SO REDUZ em
 *                                        relacao ao teto da plataforma
 *                                        (ver {@link #validateRetentionDays}).
 *   field.photo_blur_faces               desfoque de rostos nas fotos de campo.
 *   so.appointment_window_hours          janela de atendimento combinada (horas).
 *   so.require_customer_acceptance_by_type  aceite do usuario por tipo de OS:
 *                                        SIGNATURE | CODE | NONE (mapa do tenant;
 *                                        tipo fora do mapa = [A DEFINIR], recusa).
 * </pre>
 * As tres chaves field.* e as duas so.* sao nomeadas pelo proprio acceptance
 * da ED-20. Nenhuma tem valor de fabrica nesta entrega: ausencia = recusa
 * nomeada, nunca valor presumido (regra da cascata).
 *
 * <p><b>Contrato de ausencia (correcao D-3):</b> os interruptores
 * {@link #trackOnlyInShift} e {@link #photoBlurFaces} devolvem
 * {@code Optional<Boolean>} — NAO {@code boolean} primitivo — para que a
 * AUSENCIA de valor vigente seja representavel e distinta de um
 * {@code false} explicito do tenant. A implementacao de integracao DEVE
 * devolver {@code Optional.empty()} quando nao houver valor vigente e
 * NUNCA um default {@code false} por ausencia: um default silencioso
 * desligaria o desfoque de rostos sem ninguem perceber. Os consumidores
 * resolvem o interruptor SOMENTE por {@link #requireTrackOnlyInShift(Optional)}
 * e {@link #requirePhotoBlurFaces(Optional)}, que RECUSAM (excecao nomeada)
 * na ausencia — o mesmo desenho da trava de jornada, que ja recusava o
 * {@code false} para {@code track_only_in_shift}. {@code false} explicito
 * de {@code photo_blur_faces} e legitimo (o tenant desligou o desfoque);
 * ausencia, nao.
 */
public interface FieldOsParameters {

    String TRACK_ONLY_IN_SHIFT = "field.track_only_in_shift";
    String TRACK_RETENTION_DAYS = "field.track_retention_days";
    String PHOTO_BLUR_FACES = "field.photo_blur_faces";
    String APPOINTMENT_WINDOW_HOURS = "so.appointment_window_hours";
    String REQUIRE_ACCEPTANCE_BY_TYPE = "so.require_customer_acceptance_by_type";

    /** Leitura crua do interruptor; vazio = sem valor vigente. A trava e aplicada por {@link #requireTrackOnlyInShift}. */
    Optional<Boolean> trackOnlyInShift(UUID tenantId);

    Optional<Integer> trackRetentionDays(UUID tenantId);

    /** Leitura crua do interruptor de desfoque; vazio = sem valor vigente (NAO equivale a falso). Resolver por {@link #requirePhotoBlurFaces(Optional)}. */
    Optional<Boolean> photoBlurFaces(UUID tenantId);

    Optional<Integer> appointmentWindowHours(UUID tenantId);

    /** Modo de aceite exigido para o tipo de OS; vazio = tipo sem definicao no tenant. */
    Optional<CustomerAcceptance.Mode> requiredAcceptance(UUID tenantId, String orderTypeCode);

    /** Trava do aceite: rastrear so durante a jornada nao e configuravel para falso. */
    static void requireTrackOnlyInShift(boolean configuredValue) {
        if (!configuredValue) {
            throw new IllegalStateException(TRACK_ONLY_IN_SHIFT + " esta travado em verdadeiro pelo aceite da ED-20: "
                    + "rastreamento fora da jornada nao pode ser habilitado por parametro");
        }
    }

    /**
     * Resolucao do interruptor da jornada a partir da leitura crua (D-3):
     * ausencia (Optional vazio) e RECUSADA com motivo nomeado — ausencia
     * nao e falso nem verdadeiro presumido; presente, vale a trava acima.
     */
    static boolean requireTrackOnlyInShift(Optional<Boolean> configuredValue) {
        Boolean v = configuredValue == null ? null : configuredValue.orElse(null);
        if (v == null) {
            throw new IllegalStateException(TRACK_ONLY_IN_SHIFT + " nao configurado no tenant: "
                    + "sem valor explicito, o rastreamento nao opera (ausencia nao e um default)");
        }
        requireTrackOnlyInShift(v.booleanValue());
        return v;
    }

    /**
     * Resolucao do interruptor de desfoque a partir da leitura crua (D-3):
     * ausencia (Optional vazio) e RECUSADA com motivo nomeado pelo
     * consumidor do desfoque — sem parametro explicito ele nao opera, em
     * vez de presumir falso e desligar o desfoque silenciosamente.
     * {@code false} explicito e devolvido como esta (desfoque desligado
     * por decisao do tenant).
     */
    static boolean requirePhotoBlurFaces(Optional<Boolean> configuredValue) {
        Boolean v = configuredValue == null ? null : configuredValue.orElse(null);
        if (v == null) {
            throw new IllegalStateException(PHOTO_BLUR_FACES + " nao configurado no tenant: "
                    + "sem valor explicito, o registro de foto nao opera (ausencia nao e falso; "
                    + "um default falso desligaria o desfoque de rostos silenciosamente)");
        }
        return v;
    }

    /** Conveniencia: resolve o desfoque direto da fonte de parametros do tenant. */
    static boolean resolvePhotoBlurFaces(FieldOsParameters parameters, UUID tenantId) {
        java.util.Objects.requireNonNull(parameters, "parametros do tenant sao obrigatorios");
        java.util.Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        return requirePhotoBlurFaces(parameters.photoBlurFaces(tenantId));
    }

    /** Conveniencia: resolve a trava de jornada direto da fonte de parametros do tenant. */
    static boolean resolveTrackOnlyInShift(FieldOsParameters parameters, UUID tenantId) {
        java.util.Objects.requireNonNull(parameters, "parametros do tenant sao obrigatorios");
        java.util.Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        return requireTrackOnlyInShift(parameters.trackOnlyInShift(tenantId));
    }

    /**
     * Retencao so reduz: o valor do tenant precisa ser positivo e nao pode
     * exceder o teto da plataforma informado pelo chamador (o teto vem do
     * catalogo vigente; nenhuma fonte fixa um numero nesta entrega, logo
     * nenhum numero e embutido aqui).
     */
    static int validateRetentionDays(int tenantDays, int platformCeilingDays) {
        if (platformCeilingDays <= 0) throw new IllegalArgumentException("teto de retencao da plataforma deve ser positivo");
        if (tenantDays <= 0) throw new IllegalArgumentException(TRACK_RETENTION_DAYS + " deve ser positivo");
        if (tenantDays > platformCeilingDays) {
            throw new IllegalArgumentException(TRACK_RETENTION_DAYS + "=" + tenantDays + " excede o teto da plataforma ("
                    + platformCeilingDays + "): a retencao da trilha so reduz");
        }
        return tenantDays;
    }

    static int requireAppointmentWindowHours(Optional<Integer> value) {
        Integer v = value.orElseThrow(() -> new IllegalStateException(
                APPOINTMENT_WINDOW_HOURS + " nao configurado no tenant: sem janela combinada, o aviso 'a caminho' nao estima janela"));
        if (v <= 0) throw new IllegalArgumentException(APPOINTMENT_WINDOW_HOURS + " deve ser positivo");
        return v;
    }

    static Map<String, CustomerAcceptance.Mode> copyAcceptanceMap(Map<String, CustomerAcceptance.Mode> map) {
        return map == null ? Map.of() : Map.copyOf(map);
    }
}
