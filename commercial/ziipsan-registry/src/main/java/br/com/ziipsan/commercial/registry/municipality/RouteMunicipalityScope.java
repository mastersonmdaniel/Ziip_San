package br.com.ziipsan.commercial.registry.municipality;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * MM-03 — Escopo de municipio de UMA rota de leitura (P-BF-07).
 *
 * <p>Metadado de SERVIDOR produzido a partir das UCs da rota pela
 * derivacao canonica do MM-01 ({@link MunicipalityDerivation} / view
 * {@code consumer_unit_municipality}) — definido UMA vez la, consumido
 * aqui por nome. O escopo ACOMPANHA a montagem do pacote de rota, mas
 * NAO entra no artefacto da ED-19 ({@code RoutePackage} /
 * {@code FieldTariffSnapshot} intocados — D-MM-04) nem em formula
 * nenhuma: dimensao e escopo, nunca formula (regra dura 3 do bloco).
 *
 * <p>A fonte da composicao da rota e {@code consumer_units.reading_route_id}
 * (V3_1). A coluna {@code reading_routes.locality_id} (V3_6, anulavel e
 * ausente na era V3_1) NAO e fonte desta derivacao: e atributo de
 * cadastro da rota, nao a composicao real dela — quem decide o
 * municipio da rota sao as UCs que ela de facto serve, pela cadeia.
 *
 * <p>Classificacao (sobre TODAS as UCs da rota):
 * <ul>
 *   <li>{@code EMPTY} — a rota nao tem nenhuma UC;</li>
 *   <li>{@code PURE} — todas as UCs derivam e derivam para exactamente
 *       UM {@code municipality_ibge} (duas localidades distintas com o
 *       mesmo IBGE continuam PURE: o municipio e o IBGE, nao a
 *       localidade);</li>
 *   <li>{@code MIXED} — tudo o resto: dois ou mais IBGEs derivados, OU
 *       alguma UC sem municipio derivavel (sem quadra, cadeia quebrada
 *       ou localidade com IBGE nulo — os estados nao-DERIVED do MM-01).
 *       Uma UC inderivavel impede CERTIFICAR a pureza, por isso
 *       {@code MIXED} le-se "nao pura": o relatorio de excecoes
 *       distingue o motivo ({@link #mixedReason()}).</li>
 * </ul>
 *
 * <p>O record e auto-guardado: o construtor recomputa o {@link Kind} a
 * partir das contagens e recusa um escopo inconsistente — nao existe
 * escopo {@code PURE} com dois municipios nem total diferente da soma
 * das contagens mais as inderivaveis.
 *
 * @param routeId              rota ({@code reading_routes.id})
 * @param tenantId             tenant dono da rota
 * @param kind                 classificacao da rota
 * @param unitsByMunicipality  UCs derivadas por {@code municipality_ibge}
 *                             (so IBGEs presentes; nunca chave nula)
 * @param underivedUnits       UCs da rota sem municipio derivavel
 * @param totalUnits           total de UCs da rota
 *                             (= soma das contagens + {@code underivedUnits})
 */
public record RouteMunicipalityScope(UUID routeId, UUID tenantId, Kind kind,
                                     Map<String, Integer> unitsByMunicipality,
                                     int underivedUnits, int totalUnits) {

    /** Classificacao de pureza da rota. */
    public enum Kind {
        /** Todas as UCs derivam para um unico municipio. */
        PURE,
        /** Nao pura: ver {@link RouteMunicipalityScope#mixedReason()}. */
        MIXED,
        /** Rota sem nenhuma UC. */
        EMPTY
    }

    /** Motivo de uma rota ser {@link Kind#MIXED} (NONE fora de MIXED). */
    public enum MixedReason {
        NONE,
        MULTIPLE_MUNICIPALITIES,
        UNDETERMINED_UNITS,
        MULTIPLE_AND_UNDETERMINED
    }

    public RouteMunicipalityScope {
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(unitsByMunicipality, "unitsByMunicipality");
        Map<String, Integer> sorted = new TreeMap<>();
        for (Map.Entry<String, Integer> e : unitsByMunicipality.entrySet()) {
            Objects.requireNonNull(e.getKey(), "municipio nulo nas contagens");
            if (e.getKey().isBlank()) {
                throw new IllegalArgumentException("municipio em branco nas contagens");
            }
            Objects.requireNonNull(e.getValue(), "contagem nula para " + e.getKey());
            if (e.getValue() <= 0) {
                throw new IllegalArgumentException(
                        "contagem deve ser > 0 para " + e.getKey() + ": " + e.getValue());
            }
            sorted.put(e.getKey(), e.getValue());
        }
        unitsByMunicipality = Collections.unmodifiableMap(sorted);
        if (underivedUnits < 0) {
            throw new IllegalArgumentException("underivedUnits negativo: " + underivedUnits);
        }
        if (totalUnits < 0) {
            throw new IllegalArgumentException("totalUnits negativo: " + totalUnits);
        }
        int derived = sorted.values().stream().mapToInt(Integer::intValue).sum();
        if (totalUnits != derived + underivedUnits) {
            throw new IllegalArgumentException("totalUnits (" + totalUnits
                    + ") != derivadas (" + derived + ") + inderivaveis (" + underivedUnits + ")");
        }
        Kind expected = classify(sorted, underivedUnits, totalUnits);
        if (kind != expected) {
            throw new IllegalArgumentException("kind " + kind + " inconsistente com as "
                    + "contagens (esperado " + expected + ")");
        }
    }

    /** A regra de classificacao, unica — usada pelo construtor e pela fabrica. */
    public static Kind classify(Map<String, Integer> unitsByMunicipality,
                                int underivedUnits, int totalUnits) {
        if (totalUnits == 0) {
            return Kind.EMPTY;
        }
        if (underivedUnits == 0 && unitsByMunicipality.size() == 1) {
            return Kind.PURE;
        }
        return Kind.MIXED;
    }

    /** Fabrica: classifica a partir das contagens e deriva o total. */
    public static RouteMunicipalityScope of(UUID routeId, UUID tenantId,
                                            Map<String, Integer> unitsByMunicipality,
                                            int underivedUnits) {
        int derived = unitsByMunicipality.values().stream()
                .mapToInt(Integer::intValue).sum();
        int total = derived + underivedUnits;
        return new RouteMunicipalityScope(routeId, tenantId,
                classify(unitsByMunicipality, underivedUnits, total),
                unitsByMunicipality, underivedUnits, total);
    }

    public boolean isPure() {
        return kind == Kind.PURE;
    }

    /** O municipio da rota — presente SOMENTE quando {@code PURE}. */
    public Optional<String> municipalityIbge() {
        return kind == Kind.PURE
                ? Optional.of(unitsByMunicipality.keySet().iterator().next())
                : Optional.empty();
    }

    /** Municipios derivados presentes (vazio em EMPTY e em MIXED so-inderivavel). */
    public Set<String> municipalities() {
        return unitsByMunicipality.keySet();
    }

    /** UCs com municipio derivavel (soma das contagens). */
    public int derivedUnits() {
        return totalUnits - underivedUnits;
    }

    /** Por que esta rota e MIXED; {@code NONE} quando nao e. */
    public MixedReason mixedReason() {
        if (kind != Kind.MIXED) {
            return MixedReason.NONE;
        }
        boolean multiple = unitsByMunicipality.size() >= 2;
        boolean undetermined = underivedUnits > 0;
        if (multiple && undetermined) {
            return MixedReason.MULTIPLE_AND_UNDETERMINED;
        }
        if (multiple) {
            return MixedReason.MULTIPLE_MUNICIPALITIES;
        }
        return MixedReason.UNDETERMINED_UNITS;
    }
}
