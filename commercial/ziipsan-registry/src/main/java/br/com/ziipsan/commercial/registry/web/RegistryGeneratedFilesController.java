package br.com.ziipsan.commercial.registry.web;

import br.com.ziipsan.commercial.registry.application.RegistryExportService;
import br.com.ziipsan.commercial.registry.domain.GeneratedFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * P1.12: "Arquivos gerados" — ponto único de entrega das exportações
 * assíncronas (e, no futuro, dos relatórios agendados da P3.22, que escrevem
 * na mesma tabela; esta frente não constrói a P3.22). Leitura do cadastro
 * basta para listar; o download exige ser o autor ou ter
 * {@code registry:export} (regra no serviço).
 */
@RestController
@RequestMapping(RegistryRoles.BASE + "/generated-files")
@PreAuthorize(RegistryRoles.READ)
public class RegistryGeneratedFilesController {

    private final RegistryExportService service;

    public RegistryGeneratedFilesController(RegistryExportService service) {
        this.service = service;
    }

    public record GeneratedFileView(String id, String fileName, String resource, String status,
                                    Long rowCount, String createdAt, String readyAt) {
    }

    @GetMapping
    public List<GeneratedFileView> list() {
        return service.listGeneratedFiles().stream().map(f -> new GeneratedFileView(
                f.getId().toString(),
                f.getFileName(),
                f.getResource(),
                f.getStatus().name(),
                f.getRowCount(),
                f.getCreatedAt() == null ? null : f.getCreatedAt().toString(),
                f.getReadyAt() == null ? null : f.getReadyAt().toString())).toList();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        GeneratedFile file = service.downloadGeneratedFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.getFileName()).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(file.getContent());
    }
}
