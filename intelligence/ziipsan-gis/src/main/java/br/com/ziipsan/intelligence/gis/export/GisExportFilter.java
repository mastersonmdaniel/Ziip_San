package br.com.ziipsan.intelligence.gis.export;

import java.time.Instant;
import java.util.List;

/**
 * Filtros de exportação do §9.3 (correção ED-67, D9): área (bbox),
 * domínio, ciclo de vida e "rede na data" — antes inexpressáveis na
 * porta {@code FeatureSource.load(UUID, String)}, que só transportava
 * a camada. A camada continua sendo o recorte da fonte; os demais
 * filtros são aplicados sobre as feições carregadas:
 *
 * <ul>
 *   <li>{@code bbox} — alguma posição da feição dentro da área;</li>
 *   <li>{@code domain} — prefixo da chave da camada (ex.: {@code water},
 *       {@code sewer});</li>
 *   <li>{@code lifecycleStatus} — igualdade com o ciclo de vida da feição;</li>
 *   <li>{@code networkAt} ("rede na data") — a feição precisa ter a
 *       propriedade {@code datetime} (ISO-8601) até a data pedida;
 *       feição sem a propriedade é excluída, nunca incluída em silêncio
 *       (mesma disciplina conservadora do {@code GisOgcQuery}). A leitura
 *       histórica completa (rede na data via GIS-04) segue na fonte,
 *       quando a integração a ligar.</li>
 * </ul>
 * Campo {@code null} = filtro ausente.
 */
public record GisExportFilter(List<Double> bbox, String domain, String lifecycleStatus, Instant networkAt) {

    public GisExportFilter {
        if (bbox != null && bbox.size() != 4)
            throw new IllegalArgumentException("filtro de área (bbox) exige 4 números (minLon,minLat,maxLon,maxLat)");
        bbox = bbox == null ? null : List.copyOf(bbox);
    }

    public boolean test(GisExportFeature f) {
        if (bbox != null && f.coordinates().stream().noneMatch(c ->
                c.get(0) >= bbox.get(0) && c.get(0) <= bbox.get(2)
             && c.get(1) >= bbox.get(1) && c.get(1) <= bbox.get(3))) return false;
        if (domain != null && !f.layerKey().startsWith(domain)) return false;
        if (lifecycleStatus != null && !lifecycleStatus.equals(f.lifecycleStatus())) return false;
        if (networkAt != null) {
            Object v = f.properties().get("datetime");
            if (v == null) return false;
            if (Instant.parse(String.valueOf(v)).isAfter(networkAt)) return false;
        }
        return true;
    }
}
