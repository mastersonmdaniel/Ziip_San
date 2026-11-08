package br.com.ziipsan.commercial.registry.quality;

import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.BlockFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.UnitFact;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pontuacao por rota e setor, indicador de completude e tendencia da
 * qualidade cadastral (P3.32), sobre os achados do
 * {@link CadastralQualityEngine}. Calculo puro, deterministico, recomputavel
 * a mao — os testes conferem cada numero.
 *
 * <p>Formula (unica, documentada): para um grupo (rota, setor ou o tenant
 * inteiro) com {@code U} unidades avaliadas e achados somando {@code P}
 * pontos de penalidade ({@link QualitySeverity#points()}):
 * <pre>
 *   pontuacao   = max(0, 100 - (P x 10) / U)          (2 casas, HALF_UP)
 *   completude  = (unidades sem nenhum achado) / U x 100   (2 casas, HALF_UP)
 * </pre>
 * Um achado CRITICAL (10 pontos) numa rota de 10 unidades tira 10 pontos da
 * pontuacao; numa rota de 100 unidades, tira 1. Grupo sem unidades nao e
 * emitido. Unidade sem rota so entra no grupo do tenant; unidade sem quadra
 * (ou quadra sem setor) nao entra no grupo de setor.
 *
 * <p>A tendencia compara a pontuacao de duas varreduras (a serie persistida
 * em {@code cadastral_quality_scores}): delta = atual - anterior; UP se
 * positivo, DOWN se negativo, FLAT se zero (as pontuacoes tem 2 casas, o
 * delta e exato).
 */
public final class QualityScoreCalculator {

    public static final String GROUP_TENANT = "TENANT";
    public static final String GROUP_ROUTE = "ROUTE";
    public static final String GROUP_SECTOR = "SECTOR";

    /** Pontuacao de um grupo numa varredura. */
    public record GroupScore(
            String groupType,
            UUID groupId,
            int totalUnits,
            int unitsWithFindings,
            int findingsCount,
            int penaltyPoints,
            BigDecimal score,
            BigDecimal completenessPercent) {
    }

    /** Comparacao de pontuacao entre varreduras. */
    public record Trend(BigDecimal previousScore, BigDecimal currentScore,
                        BigDecimal delta, Direction direction) {

        public enum Direction { UP, DOWN, FLAT }

        public static Trend compare(BigDecimal previousScore, BigDecimal currentScore) {
            if (previousScore == null || currentScore == null) {
                throw new IllegalArgumentException("tendencia exige as duas pontuacoes");
            }
            BigDecimal delta = currentScore.subtract(previousScore).setScale(2, RoundingMode.HALF_UP);
            Direction direction = delta.signum() > 0 ? Direction.UP
                    : delta.signum() < 0 ? Direction.DOWN : Direction.FLAT;
            return new Trend(previousScore, currentScore, delta, direction);
        }
    }

    /** Pontuacao do tenant inteiro (todas as unidades nao excluidas da fotografia). */
    public GroupScore tenantScore(List<UnitFact> units, List<QualityFinding> findings) {
        List<UnitFact> evaluated = units.stream().filter(u -> !u.deleted()).toList();
        return scoreFor(GROUP_TENANT, null, evaluated, findings, true);
    }

    /** Pontuacao por rota de leitura, ordenada pelo id da rota. */
    public List<GroupScore> byRoute(List<UnitFact> units, List<QualityFinding> findings) {
        Map<UUID, List<UnitFact>> groups = new LinkedHashMap<>();
        for (UnitFact unit : units) {
            if (!unit.deleted() && unit.readingRouteId() != null) {
                groups.computeIfAbsent(unit.readingRouteId(), k -> new ArrayList<>()).add(unit);
            }
        }
        return groups.entrySet().stream()
                .map(e -> scoreFor(GROUP_ROUTE, e.getKey(), e.getValue(), findings, false))
                .sorted(Comparator.comparing(g -> g.groupId().toString()))
                .toList();
    }

    /** Pontuacao por setor comercial (via quadra da unidade), ordenada pelo id do setor. */
    public List<GroupScore> bySector(List<UnitFact> units, List<QualityFinding> findings,
                                     List<BlockFact> blocks) {
        Map<UUID, UUID> sectorByBlock = blocks.stream()
                .filter(b -> b.sectorId() != null)
                .collect(Collectors.toMap(BlockFact::id, BlockFact::sectorId, (a, b) -> a));
        Map<UUID, List<UnitFact>> groups = new LinkedHashMap<>();
        for (UnitFact unit : units) {
            if (unit.deleted() || unit.blockId() == null) {
                continue;
            }
            UUID sectorId = sectorByBlock.get(unit.blockId());
            if (sectorId != null) {
                groups.computeIfAbsent(sectorId, k -> new ArrayList<>()).add(unit);
            }
        }
        return groups.entrySet().stream()
                .map(e -> scoreFor(GROUP_SECTOR, e.getKey(), e.getValue(), findings, false))
                .sorted(Comparator.comparing(g -> g.groupId().toString()))
                .toList();
    }

    private GroupScore scoreFor(String groupType, UUID groupId, List<UnitFact> groupUnits,
                                List<QualityFinding> allFindings, boolean tenantWide) {
        int total = groupUnits.size();
        if (total == 0) {
            throw new IllegalArgumentException("grupo sem unidades nao tem pontuacao: " + groupType);
        }
        Set<UUID> unitIds = groupUnits.stream().map(UnitFact::id).collect(Collectors.toSet());
        Set<UUID> customerIds = groupUnitsCustomerIds(groupUnits);
        // Achado de cliente (unitId nulo) entra no grupo do tenant sempre;
        // em rota/setor, so quando o mapa cliente -> unidade o atribui ao
        // grupo. Achado com unitId entra no grupo da unidade dele.
        List<QualityFinding> groupFindings = allFindings.stream()
                .filter(f -> f.unitId() != null
                        ? unitIds.contains(f.unitId())
                        : tenantWide || (f.customerId() != null
                                && customerIds.contains(f.customerId())))
                .toList();
        int penalty = groupFindings.stream().mapToInt(f -> f.severity().points()).sum();
        Set<UUID> unitsHit = groupFindings.stream()
                .map(f -> f.unitId() != null ? f.unitId() : unitOfCustomer(groupUnits, f.customerId()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        int withFindings = unitsHit.size();
        // Precisao alta na divisao e arredondamento UMA vez, no resultado
        // (correcao R1: arredondar a divisao antes de subtrair desviava
        // 0,01 no meio-exato — P=1, U=16 dava 99,37 em vez dos 99,38 da
        // formula 100 - 0,625 com HALF_UP em 2 casas).
        BigDecimal score = BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(penalty * 10L)
                        .divide(BigDecimal.valueOf(total), 10, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
        if (score.signum() < 0) {
            score = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal completeness = BigDecimal.valueOf((long) (total - withFindings) * 100L)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new GroupScore(groupType, groupId, total, withFindings,
                groupFindings.size(), penalty, score, completeness);
    }

    // Os vinculos nao viajam com o UnitFact: a atribuicao de achado de
    // cliente ao grupo usa o mapa opcional abaixo. Sem mapa, achado de
    // cliente conta so no grupo do tenant quando a fiacao o atribui.
    // A calculadora e IMUTAVEL (correcao R2): o mapa entra na construcao e
    // withCustomerUnits devolve uma instancia NOVA — nada de estado mutado
    // compartilhado entre tenants/varreduras num singleton.
    private final Map<UUID, UUID> unitByCustomer;

    public QualityScoreCalculator() {
        this.unitByCustomer = Map.of();
    }

    private QualityScoreCalculator(Map<UUID, UUID> unitByCustomer) {
        this.unitByCustomer = unitByCustomer;
    }

    /**
     * Devolve uma calculadora NOVA com a atribuicao cliente -> unidade
     * (titular vigente) usada nos grupos; esta instancia nao muda.
     */
    public QualityScoreCalculator withCustomerUnits(Map<UUID, UUID> unitByCustomer) {
        return new QualityScoreCalculator(
                unitByCustomer == null ? Map.of() : Map.copyOf(unitByCustomer));
    }

    private Set<UUID> groupUnitsCustomerIds(List<UnitFact> groupUnits) {
        Set<UUID> unitIds = groupUnits.stream().map(UnitFact::id).collect(Collectors.toSet());
        return unitByCustomer.entrySet().stream()
                .filter(e -> unitIds.contains(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private UUID unitOfCustomer(List<UnitFact> groupUnits, UUID customerId) {
        UUID unitId = unitByCustomer.get(customerId);
        if (unitId == null) {
            return null;
        }
        return groupUnits.stream().anyMatch(u -> u.id().equals(unitId)) ? unitId : null;
    }
}
