package br.com.ziipsan.commercial.registry.web;

import br.com.ziipsan.commercial.registry.importer.ImportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * P1.40: rotas do importador genérico de planilha, sob o cadastro.
 *
 * <p>Permissões reutilizadas do cadastro (sem permissão nova no catálogo:
 * importar é escrever no cadastro): leitura para o histórico, modelos e
 * relatórios; escrita para enviar, validar, aplicar e reverter. O upload é
 * multipart (primeiro multipart do código; o limite de corpo da plataforma,
 * 1 MiB por padrão, continua valendo — planilha de catálogo cabe nele, e a
 * carga de legado é da P1.2, não daqui).
 *
 * <p>Validação, aplicação e reversão são assíncronas: o POST devolve 202 com
 * o job, e o worker da P1.9 executa (tipos {@code registry.import.*}).
 * Sem worker rodando, o job fica no estado em que estava — nunca é aplicado
 * na requisição. O modelo e o relatório de recusas são downloads diretos.
 */
@RestController
@RequestMapping(RegistryRoles.BASE + "/imports")
@PreAuthorize(RegistryRoles.READ)
public class ImportController {

    private final ImportService service;

    public ImportController(ImportService service) {
        this.service = service;
    }

    /** Esquemas declarados, com colunas, tipos, domínios e exemplos (base do modelo). */
    @GetMapping("/schemas")
    public List<ImportService.SchemaView> schemas() {
        return service.listSchemas();
    }

    /** Modelo para baixar, gerado do esquema (cabeçalho + linha de exemplo). */
    @GetMapping("/schemas/{code}/template")
    public ResponseEntity<byte[]> template(@PathVariable String code) {
        String csv = service.templateCsv(code);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("modelo-" + code + ".csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Histórico de importações (menu Importações): quem, quando, arquivo, totais, OS de lote. */
    @GetMapping
    public List<ImportService.JobView> list() {
        return service.listJobs();
    }

    @GetMapping("/{id}")
    public ImportService.JobView get(@PathVariable UUID id) {
        return service.getJob(id);
    }

    /** Relatório de recusas em JSON (linha, campo, motivo). */
    @GetMapping("/{id}/rejections")
    public List<ImportService.RejectionView> rejections(@PathVariable UUID id) {
        return service.listRejections(id);
    }

    /** Relatório de recusas para baixar (CSV). */
    @GetMapping("/{id}/rejections.csv")
    public ResponseEntity<byte[]> rejectionsCsv(@PathVariable UUID id) {
        String csv = service.rejectionsCsv(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("recusas-" + id + ".csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Envia o arquivo para a área de preparação. Reenvio do mesmo arquivo devolve o job existente. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public ImportService.JobView upload(@RequestParam("schemaCode") String schemaCode,
                                        @RequestParam("file") MultipartFile file) throws IOException {
        // Reenvio do mesmo arquivo devolve o job existente (duplicate=true no
        // corpo); o 201 do contrato não significa efeito duplicado.
        return service.upload(schemaCode, file.getOriginalFilename(), file.getBytes());
    }

    /** Enfileira a validação (worker). */
    @PostMapping("/{id}/validate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(RegistryRoles.WRITE)
    public ImportService.JobView validate(@PathVariable UUID id) {
        return service.enqueueValidate(id);
    }

    /** Enfileira a aplicação (worker). Só depois da validação; recusadas ficam fora do lote. */
    @PostMapping("/{id}/apply")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(RegistryRoles.WRITE)
    public ImportService.JobView apply(@PathVariable UUID id) {
        return service.enqueueApply(id);
    }

    /** Enfileira a correção por OS inversa do lote (worker). */
    @PostMapping("/{id}/reverse")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(RegistryRoles.WRITE)
    public ImportService.JobView reverse(@PathVariable UUID id) {
        return service.enqueueReverse(id);
    }
}
