package br.com.ziipsan.platform.central.complete;

import br.com.ziipsan.platform.observability.SloDefinition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * P3.15: visões das SETE abas próprias da Central completa sobre os
 * instantâneos agregados e a biblioteca de normas. Serviço puro — a
 * coleta e a renderização são a fiação da integração (padrão P2.11).
 *
 * <p>Correção pós-revisão (M3): Infraestrutura e Radar tinham só
 * helpers soltos em {@code CentralCompleteAlerts}, sem visão de aba —
 * agora têm {@link InfraTabView} e {@link RadarTabView} vinculadas a
 * {@code CentralCompleteTabs.INFRAESTRUTURA}/{@code RADAR}, e o tier da
 * Infra deriva do burn rate calculado pela P3.11.
 */
public final class CentralCompleteService {

    public record CollectionTabView(CentralCompleteTabs tab, CentralSnapshots.CollectionSnapshot snapshot) {
    }

    public record PixTabView(CentralCompleteTabs tab, CentralSnapshots.PixSnapshot snapshot) {
    }

    public record LgpdTabView(CentralCompleteTabs tab, CentralSnapshots.LgpdSnapshot snapshot,
                              boolean hasOverdue) {
    }

    public record RegulatoryTabView(CentralCompleteTabs tab, List<RegulatoryLibrary.NormEntry> norms,
                                    List<RegulatoryLibrary.NormEntry> futureEffect,
                                    RegulatoryLibrary.AgencyProfile agencyProfile) {
    }

    public record InfraTabView(CentralCompleteTabs tab, CentralCompleteAlerts.InfraSloView infra) {
    }

    public record RadarTabView(CentralCompleteTabs tab, List<CentralCompleteAlerts.RadarItem> items) {
    }

    public record SupportTabView(CentralCompleteTabs tab, CentralSnapshots.SupportSnapshot snapshot,
                                 Optional<BigDecimal> csatAverage) {
    }

    public CollectionTabView collectionTab(CentralSnapshots.CollectionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "instantâneo de arrecadação é obrigatório");
        return new CollectionTabView(CentralCompleteTabs.ARRECADACAO, snapshot);
    }

    public PixTabView pixTab(CentralSnapshots.PixSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "instantâneo Pix é obrigatório");
        return new PixTabView(CentralCompleteTabs.PIX, snapshot);
    }

    public LgpdTabView lgpdTab(CentralSnapshots.LgpdSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "instantâneo LGPD é obrigatório");
        boolean overdue = snapshot.incidentsOverdue() > 0 || snapshot.overdueDataSubjectRequests() > 0;
        return new LgpdTabView(CentralCompleteTabs.LGPD, snapshot, overdue);
    }

    /**
     * Aba Regulatório: a consulta à biblioteca da P1.34 com o filtro (a
     * situação do filtro é a DERIVADA na referência — M5, via
     * {@code RegulatoryLibrary.query}), as normas de vigência futura na
     * referência e o perfil da agência (agência nula = sem perfil nesta
     * visão, declarado, não presumido).
     */
    public RegulatoryTabView regulatoryTab(RegulatoryLibrary library, RegulatoryLibrary.NormFilter filter,
                                           LocalDate reference, String agency) {
        Objects.requireNonNull(library, "biblioteca de normas é obrigatória");
        Objects.requireNonNull(reference, "referência é obrigatória");
        return new RegulatoryTabView(CentralCompleteTabs.REGULATORIO, library.query(filter, reference),
                library.withFutureEffect(reference),
                agency == null ? null : library.profileForAgency(agency));
    }

    /**
     * Aba Infraestrutura com SLO: burn rates calculados pela P3.11 nas
     * janelas curta e longa e o tier derivado deles (M3).
     */
    public InfraTabView infrastructureTab(SloDefinition slo, long totalShort, long badShort,
                                          long totalLong, long badLong) {
        Objects.requireNonNull(slo, "SLO é obrigatório na aba Infraestrutura");
        return new InfraTabView(CentralCompleteTabs.INFRAESTRUTURA,
                CentralCompleteAlerts.infraSlo(slo, totalShort, badShort, totalLong, badLong));
    }

    /** Aba Radar: vencimentos vencidos e dentro da janela do chamador, ordenados por vencimento. */
    public RadarTabView radarTab(List<CentralCompleteAlerts.RadarItem> items, LocalDate reference, long windowDays) {
        return new RadarTabView(CentralCompleteTabs.RADAR,
                CentralCompleteAlerts.radarWithin(items, reference, windowDays));
    }

    /** Aba Suporte: média CSAT só existe com respostas; sem resposta, vazia — nunca zero nem cinco presumidos. */
    public SupportTabView supportTab(CentralSnapshots.SupportSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "instantâneo de suporte é obrigatório");
        Optional<BigDecimal> csat = snapshot.csatResponses() == 0
                ? Optional.empty()
                : Optional.of(BigDecimal.valueOf(snapshot.csatSum())
                        .divide(BigDecimal.valueOf(snapshot.csatResponses()), 2, RoundingMode.HALF_UP));
        return new SupportTabView(CentralCompleteTabs.SUPORTE, snapshot, csat);
    }
}
