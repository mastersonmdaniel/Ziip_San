package br.com.ziipsan.operations.metering.field;

import br.com.ziipsan.commercial.billingrules.AmountLine;
import br.com.ziipsan.commercial.billingrules.FieldBillInput;
import br.com.ziipsan.commercial.billingrules.FieldTariffSnapshot;
import br.com.ziipsan.commercial.billingrules.RulesVersion;
import br.com.ziipsan.commercial.billingrules.TariffBracket;
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Montador do pacote de rota (P-03 / RG2-C-P2): monta o RoutePackage real
 * da ED-19 e o DTO de entrega no formato do espelho TS, a partir de
 * entrada tipada explicita — e falha fechado nas recusas nomeadas.
 * Valores do caso simples-uma-economia do fixture de contrato da ED-19.
 */
class RoutePackageAssemblerTest {

    private static final OffsetDateTime ISSUED_AT =
            OffsetDateTime.of(2026, 11, 7, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID ROUTE = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID TASK = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID INSTALLATION = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final RoutePackageAssembler assembler = new RoutePackageAssembler();

    private static FieldTariffSnapshot snapshot() {
        return new FieldTariffSnapshot(
                UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
                UUID.fromString("00000000-0000-0000-0000-0000000000cc"),
                10, new BigDecimal("12.5"),
                List.of(new TariffBracket(10, new BigDecimal("1.5")),
                        new TariffBracket(10, new BigDecimal("2.5")),
                        TariffBracket.open(new BigDecimal("4.0"))),
                true, new BigDecimal("80"),
                Map.of("tariff", "v2026-01"));
    }

    private static FieldBillInput baseInput() {
        // currentReading = previousReading: o marcador que o RouteTask exige.
        return new FieldBillInput(TASK, UNIT, INSTALLATION, 1000, 1000, 5, 1,
                snapshot(), List.of(), List.of(), LocalDate.of(2026, 12, 10));
    }

    private static RoutePackageAssembler.TaskInput taskInput() {
        return new RoutePackageAssembler.TaskInput(baseInput(), null,
                MeasurementKind.WATER, YearMonth.of(2026, 11), 1);
    }

    private static RoutePackageAssembler.PackageInput packageInput(
            List<RoutePackageAssembler.TaskInput> tasks) {
        return new RoutePackageAssembler.PackageInput(ROUTE, TENANT, ISSUED_AT, "2026-11",
                RulesVersion.CONTRACT, SHA, tasks,
                List.of(new RoutePackageAssembler.OccurrenceInput("NORMAL", false, true)),
                Map.of("tariff", "v2026-01"), Map.of("CONTA", "tpl-v3"), 10);
    }

    @Test
    @DisplayName("monta o RoutePackage real da ED-19: a leitura de campo entra por toInput")
    void assemblesRealRoutePackage() {
        var assembled = assembler.assemble(packageInput(List.of(taskInput())));
        var pkg = assembled.routePackage();
        pkg.requireCompatibleRules();
        assertThat(pkg.routeId()).isEqualTo(ROUTE);
        assertThat(pkg.tenantId()).isEqualTo(TENANT);
        assertThat(pkg.rulesArtifactSha256()).isEqualTo(SHA);
        assertThat(pkg.sendEveryN()).isEqualTo(10);
        assertThat(pkg.tasks()).hasSize(1);
        var realInput = pkg.tasks().get(0).toInput(1012);
        assertThat(realInput.currentReading()).isEqualTo(1012);
        assertThat(realInput.previousReading()).isEqualTo(1000);
        assertThat(realInput.dueDate()).isEqualTo(LocalDate.of(2026, 12, 10));
    }

    @Test
    @DisplayName("o DTO de entrega espelha o RoutePackage TS, com o bloco de emissao por tarefa")
    void deliveryDtoMirrorsTsPackage() {
        var dto = assembler.assemble(packageInput(List.of(taskInput()))).delivery();
        assertThat(dto.routeId()).isEqualTo(ROUTE.toString());
        assertThat(dto.reference()).isEqualTo("2026-11");
        assertThat(dto.downloadedAt()).isEqualTo(ISSUED_AT.toString());
        assertThat(dto.occurrences()).hasSize(1);
        assertThat(dto.occurrences().get(0).code()).isEqualTo("NORMAL");
        assertThat(dto.occurrences().get(0).requiresReading()).isTrue();

        var task = dto.tasks().get(0);
        assertThat(task.taskId()).isEqualTo(TASK.toString());
        assertThat(task.unitId()).isEqualTo(UNIT.toString());
        assertThat(task.installationId()).isEqualTo(INSTALLATION.toString());
        assertThat(task.kind()).isEqualTo("WATER");
        assertThat(task.reference()).isEqualTo("2026-11");
        assertThat(task.previousReading()).isEqualTo(1000);
        assertThat(task.meterDigits()).isEqualTo(5);
        assertThat(task.sequence()).isEqualTo(1);

        var emission = task.emission();
        assertThat(emission.economies()).isEqualTo(1);
        assertThat(emission.debits()).isEmpty();
        assertThat(emission.credits()).isEmpty();
        assertThat(emission.dueDate()).isEqualTo("2026-12-10");
        assertThat(emission.barcodeValue()).isNull();
        var snap = emission.snapshot();
        assertThat(snap.tariffVersionId()).isEqualTo("00000000-0000-0000-0000-0000000000aa");
        assertThat(snap.categoryId()).isEqualTo("00000000-0000-0000-0000-0000000000cc");
        assertThat(snap.minimumM3PerEconomy()).isEqualTo(10);
        assertThat(snap.fixedChargePerEconomy()).isEqualByComparingTo("12.5");
        assertThat(snap.brackets()).hasSize(3);
        assertThat(snap.brackets().get(0).pricePerM3()).isEqualByComparingTo("1.5");
        assertThat(snap.brackets().get(0).widthM3()).isEqualTo(10);
        assertThat(snap.brackets().get(2).widthM3()).isNull();
        assertThat(snap.sewerEnabled()).isTrue();
        assertThat(snap.sewerPct()).isEqualByComparingTo("80");
        assertThat(snap.parameterVersions()).containsEntry("tariff", "v2026-01");
    }

    @Test
    @DisplayName("debitos, creditos e barcodeValue da tarefa chegam ao DTO sem traducao")
    void debitsCreditsAndBarcodeReachDto() {
        var base = new FieldBillInput(TASK, UNIT, INSTALLATION, 2000, 2000, 5, 3,
                snapshot(),
                List.of(new AmountLine("TAXA_SERVICO", new BigDecimal("15.0"))),
                List.of(new AmountLine("CREDITO_ANTERIOR", new BigDecimal("20.0"))),
                LocalDate.of(2026, 12, 10));
        var task = new RoutePackageAssembler.TaskInput(base, "846900000000001",
                MeasurementKind.WELL, YearMonth.of(2026, 11), 2);
        var dto = assembler.assemble(packageInput(List.of(task))).delivery();
        var emission = dto.tasks().get(0).emission();
        assertThat(dto.tasks().get(0).kind()).isEqualTo("WELL");
        assertThat(emission.economies()).isEqualTo(3);
        assertThat(emission.debits()).hasSize(1);
        assertThat(emission.debits().get(0).code()).isEqualTo("TAXA_SERVICO");
        assertThat(emission.debits().get(0).amount()).isEqualByComparingTo("15.0");
        assertThat(emission.credits().get(0).code()).isEqualTo("CREDITO_ANTERIOR");
        assertThat(emission.barcodeValue()).isEqualTo("846900000000001");
    }

    @Test
    @DisplayName("tarefa sem entrada base e recusada com nome, na entrada tipada")
    void taskWithoutBaseIsRefused() {
        assertThatThrownBy(() -> new RoutePackageAssembler.TaskInput(null, null,
                MeasurementKind.WATER, YearMonth.of(2026, 11), 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("entrada base da tarefa e obrigatoria");
    }

    @Test
    @DisplayName("pacote sem tarefas e recusado com nome")
    void packageWithoutTasksIsRefused() {
        assertThatThrownBy(() -> assembler.assemble(packageInput(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem nenhuma tarefa");
    }

    @Test
    @DisplayName("hash do artefato ausente e recusado pelo construtor real do pacote")
    void missingArtifactHashIsRefused() {
        var input = new RoutePackageAssembler.PackageInput(ROUTE, TENANT, ISSUED_AT, "2026-11",
                RulesVersion.CONTRACT, " ", List.of(taskInput()),
                List.of(), Map.of(), Map.of(), 10);
        assertThatThrownBy(() -> assembler.assemble(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hash do artefato");
    }

    @Test
    @DisplayName("base com leitura atual diferente da anterior (marcador quebrado) e recusada")
    void baseWithBrokenMarkerIsRefused() {
        var base = new FieldBillInput(TASK, UNIT, INSTALLATION, 1000, 1001, 5, 1,
                snapshot(), List.of(), List.of(), LocalDate.of(2026, 12, 10));
        var task = new RoutePackageAssembler.TaskInput(base, null,
                MeasurementKind.WATER, YearMonth.of(2026, 11), 1);
        assertThatThrownBy(() -> assembler.assemble(packageInput(List.of(task))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("marcador");
    }
}
