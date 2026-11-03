package br.com.ziipsan.platform.deployment;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uma linha de {@code platform.deployment} (P0.42, CENTRAL-ZIIP §4).
 *
 * <p>{@code commitHash} pode ser nulo: o job migrate grava nulo quando não há
 * {@code git.properties} no artefato (nunca inventa um commit). Quem apresenta o
 * registro traduz nulo para {@code "unknown"}; o banco guarda a ausência, não um
 * texto que parece commit.
 */
public record Deployment(UUID id, String version, String commitHash, String environment,
                         OffsetDateTime startedAt, OffsetDateTime finishedAt, OffsetDateTime createdAt) {
}
