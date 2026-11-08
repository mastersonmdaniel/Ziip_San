package br.com.ziipsan.intelligence.losses.nr15;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * O teste do aceite da ED-78: reconcilia o balanco padronizado
 * publicado (NR 15) com o balanco por DMC da ED-85 do mesmo periodo.
 * As fotos sao produzidas pelo WaterBalanceCalculator REAL da ED-85.
 */
class StandardizedBalanceReconciliationTest {

    @Test
    @DisplayName("ACEITE: balanco publicado reconcilia com a soma dos balancos por DMC da ED-85 (mesmo periodo)")
    void publishedReconcilesWithEd85() {
        List<DmcBalanceSnapshot> snapshots = Nr15Fixtures.yearSnapshots();
        var service = Nr15Fixtures.declarationService(Nr15Fixtures.applicablePort(), snapshots,
                Nr15Fixtures.producedPort(Nr15Fixtures.PRODUCED));
        var output = service.buildAnnualOutput(Nr15Fixtures.TENANT, Nr15Fixtures.YEAR);

        // Somas conferidas a mao sobre as entradas declaradas do fixture.
        var balance = output.balance();
        assertThat(balance.systemInputM3()).isEqualTo(180_000.0);
        assertThat(balance.exportedM3()).isEqualTo(6_000.0);
        assertThat(balance.netInputM3()).isEqualTo(174_000.0);
        assertThat(balance.billedMeteredM3()).isEqualTo(108_000.0);
        assertThat(balance.billedUnmeteredM3()).isEqualTo(1_200.0);
        assertThat(balance.billedTotalM3()).isEqualTo(109_200.0);
        assertThat(balance.authorizedUnbilledM3()).isEqualTo(7_200.0);
        assertThat(balance.authorizedConsumptionM3()).isEqualTo(116_400.0);
        assertThat(balance.totalLossesM3()).isEqualTo(57_600.0);
        assertThat(balance.apparentLossesM3OrNull()).isEqualTo(8_400.0);
        assertThat(balance.realLossesM3OrNull()).isEqualTo(49_200.0);
        assertThat(balance.nonRevenueWaterM3()).isEqualTo(64_800.0);
        assertThat(balance.nonRevenuePercent()).isCloseTo(37.2414, within(0.001));
        assertThat(balance.dmcCount()).isEqualTo(2);
        assertThat(balance.snapshotCount()).isEqualTo(24);
        assertThat(balance.systemInputMatrixUnit()).isEqualTo(180.0);
        assertThat(balance.producedDivergenceM3()).isEqualTo(0.0);
        assertThat(balance.producedReconciled()).isTrue();

        // A reconciliacao em si: 12 componentes, nenhuma divergencia.
        assertThat(output.reconciliation().components()).hasSize(12);
        assertThat(output.reconciliation().reconciled()).isTrue();
        assertThat(output.reconciliation().divergences()).isEmpty();

        // Declaracao emitida em DRAFT, com a agencia do perfil.
        var declaration = service.declare(Nr15Fixtures.TENANT, Nr15Fixtures.YEAR);
        assertThat(declaration.status()).isEqualTo(AnnualDeclaration.Status.DRAFT);
        assertThat(declaration.agencyCode()).isEqualTo("AGENCIA-TESTE");
        assertThat(declaration.baseYear()).isEqualTo(Nr15Fixtures.YEAR);
    }

    @Test
    @DisplayName("reconciliacao acusa componente adulterado no balanco publicado (perda real +100 m3)")
    void tamperedComponentDiverges() {
        List<DmcBalanceSnapshot> snapshots = Nr15Fixtures.yearSnapshots();
        var balance = Nr15BalanceAssembler.assemble(Nr15Fixtures.TENANT, Nr15Fixtures.YEAR,
                snapshots, Nr15Fixtures.producedPort(Nr15Fixtures.PRODUCED));
        var tampered = new StandardizedWaterBalance(balance.tenantId(), balance.baseYear(),
                balance.dmcCount(), balance.snapshotCount(), balance.systemInputM3(),
                balance.exportedM3(), balance.netInputM3(), balance.billedMeteredM3(),
                balance.billedUnmeteredM3(), balance.billedTotalM3(), balance.authorizedUnbilledM3(),
                balance.authorizedConsumptionM3(), balance.totalLossesM3(),
                balance.apparentLossesM3OrNull(), balance.realLossesM3OrNull() + 100.0,
                balance.nonRevenueWaterM3(), balance.nonRevenuePercent(),
                balance.producedVolumeP42M3(), balance.producedDivergenceM3(), balance.confidence());
        var result = Nr15Reconciler.reconcile(tampered, snapshots);
        assertThat(result.reconciled()).isFalse();
        assertThat(result.divergences()).hasSize(1);
        assertThat(result.divergences().get(0).component()).isEqualTo("realLossesM3");
        assertThat(result.divergences().get(0).differenceOrNull()).isCloseTo(100.0, within(1e-9));
    }

    @Test
    @DisplayName("completude do Anexo I: mes faltante de um DMC recusa nomeando DMC e mes")
    void missingMonthRefuses() {
        List<DmcBalanceSnapshot> snapshots = new ArrayList<>(Nr15Fixtures.yearSnapshots());
        snapshots.removeIf(s -> s.dmcId().equals(Nr15Fixtures.DMC_B)
                && s.period().equals(YearMonth.of(Nr15Fixtures.YEAR, 6)));
        assertThatThrownBy(() -> Nr15BalanceAssembler.assemble(Nr15Fixtures.TENANT,
                Nr15Fixtures.YEAR, snapshots, Nr15Fixtures.producedPort(Nr15Fixtures.PRODUCED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-06");
    }

    @Test
    @DisplayName("foto duplicada (mesmo DMC e mes) recusa: a soma contaria o mes em dobro")
    void duplicateSnapshotRefuses() {
        List<DmcBalanceSnapshot> snapshots = new ArrayList<>(Nr15Fixtures.yearSnapshots());
        snapshots.add(snapshots.get(0));
        assertThatThrownBy(() -> Nr15BalanceAssembler.assemble(Nr15Fixtures.TENANT,
                Nr15Fixtures.YEAR, snapshots, Nr15Fixtures.producedPort(Nr15Fixtures.PRODUCED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicada");
    }

    @Test
    @DisplayName("volume produzido ausente na P4.2 recusa (nunca zero, nunca a soma em silencio)")
    void producedMissingRefuses() {
        var emptyPort = Nr15Fixtures.producedPort(0.0);
        ProducedVolumePort missing = (tenantId, baseYear) -> java.util.Optional.empty();
        assertThat(emptyPort).isNotNull();
        assertThatThrownBy(() -> Nr15BalanceAssembler.assemble(Nr15Fixtures.TENANT,
                Nr15Fixtures.YEAR, Nr15Fixtures.yearSnapshots(), missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NR15_PRODUCED_VOLUME_MISSING");
    }

    @Test
    @DisplayName("volume produzido divergente fica gravado no balanco mas bloqueia a declaracao")
    void producedDivergenceBlocksDeclaration() {
        var service = Nr15Fixtures.declarationService(Nr15Fixtures.applicablePort(),
                Nr15Fixtures.yearSnapshots(), Nr15Fixtures.producedPort(180_500.0));
        var output = service.buildAnnualOutput(Nr15Fixtures.TENANT, Nr15Fixtures.YEAR);
        assertThat(output.balance().producedDivergenceM3()).isEqualTo(500.0);
        assertThat(output.balance().producedReconciled()).isFalse();
        assertThatThrownBy(() -> service.declare(Nr15Fixtures.TENANT, Nr15Fixtures.YEAR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NR15_PRODUCED_VOLUME_DIVERGES");
    }

    @Test
    @DisplayName("aparentes desconhecidas num DMC: ano sem aparente/real (null), e a reconciliacao fecha nos nulos")
    void unknownApparentPropagatesAsUnknown() {
        List<DmcBalanceSnapshot> snapshots = Nr15Fixtures.yearSnapshots(false, true, true);
        var balance = Nr15BalanceAssembler.assemble(Nr15Fixtures.TENANT, Nr15Fixtures.YEAR,
                snapshots, Nr15Fixtures.producedPort(Nr15Fixtures.PRODUCED));
        assertThat(balance.apparentLossesM3OrNull()).isNull();
        assertThat(balance.realLossesM3OrNull()).isNull();
        assertThat(balance.totalLossesM3()).isEqualTo(57_600.0);
        var result = Nr15Reconciler.reconcile(balance, snapshots);
        assertThat(result.reconciled()).isTrue();
        var indicators = Nr15Indicators.forPeriod("2026", snapshots);
        assertThat(indicators.realLossesLPerConnDayOrNull()).isNull();
        assertThat(indicators.apparentLossesLPerConnDayOrNull()).isNull();
        assertThat(indicators.iliOrNull()).isNull();
    }

    @Test
    @DisplayName("fluxo da declaracao: DRAFT -> SUBMITTED -> PUBLISHED, com guardas")
    void declarationLifecycle() {
        var service = Nr15Fixtures.declarationService(Nr15Fixtures.applicablePort(),
                Nr15Fixtures.yearSnapshots(), Nr15Fixtures.producedPort(Nr15Fixtures.PRODUCED));
        var declaration = service.declare(Nr15Fixtures.TENANT, Nr15Fixtures.YEAR);
        var publication = new AnnualDeclaration.Publication(
                AnnualDeclaration.PublicationChannel.PROVIDER_SITE,
                "https://prestador.exemplo/declaracoes/2026", Instant.parse("2027-03-01T12:00:00Z"));
        assertThatThrownBy(() -> declaration.publish(publication))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUBMITTED");
        assertThatThrownBy(() -> new AnnualDeclaration.Publication(
                AnnualDeclaration.PublicationChannel.PROVIDER_SITE, "  ",
                Instant.parse("2027-03-01T12:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
        declaration.submit(Instant.parse("2027-02-15T12:00:00Z"));
        assertThat(declaration.status()).isEqualTo(AnnualDeclaration.Status.SUBMITTED);
        declaration.publish(publication);
        assertThat(declaration.status()).isEqualTo(AnnualDeclaration.Status.PUBLISHED);
        assertThat(declaration.publication().channel())
                .isEqualTo(AnnualDeclaration.PublicationChannel.PROVIDER_SITE);
    }
}
