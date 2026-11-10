package br.com.ziipsan.grs.mtr.collection;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E9/E10.
import br.com.ziipsan.core.fieldos.Tracking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operação de resíduos sólidos do tenant-coletor (OPE-31, ED-104 E9) — núcleo puro.
 *
 * <p>Consumo por portas, sem duplicar vizinhos:
 * <ul>
 *   <li>roteiro: a porta {@link RoutePlanningPort} é implementada na integração sobre o
 *       planeador da ED-65 ({@code GisWasteRoutePlanner}); aqui só se consome a ordem devolvida;</li>
 *   <li>GPS: os pontos da execução são validados pelo portão da ED-20
 *       ({@link Tracking#ingest}) — aviso ao empregado, jornada e acesso da chefia são da ED-20;</li>
 *   <li>frequência por sector: chave {@code waste.collection_frequency_by_sector} (nome exacto
 *       do aceite) — sector sem frequência configurada recusa o plano;</li>
 *   <li>pesagem: chave {@code waste.weighing_required} (nome exacto do aceite) — com a chave
 *       activa, a execução sem ticket de balança não fecha.</li>
 * </ul>
 * Cooperativas entram como parceiros/destinadores registados; grandes geradores como geradores
 * registados; RCC/RSS pelos esquemas do E5 (o tipo de fluxo é declarado, nunca deduzido).
 */
public final class CollectionOperation {

    public static final String KEY_FREQUENCY_BY_SECTOR = "waste.collection_frequency_by_sector";
    public static final String KEY_WEIGHING_REQUIRED = "waste.weighing_required";

    private CollectionOperation() {
    }

    /** Porta de roteirização — na integração, sobre o GisWasteRoutePlanner da ED-65. */
    public interface RoutePlanningPort {
        /** Ordem de visita das paragens do sector; vazia/lista sem a paragem = problema a reportar. */
        List<String> planRoute(String sectorId, List<String> stopIds);
    }

    /** Porta de trilha GPS — na integração, sobre a ingestão governada da ED-20. */
    public interface GpsTrackPort {
        Tracking.TrackPoint ingest(Tracking.TrackPoint point, Tracking.Shift shift,
                                   boolean trackOnlyInShiftConfigured, boolean employeeNoticeAcknowledged);
    }

    public enum FlowKind { REGULAR, SELECTIVE, RCC, RSS, LARGE_GENERATOR }

    public enum PartnerKind { COOPERATIVE, DESTINATION, TRANSPORTER }

    /** Sector de coleta com a sua frequência declarada (dias por semana, 1..7). */
    public record Sector(String sectorId, String name) {
        public Sector {
            if (sectorId == null || sectorId.isBlank()) {
                throw new IllegalArgumentException("sector sem identificador");
            }
        }
    }

    /** Ticket de balança: número, instante e massa líquida pesada. */
    public record WeighingTicket(String ticketNumber, Instant weighedAt, BigDecimal netKg) {
        public WeighingTicket {
            if (ticketNumber == null || ticketNumber.isBlank()) {
                throw new IllegalArgumentException("ticket de balança sem número");
            }
            if (weighedAt == null) {
                throw new IllegalArgumentException("ticket de balança sem instante da pesagem");
            }
            if (netKg == null || netKg.signum() <= 0) {
                throw new IllegalArgumentException("ticket de balança sem massa líquida positiva");
            }
        }
    }

    /** Parceiro registado da operação (cooperativa de catadores, destinador, transportador). */
    public record Partner(UUID id, PartnerKind kind, String name) {
        public Partner {
            if (id == null || kind == null || name == null || name.isBlank()) {
                throw new IllegalArgumentException("parceiro exige id, tipo e nome");
            }
        }
    }

    /** Grande gerador registado atendido pela operação (não é um MTR: é cadastro da coleta). */
    public record LargeGenerator(UUID id, String name, FlowKind flow) {
        public LargeGenerator {
            if (id == null || name == null || name.isBlank()) {
                throw new IllegalArgumentException("grande gerador exige id e nome");
            }
            if (flow == null) {
                throw new IllegalArgumentException("grande gerador exige o fluxo declarado");
            }
        }
    }

    /**
     * Frequência por sector a partir do parâmetro {@code waste.collection_frequency_by_sector}:
     * mapa sectorId → dias/semana (JSON do parâmetro, lido por quem chama). Sector ausente ou
     * frequência fora de 1..7 recusa o plano — frequência não se presume.
     */
    public static int requireFrequency(Map<String, Integer> frequencyBySector, String sectorId) {
        Integer days = frequencyBySector == null ? null : frequencyBySector.get(sectorId);
        if (days == null) {
            throw new IllegalStateException("sector " + sectorId + " sem frequência em "
                    + KEY_FREQUENCY_BY_SECTOR + ": o plano de coleta não nasce sem frequência declarada");
        }
        if (days < 1 || days > 7) {
            throw new IllegalArgumentException("frequência do sector " + sectorId + " fora de 1..7 dias/semana: " + days);
        }
        return days;
    }

    /** Plano operacional de um sector: frequência validada + roteiro ordenado pela porta da ED-65. */
    public record CollectionPlan(String sectorId, int frequencyDaysPerWeek, FlowKind flow,
                                 List<String> orderedStopIds) {
        public CollectionPlan {
            orderedStopIds = orderedStopIds == null ? List.of() : List.copyOf(orderedStopIds);
        }
    }

    public static CollectionPlan plan(Sector sector, FlowKind flow, List<String> stopIds,
                                      Map<String, Integer> frequencyBySector, RoutePlanningPort routes) {
        int frequency = requireFrequency(frequencyBySector, sector.sectorId());
        if (stopIds == null || stopIds.isEmpty()) {
            throw new IllegalArgumentException("plano sem paragens não é plano");
        }
        List<String> ordered = routes.planRoute(sector.sectorId(), stopIds);
        if (ordered == null || ordered.size() != stopIds.size() || !ordered.containsAll(stopIds)) {
            throw new IllegalStateException("roteiro devolvido pela porta da ED-65 não cobre as paragens do sector "
                    + sector.sectorId());
        }
        return new CollectionPlan(sector.sectorId(), frequency, flow, ordered);
    }

    /** Execução de uma coleta: plano + tickets de balança + pontos GPS já ingeridos pela ED-20. */
    public static final class Execution {
        private final CollectionPlan plan;
        private final List<WeighingTicket> tickets = new ArrayList<>();
        private final List<Tracking.TrackPoint> track = new ArrayList<>();
        private boolean closed;

        public Execution(CollectionPlan plan) {
            if (plan == null) {
                throw new IllegalArgumentException("execução exige plano");
            }
            this.plan = plan;
        }

        public void addTicket(WeighingTicket ticket) {
            if (closed) {
                throw new IllegalStateException("execução já fechada");
            }
            tickets.add(java.util.Objects.requireNonNull(ticket, "ticket é obrigatório"));
        }

        /** O ponto só entra depois de passar pelo portão de ingestão da ED-20 (porta). */
        public void addTrackPoint(GpsTrackPort gps, Tracking.TrackPoint point, Tracking.Shift shift,
                                  boolean trackOnlyInShiftConfigured, boolean employeeNoticeAcknowledged) {
            if (closed) {
                throw new IllegalStateException("execução já fechada");
            }
            track.add(gps.ingest(point, shift, trackOnlyInShiftConfigured, employeeNoticeAcknowledged));
        }

        /**
         * Fecho da execução. Com {@code waste.weighing_required} activo, sem ticket de balança
         * a execução NÃO fecha. Inactivo, fecha sem ticket (a massa total fica zero e declarada).
         */
        public ExecutionSummary close(boolean weighingRequired) {
            if (closed) {
                throw new IllegalStateException("execução já fechada");
            }
            if (weighingRequired && tickets.isEmpty()) {
                throw new IllegalStateException("pesagem exigida por " + KEY_WEIGHING_REQUIRED
                        + ": execução sem ticket de balança não fecha");
            }
            closed = true;
            BigDecimal total = tickets.stream().map(WeighingTicket::netKg)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new ExecutionSummary(plan.sectorId(), plan.flow(), tickets.size(), total, track.size());
        }

        public CollectionPlan plan() {
            return plan;
        }

        public List<WeighingTicket> tickets() {
            return List.copyOf(tickets);
        }
    }

    public record ExecutionSummary(String sectorId, FlowKind flow, int ticketCount,
                                   BigDecimal totalWeighedKg, int trackPointCount) {
    }

    /**
     * Indicadores da operação (E10). Só entram indicadores cuja fórmula é aritmética directa
     * sobre os dados da operação e está declarada aqui:
     * <ul>
     *   <li>{@code weighedKgPerExecution} = massa pesada total / nº de execuções;</li>
     *   <li>{@code weighingCoverage} = execuções com ticket / nº de execuções (quando a pesagem
     *       é exigida, o fecho já garante 1 — o indicador mede a operação real, não a regra);</li>
     *   <li>{@code executionsPerSector} = contagem por sector.</li>
     * </ul>
     * As fórmulas oficiais dos indicadores das NR ANA 7/2024 e NR ANA 14/2025 são
     * [SEM FONTE] nesta sessão — o texto integral das resoluções não foi lido (o levantamento
     * confirma a existência das normas e declara as fórmulas NÃO CONFIRMADAS) — pendência
     * P-ED104-07. Nenhum indicador com nome ou fórmula da ANA é inventado.
     */
    public static Map<String, Object> operationalIndicators(List<ExecutionSummary> summaries) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (summaries == null || summaries.isEmpty()) {
            out.put("executions", 0);
            return out;
        }
        BigDecimal totalKg = summaries.stream().map(ExecutionSummary::totalWeighedKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long withTickets = summaries.stream().filter(s -> s.ticketCount() > 0).count();
        Map<String, Long> perSector = new LinkedHashMap<>();
        for (ExecutionSummary s : summaries) {
            perSector.merge(s.sectorId(), 1L, Long::sum);
        }
        out.put("executions", summaries.size());
        out.put("totalWeighedKg", totalKg.toPlainString());
        out.put("weighedKgPerExecution",
                totalKg.divide(BigDecimal.valueOf(summaries.size()), 3, java.math.RoundingMode.HALF_UP).toPlainString());
        out.put("weighingCoverage",
                BigDecimal.valueOf(withTickets)
                        .divide(BigDecimal.valueOf(summaries.size()), 4, java.math.RoundingMode.HALF_UP).toPlainString());
        out.put("executionsPerSector", perSector);
        out.put("anaIndicators", "[SEM FONTE] fórmulas das NR ANA 7/2024 e 14/2025 não lidas (P-ED104-07)");
        return out;
    }
}
