package br.com.ziipsan.operations.metering.exchange;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.operations.metering.domain.MeterReading;
import br.com.ziipsan.operations.metering.domain.ReadingAnomaly;
import br.com.ziipsan.operations.metering.domain.ReadingImportBatch;
import br.com.ziipsan.operations.metering.domain.ReadingImportRecord;
import br.com.ziipsan.operations.metering.domain.ReadingTask;
import br.com.ziipsan.operations.metering.domain.ReadingVendor;
import br.com.ziipsan.operations.metering.repository.MeterReadingRepository;
import br.com.ziipsan.operations.metering.repository.ReadingAnomalyRepository;
import br.com.ziipsan.operations.metering.repository.ReadingImportBatchRepository;
import br.com.ziipsan.operations.metering.repository.ReadingImportRecordRepository;
import br.com.ziipsan.operations.metering.repository.ReadingTaskRepository;
import br.com.ziipsan.operations.metering.repository.ReadingVendorRepository;
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import br.com.ziipsan.operations.metering.rules.ReadingStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Leitura terceirizada (P2.7, sobre a V5_3): criacao das tarefas de leitura
 * ligadas a rota e ao ciclo, exportacao do arquivo de ida e importacao do
 * arquivo de volta, os dois no leiaute do fornecedor
 * ({@link VendorReadingLayout}), e a idempotencia do aceite nos dois niveis:
 *
 * <ul>
 *   <li><b>mesmo arquivo</b>: o lote e identificado pelo SHA-256 do conteudo
 *       ({@code reading_import_batches.file_sha256}, UNIQUE por tenant). O
 *       reenvio devolve o lote ja processado com {@code duplicateFile}, sem
 *       criar registro nem leitura;</li>
 *   <li><b>mesmo registro</b>: cada linha tem a sua chave
 *       ({@link VendorReadingLayout#recordKey}). Se ja existe um registro
 *       {@code IMPORTED} com a chave, a linha nova vira um registro
 *       {@code DUPLICATE} de auditoria e nenhuma leitura e criada. A mesma
 *       chave vai em {@code meter_readings.idempotency_key}, cuja UNIQUE
 *       parcial da V5_4 e a trava final no banco.</li>
 * </ul>
 *
 * <p>Ausencia e erro explicito, nunca padrao: fornecedor inexistente ou
 * inativo, leiaute diferente do contratado, tarefa duplicada, rota sem
 * tarefas e linha sem tarefa correspondente sao recusados com o motivo -- a
 * linha recusada vira registro {@code ERROR} e nao vira leitura, como no
 * retorno de leitura interno do modulo. A volta tambem recusa, com registro
 * {@code ERROR} proprio, a linha cuja tarefa encontrada pertence a outro
 * fornecedor ({@code TASK_VENDOR_MISMATCH}) e a linha cuja instalacao
 * diverge da instalacao da tarefa ({@code INSTALLATION_MISMATCH}). A leitura importada nasce com
 * {@code source = COLLECTOR} (a origem "coletor" que a V5_1 ja preve) e
 * {@code readingStatus} REALIZED com leitura, NOT_REALIZED sem leitura (so
 * com anormalidade, como manda o CHECK de {@code meter_readings}).
 *
 * <p>Classe simples, sem anotacao de componente, como os demais servicos do
 * projeto: quem a instancia e a fiacao da aplicacao. Os tres metodos publicos
 * de escrita sao {@link Transactional} (correcao RG-LOTE2/S2, revisao geral
 * de 08/11/2026): a revisao cruzada da P2.7 ja tinha nomeado a ausencia de
 * transacao de lote como pendencia da integracao, e a checagem da revisao
 * geral na main (HEAD 19f5890) confirmou que nenhum chamador, fachada ou job
 * na raiz de composicao embrulha estas chamadas em transacao -- o scan da
 * aplicacao nem alcanca este modulo ainda. A atomicidade, entao, e declarada
 * aqui, no ponto de menor impacto: uma transacao por operacao publica
 * (criacao das tarefas, exportacao e importacao do lote inteiro). Para a
 * anotacao valer, a fiacao deve expor a instancia como bean gerenciado pelo
 * Spring (o mesmo requisito dos demais servicos {@code @Transactional} do
 * projeto); instancia criada fora do contexto nao e interceptada.
 *
 * <p>Consequencia assumida da transacao de lote na importacao: o caminho de
 * arquivo estruturalmente invalido ({@code parse} lanca excecao) marcava o
 * lote {@code REJECTED} e o gravava antes de relancar; com a transacao, essa
 * gravacao desfaz junto com o restante do lote e o lote rejeitado nao fica
 * persistido. A excecao e o motivo continuam propagando ao chamador, e o
 * reenvio do mesmo arquivo e rejeitado de novo, sem criar leitura nem
 * registro -- nenhum efeito parcial sobrevive em nenhum dos dois desenhos.
 */
public class OutsourcedReadingService {

    private final ReadingVendorRepository vendors;
    private final ReadingTaskRepository tasks;
    private final ReadingImportBatchRepository batches;
    private final ReadingImportRecordRepository records;
    private final MeterReadingRepository readings;
    private final ReadingAnomalyRepository anomalies;
    private final Clock clock;

    public OutsourcedReadingService(ReadingVendorRepository vendors, ReadingTaskRepository tasks,
                                    ReadingImportBatchRepository batches,
                                    ReadingImportRecordRepository records,
                                    MeterReadingRepository readings,
                                    ReadingAnomalyRepository anomalies, Clock clock) {
        this.vendors = Objects.requireNonNull(vendors, "O repositorio de fornecedores e obrigatorio");
        this.tasks = Objects.requireNonNull(tasks, "O repositorio de tarefas e obrigatorio");
        this.batches = Objects.requireNonNull(batches, "O repositorio de lotes e obrigatorio");
        this.records = Objects.requireNonNull(records, "O repositorio de registros e obrigatorio");
        this.readings = Objects.requireNonNull(readings, "O repositorio de leituras e obrigatorio");
        this.anomalies = Objects.requireNonNull(anomalies, "O repositorio de anormalidades e obrigatorio");
        this.clock = Objects.requireNonNull(clock, "O relogio e obrigatorio");
    }

    /** Uma unidade a ler na criacao das tarefas: o que o ciclo entrega para uma rota e referencia. */
    public record TaskAssignment(UUID unitId, UUID installationId, MeasurementKind kind, int sequenceNo,
                                 Integer previousReading, LocalDate plannedOn) {
    }

    /** O vinculo da tarefa com o ciclo da V4_3 (grupo, referencia e item do cronograma da leitura). */
    public record CycleLink(UUID groupId, UUID billingReferenceId, UUID cycleScheduleItemId) {
    }

    /** Resultado de uma importacao: o lote e os contadores; {@code duplicateFile} marca o reenvio identico. */
    public record ImportOutcome(ReadingImportBatch batch, boolean duplicateFile, int imported,
                                int duplicates, int errors) {
    }

    /**
     * Cria as tarefas de uma rota e referencia para o fornecedor, ligadas ao
     * ciclo pelo {@link CycleLink}. Tarefa repetida (mesma unidade, tipo,
     * rota e referencia) e recusada antes de gravar qualquer uma -- inclusive
     * a repeticao dentro da propria lista de entrada, detectada em memoria
     * antes de qualquer consulta ou gravacao.
     */
    @Transactional
    public List<ReadingTask> createTasks(UUID tenantId, UUID vendorId, String routeCode, YearMonth reference,
                                         CycleLink cycleLink, List<TaskAssignment> assignments, UUID actor) {
        ReadingVendor vendor = requireActiveVendor(tenantId, vendorId);
        Objects.requireNonNull(routeCode, "A rota e obrigatoria");
        Objects.requireNonNull(reference, "A referencia e obrigatoria");
        Objects.requireNonNull(cycleLink, "O vinculo com o ciclo e obrigatorio");
        if (assignments == null || assignments.isEmpty()) {
            throw new ApiException.Conflict("reading-tasks-empty",
                    "Nenhuma unidade informada para criar as tarefas de leitura da rota " + routeCode + ".");
        }
        LocalDate month = reference.atDay(1);
        Set<String> seenInList = new HashSet<>();
        for (TaskAssignment assignment : assignments) {
            if (!seenInList.add(assignment.unitId() + "|" + assignment.kind())) {
                throw new ApiException.Conflict("reading-task-duplicate",
                        "A propria lista repete a tarefa da unidade " + assignment.unitId() + " ("
                                + assignment.kind() + ") na rota " + routeCode + " em " + reference + ".");
            }
        }
        for (TaskAssignment assignment : assignments) {
            if (tasks.existsByTenantIdAndReferenceMonthAndRouteCodeAndUnitIdAndKind(
                    tenantId, month, routeCode, assignment.unitId(), assignment.kind())) {
                throw new ApiException.Conflict("reading-task-duplicate",
                        "Ja existe tarefa de leitura para a unidade " + assignment.unitId() + " ("
                                + assignment.kind() + ") na rota " + routeCode + " em " + reference + ".");
            }
        }
        List<ReadingTask> created = new ArrayList<>();
        for (TaskAssignment assignment : assignments) {
            ReadingTask task = new ReadingTask();
            task.setTenantId(tenantId);
            task.setVendorId(vendor.getId());
            task.setRouteCode(routeCode);
            task.setReferenceMonth(reference);
            task.setGroupId(cycleLink.groupId());
            task.setBillingReferenceId(cycleLink.billingReferenceId());
            task.setCycleScheduleItemId(cycleLink.cycleScheduleItemId());
            task.setUnitId(assignment.unitId());
            task.setInstallationId(assignment.installationId());
            task.setKind(assignment.kind());
            task.setSequenceNo(assignment.sequenceNo());
            task.setPreviousReading(assignment.previousReading());
            task.setPlannedOn(assignment.plannedOn());
            task.setStatus(ReadingTask.Status.PENDING);
            task.setCreatedBy(actor);
            task.setUpdatedBy(actor);
            created.add(tasks.save(task));
        }
        return created;
    }

    /**
     * Arquivo de ida do fornecedor: as tarefas da rota e referencia no
     * leiaute dele, na ordem de leitura. Tarefas PENDING viram EXPORTED;
     * exportar de novo devolve o mesmo arquivo (as ja exportadas entram de
     * novo), porque o fornecedor pode ter perdido a ida -- quem protege a
     * volta e a idempotencia, nao a ida.
     */
    @Transactional
    public String exportFile(UUID tenantId, UUID vendorId, String routeCode, YearMonth reference, UUID actor) {
        ReadingVendor vendor = requireActiveVendor(tenantId, vendorId);
        requireLayout(vendor);
        List<ReadingTask> routeTasks = tasks
                .findByTenantIdAndVendorIdAndRouteCodeAndReferenceMonthOrderBySequenceNoAsc(
                        tenantId, vendorId, routeCode, reference.atDay(1))
                .stream()
                .filter(t -> t.getStatus() == ReadingTask.Status.PENDING
                        || t.getStatus() == ReadingTask.Status.EXPORTED)
                .toList();
        if (routeTasks.isEmpty()) {
            throw new ApiException.Conflict("reading-export-empty",
                    "Nenhuma tarefa pendente ou exportada na rota " + routeCode + " em " + reference
                            + " para o fornecedor " + vendor.getCode() + ".");
        }
        List<VendorReadingLayout.VendorLine> lines = new ArrayList<>();
        for (ReadingTask task : routeTasks) {
            lines.add(new VendorReadingLayout.VendorLine(task.getSequenceNo(), vendor.getCode(),
                    task.getRouteCode(), task.referenceMonth(), task.getUnitId(), task.getInstallationId(),
                    task.getKind(), task.getPlannedOn(), task.getPreviousReading(),
                    null, null, null, null));
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (ReadingTask task : routeTasks) {
            if (task.getStatus() == ReadingTask.Status.PENDING) {
                task.setStatus(ReadingTask.Status.EXPORTED);
                task.setExportedAt(now);
                task.setUpdatedBy(actor);
                tasks.save(task);
            }
        }
        return VendorReadingLayout.formatTasks(lines);
    }

    /**
     * Importa o arquivo de volta do fornecedor. Reenvio do mesmo conteudo
     * devolve o lote original com {@code duplicateFile = true} e nao toca em
     * registros nem leituras. Nas linhas: registro ja importado vira
     * DUPLICATE; linha sem tarefa, sem data, sem leitura e sem anormalidade,
     * ou com anormalidade fora do catalogo vira ERROR com o motivo; as demais
     * viram leitura e cumprem a tarefa (READ com leitura, NOT_READ sem).
     */
    @Transactional
    public ImportOutcome importFile(UUID tenantId, UUID vendorId, String fileName, String externalRef,
                                    String content, UUID actor) {
        ReadingVendor vendor = requireActiveVendor(tenantId, vendorId);
        requireLayout(vendor);
        Objects.requireNonNull(content, "O conteudo do arquivo e obrigatorio");
        String sha256 = VendorReadingLayout.sha256Hex(content);
        Optional<ReadingImportBatch> already = batches.findByTenantIdAndFileSha256(tenantId, sha256);
        if (already.isPresent()) {
            ReadingImportBatch batch = already.get();
            return new ImportOutcome(batch, true, batch.getImportedRecords(),
                    batch.getDuplicateRecords(), batch.getErrorRecords());
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        ReadingImportBatch batch = new ReadingImportBatch();
        batch.setTenantId(tenantId);
        batch.setVendorId(vendor.getId());
        batch.setFileName(Objects.requireNonNull(fileName, "O nome do arquivo e obrigatorio"));
        batch.setFileSha256(sha256);
        batch.setLayoutCode(vendor.getLayoutCode());
        batch.setExternalRef(externalRef);
        batch.setStatus(ReadingImportBatch.Status.RECEIVED);
        batch.setReceivedAt(now);
        batch.setCreatedBy(actor);
        batch.setUpdatedBy(actor);
        batch = batches.save(batch);

        List<VendorReadingLayout.ParsedLine> parsed;
        try {
            parsed = VendorReadingLayout.parse(content);
        } catch (RuntimeException e) {
            batch.setStatus(ReadingImportBatch.Status.REJECTED);
            batch.setProcessedAt(OffsetDateTime.now(clock));
            batch.setUpdatedBy(actor);
            batches.save(batch);
            throw e;
        }

        int imported = 0;
        int duplicates = 0;
        int errors = 0;
        for (VendorReadingLayout.ParsedLine parsedLine : parsed) {
            RecordResult result = importLine(tenantId, vendor, batch, parsedLine, actor);
            switch (result) {
                case IMPORTED -> imported++;
                case DUPLICATE -> duplicates++;
                case ERROR -> errors++;
            }
        }
        batch.setTotalRecords(parsed.size());
        batch.setImportedRecords(imported);
        batch.setDuplicateRecords(duplicates);
        batch.setErrorRecords(errors);
        batch.setStatus(ReadingImportBatch.Status.PROCESSED);
        batch.setProcessedAt(OffsetDateTime.now(clock));
        batch.setUpdatedBy(actor);
        batch = batches.save(batch);
        return new ImportOutcome(batch, false, imported, duplicates, errors);
    }

    private enum RecordResult { IMPORTED, DUPLICATE, ERROR }

    private RecordResult importLine(UUID tenantId, ReadingVendor vendor, ReadingImportBatch batch,
                                    VendorReadingLayout.ParsedLine parsedLine, UUID actor) {
        if (parsedLine.isError()) {
            saveRecord(tenantId, batch, parsedLine.lineNo(), null, null, null, null,
                    ReadingImportRecord.Status.ERROR, "LINE_UNREADABLE", parsedLine.error(), actor);
            return RecordResult.ERROR;
        }
        VendorReadingLayout.VendorLine line = parsedLine.line();
        if (line.unitId() == null || line.kind() == null || line.reference() == null
                || line.routeCode() == null) {
            saveRecord(tenantId, batch, parsedLine.lineNo(), null, line.unitId(), null, null,
                    ReadingImportRecord.Status.ERROR, "LINE_INCOMPLETE",
                    "Linha sem unidade, tipo, rota ou referencia: nao da para saber de que tarefa ela e", actor);
            return RecordResult.ERROR;
        }
        if (line.vendorCode() != null && !line.vendorCode().equals(vendor.getCode())) {
            saveRecord(tenantId, batch, parsedLine.lineNo(), null, line.unitId(), null, null,
                    ReadingImportRecord.Status.ERROR, "VENDOR_MISMATCH",
                    "Linha de outro fornecedor (" + line.vendorCode() + ") no arquivo de "
                            + vendor.getCode(), actor);
            return RecordResult.ERROR;
        }
        String recordKey = VendorReadingLayout.recordKey(vendor.getCode(), line.routeCode(),
                line.reference(), line.unitId(), line.kind());
        if (records.findByTenantIdAndRecordKeyAndStatus(tenantId, recordKey,
                ReadingImportRecord.Status.IMPORTED).isPresent()) {
            saveRecord(tenantId, batch, parsedLine.lineNo(), recordKey, line.unitId(), null, null,
                    ReadingImportRecord.Status.DUPLICATE, null, null, actor);
            return RecordResult.DUPLICATE;
        }
        Optional<ReadingTask> found = tasks.findByTenantIdAndReferenceMonthAndRouteCodeAndUnitIdAndKind(
                tenantId, line.reference().atDay(1), line.routeCode(), line.unitId(), line.kind());
        if (found.isEmpty()) {
            saveRecord(tenantId, batch, parsedLine.lineNo(), recordKey, line.unitId(), null, null,
                    ReadingImportRecord.Status.ERROR, "TASK_NOT_FOUND",
                    "Nenhuma tarefa de leitura para esta unidade, tipo, rota e referencia", actor);
            return RecordResult.ERROR;
        }
        ReadingTask task = found.get();
        if (!Objects.equals(task.getVendorId(), vendor.getId())) {
            return error(tenantId, batch, parsedLine.lineNo(), recordKey, line, task,
                    "TASK_VENDOR_MISMATCH",
                    "A tarefa encontrada pertence a outro fornecedor (" + task.getVendorId()
                            + "), nao ao fornecedor do arquivo (" + vendor.getCode() + ")", actor);
        }
        if (line.installationId() != null && !line.installationId().equals(task.getInstallationId())) {
            return error(tenantId, batch, parsedLine.lineNo(), recordKey, line, task,
                    "INSTALLATION_MISMATCH",
                    "A instalacao da linha (" + line.installationId()
                            + ") diverge da instalacao da tarefa (" + task.getInstallationId() + ")", actor);
        }
        if (line.readOn() == null) {
            return error(tenantId, batch, parsedLine.lineNo(), recordKey, line, task,
                    "READING_DATE_MISSING", "Linha sem DATA_LEITURA", actor);
        }
        UUID anomalyId = null;
        if (line.anomalyCode() != null) {
            Optional<ReadingAnomaly> anomaly = anomalies.findByCode(line.anomalyCode());
            if (anomaly.isEmpty()) {
                return error(tenantId, batch, parsedLine.lineNo(), recordKey, line, task,
                        "ANOMALY_NOT_IN_CATALOG",
                        "Anormalidade fora do catalogo do tenant: " + line.anomalyCode(), actor);
            }
            anomalyId = anomaly.get().getId();
        }
        if (line.reading() == null && anomalyId == null) {
            return error(tenantId, batch, parsedLine.lineNo(), recordKey, line, task,
                    "READING_AND_ANOMALY_MISSING",
                    "Linha sem LEITURA e sem COD_ANOMALIA: nao ha o que registrar", actor);
        }
        if (task.getStatus() == ReadingTask.Status.READ || task.getStatus() == ReadingTask.Status.NOT_READ) {
            saveRecord(tenantId, batch, parsedLine.lineNo(), recordKey, line.unitId(), task.getId(), null,
                    ReadingImportRecord.Status.DUPLICATE, null, null, actor);
            return RecordResult.DUPLICATE;
        }

        MeterReading reading = new MeterReading();
        reading.setTenantId(tenantId);
        reading.setUnitId(task.getUnitId());
        reading.setInstallationId(task.getInstallationId());
        reading.setKind(task.getKind());
        reading.setReferenceMonth(line.reference());
        reading.setReadOn(line.readOn());
        reading.setReading(line.reading());
        reading.setReadingStatus(line.reading() != null ? ReadingStatus.REALIZED : ReadingStatus.NOT_REALIZED);
        reading.setAnomalyId(anomalyId);
        reading.setInformedConsumption(line.informedConsumption());
        reading.setSource(MeterReading.Source.COLLECTOR);
        reading.setCapturedAt(OffsetDateTime.now(clock));
        reading.setIdempotencyKey(recordKey);
        reading.setCreatedBy(actor);
        reading.setUpdatedBy(actor);
        reading = readings.save(reading);

        task.setStatus(line.reading() != null ? ReadingTask.Status.READ : ReadingTask.Status.NOT_READ);
        task.setReadingId(reading.getId());
        task.setReadingReference(line.reference().atDay(1));
        task.setUpdatedBy(actor);
        tasks.save(task);

        saveRecord(tenantId, batch, parsedLine.lineNo(), recordKey, line.unitId(), task.getId(),
                reading.getId(), ReadingImportRecord.Status.IMPORTED, null, null, actor);
        return RecordResult.IMPORTED;
    }

    private RecordResult error(UUID tenantId, ReadingImportBatch batch, int lineNo, String recordKey,
                               VendorReadingLayout.VendorLine line, ReadingTask task, String code,
                               String message, UUID actor) {
        saveRecord(tenantId, batch, lineNo, recordKey, line.unitId(), task.getId(), null,
                ReadingImportRecord.Status.ERROR, code, message, actor);
        return RecordResult.ERROR;
    }

    private ReadingImportRecord saveRecord(UUID tenantId, ReadingImportBatch batch, int lineNo,
                                           String recordKey, UUID unitId, UUID taskId, UUID readingId,
                                           ReadingImportRecord.Status status, String errorCode,
                                           String errorMessage, UUID actor) {
        ReadingImportRecord record = new ReadingImportRecord();
        record.setTenantId(tenantId);
        record.setBatchId(batch.getId());
        record.setLineNo(lineNo);
        record.setRecordKey(recordKey == null ? "LINE:" + batch.getId() + ":" + lineNo : recordKey);
        record.setUnitId(unitId);
        record.setTaskId(taskId);
        record.setReadingId(readingId);
        record.setStatus(status);
        record.setErrorCode(errorCode);
        record.setErrorMessage(errorMessage);
        record.setCreatedBy(actor);
        record.setUpdatedBy(actor);
        return records.save(record);
    }

    private ReadingVendor requireActiveVendor(UUID tenantId, UUID vendorId) {
        ReadingVendor vendor = vendors.findByTenantIdAndId(tenantId, vendorId)
                .orElseThrow(() -> new ApiException.NotFound(
                        "Fornecedor de leitura nao encontrado para este tenant."));
        if (!vendor.isActive()) {
            throw new ApiException.Conflict("reading-vendor-inactive",
                    "O fornecedor " + vendor.getCode() + " esta inativo e nao pode receber nem devolver arquivos.");
        }
        return vendor;
    }

    private void requireLayout(ReadingVendor vendor) {
        if (!VendorReadingLayout.LAYOUT_CODE.equals(vendor.getLayoutCode())) {
            throw new ApiException.Conflict("reading-vendor-layout-unsupported",
                    "O leiaute " + vendor.getLayoutCode() + " do fornecedor " + vendor.getCode()
                            + " nao e o leiaute implementado (" + VendorReadingLayout.LAYOUT_CODE + ").");
        }
    }
}
