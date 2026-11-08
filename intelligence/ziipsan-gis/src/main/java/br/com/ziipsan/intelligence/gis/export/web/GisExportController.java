package br.com.ziipsan.intelligence.gis.export.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.intelligence.gis.export.GisExportFilter;
import br.com.ziipsan.intelligence.gis.export.GisExportFormats;
import br.com.ziipsan.intelligence.gis.export.GisExportGuard;
import br.com.ziipsan.intelligence.gis.export.GisExportProfiles;
import br.com.ziipsan.intelligence.gis.export.GisExportService;
import br.com.ziipsan.intelligence.gis.export.GisMgbMetadata;
import br.com.ziipsan.intelligence.gis.ogc.GisOgcContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exportação GIS-16: POST com formato/camada/perfil.
 *
 * <p>Correções ED-67 (revisão independente):
 * <ul>
 *   <li><b>D8</b> — o metadado MGB ACOMPANHA a exportação: a
 *       resposta é um pacote ZIP com o arquivo de dados e a entrada
 *       {@code metadata-mgb.xml} com o XML completo gerado pelo
 *       serviço (antes o controller DESCARTAVA
 *       {@code result.metadataXml()} e devolvia um cabeçalho
 *       placeholder). O {@code ExportInput} coleta também
 *       {@code nominalAccuracy} e {@code updateFrequency} (campos do
 *       §3.8 que antes eram passados como {@code null});</li>
 *   <li><b>D9</b> — o input expressa os filtros do §9.3 (área,
 *       domínio, ciclo de vida, rede na data) via
 *       {@link GisExportFilter};</li>
 *   <li><b>D7</b> — agregados/piso não são responsabilidade deste
 *       controller: o serviço deriva os agregados da fonte e lê o
 *       parâmetro do tenant pela porta quando ligada;</li>
 *   <li><b>D13</b> — formato ausente é 400 declarado (nunca NPE/500)
 *       e as recusas de estado/pendência (sem tenant, sem porta de
 *       registro, GPKG/SHP sem GDAL) viram problem+json.</li>
 * </ul>
 * Audiência: credencial de terceiro (authority gis:third-party) ou
 * backoffice, pelo contexto.
 */
@RestController
@RequestMapping("/api/v1/gis/exports")
@PreAuthorize("hasAuthority('gis:view')")
public class GisExportController {

    private final GisExportService service;
    public GisExportController(GisExportService service){ this.service = service; }

    public record ExportInput(String format, String layerKey, String profile, String purpose,
                              String metadataTitle, String responsibleParty,
                              java.util.List<Double> bbox, String verticalDatum,
                              String nominalAccuracy, String updateFrequency,
                              java.util.List<Double> filterBbox, String domain,
                              String lifecycleStatus, String networkAt) {}

    @PostMapping(produces = "application/zip")
    public ResponseEntity<byte[]> export(@RequestBody ExportInput in) {
        try {
            var audience = GisOgcContext.thirdParty() ? GisExportGuard.Audience.THIRD_PARTY
                    : GisExportGuard.Audience.BACKOFFICE;
            if (in.format() == null || in.format().isBlank())
                throw new ApiException.BadRequest("gis-export", "formato de exportação é obrigatório");
            if (in.bbox() == null || in.bbox().size() != 4 || in.verticalDatum() == null || in.verticalDatum().isBlank())
                throw new ApiException.BadRequest("gis-export", "metadado MGB exige bbox (4 números) e verticalDatum do tenant — nenhum valor é presumido");
            if (in.nominalAccuracy() == null || in.nominalAccuracy().isBlank()
                    || in.updateFrequency() == null || in.updateFrequency().isBlank())
                throw new ApiException.BadRequest("gis-export", "metadado MGB exige nominalAccuracy e updateFrequency (§3.8) — nenhum valor é presumido");
            var metadata = new GisMgbMetadata.Metadata(in.metadataTitle(), in.responsibleParty(),
                    in.bbox(), 4674, in.verticalDatum(), in.nominalAccuracy(), Instant.now(),
                    "cadastro técnico do tenant (GIS)", "LGPD/infraestrutura crítica conforme §12", in.updateFrequency());
            GisExportFilter filter = (in.filterBbox() == null && in.domain() == null
                    && in.lifecycleStatus() == null && in.networkAt() == null) ? null
                    : new GisExportFilter(in.filterBbox(), in.domain(), in.lifecycleStatus(),
                            in.networkAt() == null ? null : Instant.parse(in.networkAt()));
            var req = new GisExportService.ExportRequest(
                    GisExportFormats.valueOf(in.format()), in.layerKey(),
                    in.profile() == null ? GisExportProfiles.Profile.GENERIC
                            : GisExportProfiles.Profile.valueOf(in.profile()),
                    audience, GisOgcContext.canViewPersonal(), 5, List.of(), metadata, in.purpose(), filter);
            var result = service.export(GisOgcContext.tenantId(), GisOgcContext.userId(), req);
            byte[] pack = packWithMetadata(result.filename(), result.content(), result.metadataXml());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"export-" + in.layerKey() + "-com-metadado.zip\"")
                    .header("X-GIS-Metadata", "MGB/ISO19115 — XML completo na entrada metadata-mgb.xml do pacote")
                    .body(pack);
        } catch (SecurityException e) { throw new ApiException.Forbidden(e.getMessage()); }
          catch (IllegalArgumentException e) { throw new ApiException.BadRequest("gis-export", e.getMessage()); }
          catch (IllegalStateException e) { throw new ApiException.Conflict("gis-export-state", e.getMessage()); }
          catch (UnsupportedOperationException e) { throw new ApiException.BadRequest("gis-export-pendente", e.getMessage()); }
    }

    /** Pacote da exportação: arquivo de dados + metadata-mgb.xml (D8). */
    static byte[] packWithMetadata(String dataFilename, byte[] content, String metadataXml) {
        try {
            var baos = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry(dataFilename));
                zip.write(content);
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("metadata-mgb.xml"));
                zip.write(metadataXml.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("falha ao montar o pacote de exportação com o metadado MGB", e);
        }
    }
}
