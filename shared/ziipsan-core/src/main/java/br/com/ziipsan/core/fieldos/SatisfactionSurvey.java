package br.com.ziipsan.core.fieldos;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pesquisa de satisfacao ao final do atendimento (ED-20), registrada no
 * atendimento da ED-35 pela porta {@link FieldOsPorts.SacCasePort}.
 * A ESCALA das respostas (codigos de avaliacao) e do formulario do tenant:
 * esta entrega exige um codigo de avaliacao presente e nao inventa escala.
 */
public record SatisfactionSurvey(UUID orderId, String ratingCode, String commentOrNull, Instant answeredAt) {
    public SatisfactionSurvey {
        Objects.requireNonNull(orderId, "OS e obrigatoria");
        if (ratingCode == null || ratingCode.isBlank()) throw new IllegalArgumentException(
                "codigo de avaliacao da pesquisa e obrigatorio (escala definida no formulario do tenant)");
        Objects.requireNonNull(answeredAt, "instante da resposta e obrigatorio");
    }
}
