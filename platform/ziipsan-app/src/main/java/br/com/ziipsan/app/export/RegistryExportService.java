package br.com.ziipsan.app.export;

import br.com.ziipsan.commercial.registry.domain.GeneratedFile;
import br.com.ziipsan.commercial.registry.repository.GeneratedFileRepository;
import br.com.ziipsan.commercial.registry.service.PersonalDataAccessService;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.infra.queue.MessageQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * P1.12: regra ÚNICA de exportação de listas.
 *
 * <ul>
 *   <li>Permissão {@code registry:export} por recurso (o plano escreve
 *       {@code *:export}; aqui o recurso é o registry): sem ela, 403 com a
 *       negação registrada em personal_data_access_log (ver
 *       {@link PersonalDataAccessService#authorizeExport});</li>
 *   <li>máscara pelo teto do usuário (ADR-0008): o CSV é montado SÓ a partir
 *       dos resumos da lista ({@code documentMasked} / {@code userDocumentMasked}),
 *       que já chegam mascarados pelo servidor — nenhum campo de documento em
 *       claro entra neste serviço, nem no arquivo, nem no log;</li>
 *   <li>registro: cada exportação (síncrona ou enfileirada) deixa um registro
 *       EXPORT em personal_data_access_log, com recurso e quantidade;</li>
 *   <li>limite: até {@code ziipsan.export.sync-limit} linhas (padrão 1000), o
 *       arquivo volta na resposta; acima dele, a exportação é enfileirada na
 *       outbox da P1.9 (tipo {@code registry.export}) e o arquivo fica em
 *       "Arquivos gerados" quando o worker o concluir. O limite é parâmetro
 *       operacional desta frente, ajustável por propriedade — o plano fixa
 *       que há um limite, não o número;</li>
 *   <li>os filtros são exatamente os da lista (mesmo corpo do search): filtro
 *       ainda sem suporte no search (ex.: cidade em clientes) falha aqui com o
 *       mesmo 400, em vez de exportar um conjunto diferente do listado.</li>
 * </ul>
 *
 * <p>Sem paginação do cliente na exportação: o serviço pagina internamente em
 * lotes de 100 (teto do PageSpec) até o limite + 1 — o "+1" é só para detectar
 * que passou do limite sem carregar a lista inteira na memória.
 */
@Service
public class RegistryExportService {

    /** Tipo da mensagem da outbox (P1.9) que gera o arquivo assíncrono. */
    public static final String EXPORT_JOB_TYPE = "registry.export";

    /** Limite síncrono padrão; o plano exige um limite, sem fixar o número. */
    public static final int DEFAULT_SYNC_LIMIT = 1000;

    /** Lote interno de leitura (teto do PageSpec). */
    static final int PAGE_SIZE = 100;

    public sealed interface Result permits Sync, Queued {
    }

    public record Sync(byte[] csv, String fileName, long rows) implements Result {
    }

    public record Queued(UUID generatedFileId) implements Result {
    }

    /** CSV pronto com a contagem de linhas (o handler grava os dois no arquivo). */
    public record CsvResult(byte[] csv, long rows) {
    }

    private final CustomerService customers;
    private final ConsumerUnitService units;
    private final PersonalDataAccessService accessLog;
    private final GeneratedFileRepository files;
    private final MessageQueue queue;
    private final int syncLimit;

    public RegistryExportService(CustomerService customers, ConsumerUnitService units,
                                 PersonalDataAccessService accessLog, GeneratedFileRepository files,
                                 MessageQueue queue,
                                 @Value("${ziipsan.export.sync-limit:" + DEFAULT_SYNC_LIMIT + "}") int syncLimit) {
        this.customers = customers;
        this.units = units;
        this.accessLog = accessLog;
        this.files = files;
        this.queue = queue;
        this.syncLimit = syncLimit;
    }

    /** Exportação de clientes a partir dos filtros da lista (corpo do search). */
    @Transactional
    public Result exportCustomers(Map<String, Object> filters) {
        accessLog.authorizeExport("customers");
        List<CustomerDtos.CustomerSummary> rows = collectCustomers(filters, syncLimit + 1);
        if (rows.size() > syncLimit) {
            return enqueue("customers", filters, fileName("customers"));
        }
        byte[] csv = toCsv(
                List.of("Nome", "Tipo de pessoa", "Documento", "Imóveis", "Excluído"),
                rows.stream().map(r -> List.of(
                        nullToEmpty(r.name()),
                        r.personType() == null ? "" : r.personType().name(),
                        nullToEmpty(r.documentMasked()),
                        String.valueOf(r.unitCount()),
                        r.deleted() ? "Sim" : "Não")).toList());
        accessLog.recordExport("customers", "rows=" + rows.size());
        return new Sync(csv, fileName("customers"), rows.size());
    }

    /** Exportação de imóveis a partir dos filtros da lista (corpo do search). */
    @Transactional
    public Result exportUnits(Map<String, Object> filters) {
        accessLog.authorizeExport("consumer-units");
        List<ConsumerUnitDtos.UnitSummary> rows = collectUnits(filters, syncLimit + 1);
        if (rows.size() > syncLimit) {
            return enqueue("consumer-units", filters, fileName("consumer-units"));
        }
        byte[] csv = toCsv(
                List.of("Matrícula", "Inscrição", "Titular", "Documento do titular", "Endereço",
                        "Categoria principal", "Água", "Esgoto", "Economias"),
                rows.stream().map(r -> List.of(
                        matricula(r),
                        nullToEmpty(r.inscriptionText()),
                        nullToEmpty(r.userName()),
                        nullToEmpty(r.userDocumentMasked()),
                        nullToEmpty(r.addressText()),
                        nullToEmpty(r.mainCategoryName()),
                        nullToEmpty(r.waterStatusName()),
                        nullToEmpty(r.sewerStatusName()),
                        String.valueOf(r.economies()))).toList());
        accessLog.recordExport("consumer-units", "rows=" + rows.size());
        return new Sync(csv, fileName("consumer-units"), rows.size());
    }

    /**
     * Gera o CSV de um arquivo enfileirado, sem limite (o worker da P1.9 chama
     * isto com os filtros guardados na mensagem). Idempotente por fora: quem
     * chama só marca READY se ainda estiver QUEUED (ver o handler).
     */
    @Transactional(readOnly = true)
    public CsvResult buildAsyncCsv(String resource, Map<String, Object> filters) {
        if ("customers".equals(resource)) {
            List<CustomerDtos.CustomerSummary> rows = collectCustomers(filters, Integer.MAX_VALUE);
            return new CsvResult(toCsv(
                    List.of("Nome", "Tipo de pessoa", "Documento", "Imóveis", "Excluído"),
                    rows.stream().map(r -> List.of(
                            nullToEmpty(r.name()),
                            r.personType() == null ? "" : r.personType().name(),
                            nullToEmpty(r.documentMasked()),
                            String.valueOf(r.unitCount()),
                            r.deleted() ? "Sim" : "Não")).toList()), rows.size());
        }
        if ("consumer-units".equals(resource)) {
            List<ConsumerUnitDtos.UnitSummary> rows = collectUnits(filters, Integer.MAX_VALUE);
            return new CsvResult(toCsv(
                    List.of("Matrícula", "Inscrição", "Titular", "Documento do titular", "Endereço",
                            "Categoria principal", "Água", "Esgoto", "Economias"),
                    rows.stream().map(r -> List.of(
                            matricula(r),
                            nullToEmpty(r.inscriptionText()),
                            nullToEmpty(r.userName()),
                            nullToEmpty(r.userDocumentMasked()),
                            nullToEmpty(r.addressText()),
                            nullToEmpty(r.mainCategoryName()),
                            nullToEmpty(r.waterStatusName()),
                            nullToEmpty(r.sewerStatusName()),
                            String.valueOf(r.economies()))).toList()), rows.size());
        }
        throw new ApiException.BadRequest("invalid-resource", "Recurso de exportação desconhecido: " + resource);
    }

    /** Lista os arquivos gerados do tenant, os mais recentes primeiro. */
    @Transactional(readOnly = true)
    public List<GeneratedFile> listGeneratedFiles() {
        return files.findByTenantIdOrderByCreatedAtDesc(RegistryContext.tenantId());
    }

    /**
     * Baixa um arquivo pronto: o dono do arquivo ou quem tem
     * {@code registry:export}. O tenant já isola por schema/RLS; sem READY, 404
     * (o arquivo ainda não existe para download).
     */
    @Transactional(readOnly = true)
    public GeneratedFile downloadGeneratedFile(UUID id) {
        GeneratedFile file = files.findByIdAndTenantId(id, RegistryContext.tenantId())
                .orElseThrow(() -> new ApiException.NotFound("Arquivo gerado não encontrado."));
        if (file.getStatus() != GeneratedFile.Status.READY) {
            throw new ApiException.NotFound("Arquivo gerado ainda não está pronto.");
        }
        UUID userId = TenantContextHolder.getUserId();
        boolean owner = userId != null && userId.equals(file.getUserId());
        if (!owner && !hasExportPermission()) {
            throw new ApiException.Forbidden("O download deste arquivo exige ser o autor ou ter registry:export.");
        }
        return file;
    }

    private Queued enqueue(String resource, Map<String, Object> filters, String fileName) {
        GeneratedFile file = RegistryContext.forCreate(new GeneratedFile());
        // O id é a chave de deduplicação da outbox e volta na resposta 202:
        // precisa existir antes do save (o @PrePersist só o geraria no persist).
        file.setId(UUID.randomUUID());
        file.setUserId(TenantContextHolder.getUserId());
        file.setResource(resource);
        file.setStatus(GeneratedFile.Status.QUEUED);
        file.setFileName(fileName);
        files.save(file);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedFileId", file.getId().toString());
        payload.put("resource", resource);
        payload.put("filters", filters);
        // O id do arquivo é a chave de deduplicação: republicar o mesmo pedido
        // não cria um segundo arquivo na fila (contrato da P1.9).
        queue.publish(new MessageQueue.QueuedMessage(file.getId(), RegistryContext.tenantId(),
                EXPORT_JOB_TYPE, Json.write(payload)));
        accessLog.recordExport(resource, "queued");
        return new Queued(file.getId());
    }

    private List<CustomerDtos.CustomerSummary> collectCustomers(Map<String, Object> filters, int maxRows) {
        List<CustomerDtos.CustomerSummary> rows = new ArrayList<>();
        int page = 0;
        while (rows.size() < maxRows) {
            CustomerDtos.CustomerSearch search = ExportFilters.customers(filters, page, PAGE_SIZE);
            PageResult<CustomerDtos.CustomerSummary> result = customers.search(search);
            rows.addAll(result.content());
            if (result.content().isEmpty() || rows.size() >= result.totalElements()) {
                break;
            }
            page++;
        }
        return rows;
    }

    private List<ConsumerUnitDtos.UnitSummary> collectUnits(Map<String, Object> filters, int maxRows) {
        List<ConsumerUnitDtos.UnitSummary> rows = new ArrayList<>();
        int page = 0;
        while (rows.size() < maxRows) {
            ConsumerUnitDtos.UnitSearch search = ExportFilters.units(filters, page, PAGE_SIZE);
            PageResult<ConsumerUnitDtos.UnitSummary> result = units.search(search);
            rows.addAll(result.content());
            if (result.content().isEmpty() || rows.size() >= result.totalElements()) {
                break;
            }
            page++;
        }
        return rows;
    }

    private static boolean hasExportPermission() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> PersonalDataAccessService.EXPORT_PERMISSION.equals(a.getAuthority()));
    }

    static String fileName(String resource) {
        String base = "customers".equals(resource) ? "clientes" : "imoveis";
        return base + "-" + LocalDate.now() + ".csv";
    }

    private static String matricula(ConsumerUnitDtos.UnitSummary r) {
        if (r.registrationDv() == null || r.registrationDv().isBlank()) {
            return nullToEmpty(r.registration());
        }
        return r.registration() + "-" + r.registrationDv();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * CSV RFC 4180 com BOM UTF-8 (o Excel em PT-BR abre acentuado): campo com
     * vírgula, aspa ou quebra de linha vai entre aspas, com aspa duplicada.
     * Só recebe valores já mascarados — este método nunca vê documento em claro.
     */
    static byte[] toCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder out = new StringBuilder();
        out.append('\uFEFF');
        appendRow(out, headers);
        for (List<String> row : rows) {
            appendRow(out, row);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            String cell = cells.get(i) == null ? "" : cells.get(i);
            if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
                out.append('"').append(cell.replace("\"", "\"\"")).append('"');
            } else {
                out.append(cell);
            }
        }
        out.append("\r\n");
    }
}
