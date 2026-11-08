package br.com.ziipsan.intelligence.gis.asset;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Programa de exercicio de valvulas, teste de hidrantes e descarga
 * de rede por criticidade — OPE-09. E o programa que PRODUZ o dado
 * de operabilidade que a GIS-11 (ED-54) consome: pendencia
 * P-ED54-05 ("a operabilidade real das valvulas e dado da ED-56").
 *
 * <p>Parametros:
 * <ul>
 *   <li>{@code maint.valve_exercise_years_by_class} — mapa
 *       criticidade -> anos. ESTE tem referencia no proprio aceite:
 *       "referencia da AWWA: criticas a cada ano, demais a cada
 *       cinco anos" — por isso {@link #AWWA_REFERENCE_YEARS} e o
 *       valor de fabrica registrado no SQL irmao (classe CRITICAL=1,
 *       demais classes=5 quando o tenant nao detalha);</li>
 *   <li>{@code maint.hydrant_test_months} — meses entre testes de
 *       hidrante: o aceite NAO fixa numero, logo SEM valor de
 *       fabrica; sem o parametro do tenant, hidrante nenhum ganha
 *       vencimento por calendario.</li>
 * </ul>
 *
 * <p>Resultados do exercicio: numero de voltas para fechar, estado
 * encontrado e deixado; do hidrante: vazao e pressao e a data do
 * ultimo teste. Valvula que nao fecha: atualiza
 * {@code operational_status} para inoperavel (via
 * {@link AssetPorts.ValveOperabilityPort}, lido pela ED-54) e entra
 * na FILA DE SUBSTITUICAO. Indicador: percentual de valvulas
 * inoperantes do inventario.
 */
public final class ValveHydrantProgram {

    private ValveHydrantProgram() {
    }

    /** Referencia AWWA citada no aceite: critica = 1 ano; demais = 5 anos. */
    public static final int AWWA_CRITICAL_YEARS = 1;
    public static final int AWWA_DEFAULT_YEARS = 5;
    public static final String CRITICAL = "CRITICAL";

    /** Anos de exercicio da classe: mapa do tenant; sem a classe, a referencia AWWA (5). */
    public static int exerciseYearsFor(String criticalityClass, Map<String, Integer> yearsByClass) {
        if (yearsByClass != null) {
            Integer v = yearsByClass.get(criticalityClass);
            if (v != null) {
                if (v <= 0) throw new IllegalArgumentException("anos de exercicio devem ser positivos: " + criticalityClass);
                return v;
            }
        }
        return CRITICAL.equals(criticalityClass) ? AWWA_CRITICAL_YEARS : AWWA_DEFAULT_YEARS;
    }

    /** Proximo exercicio devido: ultimo exercicio (ou entrada em operacao) + anos da classe. */
    public static Optional<LocalDate> nextExerciseDue(LocalDate lastExercisedOn, LocalDate inOperationSince,
                                                      String criticalityClass, Map<String, Integer> yearsByClass) {
        LocalDate base = lastExercisedOn != null ? lastExercisedOn : inOperationSince;
        if (base == null) return Optional.empty();
        return Optional.of(base.plusYears(exerciseYearsFor(criticalityClass, yearsByClass)));
    }

    public static boolean exerciseDue(LocalDate lastExercisedOn, LocalDate inOperationSince,
                                      String criticalityClass, Map<String, Integer> yearsByClass,
                                      LocalDate referenceDate) {
        return nextExerciseDue(lastExercisedOn, inOperationSince, criticalityClass, yearsByClass)
                .map(due -> !referenceDate.isBefore(due)).orElse(false);
    }

    /** Hidrante: sem {@code maint.hydrant_test_months} do tenant, sem vencimento (nunca inventado). */
    public static Optional<LocalDate> nextHydrantTestDue(LocalDate lastTestedOn, Integer testMonths) {
        if (testMonths == null) return Optional.empty();
        if (testMonths <= 0) throw new IllegalArgumentException("maint.hydrant_test_months deve ser positivo");
        if (lastTestedOn == null) return Optional.empty();
        return Optional.of(lastTestedOn.plusMonths(testMonths));
    }

    /** Resultado de um exercicio de valvula (campos do aceite). */
    public record ValveExerciseResult(UUID valveAssetId, LocalDateTime exercisedAt,
                                      double turnsToClose, String foundPosition, String leftPosition,
                                      boolean closesFully, UUID workOrderId) {
        public ValveExerciseResult {
            Objects.requireNonNull(valveAssetId, "exercicio sem valvula");
            Objects.requireNonNull(exercisedAt, "exercicio sem instante");
            if (turnsToClose < 0) throw new IllegalArgumentException("voltas para fechar negativas");
            if (!List.of("OPEN", "CLOSED", "PARTIAL").contains(foundPosition))
                throw new IllegalArgumentException("estado encontrado invalido: " + foundPosition);
            if (!List.of("OPEN", "CLOSED", "PARTIAL").contains(leftPosition))
                throw new IllegalArgumentException("estado deixado invalido: " + leftPosition);
            if (closesFully && turnsToClose == 0)
                throw new IllegalArgumentException("valvula que fecha registra ao menos a medicao de voltas (>0) ou 0 declarado impossivel");
        }

        /** Operavel = fecha por completo no exercicio. */
        public boolean operable() { return closesFully; }
    }

    /** Resultado de um teste de hidrante (vazao e pressao medidos em campo). */
    public record HydrantTestResult(UUID hydrantAssetId, LocalDate testedOn,
                                    double flowRate, String flowUnit, double pressure, String pressureUnit,
                                    UUID workOrderId) {
        public HydrantTestResult {
            Objects.requireNonNull(hydrantAssetId, "teste sem hidrante");
            Objects.requireNonNull(testedOn, "teste sem data (a data do ultimo teste e campo do aceite)");
            if (flowRate < 0 || pressure < 0) throw new IllegalArgumentException("vazao/pressao negativas");
            if (flowUnit == null || flowUnit.isBlank() || pressureUnit == null || pressureUnit.isBlank())
                throw new IllegalArgumentException("vazao e pressao exigem a unidade medida (nenhuma unidade presumida)");
        }
    }

    /**
     * Aplica o resultado do exercicio: grava a operabilidade pela
     * porta que a ED-54 le e devolve se a valvula entra na fila de
     * substituicao (a que nao fecha). Tambem entrega o retrato com
     * frescor ({@link #assessOperability}) — o exercicio acabou de
     * acontecer, logo o estado e conhecido (OPERABLE/NOT_OPERABLE).
     */
    public static boolean applyExerciseResult(AssetPorts.ValveOperabilityPort port, UUID tenantId,
                                              ValveExerciseResult result) {
        Objects.requireNonNull(port, "porta de operabilidade e obrigatoria");
        port.applyValveExerciseResult(tenantId, result.valveAssetId(), result.operable(),
                result.leftPosition(), result.exercisedAt(), result.workOrderId());
        port.deliverOperabilitySnapshot(tenantId, new AssetPorts.ValveOperabilitySnapshot(
                result.valveAssetId(),
                result.operable() ? AssetPorts.ValveOperabilityState.OPERABLE
                        : AssetPorts.ValveOperabilityState.NOT_OPERABLE,
                result.exercisedAt().toLocalDate(), null));
        return !result.operable();
    }

    // ------------------------------------------- frescor (correcao D3)

    /**
     * Operabilidade COM FRESCOR para a ED-54 (correcao D3 da revisao
     * da ED-56; fecha o gap da P-ED54-05). Regra declarada do
     * produtor, usando SO os parametros existentes
     * ({@code maint.valve_exercise_years_by_class} — AWWA/tenant;
     * nenhum prazo novo e inventado):
     * <ul>
     *   <li>sem resultado de exercicio ({@code lastOperable == null})
     *       ou sem data de exercicio: {@code UNKNOWN} — nunca
     *       operavel por omissao;</li>
     *   <li>ultimo exercicio NAO fechou: {@code NOT_OPERABLE} (fato
     *       conhecido; o vencimento nao apaga um defeito conhecido —
     *       a valvula segue na fila de substituicao);</li>
     *   <li>ultimo exercicio fechou MAS esta vencido na data de
     *       referencia (regua da classe): {@code UNKNOWN} — o
     *       {@code true} velho nao sai como operavel;</li>
     *   <li>ultimo exercicio fechou e dentro do prazo:
     *       {@code OPERABLE}.</li>
     * </ul>
     * O retrato carrega a data do ultimo exercicio e a do vencimento,
     * para a ED-54 exibir o frescor junto do estado.
     */
    public static AssetPorts.ValveOperabilitySnapshot assessOperability(
            UUID valveAssetId, Boolean lastOperable, LocalDate lastExercisedOn,
            LocalDate inOperationSince, String criticalityClass,
            Map<String, Integer> yearsByClass, LocalDate referenceDate) {
        Objects.requireNonNull(valveAssetId, "avaliacao de operabilidade sem valvula");
        Objects.requireNonNull(referenceDate, "avaliacao de operabilidade sem data de referencia");
        Optional<LocalDate> due = nextExerciseDue(lastExercisedOn, inOperationSince,
                criticalityClass, yearsByClass);
        if (lastOperable == null || lastExercisedOn == null)
            return new AssetPorts.ValveOperabilitySnapshot(valveAssetId,
                    AssetPorts.ValveOperabilityState.UNKNOWN, lastExercisedOn, due.orElse(null));
        if (!lastOperable)
            return new AssetPorts.ValveOperabilitySnapshot(valveAssetId,
                    AssetPorts.ValveOperabilityState.NOT_OPERABLE, lastExercisedOn, due.orElse(null));
        boolean stale = due.map(d -> !referenceDate.isBefore(d)).orElse(false);
        return new AssetPorts.ValveOperabilitySnapshot(valveAssetId,
                stale ? AssetPorts.ValveOperabilityState.UNKNOWN
                        : AssetPorts.ValveOperabilityState.OPERABLE,
                lastExercisedOn, due.orElse(null));
    }

    /** Fila de substituicao: valvulas exercitadas que nao fecharam, na ordem dos resultados. */
    public static List<UUID> replacementQueue(List<ValveExerciseResult> results) {
        return results.stream().filter(r -> !r.operable()).map(ValveExerciseResult::valveAssetId).toList();
    }

    /**
     * Visao do programa de valvulas/hidrantes (OPE-09) exposta pela
     * superficie (correcao D8 da revisao da ED-56: o DTO
     * {@code ValveProgramView} deixou de ser morto — ele e montado
     * aqui e devolvido por {@code AssetOperations.valveProgram} e
     * pelo endpoint {@code GET /valve-program} do controller).
     */
    public static AssetDtos.ValveProgramView programView(
            UUID valveAssetId, LocalDate lastExercisedOn, LocalDate inOperationSince,
            String criticalityClass, Map<String, Integer> yearsByClass,
            List<ValveExerciseResult> results, long totalValves) {
        long inoperable = results.stream().filter(r -> !r.operable()).count();
        return new AssetDtos.ValveProgramView(valveAssetId,
                nextExerciseDue(lastExercisedOn, inOperationSince, criticalityClass, yearsByClass).orElse(null),
                inoperablePercent(totalValves, inoperable),
                replacementQueue(results));
    }

    /** Percentual de valvulas inoperantes; denominador zero = null (sem base). */
    public static Double inoperablePercent(long totalValves, long inoperableValves) {
        if (inoperableValves > totalValves) throw new IllegalArgumentException("inoperantes maior que o total");
        if (totalValves == 0) return null;
        return 100.0 * inoperableValves / totalValves;
    }

    /** OS agrupadas por regiao para os exercicios/testes/descargas devidos (aceite OPE-09). */
    public static List<AssetPorts.CreatedOrder> generateGroupedOrders(
            AssetPorts.WorkOrderPort orders, UUID tenantId, AssetPorts.OrderPurpose purpose,
            String targetType, List<UUID> dueAssetIds, Map<UUID, String> regionByAsset,
            LocalDate windowDate, UUID requestedBy) {
        Objects.requireNonNull(orders, "porta de OS da ED-36 e obrigatoria");
        List<AssetPorts.OrderRequest> requests = dueAssetIds.stream()
                .map(id -> new AssetPorts.OrderRequest(tenantId, purpose, targetType, id,
                        regionByAsset.get(id), windowDate.atStartOfDay(),
                        "OPE09:" + purpose.name(), requestedBy))
                .toList();
        return orders.createGrouped(requests);
    }
}
