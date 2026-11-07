package br.com.ziipsan.operations.metering.field;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.operations.metering.domain.MeterReading;
import br.com.ziipsan.operations.metering.repository.MeterReadingRepository;
import br.com.ziipsan.operations.metering.rules.ReadingStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sincronizacao das leituras de campo (ED-18), uma rota por chamada: o aparelho envia as
 * tarefas executadas offline e recebe um recibo por tarefa.
 *
 * <h2>Regras, na ordem em que rodam</h2>
 * <ol>
 *   <li><b>Portao do aparelho:</b> {@link FieldDeviceGate#assertCanSync} antes de
 *       qualquer tarefa: nao registrado, pendente, bloqueado ou retirado nao sincroniza
 *       (a diretiva BLOCK/RETIRE chega ao aparelho pela rota de contato, nao por aqui).</li>
 *   <li><b>Idempotencia por task_id:</b> tarefa ja sincronizada devolve o recibo original
 *       com {@code duplicate = true} e NAO cria segunda leitura — o reenvio depois de uma
 *       queda de rede no meio da sincronizacao e o caso normal, nao excecao.</li>
 *   <li><b>Ocorrencia do catalogo da P2.5:</b> codigo fora do catalogo do tenant e
 *       recusado; ocorrencia que exige foto sem foto e recusada. Os efeitos da ocorrencia
 *       NAO sao aplicados aqui: quem aplica e a cascata de consumo da P2.5/P2.7 sobre a
 *       leitura gravada, com os efeitos lidos do mesmo catalogo.</li>
 *   <li><b>OCR:</b> com {@code read.ocr_mode} ligado, a comparacao de
 *       {@link OcrComparison} roda sobre os digitos (a imagem nunca sai do aparelho);
 *       veredito REVIEW manda a tarefa a fila de revisao junto do aceite da leitura —
 *       a leitura sincroniza uma unica vez, marcada para revisao, nunca duplicada
 *       entre "aceita" e "em revisao".</li>
 * </ol>
 *
 * <p>Limite declarado: a validacao fina do retorno (ReadingValidator da P2.7:
 * leiturista, unidade, janela de datas) roda no caminho de aceite da leitura na
 * integracao da camada; esta sincronizacao aplica o portao do aparelho, a
 * idempotencia, o catalogo de ocorrencias e o OCR — nao duplica o validador.
 *
 * <p>JSON gravado em coluna jsonb passa SEMPRE por {@link Json} do core (a licao da
 * classe: {@code Map.toString()} nao e JSON e o PostgreSQL recusa).
 */
@Service
public class FieldSyncService {

    private final FieldDeviceGate devices;
    private final ReadingOccurrenceCatalog occurrences;
    private final SyncedTaskStore syncedTasks;
    private final ReadingReviewQueue reviewQueue;
    private final MeterReadingRepository readings;

    public FieldSyncService(FieldDeviceGate devices, ReadingOccurrenceCatalog occurrences,
                            SyncedTaskStore syncedTasks, ReadingReviewQueue reviewQueue,
                            MeterReadingRepository readings) {
        this.devices = devices;
        this.occurrences = occurrences;
        this.syncedTasks = syncedTasks;
        this.reviewQueue = reviewQueue;
        this.readings = readings;
    }

    public List<FieldSyncReceipt> syncRoute(UUID tenantId, UUID deviceId,
                                            List<FieldReadingSubmission> submissions,
                                            FieldReadingParameters parameters,
                                            Map<UUID, Integer> meterDigitsByInstallation,
                                            Map<UUID, Integer> previousReadingByInstallation,
                                            OffsetDateTime now) {
        devices.assertCanSync(tenantId, deviceId, now);
        List<FieldSyncReceipt> receipts = new ArrayList<>();
        for (FieldReadingSubmission submission : submissions) {
            receipts.add(syncOne(tenantId, submission, parameters,
                    meterDigitsByInstallation, previousReadingByInstallation, now));
        }
        return List.copyOf(receipts);
    }

    private FieldSyncReceipt syncOne(UUID tenantId, FieldReadingSubmission submission,
                                     FieldReadingParameters parameters,
                                     Map<UUID, Integer> meterDigitsByInstallation,
                                     Map<UUID, Integer> previousReadingByInstallation,
                                     OffsetDateTime now) {
        var prior = syncedTasks.find(tenantId, submission.taskId());
        if (prior.isPresent()) {
            return prior.get().asDuplicate();
        }

        ReadingOccurrence occurrence = null;
        if (submission.occurrenceCode() != null) {
            occurrence = occurrences.findByCode(tenantId, submission.occurrenceCode())
                    .orElseThrow(() -> ApiException.Validation.of(new ApiException.FieldError(
                            "occurrenceCode", "occurrence-unknown",
                            "Ocorrencia fora do catalogo de leitura do tenant: " + submission.occurrenceCode())));
            if (occurrence.requiresPhoto() && submission.photoRefs().isEmpty()) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        "photoRefs", "photo-required",
                        "A ocorrencia " + occurrence.code() + " exige foto."));
            }
            if (occurrence.requiresReading() && submission.reading() == null) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        "reading", "reading-required",
                        "A ocorrencia " + occurrence.code() + " exige leitura."));
            }
        }

        int meterDigits = meterDigitsByInstallation.getOrDefault(submission.installationId(), 0);
        OcrVerdict verdict = OcrComparison.evaluate(parameters.ocrMode(), parameters.ocrToleranceDigits(),
                meterDigits, submission.reading(), submission.ocrValue(),
                previousReadingByInstallation.get(submission.installationId()));
        boolean review = verdict == OcrVerdict.REVIEW;

        MeterReading reading = new MeterReading();
        reading.setTenantId(tenantId);
        reading.setUnitId(submission.unitId());
        reading.setInstallationId(submission.installationId());
        reading.setKind(submission.kind());
        reading.setReferenceMonth(submission.reference());
        reading.setReadOn(submission.readOn());
        reading.setReading(submission.reading());
        reading.setReadingStatus(submission.reading() == null ? ReadingStatus.NOT_REALIZED : ReadingStatus.REALIZED);
        reading.setSource(MeterReading.Source.MOBILE);
        reading.setCapturedAt(submission.capturedAt() == null ? now : submission.capturedAt());
        reading.setIdempotencyKey(submission.idempotencyKey());
        if (review || verdict == OcrVerdict.SUGGESTION) {
            Map<String, Object> warnings = new LinkedHashMap<>();
            warnings.put("fieldOcrVerdict", verdict.name());
            warnings.put("ocrValue", submission.ocrValue());
            warnings.put("taskId", submission.taskId().toString());
            reading.setValidationWarnings(Json.write(warnings));
        }
        MeterReading saved = readings.save(reading);

        if (review) {
            reviewQueue.enqueue(tenantId, submission.taskId(), "OCR_DIVERGENCE");
        }
        FieldSyncReceipt receipt = new FieldSyncReceipt(submission.taskId(), saved.getId(), review, false);
        syncedTasks.save(tenantId, receipt);
        return receipt;
    }
}
