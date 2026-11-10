package br.com.ziipsan.commercial.registry.municipality;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * MM-01 — Derivacao canonica UC -> municipio, forma Java (D-MM-02).
 *
 * <p>Espelho em memoria da view {@code consumer_unit_municipality}
 * (MM-01__consumer_unit_municipality.sql): a cadeia e UMA so —
 * {@code consumer_units -> blocks -> commercial_sectors -> localities.municipality_ibge} —
 * com ligacoes por (tenant_id, id), como as FK compostas da V3_1.
 * Os demais pacotes MM consomem esta derivacao por nome; nao a
 * reimplementam.
 *
 * <p>Casos de borda reais do DDL (nao inventados):
 * <ul>
 *   <li>UC sem quadra ({@code block_id} NULL, permitido pela V3_1):
 *       sem derivacao — {@link Optional#empty()};</li>
 *   <li>localidade com {@code municipality_ibge} NULL (coluna anulavel
 *       na V3_1): a cadeia esta integra, mas o municipio e nulo —
 *       a derivacao devolve vazio e a view SQL devolve a linha com
 *       NULL; a distincao fica registada em
 *       {@link #deriveDetailed};</li>
 *   <li>duas localidades do mesmo tenant com o mesmo IBGE (o DDL so
 *       impoe unicidade de (tenant_id, code)): {@link #distinctMunicipalities}
 *       deduplica — um perfil por IBGE, nunca dois;</li>
 *   <li>ligacao para tenant diferente: nunca resolve (a FK composta
 *       por tenant e parte da cadeia).</li>
 * </ul>
 *
 * <p>Classe pura, sem JPA/Spring: opera sobre os identificadores e o
 * IBGE que as entidades vigentes ({@code Locality}, {@code CommercialSector},
 * {@code Block}, {@code ConsumerUnit}) ja expoem.
 */
public final class MunicipalityDerivation {

    /** Localidade: a ponta da cadeia que carrega o IBGE (V3_1). */
    public record LocalityRef(UUID tenantId, UUID id, String municipalityIbge) {
        public LocalityRef {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(id, "id");
        }
    }

    /** Setor comercial: locality_id NOT NULL (V3_1). */
    public record SectorRef(UUID tenantId, UUID id, UUID localityId) {
        public SectorRef {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(localityId, "localityId");
        }
    }

    /** Quadra: sector_id NOT NULL (V3_1). */
    public record BlockRef(UUID tenantId, UUID id, UUID sectorId) {
        public BlockRef {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sectorId, "sectorId");
        }
    }

    /** UC: block_id anulavel (V3_1) — NULL significa sem derivacao. */
    public record UnitRef(UUID tenantId, UUID id, UUID blockId) {
        public UnitRef {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(id, "id");
        }
    }

    /** Resultado detalhado: distingue cadeia quebrada de IBGE nulo. */
    public enum DerivationStatus {
        /** Cadeia integra e IBGE presente. */
        DERIVED,
        /** UC sem quadra (block_id NULL). */
        NO_BLOCK,
        /** Ligacao da cadeia inexistente para o tenant da UC. */
        BROKEN_CHAIN,
        /** Cadeia integra, mas a localidade tem municipality_ibge NULL. */
        NULL_MUNICIPALITY
    }

    public record Derivation(DerivationStatus status, String municipalityIbge) {
        public Optional<String> municipality() {
            return status == DerivationStatus.DERIVED
                    ? Optional.of(municipalityIbge) : Optional.empty();
        }
    }

    private final Map<Key, LocalityRef> localities = new LinkedHashMap<>();
    private final Map<Key, SectorRef> sectors = new LinkedHashMap<>();
    private final Map<Key, BlockRef> blocks = new LinkedHashMap<>();

    private record Key(UUID tenantId, UUID id) {}

    public MunicipalityDerivation(Iterable<LocalityRef> localities,
                                  Iterable<SectorRef> sectors,
                                  Iterable<BlockRef> blocks) {
        localities.forEach(l -> this.localities.put(new Key(l.tenantId(), l.id()), l));
        sectors.forEach(s -> this.sectors.put(new Key(s.tenantId(), s.id()), s));
        blocks.forEach(b -> this.blocks.put(new Key(b.tenantId(), b.id()), b));
    }

    /** Deriva o municipio da UC; vazio quando nao ha IBGE derivavel. */
    public Optional<String> derive(UnitRef unit) {
        return deriveDetailed(unit).municipality();
    }

    /** Derivacao com o motivo, para relatorios de excecao dos pacotes MM. */
    public Derivation deriveDetailed(UnitRef unit) {
        Objects.requireNonNull(unit, "unit");
        if (unit.blockId() == null) {
            return new Derivation(DerivationStatus.NO_BLOCK, null);
        }
        BlockRef block = blocks.get(new Key(unit.tenantId(), unit.blockId()));
        if (block == null) {
            return new Derivation(DerivationStatus.BROKEN_CHAIN, null);
        }
        SectorRef sector = sectors.get(new Key(unit.tenantId(), block.sectorId()));
        if (sector == null) {
            return new Derivation(DerivationStatus.BROKEN_CHAIN, null);
        }
        LocalityRef locality = localities.get(new Key(unit.tenantId(), sector.localityId()));
        if (locality == null) {
            return new Derivation(DerivationStatus.BROKEN_CHAIN, null);
        }
        if (locality.municipalityIbge() == null) {
            return new Derivation(DerivationStatus.NULL_MUNICIPALITY, null);
        }
        return new Derivation(DerivationStatus.DERIVED, locality.municipalityIbge());
    }

    /**
     * IBGEs distintos de um tenant a partir das suas localidades —
     * a via (a) do backfill do MM-01 (§5.1 do estudo): deduplica
     * localidades com o mesmo IBGE e ignora IBGE nulo.
     */
    public static Set<String> distinctMunicipalities(UUID tenantId,
                                                       Iterable<LocalityRef> localities) {
        Set<String> out = new LinkedHashSet<>();
        for (LocalityRef l : localities) {
            if (l.tenantId().equals(tenantId) && l.municipalityIbge() != null) {
                out.add(l.municipalityIbge());
            }
        }
        return out;
    }
}
