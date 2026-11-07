package br.com.ziipsan.platform.central.complete;

import br.com.ziipsan.platform.observability.BurnRateCalculator;
import br.com.ziipsan.platform.observability.SloDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.15: registro das abas (próprias × remissões) e visões das SETE abas sobre os instantâneos (M3: Infra e Radar incluídas). */
class CentralCompleteServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final YearMonth NOV = YearMonth.of(2026, 11);

    @Test
    @DisplayName("registro: 7 abas próprias da P3.15 na ordem do aceite e 3 remissões declaradas (P3.37, ED-58, ED-98)")
    void tabRegistryOwnVsReferrals() {
        assertThat(CentralCompleteTabs.deliveredByP315()).containsExactly(
                CentralCompleteTabs.ARRECADACAO, CentralCompleteTabs.PIX, CentralCompleteTabs.LGPD,
                CentralCompleteTabs.REGULATORIO, CentralCompleteTabs.INFRAESTRUTURA,
                CentralCompleteTabs.RADAR, CentralCompleteTabs.SUPORTE);
        assertThat(CentralCompleteTabs.referrals()).extracting(CentralCompleteTabs::originTask)
                .containsExactly("P3.37", "ED-58", "ED-98");
        assertThat(CentralCompleteTabs.OBRIGACOES_TENANT.route())
                .as("a agenda de obrigações é tela do tenant (ED-98), não rota da Central").isNull();
        assertThat(CentralCompleteTabs.INTEGRACOES.route()).isEqualTo("/central/integracoes");
    }

    @Test
    @DisplayName("abas Arrecadação e Pix devolvem o instantâneo; Pix incoerente (confirmados > recebidos) é recusado")
    void collectionAndPixTabs() {
        CentralCompleteService svc = new CentralCompleteService();
        var collection = new CentralSnapshots.CollectionSnapshot(TENANT, NOV, 120, 345_600, 3, 1, 2);
        assertThat(svc.collectionTab(collection).tab()).isEqualTo(CentralCompleteTabs.ARRECADACAO);
        assertThat(svc.collectionTab(collection).snapshot().settledAmountCents()).isEqualTo(345_600);
        assertThatThrownBy(() -> new CentralSnapshots.PixSnapshot(TENANT, NOV, 10, 11, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("incoerente");
    }

    @Test
    @DisplayName("aba LGPD sinaliza vencidos; aba Suporte: CSAT 45/10 = 4,50 à mão e sem respostas = média vazia")
    void lgpdAndSupportTabs() {
        CentralCompleteService svc = new CentralCompleteService();
        var lgpd = new CentralSnapshots.LgpdSnapshot(TENANT, NOV, 1, 1, 1, 2, 0, 3);
        assertThat(svc.lgpdTab(lgpd).hasOverdue()).isTrue();
        var support = new CentralSnapshots.SupportSnapshot(TENANT, NOV,
                Map.of("S1", 1L, "S2", 0L, "S3", 2L, "S4", 0L, "S5", 0L), 1, 10, 45);
        CentralCompleteService.SupportTabView view = svc.supportTab(support);
        assertThat(view.snapshot().totalOpen()).isEqualTo(3);
        assertThat(view.csatAverage()).contains(new java.math.BigDecimal("4.50"));
        var noCsat = new CentralSnapshots.SupportSnapshot(TENANT, NOV,
                Map.of("S1", 0L, "S2", 0L, "S3", 0L, "S4", 0L, "S5", 0L), 0, 0, 0);
        assertThat(svc.supportTab(noCsat).csatAverage()).isEmpty();
    }

    @Test
    @DisplayName("instantâneo de suporte sem as cinco severidades declaradas é recusado")
    void supportSnapshotRequiresFiveSeverities() {
        assertThatThrownBy(() -> new CentralSnapshots.SupportSnapshot(TENANT, NOV,
                Map.of("S1", 0L, "S2", 0L), 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cinco severidades");
    }

    @Test
    @DisplayName("aba Regulatório combina filtro, vigência futura e perfil da agência na mesma visão")
    void regulatoryTabView() {
        RegulatoryLibrary library = new RegulatoryLibrary(List.of(
                new RegulatoryLibrary.NormEntry("n1", "Norma sintética", "REGULATORY", "VIGENCIA_FUTURA",
                        "OFICIAL", "saneamento", "ANA", "billing", "https://www.gov.br/ana", "Resumo próprio.",
                        LocalDate.of(2027, 2, 1), null,
                        List.of(new RegulatoryLibrary.NormEdition("2027", LocalDate.of(2027, 2, 1), null)))));
        CentralCompleteService.RegulatoryTabView view = new CentralCompleteService().regulatoryTab(
                library, new RegulatoryLibrary.NormFilter(null, null, null, null, null),
                LocalDate.of(2026, 11, 7), "ANA");
        assertThat(view.tab()).isEqualTo(CentralCompleteTabs.REGULATORIO);
        assertThat(view.norms()).hasSize(1);
        assertThat(view.futureEffect()).hasSize(1);
        assertThat(view.agencyProfile().mandatoryNorms()).hasSize(1);
    }

    @Test
    @DisplayName("M3 — aba Infraestrutura existe como visão: vinculada a INFRAESTRUTURA, burn 2,5 calculado e tier TICKET_SLOW derivado dele")
    void infrastructureTabView() {
        // À mão: 250 ruins/100.000 com SLO 99,9% → burn 2,5 (P3.11);
        // tierFor(2,5; 2,5) = TICKET_SLOW (longa >= 1, curta < 6).
        CentralCompleteService.InfraTabView view = new CentralCompleteService().infrastructureTab(
                new SloDefinition("api.availability", 99.9, 30), 100_000, 250, 100_000, 250);
        assertThat(view.tab()).isEqualTo(CentralCompleteTabs.INFRAESTRUTURA);
        assertThat(view.infra().burnShortWindow().burnRate())
                .isCloseTo(2.5, org.assertj.core.api.Assertions.within(1e-12));
        assertThat(view.infra().tierShortLong()).isEqualTo(BurnRateCalculator.AlertTier.TICKET_SLOW);
    }

    @Test
    @DisplayName("M3 — aba Radar existe como visão: vinculada a RADAR, com vencidos e itens da janela ordenados por vencimento")
    void radarTabView() {
        LocalDate ref = LocalDate.of(2026, 11, 7);
        var items = List.of(
                new CentralCompleteAlerts.RadarItem("CONTRATO", TENANT, LocalDate.of(2026, 11, 20), "renovação"),
                new CentralCompleteAlerts.RadarItem("CERTIFICADO", TENANT, LocalDate.of(2026, 11, 1), "vencido"),
                new CentralCompleteAlerts.RadarItem("CONTRATO", TENANT, LocalDate.of(2027, 6, 1), "longe"));
        CentralCompleteService.RadarTabView view = new CentralCompleteService().radarTab(items, ref, 30);
        assertThat(view.tab()).isEqualTo(CentralCompleteTabs.RADAR);
        assertThat(view.items()).extracting(CentralCompleteAlerts.RadarItem::kind)
                .containsExactly("CERTIFICADO", "CONTRATO");
    }
}
