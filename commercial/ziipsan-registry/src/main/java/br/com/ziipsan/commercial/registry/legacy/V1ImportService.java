package br.com.ziipsan.commercial.registry.legacy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Motor da P2.10 — importador da v1 com 120 meses, construído SOBRE o
 * framework declarativo de migração da P1.2 (PLT-16), sem framework novo:
 *
 * <ul>
 *   <li>unidades e prestações a vencer são encenadas pelo
 *       {@link LegacyImportService} da P1.2 (carga, ensaio, conciliação e
 *       aplicação das unidades com a OS de lote são dele);</li>
 *   <li>esta classe acrescenta o que é só da v1: a carga do HISTÓRICO
 *       mensal (até {@link V1HistoryRecord#MAX_MONTHS} meses por unidade)
 *       pela declaração {@link V1HistoryMapping}, a SIMULAÇÃO (conferência
 *       de saldo + contagens, sem gravar nada fora do staging) e a
 *       APLICAÇÃO do restante do lote — histórico, prestações virando
 *       débitos a cobrar um a um (P2.5, DAD-20) e a situação CAD-68 das
 *       unidades sem usuário vigente (P1.41).</li>
 * </ul>
 *
 * <p>Pré-condição da aplicação: o lote já foi aplicado pela P1.2
 * (unidades criadas e confirmadas pela OS de lote). Unidade não aplicada
 * (recusada ou pendente) não recebe débito, histórico nem situação —
 * débito em unidade inexistente é exatamente o erro que a ordem das
 * camadas existe para evitar.
 *
 * <p>Ordem dentro da aplicação, por unidade aplicada: (1) situação de
 * ocupação; (2) prestações → débitos, SOMENTE se a conferência de saldo
 * da matrícula confere ({@link V1BalanceConference}) — matrícula divergente
 * não gera débito nenhum e volta no resultado com o motivo; (3) resíduo
 * de saldo → um débito {@code LEGACY_IMPORT}, quando positivo; (4)
 * histórico mensal → aplicado.
 *
 * <p>Idempotência: reaplicar não duplica nada — débito de prestação já
 * vinculado é pulado (vínculo da V14_2), resíduo já criado é pulado,
 * mês de histórico já aplicado é pulado e situação já registrada é
 * pulada. Recarregar o histórico com a mesma chave de lote é recusado
 * linha a linha por duplicidade, sem duplicar staging.
 *
 * <p>Fora de escopo (P4.1): simular, desfazer, reparcelar e antecipar
 * parcelamento. Aqui o acordo migrado é só identidade + prestações como
 * débitos; nenhuma operação sobre ele é oferecida.
 */
public final class V1ImportService {

    private static final DateTimeFormatter DATE_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LegacyStagingStore staging;
    private final V1MigrationStore store;
    private final InstallmentDebitGateway debitGateway;
    private final UnitSituationGateway situationGateway;

    public V1ImportService(LegacyStagingStore staging, V1MigrationStore store,
                           InstallmentDebitGateway debitGateway, UnitSituationGateway situationGateway) {
        if (staging == null || store == null || debitGateway == null || situationGateway == null) {
            throw new IllegalArgumentException("staging, store e gateways são obrigatórios.");
        }
        this.staging = staging;
        this.store = store;
        this.debitGateway = debitGateway;
        this.situationGateway = situationGateway;
    }

    /** Configuração da aplicação: códigos do catálogo {@code debit_types} do tenant e mês de corte. */
    public record ApplyConfig(String installmentDebitTypeCode, String residualDebitTypeCode,
                              LocalDate cutoverMonth) {
        public ApplyConfig {
            if (installmentDebitTypeCode == null || installmentDebitTypeCode.isBlank()
                    || residualDebitTypeCode == null || residualDebitTypeCode.isBlank()) {
                throw new IllegalArgumentException("Os códigos de debit_types (prestação e resíduo)"
                        + " são obrigatórios: são itens do catálogo do tenant (P2.5).");
            }
            if (cutoverMonth == null || cutoverMonth.getDayOfMonth() != 1) {
                throw new IllegalArgumentException("cutoverMonth é o primeiro dia do mês de corte.");
            }
        }
    }

    public record RejectedRow(int rowNo, List<LegacyIssue> issues) {
    }

    public record LoadHistoryOutcome(int accepted, List<RejectedRow> rejectedRows) {
    }

    /** Simulação: o que a aplicação faria, sem ter feito nada. */
    public record Simulation(
            V1BalanceConference.Report conference,
            int appliedUnits,
            int historyToApply,
            int historyRejected,
            int installmentsToConvert,
            int vacantUnits,
            int occupiedUnits) {
    }

    public record ApplyOutcome(
            V1BalanceConference.Report conference,
            int installmentDebitsCreated,
            int residualDebitsCreated,
            int historyApplied,
            int historyRejected,
            int situationsApplied,
            int unitsSkippedByConference,
            boolean alreadyFullyApplied) {
    }

    // ------------------------------------------------------------------
    // Carga do histórico (staging, sobre o lote da P1.2)
    // ------------------------------------------------------------------

    public LoadHistoryOutcome loadHistory(UUID batchId, V1HistoryMapping mapping,
                                            LegacySourceReader reader, LocalDate cutoverMonth) {
        LegacyBatch batch = requireBatch(batchId);
        if (mapping == null || reader == null) {
            throw new IllegalArgumentException("mapping e reader são obrigatórios.");
        }
        if (cutoverMonth == null || cutoverMonth.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("cutoverMonth é o primeiro dia do mês de corte.");
        }
        LocalDate windowStart = cutoverMonth.minusMonths(V1HistoryRecord.MAX_MONTHS - 1L);
        Map<String, StagedUnit> unitsByKey = new LinkedHashMap<>();
        for (StagedUnit unit : staging.listUnits(batch.id())) {
            unitsByKey.put(unit.legacyKey(), unit);
        }
        Set<String> seen = new LinkedHashSet<>();
        for (V1HistoryRecord staged : store.listHistory(batchId)) {
            seen.add(staged.unitLegacyKey() + "|" + staged.referenceMonth());
        }
        List<V1HistoryRecord> accepted = new ArrayList<>();
        List<RejectedRow> rejected = new ArrayList<>();
        for (LegacySourceReader.LegacyRow row : reader.read()) {
            List<LegacyIssue> issues = new ArrayList<>();
            Map<String, Object> values = mapFields(mapping, row, issues);
            String unitKey = (String) values.get("UNIT_LEGACY_KEY");
            LocalDate month = (LocalDate) values.get("REFERENCE_MONTH");
            if (unitKey == null) {
                issues.add(LegacyIssue.error("unitLegacyKey", "REQUIRED_MISSING",
                        "Linha de histórico sem a chave da unidade: não encenada."));
            } else if (!unitsByKey.containsKey(unitKey)) {
                issues.add(LegacyIssue.error("unitLegacyKey", "REFERENCE_UNKNOWN",
                        "Histórico sem unidade encenada no lote: não encenado."));
            }
            if (month == null) {
                issues.add(LegacyIssue.error("referenceMonth", "REQUIRED_MISSING",
                        "Linha de histórico sem competência: não encenada."));
            } else if (month.isAfter(cutoverMonth)) {
                issues.add(LegacyIssue.error("referenceMonth", "HISTORY_AFTER_CUTOVER",
                        "Competência depois do mês de corte da migração: não encenada."));
            } else if (month.isBefore(windowStart)) {
                issues.add(LegacyIssue.error("referenceMonth", "HISTORY_OUT_OF_WINDOW",
                        "Competência fora da janela de 120 meses: não encenada."));
            }
            boolean hasErrors = issues.stream().anyMatch(LegacyIssue::isError);
            if (!hasErrors && !seen.add(unitKey + "|" + month)) {
                issues.add(LegacyIssue.error("referenceMonth", "DUPLICATE_HISTORY_MONTH",
                        "Mês de histórico duplicado para a unidade: não encenado."));
                hasErrors = true;
            }
            if (hasErrors) {
                rejected.add(new RejectedRow(row.rowNo(), List.copyOf(issues)));
                continue;
            }
            StagedUnit unit = unitsByKey.get(unitKey);
            accepted.add(new V1HistoryRecord(UUID.randomUUID(), batchId, unit.id(), unitKey,
                    row.rowNo(), month,
                    (BigDecimal) values.get("CONSUMPTION_M3"),
                    (Long) values.get("BILLED_AMOUNT_CENTS"),
                    (LocalDate) values.get("DUE_DATE"),
                    List.of()));
        }
        store.insertHistory(batchId, accepted);
        for (V1HistoryRecord record : accepted) {
            staging.insertLineage(batchId, "UNIT", batch.sourceSystem(),
                    record.unitLegacyKey() + "@" + record.referenceMonth(),
                    mapping.name(), mapping.version(), record.id());
        }
        return new LoadHistoryOutcome(accepted.size(), List.copyOf(rejected));
    }

    // ------------------------------------------------------------------
    // Simulação (não grava nada fora do staging)
    // ------------------------------------------------------------------

    public Simulation simulate(UUID batchId) {
        requireBatch(batchId);
        List<StagedUnit> units = staging.listUnits(batchId);
        List<StagedInstallment> installments = staging.listInstallments(batchId);
        List<V1HistoryRecord> history = store.listHistory(batchId);
        V1BalanceConference.Report conference = V1BalanceConference.confer(units, installments);
        Set<String> conferenceOk = new LinkedHashSet<>();
        for (V1BalanceConference.MatriculaConference row : conference.rows()) {
            if (row.matches()) {
                conferenceOk.add(row.legacyKey());
            }
        }
        int appliedUnits = 0;
        int vacant = 0;
        int occupied = 0;
        Set<String> appliedKeys = new LinkedHashSet<>();
        for (StagedUnit unit : units) {
            if (isApplied(unit)) {
                appliedUnits++;
                appliedKeys.add(unit.legacyKey());
                if (V1UnitSituation.VACANT.equals(V1UnitSituation.occupancyStatusFor(unit))) {
                    vacant++;
                } else {
                    occupied++;
                }
            }
        }
        int historyToApply = (int) history.stream()
                .filter(r -> r.status() == V1HistoryRecord.Status.STAGED && !r.hasErrors()
                        && appliedKeys.contains(r.unitLegacyKey())
                        && !store.historyAlreadyApplied(r.unitLegacyKey(), r.referenceMonth()))
                .count();
        int historyRejected = (int) history.stream().filter(V1HistoryRecord::hasErrors).count();
        int installmentsToConvert = (int) installments.stream()
                .filter(i -> appliedKeys.contains(i.unitLegacyKey()) && conferenceOk.contains(i.unitLegacyKey())
                        && store.debitForInstallment(i.id()).isEmpty())
                .count();
        return new Simulation(conference, appliedUnits, historyToApply, historyRejected,
                installmentsToConvert, vacant, occupied);
    }

    // ------------------------------------------------------------------
    // Aplicação
    // ------------------------------------------------------------------

    public ApplyOutcome apply(UUID tenantId, UUID batchId, UUID actorId, ApplyConfig config) {
        if (tenantId == null || actorId == null || config == null) {
            throw new IllegalArgumentException("tenantId, actorId e config são obrigatórios.");
        }
        requireBatch(batchId);
        List<StagedUnit> units = staging.listUnits(batchId);
        List<StagedInstallment> installments = staging.listInstallments(batchId);
        List<V1HistoryRecord> history = store.listHistory(batchId);
        V1BalanceConference.Report conference = V1BalanceConference.confer(units, installments);
        Map<String, V1BalanceConference.MatriculaConference> conferenceByKey = new LinkedHashMap<>();
        for (V1BalanceConference.MatriculaConference row : conference.rows()) {
            conferenceByKey.put(row.legacyKey(), row);
        }
        Map<String, List<StagedInstallment>> installmentsByUnit = new LinkedHashMap<>();
        for (StagedInstallment installment : installments) {
            installmentsByUnit.computeIfAbsent(installment.unitLegacyKey(), k -> new ArrayList<>())
                    .add(installment);
        }
        Map<String, List<V1HistoryRecord>> historyByUnit = new LinkedHashMap<>();
        for (V1HistoryRecord record : history) {
            historyByUnit.computeIfAbsent(record.unitLegacyKey(), k -> new ArrayList<>()).add(record);
        }

        int installmentDebits = 0;
        int residualDebits = 0;
        int historyApplied = 0;
        int historyRejected = 0;
        int situations = 0;
        int skippedByConference = 0;

        for (StagedUnit unit : units) {
            if (!isApplied(unit)) {
                continue;
            }
            // (1) Situação CAD-68 — vale mesmo com a conferência divergente:
            // a unidade existe e está ocupada ou vaga, diga o saldo o que disser.
            if (!store.situationRecorded(batchId, unit.id())) {
                String status = V1UnitSituation.occupancyStatusFor(unit);
                situationGateway.applyOccupancyStatus(tenantId, unit.appliedUnitId(), status, actorId);
                store.recordSituation(batchId, unit.id(), unit.appliedUnitId(), status);
                situations++;
            }
            V1BalanceConference.MatriculaConference row = conferenceByKey.get(unit.legacyKey());
            List<V1HistoryRecord> ownHistory = historyByUnit.getOrDefault(unit.legacyKey(), List.of());
            if (row == null || !row.matches()) {
                // Sem saldo conferido, a matrícula não gera débito; o
                // histórico dela também fica encenado, para a correção da
                // carga não misturar aplicado com não conferido.
                skippedByConference++;
                continue;
            }
            // (2) Prestações → débitos, uma a uma.
            List<StagedInstallment> own = installmentsByUnit.getOrDefault(unit.legacyKey(), List.of());
            // financing_term contado DENTRO da unidade em processamento
            // (não no lote): limitação conhecida — um mesmo acordo
            // presente em duas unidades teria o prazo subcontado em cada
            // uma (ver relatório, correções pós-revisão A8).
            Map<String, Integer> termByAgreement = new LinkedHashMap<>();
            for (StagedInstallment installment : own) {
                termByAgreement.merge(installment.legacyAgreementRef(), 1, Integer::sum);
            }
            for (StagedInstallment installment : own) {
                if (store.debitForInstallment(installment.id()).isPresent()) {
                    continue;
                }
                UUID agreementId = store.agreementId(batchId, installment.legacyAgreementRef());
                LocalDate month = installment.dueDate() == null
                        ? config.cutoverMonth()
                        : installment.dueDate().withDayOfMonth(1);
                UUID debitId = debitGateway.createInstallmentDebit(tenantId, actorId,
                        new InstallmentDebitGateway.InstallmentDebit(
                                unit.appliedUnitId(), config.installmentDebitTypeCode(),
                                installment.amountCents(), 1, agreementId,
                                installment.legacyAgreementRef(), installment.installmentNo(),
                                installment.dueDate(), month, month,
                                termByAgreement.get(installment.legacyAgreementRef())));
                store.linkInstallmentDebit(batchId, installment.id(), debitId);
                installmentDebits++;
            }
            // (3) Resíduo do saldo → um débito LEGACY_IMPORT.
            if (row.residualCents() > 0 && store.residualDebitForUnit(unit.id()).isEmpty()) {
                UUID debitId = debitGateway.createResidualDebit(tenantId, actorId,
                        new InstallmentDebitGateway.ResidualDebit(
                                unit.appliedUnitId(), config.residualDebitTypeCode(),
                                row.residualCents(),
                                // ≤100 caracteres (VARCHAR(100) da V4_4): a forma
                                // natural batchId + ":" + legacyKey chegaria a 137
                                // com chave de 100 chars — correção A4.
                                LegacyReferences.residualReference(batchId, unit.legacyKey()),
                                config.cutoverMonth(), config.cutoverMonth()));
                store.linkResidualDebit(batchId, unit.id(), debitId);
                residualDebits++;
            }
            // (4) Histórico mensal.
            for (V1HistoryRecord record : ownHistory) {
                if (record.status() == V1HistoryRecord.Status.APPLIED
                        || store.historyAlreadyApplied(record.unitLegacyKey(), record.referenceMonth())) {
                    continue;
                }
                if (record.hasErrors()) {
                    record.markRejected();
                    store.updateHistory(record);
                    historyRejected++;
                    continue;
                }
                store.markHistoryApplied(record, unit.appliedUnitId(), actorId);
                historyApplied++;
            }
        }
        boolean nothingDone = installmentDebits == 0 && residualDebits == 0
                && historyApplied == 0 && situations == 0;
        return new ApplyOutcome(conference, installmentDebits, residualDebits, historyApplied,
                historyRejected, situations, skippedByConference, nothingDone);
    }

    // ------------------------------------------------------------------
    // Mapeamento declarativo → valores tipados
    // ------------------------------------------------------------------

    private Map<String, Object> mapFields(V1HistoryMapping mapping,
                                          LegacySourceReader.LegacyRow row,
                                          List<LegacyIssue> issues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (V1HistoryMapping.FieldSpec field : mapping.fields()) {
            String raw = row.get(field.source());
            if (raw == null || raw.isBlank()) {
                if (field.required()) {
                    issues.add(LegacyIssue.error(field.source(), "REQUIRED_MISSING",
                            "Campo obrigatório ausente: " + field.source() + "."));
                }
                continue;
            }
            try {
                Object value = transform(field.transform(), raw.strip());
                if (value != null) {
                    values.put(field.target(), value);
                }
            } catch (RuntimeException e) {
                issues.add(LegacyIssue.error(field.source(), "TRANSFORM_INVALID",
                        "Valor inválido para a transformação " + field.transform() + "."));
            }
        }
        return values;
    }

    private static Object transform(String transform, String raw) {
        return switch (transform) {
            case "TEXT" -> raw;
            case "MONTH_YEAR_BR" -> {
                String[] parts = raw.split("/");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("competência fora de MM/AAAA");
                }
                yield LocalDate.of(Integer.parseInt(parts[1]), Integer.parseInt(parts[0]), 1);
            }
            case "DECIMAL_BR" -> new BigDecimal(raw.replace(".", "").replace(',', '.'));
            case "CENTS_BR" -> {
                BigDecimal decimal = new BigDecimal(raw.replace(".", "").replace(',', '.'));
                yield decimal.movePointRight(2).longValueExact();
            }
            case "DATE_BR" -> LocalDate.parse(raw, DATE_BR);
            default -> throw new IllegalArgumentException("transformação desconhecida: " + transform);
        };
    }

    private static boolean isApplied(StagedUnit unit) {
        return unit.status() == StagedUnit.Status.APPLIED && unit.appliedUnitId() != null;
    }

    private LegacyBatch requireBatch(UUID batchId) {
        if (batchId == null) {
            throw new IllegalArgumentException("batchId é obrigatório.");
        }
        return staging.findBatchById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Lote não encontrado: " + batchId));
    }
}
