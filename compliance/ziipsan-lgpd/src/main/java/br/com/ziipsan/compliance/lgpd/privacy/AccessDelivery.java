package br.com.ziipsan.compliance.lgpd.privacy;

import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrega da resposta e do arquivo de acesso ou portabilidade NO PORTAL (aceite da
 * P3.40), com registro no {@code personal_data_access_log} (tabela da {@code V1_6},
 * P0.11): toda entrega nasce de um pedido com identidade verificada ({@link IdentityGate})
 * e gera a entrada de log da entrega — sem log, a entrega não se declara feita.
 *
 * <p>A entrada de log NÃO carrega o conteúdo entregue (o log registra que houve entrega,
 * a quem, de quê e quando — o padrão do catálogo de classificação: nunca o documento em
 * claro). A correspondência campo a campo com as colunas da {@code V1_6} é da integração
 * (a DDL da {@code V1_6} não foi lida nesta frente — ver relatório).
 */
public final class AccessDelivery {

    private AccessDelivery() {
    }

    /** Tipo de entrega ao titular. */
    public enum Kind {
        /** Resposta do pedido (declaração/resumo) entregue no portal. */
        RESPONSE,
        /** Arquivo de acesso (dados do titular). */
        ACCESS_FILE,
        /** Arquivo de portabilidade (formato estruturado, art. 18, V). */
        PORTABILITY_FILE
    }

    /** Quem recebe: o próprio titular ou o representante (P1.41). */
    public enum ActorKind {
        HOLDER, REPRESENTATIVE
    }

    /** Entrada do {@code personal_data_access_log} gerada pela entrega. */
    public record AccessLogEntry(UUID requestId, UUID subjectId, UUID actorId, ActorKind actorKind,
                                 Kind kind, Instant deliveredAt) {

        public AccessLogEntry {
            Objects.requireNonNull(requestId, "requestId é obrigatório");
            Objects.requireNonNull(subjectId, "subjectId é obrigatório");
            Objects.requireNonNull(actorId, "actorId é obrigatório");
            Objects.requireNonNull(actorKind, "actorKind é obrigatório");
            Objects.requireNonNull(kind, "kind é obrigatório");
            Objects.requireNonNull(deliveredAt, "deliveredAt é obrigatório");
        }

        /** JSON da entrada, só pela classe {@code Json} do core. */
        public String toJson() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("requestId", requestId.toString());
            map.put("subjectId", subjectId.toString());
            map.put("actorId", actorId.toString());
            map.put("actorKind", actorKind.name());
            map.put("kind", kind.name());
            map.put("deliveredAt", deliveredAt.toString());
            return Json.write(map);
        }
    }

    /** Entrega realizada: o que foi entregue + a entrada de log que a prova. */
    public record Delivery(UUID requestId, Kind kind, Instant deliveredAt, AccessLogEntry logEntry) {
    }

    /**
     * Entrega ao próprio titular: exige identidade verificada antes de qualquer coisa
     * E que o chamador seja o titular DO PEDIDO (vínculo chamador × titular — quem
     * souber o {@code requestId} de outro titular não dispara a entrega).
     *
     * @param requestSubjectId titular gravado no pedido
     * @param callerSubjectId  titular da sessão do portal ({@code X-Portal-Subject})
     */
    public static Delivery deliverToHolder(UUID requestId, UUID requestSubjectId,
                                           UUID callerSubjectId, Instant verifiedAt,
                                           Kind kind, Instant deliveredAt) {
        IdentityGate.requireCallerMatchesSubject(requestSubjectId, callerSubjectId);
        IdentityGate.requireVerified(verifiedAt);
        Objects.requireNonNull(requestId, "requestId é obrigatório");
        Objects.requireNonNull(kind, "kind é obrigatório");
        Objects.requireNonNull(deliveredAt, "deliveredAt é obrigatório");
        AccessLogEntry entry = new AccessLogEntry(requestId, requestSubjectId, callerSubjectId,
                ActorKind.HOLDER, kind, deliveredAt);
        return new Delivery(requestId, kind, deliveredAt, entry);
    }

    /**
     * Entrega a representante: exige que o chamador seja o representante registado,
     * que a representação seja sobre o titular DO PEDIDO, verificação + representação
     * ACTIVE com prova, vigente e com poder REQUEST na data da entrega; o log registra
     * o representante como ator (o acesso delegado é registado — P3.3/P1.41).
     *
     * @param requestSubjectId titular gravado no pedido
     * @param callerId         quem chama a entrega (tem de ser o representante)
     */
    public static Delivery deliverToRepresentative(UUID requestId, UUID requestSubjectId,
                                                   UUID callerId,
                                                   IdentityGate.Representation representation,
                                                   Instant verifiedAt, Kind kind,
                                                   Instant deliveredAt, java.time.LocalDate today) {
        Objects.requireNonNull(representation, "representation é obrigatória");
        IdentityGate.requireCallerIsRepresentative(representation, requestSubjectId, callerId);
        IdentityGate.requireRepresentative(representation, verifiedAt, today);
        Objects.requireNonNull(requestId, "requestId é obrigatório");
        Objects.requireNonNull(kind, "kind é obrigatório");
        Objects.requireNonNull(deliveredAt, "deliveredAt é obrigatório");
        AccessLogEntry entry = new AccessLogEntry(requestId, representation.subjectId(),
                representation.representativeId(), ActorKind.REPRESENTATIVE, kind, deliveredAt);
        return new Delivery(requestId, kind, deliveredAt, entry);
    }
}
