package br.com.ziipsan.commercial.registry.importer;

import br.com.ziipsan.commercial.registry.application.RegistryContext;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;
import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * P1.40: orquestração do importador genérico.
 *
 * <p>Fluxo (validação e aplicação assíncronas pelo worker da P1.9, tipos
 * {@code registry.import.*} na outbox; o loop do worker em perfil
 * {@code worker} ainda não existe no app — ver o handler — então o
 * controlador só enfileira, e o handler executa; sem worker, o job fica em
 * RECEIVED até o worker rodar, como a exportação da P1.12):
 * RECEIVED → (worker) VALIDATED → (worker) APPLIED, com FAILED como estado
 * de retomada nos dois passos e REVERSED como terminal da correção.
 *
 * <p>Idempotência (exigência da P1.9, entrega ao menos uma vez):
 * validar um job já VALIDATED/APPLIED/REVERSED é no-op; aplicar um job já
 * APPLIED é no-op; reverter um job já REVERSED é no-op; linhas já aplicadas
 * são puladas na reaplicação e linhas já revertidas são puladas na
 * re-reversão; a OS de lote é reutilizada pelo {@code batch_order_id} do
 * job, nunca recriada. O reenvio do mesmo arquivo devolve o job existente
 * (UNIQUE tenant/esquema/SHA-256), sem novas linhas e sem nova OS.
 *
 * <p>OS de lote (ADR-0039 item 9): uma OS interna por carga, tipo
 * {@code IMPORT_BATCH} (declarado aqui quando o tenant ainda não o tem;
 * tipos são política do tenant, mas o lote precisa existir para a carga
 * andar — a declaração usa os valores de fábrica documentados abaixo).
 * A OS não carrega alvos por linha (as classes de alvo nomeiam entidades do
 * domínio, e a linha genérica não é nenhuma delas); o livro-razão por linha
 * é {@code import_rows}. A correção é outra OS, tipo
 * {@code IMPORT_BATCH_REVERSAL}, com {@code reverses_order_id} para a OS do
 * lote, no mesmo lote ({@code batch_id}).
 */
@Service
public class ImportService {

    public static final String VALIDATE_JOB_TYPE = "registry.import.validate";
    public static final String APPLY_JOB_TYPE = "registry.import.apply";
    public static final String REVERSE_JOB_TYPE = "registry.import.reverse";

    /** Tipo da OS de lote. Interno, sem aprovação: o lote é backoffice, não muda preço. */
    public static final String BATCH_ORDER_TYPE = "IMPORT_BATCH";
    /** Tipo da OS inversa do lote (correção, ADR-0039 item 5). */
    public static final String REVERSAL_ORDER_TYPE = "IMPORT_BATCH_REVERSAL";

    private static final int MAX_ERROR_CHARS = 4000;

    private final DataSource dataSource;
    private final ImportJobQueue queue;
    private final ImportSchemaRegistry schemas;

    public ImportService(DataSource dataSource, ImportJobQueue queue, ImportSchemaRegistry schemas) {
        if (dataSource == null || queue == null || schemas == null) {
            throw new IllegalArgumentException("dataSource, queue e schemas sao obrigatorios");
        }
        this.dataSource = dataSource;
        this.queue = queue;
        this.schemas = schemas;
    }

    // ------------------------------------------------------------------ visões

    public record SchemaView(String code, String label, List<ColumnView> columns) {
    }

    public record ColumnView(String name, String type, boolean required, List<String> domain, String example) {
    }

    public record JobView(UUID id, String schemaCode, String schemaLabel, String originalFilename,
                          String status, int totalRows, int acceptedCount, int rejectedCount,
                          int appliedCount, UUID batchOrderId, UUID reversalOrderId,
                          String errorMessage, String createdAt, UUID createdBy, boolean duplicate) {
    }

    public record RejectionView(int rowNumber, String field, String reasonCode, String message) {
    }

    public List<SchemaView> listSchemas() {
        return schemas.schemas().stream().map(schema -> new SchemaView(
                schema.code(), schema.label(),
                schema.columns().stream().map(column -> new ColumnView(
                        column.name(), column.type().name(), column.required(),
                        column.domain(), column.example())).toList())).toList();
    }

    public String templateCsv(String schemaCode) {
        return ImportTemplate.csv(schemas.require(schemaCode));
    }

    public List<JobView> listJobs() {
        return store().listJobs(100).stream().map(job -> view(job, false)).toList();
    }

    public JobView getJob(UUID jobId) {
        return view(store().requireJob(jobId), false);
    }

    public ImportStore.JobView getJobStoreView(UUID jobId) {
        return store().requireJob(jobId);
    }

    public List<RejectionView> listRejections(UUID jobId) {
        store().requireJob(jobId);
        return store().listRejections(jobId).stream().map(rejection -> new RejectionView(
                rejection.rowNumber(), rejection.field(), rejection.reasonCode(),
                rejection.message())).toList();
    }

    /** CSV do relatório de recusas (linha, campo, motivo, mensagem), para baixar. */
    public String rejectionsCsv(UUID jobId) {
        store().requireJob(jobId);
        StringBuilder out = new StringBuilder();
        out.append(ImportTemplate.row(List.of("linha", "campo", "motivo", "mensagem"))).append("\r\n");
        for (ImportStore.RejectionRow rejection : store().listRejections(jobId)) {
            out.append(ImportTemplate.row(List.of(
                    String.valueOf(rejection.rowNumber()), rejection.field(),
                    rejection.reasonCode(), rejection.message()))).append("\r\n");
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ recebimento

    /**
     * Recebe o arquivo (multipart do controlador), grava a área de preparação
     * e enfileira a validação. O arquivo fica em quarentena até a validação:
     * nada é aplicado antes dela. Arquivo binário ou fora de UTF-8 é recusado
     * aqui (o modelo é texto); erro de formato do CSV também (cabeçalho
     * vazio, coluna repetida, linha com campo a mais/menos, aspas abertas).
     * O cabeçalho divergente do modelo NÃO é recusado aqui: ele é uma recusa
     * de validação (linha 0 do relatório), detectada pelo worker.
     */
    public JobView upload(String schemaCode, String originalFilename, byte[] bytes) {
        ImportSchema schema = requireSchema(schemaCode);
        if (originalFilename == null || originalFilename.isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError("file", "REQUIRED",
                    "O nome do arquivo e obrigatorio."));
        }
        if (bytes == null || bytes.length == 0) {
            throw ApiException.Validation.of(new ApiException.FieldError("file", "REQUIRED",
                    "O arquivo esta vazio."));
        }
        if (containsNullByte(bytes)) {
            throw ApiException.Validation.of(new ApiException.FieldError("file", "INVALID_TYPE",
                    "O arquivo nao e texto: envie o modelo CSV preenchido."));
        }
        String content = decodeUtf8(bytes);
        ImportCsvParser.ParsedCsv csv = parseCsv(content);
        String sha256 = sha256Hex(bytes);

        ImportStore store = store();
        var existing = store.findBySchemaAndSha(schema.code(), sha256);
        if (existing.isPresent()) {
            // Reenvio do mesmo arquivo: devolve o job existente, sem duplicar
            // linhas, OS ou efeitos. Se o job anterior falhou, o usuário
            // retoma pelos endpoints de validação/aplicação.
            return view(existing.get(), true);
        }
        ImportStore.JobView job = store.createJob(schema.code(), originalFilename.strip(), sha256,
                csv.header(), actor());
        store.insertRows(job.id(), csv.rows(), actor());
        queue.publish(job.id(), RegistryContext.tenantId(),
                VALIDATE_JOB_TYPE, Json.write(Map.of("importId", job.id().toString())));
        return view(store.requireJob(job.id()), false);
    }

    /** Enfileira a validação (202 do controlador). Só de RECEIVED ou FAILED. */
    public JobView enqueueValidate(UUID jobId) {
        ImportStore.JobView job = store().requireJob(jobId);
        if (job.status() != ImportStore.JobStatus.RECEIVED && job.status() != ImportStore.JobStatus.FAILED) {
            throw new ApiException.Conflict("state-conflict",
                    "So importacao recebida ou com falha pode ser validada (esta " + job.status() + ").");
        }
        queue.publish(job.id(), RegistryContext.tenantId(),
                VALIDATE_JOB_TYPE, Json.write(Map.of("importId", job.id().toString())));
        return view(job, false);
    }

    /** Enfileira a aplicação (202). De APPLIED, é no-op (efeito aplicado uma vez). */
    public JobView enqueueApply(UUID jobId) {
        ImportStore.JobView job = store().requireJob(jobId);
        if (job.status() == ImportStore.JobStatus.APPLIED) {
            return view(job, false);
        }
        if (job.status() != ImportStore.JobStatus.VALIDATED && job.status() != ImportStore.JobStatus.FAILED) {
            throw new ApiException.Conflict("state-conflict",
                    "So importacao validada pode ser aplicada (esta " + job.status() + ").");
        }
        queue.publish(job.id(), RegistryContext.tenantId(),
                APPLY_JOB_TYPE, Json.write(Map.of("importId", job.id().toString())));
        return view(job, false);
    }

    /** Enfileira a correção por OS inversa (202). De REVERSED, é no-op. */
    public JobView enqueueReverse(UUID jobId) {
        ImportStore.JobView job = store().requireJob(jobId);
        if (job.status() == ImportStore.JobStatus.REVERSED) {
            return view(job, false);
        }
        if (job.status() != ImportStore.JobStatus.APPLIED && job.status() != ImportStore.JobStatus.FAILED) {
            throw new ApiException.Conflict("state-conflict",
                    "So importacao aplicada pode ser revertida (esta " + job.status() + ").");
        }
        queue.publish(job.id(), RegistryContext.tenantId(),
                REVERSE_JOB_TYPE, Json.write(Map.of("importId", job.id().toString())));
        return view(job, false);
    }

    // ------------------------------------------------------------------ worker

    /** Validação pelo worker. Idempotente: job já validado/aplicado/revertido é no-op. */
    public ImportStore.JobView validate(UUID jobId, UUID actorId) {
        ImportStore store = store();
        ImportStore.JobView job = store.requireJob(jobId);
        if (job.status() == ImportStore.JobStatus.VALIDATED
                || job.status() == ImportStore.JobStatus.APPLIED
                || job.status() == ImportStore.JobStatus.REVERSED) {
            return job;
        }
        ImportSchema schema = requireSchema(job.schemaCode());
        // Revalidação após aplicação parcial corromperia o livro-razão (a
        // linha aplicada passaria a DUPLICATE_IN_DATABASE): job com linha
        // aplicada não revalida — a retomada é pela aplicação, não por aqui.
        boolean anyApplied = store.listRows(jobId).stream().anyMatch(ImportStore.StagedRow::applied);
        if (anyApplied) {
            return job;
        }
        store.setStatus(jobId, ImportStore.JobStatus.VALIDATING, null, actorId);
        try {
            List<String> expectedHeader = schema.columns().stream().map(ImportColumn::name).toList();
            if (!job.header().equals(expectedHeader)) {
                return store.saveFileRejection(jobId, job.totalRows(), "HEADER_MISMATCH",
                        "Cabecalho diferente do modelo do esquema " + schema.code()
                                + ": baixe o modelo e reenvie o arquivo com os mesmos nomes de coluna.",
                        actorId);
            }
            List<ImportStore.StagedRow> staged = store.listRows(jobId);
            List<ImportCsvParser.ParsedRow> rows = staged.stream()
                    .map(row -> new ImportCsvParser.ParsedRow(row.number(), row.values())).toList();
            ImportValidator.ValidationResult result;
            try (Connection connection = store.openTenantConnection()) {
                ImportValidator.DbChecks checks = new ImportValidator.DbChecks() {
                    @Override
                    public boolean foreignKeyExists(String table, String column, String value) {
                        return store.foreignKeyExists(table, column, value);
                    }

                    @Override
                    public boolean existsInDatabase(Map<String, String> values) {
                        try {
                            return schema.applier().existsByKey(connection, schema(), tenantId(), values);
                        } catch (SQLException e) {
                            throw new IllegalStateException("Falha na checagem de duplicidade", e);
                        }
                    }
                };
                result = ImportValidator.validate(schema,
                        new ImportCsvParser.ParsedCsv(job.header(), rows), checks);
            } catch (SQLException e) {
                throw new IllegalStateException("Falha na validacao", e);
            }
            return store.saveValidation(jobId, result, actorId);
        } catch (RuntimeException e) {
            store.setStatus(jobId, ImportStore.JobStatus.FAILED, truncate(e.getMessage()), actorId);
            throw e;
        }
    }

    /** Aplicação pelo worker. Idempotente: job já APPLIED é no-op; linhas já aplicadas são puladas. */
    public ImportStore.JobView apply(UUID jobId, UUID actorId) {
        requireActor(actorId);
        ImportStore store = store();
        ImportStore.JobView job = store.requireJob(jobId);
        if (job.status() == ImportStore.JobStatus.APPLIED) {
            return job;
        }
        if (job.status() == ImportStore.JobStatus.REVERSED) {
            throw new ApiException.Conflict("state-conflict",
                    "Importacao revertida nao pode ser aplicada de novo: envie o arquivo corrigido.");
        }
        if (job.status() == ImportStore.JobStatus.RECEIVED
                || job.status() == ImportStore.JobStatus.VALIDATING) {
            throw new ApiException.Conflict("state-conflict",
                    "So importacao validada pode ser aplicada (esta " + job.status() + ").");
        }
        ImportSchema schema = requireSchema(job.schemaCode());
        store.setStatus(jobId, ImportStore.JobStatus.APPLYING, null, actorId);
        try {
            List<ImportStore.StagedRow> rows = store.listRows(jobId);
            List<ImportStore.StagedRow> accepted = rows.stream()
                    .filter(row -> row.status() == ImportStore.RowStatus.ACCEPTED).toList();
            if (accepted.isEmpty()) {
                throw new ApiException.Conflict("state-conflict",
                        "Nenhuma linha aceita para aplicar: todas foram recusadas na validacao.");
            }
            int applied = 0;
            for (ImportStore.StagedRow row : accepted) {
                if (row.applied()) {
                    applied++;
                    continue;
                }
                try (Connection connection = store.openTenantConnection()) {
                    ImportSchema.ImportApplier.Applied target =
                            schema.applier().apply(connection, schema(), tenantId(), actorId, row.values());
                    store.markRowApplied(connection, row.id(), target, actorId);
                    connection.commit();
                } catch (SQLException e) {
                    throw new IllegalStateException("Falha na aplicacao da linha " + row.number(), e);
                }
                applied++;
            }
            UUID orderId = job.batchOrderId();
            if (orderId == null) {
                orderId = createBatchOrder(actorId, job, schema, null);
                job = store.setBatchOrder(jobId, orderId, actorId);
            }
            driveOrderToClosedExecuted(orderId, actorId);
            return store.setApplied(jobId, applied, actorId);
        } catch (RuntimeException e) {
            store.setStatus(jobId, ImportStore.JobStatus.FAILED, truncate(messageOf(e)), actorId);
            throw e;
        }
    }

    /** Correção pelo worker: OS inversa do lote + inverso de cada linha aplicada. Idempotente. */
    public ImportStore.JobView reverse(UUID jobId, UUID actorId) {
        requireActor(actorId);
        ImportStore store = store();
        ImportStore.JobView job = store.requireJob(jobId);
        if (job.status() == ImportStore.JobStatus.REVERSED) {
            return job;
        }
        List<ImportStore.StagedRow> rows = store.listRows(jobId);
        boolean anyApplied = rows.stream().anyMatch(ImportStore.StagedRow::applied);
        if (job.status() != ImportStore.JobStatus.APPLIED && !anyApplied) {
            throw new ApiException.Conflict("state-conflict",
                    "So importacao aplicada pode ser revertida (esta " + job.status() + ").");
        }
        if (job.batchOrderId() == null) {
            throw new ApiException.Conflict("state-conflict",
                    "Importacao sem OS de lote nao pode ser revertida.");
        }
        ImportSchema schema = requireSchema(job.schemaCode());
        try {
            for (ImportStore.StagedRow row : rows) {
                if (!row.applied()) {
                    continue;
                }
                try (Connection connection = store.openTenantConnection()) {
                    schema.applier().reverse(connection, schema(), tenantId(), actorId,
                            new ImportSchema.ImportApplier.Applied(
                                    row.appliedTargetTable(), row.appliedTargetId(), row.appliedTargetKey()));
                    store.markRowReversed(connection, row.id(), actorId);
                    connection.commit();
                } catch (SQLException e) {
                    throw new IllegalStateException("Falha na reversao da linha " + row.number(), e);
                }
            }
            // A OS inversa nasce no mesmo lote da OS original (createBatchOrder
            // herda o batchId) e aponta para ela por reverses_order_id.
            UUID reversalId = createBatchOrder(actorId, job, schema, job.batchOrderId());
            driveOrderToClosedExecuted(reversalId, actorId);
            return store.setReversalOrder(jobId, reversalId, actorId);
        } catch (RuntimeException e) {
            store.setStatus(jobId, ImportStore.JobStatus.FAILED, truncate(messageOf(e)), actorId);
            throw e;
        }
    }

    // ------------------------------------------------------------------ OS de lote

    private UUID createBatchOrder(UUID actorId, ImportStore.JobView job, ImportSchema schema,
                                  UUID reversesOrderId) {
        ServiceOrderService orders = orders();
        String typeCode = reversesOrderId == null ? BATCH_ORDER_TYPE : REVERSAL_ORDER_TYPE;
        ensureOrderType(orders, typeCode, actorId);
        UUID batchId;
        if (reversesOrderId == null) {
            // Retomada: um crash entre criar o lote e gravar o batch_order_id
            // no job deixa o lote órfão; reutilizá-lo, nunca duplicar o código.
            batchId = store().findBatchIdByCode("IMP-" + job.id())
                    .orElseGet(() -> orders.createBatch("IMP-" + job.id(), "Importacao " + schema.code()
                            + " (" + job.originalFilename() + ")", actorId));
        } else {
            batchId = orders.getOrder(reversesOrderId).batchId();
        }
        ServiceOrderService.OrderView order = orders.createOrder(new ServiceOrderService.OrderInput(
                typeCode, ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, actorId, null, batchId, null, reversesOrderId));
        return order.id();
    }

    private void ensureOrderType(ServiceOrderService orders, String typeCode, UUID actorId) {
        try {
            orders.findTypeByCode(typeCode);
            return;
        } catch (ApiException.NotFound ignored) {
            // Tipo ainda não declarado neste tenant: declara com os valores de
            // fábrica da P1.40 (interno, sem aprovação, sem alvo por linha).
        }
        try {
            orders.declareType(new ServiceOrderService.ServiceTypeInput(
                    typeCode,
                    typeCode.equals(BATCH_ORDER_TYPE) ? "Importacao em lote" : "Correcao de importacao em lote",
                    typeCode.equals(BATCH_ORDER_TYPE) ? "IMPORT_BATCH" : "IMPORT_BATCH_REVERSAL",
                    typeCode.equals(BATCH_ORDER_TYPE) ? "IMPORT_BATCH_APPLY" : "IMPORT_BATCH_REVERSE",
                    null, ServiceOrderEnums.TypeIndicator.INTERNAL, false, false, null,
                    List.of(TargetType.LOCATION_ONLY), null, null, null,
                    false, false, false, false, List.of()), actorId);
        } catch (RuntimeException duplicate) {
            // Corrida entre dois workers declarando o tipo: o segundo perde o
            // UNIQUE e reencontra o tipo declarado pelo primeiro.
            orders.findTypeByCode(typeCode);
        }
    }

    /** Leva a OS do lote até CLOSED_EXECUTED, retomando de onde parou (DRAFT/OPEN/EXECUTED). */
    private void driveOrderToClosedExecuted(UUID orderId, UUID actorId) {
        ServiceOrderService orders = orders();
        ServiceOrderService.OrderView order = orders.getOrder(orderId);
        if (order.status() == ServiceOrderStatus.CLOSED_EXECUTED) {
            return;
        }
        if (order.status() == ServiceOrderStatus.DRAFT) {
            order = orders.submit(orderId, actorId);
        }
        if (order.status() == ServiceOrderStatus.OPEN) {
            order = orders.execute(orderId, actorId);
        }
        if (order.status() == ServiceOrderStatus.EXECUTED) {
            orders.closeExecuted(orderId, actorId);
            return;
        }
        throw new ApiException.Conflict("state-conflict",
                "OS de lote em estado inesperado para o fechamento: " + order.status() + ".");
    }

    // ------------------------------------------------------------------ apoio

    private ImportStore store() {
        return new ImportStore(dataSource, schema(), tenantId());
    }

    private ServiceOrderService orders() {
        return new ServiceOrderService(dataSource, schema(), tenantId());
    }

    private String schema() {
        String schema = TenantContextHolder.getTenant();
        if (schema == null || schema.isBlank()) {
            throw new IllegalStateException("sem tenant no contexto da requisicao");
        }
        return schema;
    }

    private UUID tenantId() {
        return RegistryContext.tenantId();
    }

    private UUID actor() {
        return TenantContextHolder.getUserId();
    }

    private void requireActor(UUID actorId) {
        if (actorId == null) {
            throw new IllegalStateException("sem autor para a OS de lote");
        }
    }

    private ImportSchema requireSchema(String code) {
        if (code == null || code.isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError("schemaCode", "REQUIRED",
                    "O esquema de importacao e obrigatorio."));
        }
        try {
            return schemas.require(code);
        } catch (ImportSchemaRegistry.UnknownSchemaException e) {
            throw ApiException.Validation.of(new ApiException.FieldError("schemaCode", "UNKNOWN_SCHEMA",
                    "Esquema de importacao desconhecido: " + e.code() + "."));
        }
    }

    private JobView view(ImportStore.JobView job, boolean duplicate) {
        String label = schemas.schemas().stream()
                .filter(schema -> schema.code().equals(job.schemaCode()))
                .map(ImportSchema::label).findFirst().orElse(job.schemaCode());
        return new JobView(job.id(), job.schemaCode(), label, job.originalFilename(),
                job.status().name(), job.totalRows(), job.acceptedCount(), job.rejectedCount(),
                job.appliedCount(), job.batchOrderId(), job.reversalOrderId(), job.errorMessage(),
                job.createdAt() == null ? null : job.createdAt().toString(), job.createdBy(), duplicate);
    }

    private static ImportCsvParser.ParsedCsv parseCsv(String content) {
        try {
            return ImportCsvParser.parse(content);
        } catch (ImportCsvParser.ParseException e) {
            throw ApiException.Validation.of(new ApiException.FieldError("file", "INVALID_CSV", e.getMessage()));
        }
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw ApiException.Validation.of(new ApiException.FieldError("file", "INVALID_TYPE",
                    "O arquivo nao esta em UTF-8: salve o modelo como CSV UTF-8 e reenvie."));
        }
    }

    private static boolean containsNullByte(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_CHARS ? message : message.substring(0, MAX_ERROR_CHARS);
    }

    private static String messageOf(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
