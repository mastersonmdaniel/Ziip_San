package br.com.ziipsan.commercial.registry.legacy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Motor de importação de legado (P1.2, PLT-16).
 *
 * <p>Pipeline, sempre nesta ordem: <b>carga</b> (lê a fonte pelo mapeamento,
 * transforma, valida e encena) → <b>ensaio</b> (perfil de qualidade +
 * conciliação, sem gravar nada no cadastro nem abrir OS) → <b>conciliação
 * gravada</b> → <b>aplicação</b> (unidades mínimas + OS interna de lote
 * que as referencia e confirma os efeitos pela P1.31).
 *
 * <p>Idempotência em dois níveis: recarregar a mesma (sistema, chave
 * externa) devolve o lote existente sem duplicar nada; reaplicar um lote
 * aplicado é no-op; reaplicar um lote parcial reaproveita as unidades já
 * criadas (pela matrícula única) e abre uma ordem nova para o conjunto.
 *
 * <p>Este serviço não conhece banco nem OS: fala com o staging pelo
 * {@link LegacyStagingStore}, com o cadastro pelo {@link LegacyUnitApplier}
 * e com a OS pelo {@link LegacyBatchOrderGateway}. Por isso os mesmos
 * testes rodam em memória e contra o PostgreSQL.
 *
 * <p>Rejeição na carga: linha sem chave legada ou com chave duplicada no
 * arquivo NÃO é encenada (a chave é a identidade no staging e o índice
 * único a recusaria) — ela volta no relatório da carga, com o número da
 * linha e o motivo, sem valor bruto. Linha com chave válida mas com
 * outros erros É encenada (com os erros), para o relatório de qualidade
 * a mostrar e a aplicação a pular.
 */
public final class LegacyImportService {

    private static final Set<String> SOURCE_KINDS = Set.of("CSV", "SQL_DUMP", "LEGACY_DB");

    private final LegacyStagingStore store;
    private final LegacyUnitApplier applier;
    private final LegacyBatchOrderGateway gateway;

    public LegacyImportService(LegacyStagingStore store, LegacyUnitApplier applier,
                               LegacyBatchOrderGateway gateway) {
        if (store == null || applier == null || gateway == null) {
            throw new IllegalArgumentException("store, applier e gateway são obrigatórios.");
        }
        this.store = store;
        this.applier = applier;
        this.gateway = gateway;
    }

    public record LoadRequest(
            LegacyMapping mapping,
            LegacySourceReader reader,
            String sourceKind,
            String externalBatchKey,
            UUID actorId) {
    }

    public record RejectedRow(int rowNo, List<LegacyIssue> issues) {
    }

    public record LoadOutcome(
            LegacyBatch batch,
            boolean alreadyExisted,
            LegacyQualityProfile profile,
            int stagedUnits,
            List<RejectedRow> rejectedRows) {
    }

    public record LoadInstallmentsOutcome(
            LegacyBatch batch,
            int accepted,
            List<RejectedRow> rejectedRows) {
    }

    public record DryRunReport(
            LegacyBatch batch,
            LegacyQualityProfile profile,
            LegacyReconciliation.Report reconciliation,
            int applicableUnits,
            int rejectedUnits) {
    }

    public record ApplyOutcome(
            LegacyBatch batch,
            UUID orderId,
            int appliedNew,
            int appliedPrevious,
            int rejected,
            boolean alreadyApplied,
            String note) {
    }

    // ------------------------------------------------------------------
    // Carga de unidades
    // ------------------------------------------------------------------

    public LoadOutcome load(LoadRequest request) {
        if (request == null || request.mapping() == null || request.reader() == null) {
            throw new IllegalArgumentException("mapping e reader são obrigatórios.");
        }
        if (request.sourceKind() == null || !SOURCE_KINDS.contains(request.sourceKind())) {
            throw new IllegalArgumentException("sourceKind inválido: " + request.sourceKind()
                    + ". Válidos: " + SOURCE_KINDS);
        }
        if (request.externalBatchKey() == null || request.externalBatchKey().isBlank()) {
            throw new IllegalArgumentException("externalBatchKey é obrigatório.");
        }
        LegacyMapping mapping = request.mapping();
        var existing = store.findBatchByKey(mapping.sourceSystem(), request.externalBatchKey());
        if (existing.isPresent()) {
            List<StagedUnit> units = store.listUnits(existing.get().id());
            List<StagedInstallment> installments = store.listInstallments(existing.get().id());
            return new LoadOutcome(existing.get(), true,
                    LegacyQualityProfile.compute(units, installments), units.size(), List.of());
        }
        List<LegacySourceReader.LegacyRow> rows = request.reader().read();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("A fonte não trouxe nenhuma linha.");
        }
        if (mapping.fields().stream().noneMatch(f -> f.target().equals("LEGACY_KEY"))) {
            throw new IllegalArgumentException("O mapeamento não tem alvo LEGACY_KEY: sem chave, não há staging.");
        }
        LegacyBatch batch = store.createBatch(mapping.sourceSystem(), mapping.name(), mapping.version(),
                request.externalBatchKey(), request.sourceKind(), request.actorId());
        try {
            List<StagedUnit> staged = new ArrayList<>();
            List<RejectedRow> rejected = new ArrayList<>();
            Set<String> seenKeys = new LinkedHashSet<>();
            Set<String> seenMatriculas = new LinkedHashSet<>();
            for (LegacySourceReader.LegacyRow row : rows) {
                StagedUnit unit = mapUnitRow(mapping, row, batch.id());
                if (unit.legacyKey() == null || unit.legacyKey().isBlank()) {
                    rejected.add(new RejectedRow(row.rowNo(), List.of(
                            LegacyIssue.error("legacyKey", "LEGACY_KEY_REQUIRED",
                                    "Linha sem chave legada: não encenada."))));
                    continue;
                }
                if (!seenKeys.add(unit.legacyKey())) {
                    rejected.add(new RejectedRow(row.rowNo(), List.of(
                            LegacyIssue.error("legacyKey", "DUPLICATE_LEGACY_KEY",
                                    "Chave legada duplicada no arquivo: só a primeira ocorrência é encenada."))));
                    continue;
                }
                if (unit.matricula() != null && !seenMatriculas.add(unit.matricula())) {
                    unit.addIssue(LegacyIssue.error("matricula", "MATRICULA_DUPLICATE",
                            "Matrícula duplicada no arquivo: só a primeira ocorrência é aplicável."));
                }
                staged.add(unit);
            }
            store.insertUnits(batch.id(), staged);
            for (StagedUnit unit : staged) {
                store.insertLineage(batch.id(), "UNIT", mapping.sourceSystem(), unit.legacyKey(),
                        mapping.name(), mapping.version(), unit.id());
            }
            return new LoadOutcome(batch, false,
                    LegacyQualityProfile.compute(staged, List.of()), staged.size(), List.copyOf(rejected));
        } catch (RuntimeException e) {
            try {
                store.deleteBatch(batch.id());
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    // ------------------------------------------------------------------
    // Carga de prestações (para a P2.10)
    // ------------------------------------------------------------------

    public LoadInstallmentsOutcome loadInstallments(UUID batchId, LegacyMapping mapping,
                                                   LegacySourceReader reader) {
        LegacyBatch batch = requireBatch(batchId);
        if (batch.isApplied()) {
            throw new IllegalStateException("O lote já foi aplicado: não aceita mais prestações.");
        }
        if (mapping == null || !mapping.hasInstallments()) {
            throw new IllegalArgumentException("O mapeamento não tem seção de prestações.");
        }
        if (reader == null) {
            throw new IllegalArgumentException("reader é obrigatório.");
        }
        Map<String, StagedUnit> unitsByKey = new LinkedHashMap<>();
        for (StagedUnit unit : store.listUnits(batchId)) {
            unitsByKey.put(unit.legacyKey(), unit);
        }
        Set<String> seen = new LinkedHashSet<>();
        for (StagedInstallment staged : store.listInstallments(batchId)) {
            seen.add(keyOf(staged.unitLegacyKey(), staged.legacyAgreementRef(), staged.installmentNo()));
        }
        List<StagedInstallment> accepted = new ArrayList<>();
        List<RejectedRow> rejected = new ArrayList<>();
        for (LegacySourceReader.LegacyRow row : reader.read()) {
            List<LegacyIssue> issues = new ArrayList<>();
            Map<String, Object> values = mapFields(mapping.installments().fields(), row, issues);
            String unitKey = stringOf(values.get("UNIT_LEGACY_KEY"));
            String agreement = stringOf(values.get("AGREEMENT_REF"));
            Long installmentNo = longOf(values.get("INSTALLMENT_NO"), issues, "installmentNo");
            Long amount = longOf(values.get("AMOUNT_CENTS"), issues, "amountCents");
            LocalDate dueDate = dateOf(values.get("DUE_DATE"), issues, "dueDate");
            if (unitKey == null) {
                issues.add(LegacyIssue.error("unitLegacyKey", "REQUIRED_MISSING",
                        "Prestação sem chave da unidade: não encenada."));
            } else if (!unitsByKey.containsKey(unitKey)) {
                issues.add(LegacyIssue.error("unitLegacyKey", "REFERENCE_UNKNOWN",
                        "Prestação sem unidade encenada no lote: não encenada."));
            }
            if (agreement == null) {
                issues.add(LegacyIssue.error("agreementRef", "REQUIRED_MISSING",
                        "Prestação sem referência ao acordo do legado: não encenada."));
            }
            if (installmentNo == null) {
                issues.add(LegacyIssue.error("installmentNo", "REQUIRED_MISSING",
                        "Prestação sem número: não encenada."));
            }
            if (amount == null) {
                issues.add(LegacyIssue.error("amountCents", "REQUIRED_MISSING",
                        "Prestação sem valor: não encenada."));
            }
            boolean hasErrors = issues.stream().anyMatch(LegacyIssue::isError);
            if (!hasErrors && !seen.add(keyOf(unitKey, agreement, installmentNo.intValue()))) {
                issues.add(LegacyIssue.error("installmentNo", "DUPLICATE_INSTALLMENT",
                        "Prestação duplicada no lote: não encenada."));
                hasErrors = true;
            }
            if (hasErrors) {
                rejected.add(new RejectedRow(row.rowNo(), List.copyOf(issues)));
                continue;
            }
            StagedInstallment installment = new StagedInstallment(UUID.randomUUID(), batchId,
                    unitsByKey.get(unitKey).id(), unitKey, agreement, installmentNo.intValue(),
                    dueDate, amount);
            accepted.add(installment);
        }
        store.insertInstallments(batchId, accepted);
        for (StagedInstallment installment : accepted) {
            store.insertLineage(batchId, "INSTALLMENT", batch.sourceSystem(),
                    installment.unitLegacyKey() + ":" + installment.legacyAgreementRef()
                            + "#" + installment.installmentNo(),
                    batch.mappingName(), batch.mappingVersion(), installment.id());
        }
        return new LoadInstallmentsOutcome(batch, accepted.size(), List.copyOf(rejected));
    }

    // ------------------------------------------------------------------
    // Ensaio e conciliação
    // ------------------------------------------------------------------

    /** Ensaio sem gravar: perfil + conciliação lidos do staging, sem tocar no cadastro nem na OS. */
    public DryRunReport dryRun(UUID batchId, LegacyReconciliation.LegacyTotals totals) {
        LegacyBatch batch = requireBatch(batchId);
        List<StagedUnit> units = store.listUnits(batchId);
        List<StagedInstallment> installments = store.listInstallments(batchId);
        int applicable = (int) units.stream().filter(StagedUnit::isApplicable).count();
        int rejected = (int) units.stream().filter(StagedUnit::hasErrors).count();
        return new DryRunReport(batch, LegacyQualityProfile.compute(units, installments),
                LegacyReconciliation.reconcile(units, totals), applicable, rejected);
    }

    /** Conciliação gravada (o ensaio em si não grava; este passo persiste o relatório). */
    public LegacyReconciliation.Report reconcile(UUID batchId, LegacyReconciliation.LegacyTotals totals) {
        LegacyBatch batch = requireBatch(batchId);
        LegacyReconciliation.Report report =
                LegacyReconciliation.reconcile(store.listUnits(batchId), totals);
        store.saveReconciliation(batch.id(), report.rows());
        return report;
    }

    // ------------------------------------------------------------------
    // Aplicação
    // ------------------------------------------------------------------

    public ApplyOutcome apply(UUID batchId, UUID actorId) {
        LegacyBatch batch = requireBatch(batchId);
        if (actorId == null) {
            throw new IllegalArgumentException("actorId é obrigatório para aplicar o lote.");
        }
        List<StagedUnit> units = store.listUnits(batchId);
        if (batch.isApplied()) {
            int previous = (int) units.stream().filter(u -> u.status() == StagedUnit.Status.APPLIED).count();
            return new ApplyOutcome(batch, batch.batchOrderId(), 0, previous, 0, true,
                    "Lote já aplicado: reaplicação não duplica unidades nem OS.");
        }
        List<StagedUnit> alreadyApplied = new ArrayList<>();
        List<StagedUnit> candidates = new ArrayList<>();
        int rejected = 0;
        for (StagedUnit unit : units) {
            if (unit.status() == StagedUnit.Status.APPLIED) {
                alreadyApplied.add(unit);
            } else if (unit.hasErrors()) {
                unit.markRejected();
                store.updateUnit(unit);
                rejected++;
            } else if (unit.status() == StagedUnit.Status.PENDING) {
                candidates.add(unit);
            }
        }
        List<StagedUnit> appliedNew = new ArrayList<>();
        for (StagedUnit unit : candidates) {
            LegacyUnitApplier.Outcome outcome = applier.apply(unit, actorId);
            if (!outcome.applied()) {
                for (LegacyIssue issue : outcome.blockingIssues()) {
                    unit.addIssue(issue);
                }
                unit.markRejected();
                store.updateUnit(unit);
                rejected++;
            } else {
                unit.markApplied(outcome.unitId());
                store.updateUnit(unit);
                appliedNew.add(unit);
            }
        }
        List<StagedUnit> orderUnits = new ArrayList<>(appliedNew);
        orderUnits.addAll(alreadyApplied);
        if (orderUnits.isEmpty()) {
            return new ApplyOutcome(batch, null, 0, alreadyApplied.size(), rejected, false,
                    "Nenhuma unidade aplicável: nenhuma OS foi aberta e o lote continua STAGED.");
        }
        LegacyBatchOrderGateway.OpenedOrder order = gateway.openBatchOrder(batch, orderUnits, actorId);
        store.updateBatchOrder(batch.id(), order.orderId());
        store.markBatchApplied(batch.id());
        LegacyBatch applied = new LegacyBatch(batch.id(), batch.sourceSystem(), batch.mappingName(),
                batch.mappingVersion(), batch.externalBatchKey(), batch.sourceKind(),
                LegacyBatch.Status.APPLIED, order.orderId());
        return new ApplyOutcome(applied, order.orderId(), appliedNew.size(),
                alreadyApplied.size(), rejected, false,
                "Lote aplicado: " + appliedNew.size() + " unidade(s) nova(s) confirmada(s) pela OS de lote.");
    }

    // ------------------------------------------------------------------
    // Mapeamento de linha → staging
    // ------------------------------------------------------------------

    private StagedUnit mapUnitRow(LegacyMapping mapping, LegacySourceReader.LegacyRow row, UUID batchId) {
        List<LegacyIssue> issues = new ArrayList<>();
        Map<String, Object> values = mapFields(mapping.fields(), row, issues);
        return new StagedUnit(
                UUID.randomUUID(), batchId, row.rowNo(),
                stringOf(values.get("LEGACY_KEY")),
                stringOf(values.get("MATRICULA")),
                stringOf(values.get("MATRICULA_DV")),
                stringOf(values.get("LOCALITY_CODE")),
                stringOf(values.get("SECTOR_CODE")),
                stringOf(values.get("BLOCK_CODE")),
                stringOf(values.get("ROUTE_CODE")),
                stringOf(values.get("CATEGORY_CODE")),
                longOf(values.get("LOT"), issues, "lot"),
                longOf(values.get("SUBLOT"), issues, "sublot"),
                longOf(values.get("READING_SEQUENCE"), issues, "readingSequence"),
                longOf(values.get("BALANCE_CENTS"), issues, "balanceCents"),
                dateOf(values.get("CONNECTED_ON"), issues, "connectedOn"),
                stringOf(values.get("HOLDER_NAME")),
                documentKind(values.get("HOLDER_DOCUMENT")),
                documentMasked(values.get("HOLDER_DOCUMENT")),
                documentValid(values.get("HOLDER_DOCUMENT")),
                issues, StagedUnit.Status.PENDING, null);
    }

    /** Aplica transformações + validações de uma seção; erros viram issues, nunca exceção de dado. */
    private Map<String, Object> mapFields(List<LegacyMapping.FieldMapping> fields,
                                          LegacySourceReader.LegacyRow row,
                                          List<LegacyIssue> issues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (LegacyMapping.FieldMapping field : fields) {
            String raw = row.get(field.source());
            if (raw == null || raw.isBlank()) {
                if (field.required()) {
                    issues.add(LegacyIssue.error(field.source(), "REQUIRED_MISSING",
                            "Campo obrigatório ausente: " + field.source() + "."));
                }
                continue;
            }
            Object value;
            try {
                value = LegacyTransform.apply(field.transform(), raw);
            } catch (IllegalArgumentException e) {
                issues.add(LegacyIssue.error(field.source(), "TRANSFORM_INVALID",
                        "Valor inválido para a transformação " + field.transform().name() + "."));
                continue;
            }
            if (value == null) {
                continue;
            }
            if (field.validation().equals("DOCUMENT_VALID")
                    && value instanceof LegacyTransform.DocumentValue document && !document.valid()) {
                issues.add(LegacyIssue.error(field.source(), "INVALID_DOCUMENT",
                        "Documento inválido: a linha fica fora do lote."));
            }
            if (field.validation().equals("NON_NEGATIVE") && value instanceof Long number && number < 0) {
                issues.add(LegacyIssue.error(field.source(), "NEGATIVE_VALUE",
                        "Valor negativo onde só vale não-negativo."));
            }
            if (field.validation().equals("MATRICULA_DIGITS") && value instanceof String text
                    && !text.chars().allMatch(Character::isDigit)) {
                issues.add(LegacyIssue.error(field.source(), "MATRICULA_DIGITS",
                        "Matrícula precisa ter só dígitos."));
            }
            values.put(field.target(), value);
        }
        return values;
    }

    private static String stringOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        return String.valueOf(value);
    }

    private static Long longOf(Object value, List<LegacyIssue> issues, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long number) {
            return number;
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                issues.add(LegacyIssue.error(field, "TRANSFORM_INVALID",
                        "Valor inteiro inválido."));
                return null;
            }
        }
        return null;
    }

    private static LocalDate dateOf(Object value, List<LegacyIssue> issues, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof String text) {
            try {
                return LocalDate.parse(text.trim());
            } catch (RuntimeException e) {
                issues.add(LegacyIssue.error(field, "TRANSFORM_INVALID",
                        "Data inválida."));
                return null;
            }
        }
        return null;
    }

    private static String documentKind(Object value) {
        if (value instanceof LegacyTransform.DocumentValue document) {
            return document.kind();
        }
        return null;
    }

    private static String documentMasked(Object value) {
        if (value instanceof LegacyTransform.DocumentValue document) {
            return document.masked();
        }
        return null;
    }

    private static Boolean documentValid(Object value) {
        if (value instanceof LegacyTransform.DocumentValue document) {
            return document.valid();
        }
        return null;
    }

    private static String keyOf(String unitKey, String agreement, int installmentNo) {
        return unitKey + "|" + agreement + "#" + installmentNo;
    }

    private LegacyBatch requireBatch(UUID batchId) {
        if (batchId == null) {
            throw new IllegalArgumentException("batchId é obrigatório.");
        }
        return store.findBatchById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Lote não encontrado: " + batchId));
    }
}
