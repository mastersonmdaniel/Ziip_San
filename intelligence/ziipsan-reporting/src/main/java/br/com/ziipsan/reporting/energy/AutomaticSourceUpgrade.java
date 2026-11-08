package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.core.fleet.FuelAndConsumption;
import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import br.com.ziipsan.reporting.indicator.IndicatorVariable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Substituicao da entrada manual da ED-77 pela fonte automatica
 * (aceite da ED-90, GES-21/OPE-08): "fonte automatica (telemetria,
 * fatura importada) no lugar da entrada manual das fichas da ED-77,
 * com grau de confianca atualizado; combustivel da frota lido da ED-59
 * quando ela existir e, antes disso, por entrada manual".
 *
 * <p>Como a P3.41 so conhece variavel {@code REPORT_VIEW} (view
 * {@code rpt_*} da P3.22) ou {@code MANUAL} com responsavel, a variavel
 * automatica vira {@code REPORT_VIEW} — e a view e MAPEAMENTO de quem
 * integra, nunca presumida (mesmo desenho da ED-48): sem o mapeamento,
 * a troca recusa nomeada e a variavel manual da ED-77 permanece.
 *
 * <p>Combustivel: a ED-59 existe na main (camada C4) — os litros sao
 * lidos dos registros REAIS dela ({@link FuelAndConsumption.FuelRecord})
 * pela porta/adaptador abaixo. Sem dado da frota para o tenant/periodo
 * (ou sem a porta ligada), o ramo manual do aceite permanece, com o
 * grau LOW da ED-77.
 */
public final class AutomaticSourceUpgrade {

    private AutomaticSourceUpgrade() {
    }

    /**
     * Consumo de energia resolvido: valor, origem efetiva e grau atualizado.
     *
     * <p>CORRECAO D2 (REVISAO-ED-90): o record VALIDA o sinal — consumo
     * resolvido tem de ser POSITIVO. Valor {@code <= 0} nao e medicao:
     * zero/negativo vindo de telemetria, fatura ou manual e recusado
     * nomeado, nunca aceito nem trocado silenciosamente por outra fonte
     * (mesmo padrao de quantidade positiva de
     * {@code EmissionInventory.EmissionActivity} e do fator de emissao).
     */
    public record ResolvedEnergy(BigDecimal kwh, EnergyDataSource source) {
        public ResolvedEnergy {
            if (kwh == null || kwh.signum() <= 0) {
                throw new IndicatorValidationException("ENERGY_RESOLVED_KWH_INVALID",
                        "consumo resolvido exige kWh positivo (medicao real): " + kwh
                                + " — valor <= 0 de qualquer fonte e recusado, nao presumido");
            }
            if (source == null) {
                throw new IndicatorValidationException("ENERGY_RESOLVED_SOURCE_REQUIRED",
                        "consumo resolvido sem a origem declarada (grau de confianca da ficha)");
            }
        }

        public ConfidenceGrade confidenceGrade() {
            return source.confidenceGrade();
        }
    }

    /**
     * Precedencia declarada das fontes de consumo: telemetria, depois a
     * fatura importada, depois a entrada manual. Sem nenhuma, recusa —
     * o consumo nao e presumido.
     *
     * <p>CORRECAO D2 (REVISAO-ED-90): a telemetria tem instalacao e mes
     * PROPRIOS ({@code installationId}/{@code referenceMonth}) — ela e
     * consultada mesmo SEM fatura (antes a assinatura amarrava a
     * consulta a fatura e a telemetria presente era ignorada, vencendo
     * o manual). A fatura so serve de reserva quando esta no escopo
     * pedido (mesma instalacao e mesmo mes, quando declarados).
     *
     * <p>CORRECAO D4 (REVISAO-ED-90): o {@code tenantId} do chamador e
     * conferido contra {@code importedBill.tenantId()} ANTES de
     * qualquer fonte ser usada — fatura de outro tenant e recusa
     * nomeada ({@code ENERGY_BILL_TENANT_MISMATCH}), nunca o consumo
     * de outro tenant devolvido em silencio.
     */
    public static ResolvedEnergy resolveKwh(EnergyPorts.TelemetryPort telemetryPort,
                                            UUID tenantId, UUID installationId,
                                            YearMonth referenceMonth,
                                            EnergyBill importedBill,
                                            BigDecimal manualKwh) {
        if (tenantId == null) {
            throw new IndicatorValidationException("ENERGY_TENANT_REQUIRED",
                    "resolucao de consumo sem o tenant do chamador");
        }
        if (importedBill != null && !tenantId.equals(importedBill.tenantId())) {
            throw new IndicatorValidationException("ENERGY_BILL_TENANT_MISMATCH",
                    "fatura do tenant " + importedBill.tenantId() + " nao resolve o "
                            + "consumo do tenant " + tenantId + ": recusa cross-tenant");
        }
        if (telemetryPort != null) {
            Optional<BigDecimal> tele = telemetryPort.kwh(tenantId, installationId, referenceMonth);
            if (tele != null && tele.isPresent()) {
                return new ResolvedEnergy(tele.get(), EnergyDataSource.TELEMETRY);
            }
        }
        boolean billInScope = importedBill != null
                && (installationId == null || installationId.equals(importedBill.installationId()))
                && (referenceMonth == null || referenceMonth.equals(importedBill.referenceMonth()));
        if (billInScope) {
            return new ResolvedEnergy(importedBill.totalKwh(), EnergyDataSource.IMPORTED_BILL);
        }
        if (manualKwh != null) {
            return new ResolvedEnergy(manualKwh, EnergyDataSource.MANUAL);
        }
        throw new IndicatorValidationException("ENERGY_DATA_UNAVAILABLE",
                "sem telemetria, sem fatura importada e sem entrada manual: "
                        + "consumo nao apurado (nunca presumido)");
    }

    /**
     * Forma legada (instalacao/mes derivados da fatura). Mantida para
     * os chamadores existentes: delega a forma desacoplada acima —
     * inclusive quando NAO ha fatura, caso em que a telemetria e
     * consultada com instalacao/mes nulos e a porta decide (a forma
     * completa, com instalacao/mes proprios, e a preferida).
     */
    public static ResolvedEnergy resolveKwh(EnergyPorts.TelemetryPort telemetryPort,
                                            UUID tenantId, EnergyBill importedBill,
                                            BigDecimal manualKwh) {
        return resolveKwh(telemetryPort, tenantId,
                importedBill == null ? null : importedBill.installationId(),
                importedBill == null ? null : importedBill.referenceMonth(),
                importedBill, manualKwh);
    }

    /**
     * Troca a variavel manual da ED-77 pela automatica no registro da
     * P3.41: devolve a variavel {@code REPORT_VIEW} pronta para a nova
     * definicao/versao. Sem o mapeamento explicito para a view
     * {@code rpt_*} que alimenta a variavel, recusa nomeada.
     */
    public static IndicatorVariable upgradeVariableToAutomatic(String variableName,
                                                               String reportView) {
        if (reportView == null || reportView.isBlank()) {
            throw new IndicatorValidationException("ENERGY_AUTOMATIC_VIEW_MAPPING_REQUIRED",
                    "variavel " + variableName + ": a fonte automatica exige o mapeamento "
                            + "explicito para a view rpt_* do nucleo da P3.22 que a alimenta; "
                            + "nenhuma view e presumida (a variavel manual da ED-77 permanece)");
        }
        return IndicatorVariable.fromReportView(variableName, reportView);
    }

    /**
     * Combustivel resolvido para o inventario: litros, origem efetiva e grau.
     *
     * <p>CORRECAO D2 (REVISAO-ED-90): o record VALIDA o sinal — litros
     * resolvidos tem de ser POSITIVOS; valor {@code <= 0} da ED-59 ou
     * do manual e recusado nomeado, nunca aceito.
     */
    public record ResolvedFuel(BigDecimal liters, EnergyDataSource source) {
        public ResolvedFuel {
            if (liters == null || liters.signum() <= 0) {
                throw new IndicatorValidationException("ESG_RESOLVED_FUEL_INVALID",
                        "combustivel resolvido exige litros positivos (medicao real): "
                                + liters + " — valor <= 0 de qualquer fonte e recusado");
            }
            if (source == null) {
                throw new IndicatorValidationException("ESG_RESOLVED_FUEL_SOURCE_REQUIRED",
                        "combustivel resolvido sem a origem declarada (grau de confianca)");
            }
        }

        public ConfidenceGrade confidenceGrade() {
            return source.confidenceGrade();
        }
    }

    /**
     * Combustivel do inventario (GES-21): ED-59 primeiro; sem dado da
     * frota, o ramo manual declarado no aceite. Sem nenhum dos dois,
     * recusa nomeada.
     */
    public static ResolvedFuel resolveFuel(EnergyPorts.FleetFuelPort fleetPort,
                                           UUID tenantId, LocalDate periodStart,
                                           LocalDate periodEnd, BigDecimal manualLiters) {
        if (fleetPort != null) {
            Optional<BigDecimal> fromFleet = fleetPort.fuelLiters(tenantId, periodStart, periodEnd);
            if (fromFleet.isPresent()) {
                return new ResolvedFuel(fromFleet.get(), EnergyDataSource.FLEET_ED59);
            }
        }
        if (manualLiters != null) {
            return new ResolvedFuel(manualLiters, EnergyDataSource.MANUAL);
        }
        throw new IndicatorValidationException("ESG_FUEL_DATA_UNAVAILABLE",
                "sem combustivel da ED-59 e sem entrada manual para o periodo: "
                        + "linha de combustivel do inventario nao apurada");
    }

    /**
     * Adaptador sobre os registros REAIS da ED-59
     * ({@link FuelAndConsumption.FuelRecord}): soma os litros dos
     * abastecimentos com data dentro do periodo (inclusive). E o corpo
     * que a fiacao usa para implementar {@link EnergyPorts.FleetFuelPort}
     * sobre a frota integrada; vazio quando nao ha abastecimento no
     * periodo (o chamador cai no ramo manual do aceite).
     *
     * <p><b>AVISO DE TENANT (correcao D6 da REVISAO-ED-90):</b> este
     * adaptador NAO recebe {@code tenantId} e os {@code FuelRecord} da
     * ED-59 nao carregam tenant — a lista recebida TEM de vir
     * pre-filtrada pela fiacao com os abastecimentos DO TENANT (e da
     * frota dele) em apuracao. Somar registros de outro tenant e
     * responsabilidade de quem chama; este metodo soma o que recebe.
     */
    public static Optional<BigDecimal> fleetLitersFromEd59(
            List<FuelAndConsumption.FuelRecord> records, LocalDate periodStart, LocalDate periodEnd) {
        if (records == null || periodStart == null || periodEnd == null) {
            return Optional.empty();
        }
        BigDecimal total = records.stream()
                .filter(r -> {
                    LocalDate day = r.fueledAt().toLocalDate();
                    return !day.isBefore(periodStart) && !day.isAfter(periodEnd);
                })
                .map(FuelAndConsumption.FuelRecord::liters)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.signum() > 0 ? Optional.of(total) : Optional.empty();
    }
}
