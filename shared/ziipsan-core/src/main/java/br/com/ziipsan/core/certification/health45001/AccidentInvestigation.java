package br.com.ziipsan.core.certification.health45001;

import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * INVESTIGAÇÃO DE ACIDENTE (ED-71) sobre a ocorrência única da P2.25,
 * com DADO DE SAÚDE DE ACESSO RESTRITO de verdade (LGPD; ADR-0041,
 * item 9 — prevalece o limite legal).
 *
 * <p>DESENHO DA RESTRIÇÃO (não é flag decorativa):
 * <ul>
 *   <li>o detalhe de saúde vive em componente SEPARADO
 *       ({@link HealthDetail}), que NÃO é campo da investigação: no SQL
 *       pendente ele mora em tabela própria
 *       ({@code cert_h45001_accident_health_data}), fora da tabela da
 *       investigação — consulta, exportação ou tela da investigação não
 *       carregam o dado de saúde;</li>
 *   <li>a ÚNICA porta de leitura é
 *       {@link #readHealthDetail(HealthAccessPolicy, UUID, String, Instant)}:
 *       exige política de acesso (conjunto de acessantes autorizados
 *       declarado pelo tenant/serviço), acessante autorizado e FINALIDADE
 *       declarada; acessante fora da política ou finalidade em branco
 *       lançam {@link SecurityException};</li>
 *   <li>CADA leitura autorizada é registrada (acessante, finalidade,
 *       instante) no livro de acessos da investigação — no SQL, a tabela
 *       {@code cert_h45001_health_access_log} é o espelho desse livro;</li>
 *   <li>{@link #toJson()} NUNCA inclui o detalhe: serializa só a
 *       investigação operacional e o marcador
 *       {@code healthDataPresent}.</li>
 * </ul>
 *
 * <p>A investigação operacional (sem saúde) guarda: ocorrência da P2.25,
 * OS ligada quando houver, data, descrição do evento (sem dado de saúde
 * — quem escreve separa os textos, como o desenho de tabelas impõe),
 * gravidade declarada, causa raiz, ações corretivas por referência,
 * investigador e o CAMPO de relação psicossocial
 * ({@code psychosocialRelated} + fatores declarados, espelho do campo do
 * inventário de perigos).
 */
public final class AccidentInvestigation {

    /** Gravidade declarada do evento — palavras do produto. */
    public enum Severity {
        NEAR_MISS, NO_INJURY, INJURY, FATAL
    }

    /** Detalhe de saúde: componente separado, lido só pela porta restrita. */
    public record HealthDetail(String detail, UUID recordedBy, LocalDate recordedOn) {
        public HealthDetail {
            if (detail == null || detail.isBlank() || detail.trim().length() > 4000) {
                throw new IllegalArgumentException("detail de saúde é obrigatório e tem no "
                        + "máximo 4000 caracteres");
            }
            detail = detail.trim();
            Objects.requireNonNull(recordedBy, "recordedBy do detalhe de saúde é obrigatório");
            Objects.requireNonNull(recordedOn, "recordedOn do detalhe de saúde é obrigatório");
        }
    }

    /**
     * Política de acesso ao dado de saúde: o conjunto de acessantes
     * autorizados é declarado pelo tenant/serviço (papéis de saúde
     * ocupacional/SST do tenant) — esta régua não presume quem são.
     */
    public record HealthAccessPolicy(Set<UUID> authorizedAccessorIds) {
        public HealthAccessPolicy {
            authorizedAccessorIds = authorizedAccessorIds == null ? Set.of()
                    : Set.copyOf(authorizedAccessorIds);
            if (authorizedAccessorIds.isEmpty()) {
                throw new IllegalArgumentException("política de acesso a dado de saúde sem "
                        + "nenhum acessante autorizado equivale a dado ilegível: declare ao "
                        + "menos um acessante (ou não registre detalhe de saúde)");
            }
        }

        /** Verdadeiro se o acessante está na política declarada. */
        public boolean authorizes(UUID accessorId) {
            return accessorId != null && authorizedAccessorIds.contains(accessorId);
        }
    }

    /** Um acesso registrado ao detalhe de saúde (livro de acessos). */
    public record HealthAccess(UUID accessorId, String purpose, Instant accessedAt) {
        public HealthAccess {
            Objects.requireNonNull(accessorId, "accessorId do acesso é obrigatório");
            if (purpose == null || purpose.isBlank()) {
                throw new IllegalArgumentException("purpose (finalidade) do acesso é obrigatório");
            }
            purpose = purpose.trim();
            Objects.requireNonNull(accessedAt, "accessedAt do acesso é obrigatório");
        }
    }

    private final Iso45001Activation activation;
    private final UUID occurrenceId;
    private final UUID orderId;
    private final LocalDate occurredOn;
    private final String eventDescription;
    private final Severity severity;
    private final String rootCause;
    private final List<String> correctiveActionRefs;
    private final UUID investigatorId;
    private final boolean psychosocialRelated;
    private final List<String> psychosocialFactors;
    private final HealthDetail healthDetail;
    private final List<HealthAccess> healthAccessLog = new ArrayList<>();

    public AccidentInvestigation(Iso45001Activation activation, UUID occurrenceId, UUID orderId,
                                   LocalDate occurredOn, String eventDescription, Severity severity,
                                   String rootCause, List<String> correctiveActionRefs,
                                   UUID investigatorId, boolean psychosocialRelated,
                                   List<String> psychosocialFactors, HealthDetail healthDetail) {
        this.activation = Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        this.occurrenceId = Objects.requireNonNull(occurrenceId,
                "occurrenceId (ocorrência única da P2.25) é obrigatório");
        this.orderId = orderId;
        this.occurredOn = Objects.requireNonNull(occurredOn, "occurredOn é obrigatório");
        this.eventDescription = requireText(eventDescription, "eventDescription", 2000);
        this.severity = Objects.requireNonNull(severity, "severity declarada é obrigatória");
        this.rootCause = requireText(rootCause, "rootCause", 2000);
        this.correctiveActionRefs = correctiveActionRefs == null ? List.of()
                : List.copyOf(correctiveActionRefs);
        if (this.correctiveActionRefs.isEmpty()
                || this.correctiveActionRefs.stream().anyMatch(r -> r == null || r.isBlank())) {
            throw new IllegalArgumentException("investigação sem ação corretiva referenciada "
                    + "não se encerra: correctiveActionRefs é obrigatório");
        }
        this.investigatorId = Objects.requireNonNull(investigatorId,
                "investigatorId é obrigatório");
        this.psychosocialRelated = psychosocialRelated;
        this.psychosocialFactors = psychosocialFactors == null ? List.of()
                : List.copyOf(psychosocialFactors);
        if (psychosocialRelated && this.psychosocialFactors.isEmpty()) {
            throw new IllegalArgumentException("psychosocialRelated exige os fatores "
                    + "psicossociais declarados (campo de risco psicossocial)");
        }
        if (!psychosocialRelated && !this.psychosocialFactors.isEmpty()) {
            throw new IllegalArgumentException("fatores psicossociais só valem com "
                    + "psychosocialRelated declarado");
        }
        this.healthDetail = healthDetail;
    }

    /** Nascimento da investigação: só com a norma ligada (a ativação é componente e reconfere). */
    public static AccidentInvestigation open(Iso45001Activation activation, UUID occurrenceId,
                                             UUID orderId, LocalDate occurredOn,
                                             String eventDescription, Severity severity,
                                             String rootCause, List<String> correctiveActionRefs,
                                             UUID investigatorId, boolean psychosocialRelated,
                                             List<String> psychosocialFactors,
                                             HealthDetail healthDetail) {
        return new AccidentInvestigation(activation, occurrenceId, orderId, occurredOn,
                eventDescription, severity, rootCause, correctiveActionRefs, investigatorId,
                psychosocialRelated, psychosocialFactors, healthDetail);
    }

    /**
     * ÚNICA porta de leitura do dado de saúde. Recusa (SecurityException)
     * acessante fora da política e finalidade em branco; cada leitura
     * autorizada fica registrada no livro de acessos.
     */
    public String readHealthDetail(HealthAccessPolicy policy, UUID accessorId, String purpose,
                                   Instant accessedAt) {
        Objects.requireNonNull(policy, "policy de acesso é obrigatória");
        if (healthDetail == null) {
            throw new IllegalStateException("esta investigação não tem detalhe de saúde "
                    + "registrado");
        }
        if (!policy.authorizes(accessorId)) {
            throw new SecurityException("acesso a dado de saúde negado: acessante fora da "
                    + "política declarada (LGPD; ADR-0041, item 9)");
        }
        HealthAccess access = new HealthAccess(accessorId, purpose, accessedAt);
        healthAccessLog.add(access);
        return healthDetail.detail();
    }

    /** Livro de acessos ao dado de saúde desta investigação (cópia imutável). */
    public List<HealthAccess> healthAccessLog() {
        return List.copyOf(healthAccessLog);
    }

    /** Verdadeiro se há detalhe de saúde registrado (sem revelar o conteúdo). */
    public boolean healthDataPresent() {
        return healthDetail != null;
    }

    public Iso45001Activation activation() { return activation; }
    public UUID occurrenceId() { return occurrenceId; }
    public UUID orderId() { return orderId; }
    public LocalDate occurredOn() { return occurredOn; }
    public String eventDescription() { return eventDescription; }
    public Severity severity() { return severity; }
    public String rootCause() { return rootCause; }
    public List<String> correctiveActionRefs() { return correctiveActionRefs; }
    public UUID investigatorId() { return investigatorId; }
    public boolean psychosocialRelated() { return psychosocialRelated; }
    public List<String> psychosocialFactors() { return psychosocialFactors; }

    /**
     * JSON da investigação operacional, só pela classe {@code Json} do
     * core. O detalhe de saúde NUNCA é serializado aqui: sai apenas o
     * marcador de presença.
     */
    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("occurrenceId", occurrenceId.toString());
        map.put("orderId", orderId == null ? "" : orderId.toString());
        map.put("occurredOn", occurredOn.toString());
        map.put("eventDescription", eventDescription);
        map.put("severity", severity.name());
        map.put("rootCause", rootCause);
        map.put("correctiveActionRefs", correctiveActionRefs);
        map.put("investigatorId", investigatorId.toString());
        map.put("psychosocialRelated", psychosocialRelated);
        map.put("psychosocialFactors", psychosocialFactors);
        map.put("healthDataPresent", healthDataPresent());
        return Json.write(map);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(field + " é obrigatório e tem no máximo "
                    + max + " caracteres");
        }
        return value.trim();
    }
}
