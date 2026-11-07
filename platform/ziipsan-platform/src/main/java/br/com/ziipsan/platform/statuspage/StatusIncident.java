package br.com.ziipsan.platform.statuspage;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * P3.18: incidente exibido na página pública de status
 * ({@code support.status_incident} + {@code status_incident_component},
 * core {@code V10_4}).
 *
 * <p>O resumo pós-incidente ({@code postIncidentSummary}/
 * {@code summaryPublishedAt}) é o bloco "Resumo publicável" do modelo de
 * revisão pós-incidente da P3.11 (PLT-23, §7 do modelo): texto curto, sem
 * dado pessoal nem detalhe interno de segurança. A revisão interna
 * completa nunca chega a este record — e o resumo só existe depois da
 * publicação por {@link PostIncidentSummary#publish}.
 */
public record StatusIncident(
        String title,
        Impact impact,
        Status status,
        List<String> affectedComponentKeys,
        Instant startedAt,
        Instant resolvedAt,
        String postIncidentSummary,
        Instant summaryPublishedAt) {

    public enum Impact { MINOR, MAJOR, CRITICAL }

    public enum Status { INVESTIGATING, IDENTIFIED, MONITORING, RESOLVED }

    public StatusIncident {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Título do incidente é obrigatório");
        }
        Objects.requireNonNull(impact, "impact do incidente é obrigatório");
        Objects.requireNonNull(status, "status do incidente é obrigatório");
        Objects.requireNonNull(startedAt, "started_at do incidente é obrigatório");
        if (affectedComponentKeys == null || affectedComponentKeys.isEmpty()) {
            throw new IllegalArgumentException(
                    "Incidente sem componente afetado não tem o que publicar: affectedComponentKeys é obrigatório");
        }
        affectedComponentKeys = List.copyOf(affectedComponentKeys);
        if (status == Status.RESOLVED && resolvedAt == null) {
            throw new IllegalArgumentException("Incidente RESOLVED exige resolved_at");
        }
        if (resolvedAt != null && resolvedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("resolved_at não pode ser anterior a started_at");
        }
        if ((postIncidentSummary == null) != (summaryPublishedAt == null)) {
            throw new IllegalArgumentException(
                    "Resumo pós-incidente e sua publicação nascem juntos: post_incident_summary e "
                            + "summary_published_at são ambos nulos ou ambos presentes");
        }
        if (postIncidentSummary != null && postIncidentSummary.isBlank()) {
            throw new IllegalArgumentException("Resumo pós-incidente publicado não pode ser vazio");
        }
    }

    /** Incidente ainda aberto (qualquer estado diferente de RESOLVED). */
    public boolean isOpen() {
        return status != Status.RESOLVED;
    }

    public boolean affects(String componentKey) {
        return affectedComponentKeys.contains(componentKey);
    }

    public boolean hasPublishedSummary() {
        return postIncidentSummary != null;
    }

    /** Cópia com o resumo publicado (records são imutáveis). */
    public StatusIncident withPublishedSummary(String summary, Instant publishedAt) {
        return new StatusIncident(title, impact, status, affectedComponentKeys, startedAt, resolvedAt,
                summary, publishedAt);
    }
}
