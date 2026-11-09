package br.com.ziipsan.core.regulatory.export;

import java.util.Objects;

/**
 * Um campo de um leiaute de saida regulatoria (ED-97).
 *
 * <p>{@code sourceStatus}: CONFIRMED quando o campo consta de fonte lida
 * neste projeto; SEM_FONTE quando a norma citada no aceite (ex.: leiaute
 * SISARIS da ARIS IN DG 1/2025) nao teve o texto do leiaute localizado
 * nas fontes — o campo entra como marcador parametrizavel nomeado, nunca
 * como leiaute oficial inventado (pendencia P-ED97-01).</p>
 */
public record LayoutField(String name, boolean required, SourceStatus sourceStatus) {
    public enum SourceStatus { CONFIRMED, SEM_FONTE }
    public LayoutField {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("campo sem nome");
        Objects.requireNonNull(sourceStatus, "sourceStatus");
    }
    public static LayoutField confirmed(String name, boolean required) {
        return new LayoutField(name, required, SourceStatus.CONFIRMED);
    }
    public static LayoutField semFonte(String name) {
        return new LayoutField(name, true, SourceStatus.SEM_FONTE);
    }
}
