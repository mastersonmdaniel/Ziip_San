package br.com.ziipsan.reporting.situation;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-93: UMA linha da tabela de estado resumida e nativa
 * ({@code ops_situation_state} no SQL pendente) — a unidade que o
 * console le. O console NUNCA le a fonte primaria: le linhas como
 * esta, gravadas pela projecao ({@link SituationProjector}).
 *
 * <p>Invariantes:
 * <ul>
 *   <li>coordenadas sao as DUAS ou NENHUMA — linha sem posicao nao
 *       ganha marcador inventado (o mapa a conta como nao
 *       posicionada, ver {@link SituationMapView});</li>
 *   <li>{@code projectedAt} anterior a {@code sourceAt} e recusado:
 *       a projecao nao antecede o fato;</li>
 *   <li>{@code detail} e retrato textual da projecao (origem, tipo,
 *       economias...), copiado defensivamente — nenhum numero aqui e
 *       calculado pelo console.</li>
 * </ul>
 */
public record SituationStateRow(UUID id, UUID tenantId, SituationItemKind kind, String itemRef,
                                String title, String statusLabel, boolean attention,
                                Double latitudeOrNull, Double longitudeOrNull,
                                Instant sourceAt, Instant projectedAt,
                                Map<String, String> detail) {

    public SituationStateRow {
        if (id == null) {
            throw new IndicatorValidationException("SITUATION_ROW_ID_REQUIRED", "id da linha de estado obrigatorio");
        }
        if (tenantId == null) {
            throw new IndicatorValidationException("TENANT_REQUIRED", "tenant e obrigatorio");
        }
        Objects.requireNonNull(kind, "tipo do item obrigatorio");
        if (itemRef == null || itemRef.isBlank()) {
            throw new IndicatorValidationException("SITUATION_ROW_REF_REQUIRED",
                    "referencia do item obrigatoria (codigo/id na fonte)");
        }
        if (title == null || title.isBlank()) {
            throw new IndicatorValidationException("SITUATION_ROW_TITLE_REQUIRED",
                    "titulo do item obrigatorio para " + itemRef);
        }
        if (statusLabel == null || statusLabel.isBlank()) {
            throw new IndicatorValidationException("SITUATION_ROW_STATUS_REQUIRED",
                    "situacao do item obrigatoria para " + itemRef);
        }
        Objects.requireNonNull(sourceAt, "instante da fonte obrigatorio");
        Objects.requireNonNull(projectedAt, "instante da projecao obrigatorio");
        if (projectedAt.isBefore(sourceAt)) {
            throw new IndicatorValidationException("SITUATION_PROJECTION_BEFORE_SOURCE",
                    "projecao em " + projectedAt + " anterior ao fato em " + sourceAt
                            + " (" + itemRef + ")");
        }
        if ((latitudeOrNull == null) != (longitudeOrNull == null)) {
            throw new IndicatorValidationException("SITUATION_COORDINATES_INCOMPLETE",
                    "coordenadas vem em par ou nao vem (" + itemRef + ")");
        }
        if (latitudeOrNull != null && (latitudeOrNull < -90.0 || latitudeOrNull > 90.0)) {
            throw new IndicatorValidationException("SITUATION_LATITUDE_INVALID",
                    "latitude fora de [-90,90]: " + latitudeOrNull);
        }
        if (longitudeOrNull != null && (longitudeOrNull < -180.0 || longitudeOrNull > 180.0)) {
            throw new IndicatorValidationException("SITUATION_LONGITUDE_INVALID",
                    "longitude fora de [-180,180]: " + longitudeOrNull);
        }
        detail = detail == null ? Map.of() : Map.copyOf(detail);
    }

    public boolean positioned() {
        return latitudeOrNull != null;
    }
}
