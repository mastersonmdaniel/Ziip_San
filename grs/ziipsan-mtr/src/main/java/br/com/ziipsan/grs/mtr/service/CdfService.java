package br.com.ziipsan.grs.mtr.service;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E2.
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.domain.WasteCdf;
import br.com.ziipsan.grs.mtr.domain.WasteCdfMtr;
import br.com.ziipsan.grs.mtr.domain.WasteDestination;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import br.com.ziipsan.grs.mtr.repository.WasteCdfMtrRepository;
import br.com.ziipsan.grs.mtr.repository.WasteCdfRepository;
import br.com.ziipsan.grs.mtr.repository.WasteDestinationRepository;
import br.com.ziipsan.grs.mtr.repository.WasteMtrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emissão do CDF interno do destinador (ED-104, E2).
 *
 * <p>O CDF agrega os MTRs DESTINED do destinador com {@code destinedAt} dentro do período
 * [início, fim] — deixa de ser 1:1 com o MTR (Portaria MMA 280/2020, art. 14 §§3º–7º, via
 * levantamento regulatório). Um MTR já agregado a outro CDF não entra duas vezes. Período sem
 * nenhum MTR destinado recusa: CDF vazio certificaria massa que não existiu.
 *
 * <p>Este CDF é registo INTERNO. O CDF oficial é o emitido pelo sistema emissor (SINIR/IMA);
 * {@code officialCdfNumber} nasce nulo e o PDF declara essa natureza em texto.
 */
@Service
@RequiredArgsConstructor
public class CdfService {

    private final WasteMtrRepository mtrRepository;
    private final WasteDestinationRepository destinationRepository;
    private final WasteCdfRepository cdfRepository;
    private final WasteCdfMtrRepository cdfMtrRepository;
    private final MtrPdfGenerator pdfGenerator;
    private final MtrDocumentProtection documents;

    public record IssueCdfRequest(UUID destinationId, LocalDate periodStart, LocalDate periodEnd,
                                  WasteCdf.DestinationMethod destinationMethod,
                                  Map<String, Object> processDetails, UUID technicianId,
                                  String technicianName, String technicianCrea) {
    }

    @Transactional
    public WasteCdf issueCdf(IssueCdfRequest request) {
        UUID tenantId = requireTenant();
        if (request.periodStart() == null || request.periodEnd() == null
                || request.periodEnd().isBefore(request.periodStart())) {
            throw new IllegalArgumentException("período do CDF inválido");
        }
        WasteDestination destination = destinationRepository.findByIdAndTenantId(request.destinationId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Destination not found"));

        OffsetDateTime from = request.periodStart().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = request.periodEnd().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        List<WasteMtr> destined = mtrRepository.findDestinedInPeriod(tenantId, destination.getId(), from, to);
        if (destined.isEmpty()) {
            throw new IllegalStateException("nenhum MTR destinado no período para o destinador "
                    + destination.getId() + ": CDF vazio não é emitido");
        }
        List<UUID> ids = destined.stream().map(WasteMtr::getId).toList();
        List<UUID> alreadyLinked = cdfMtrRepository.findAlreadyLinkedMtrIds(tenantId, ids);
        List<WasteMtr> fresh = destined.stream().filter(m -> !alreadyLinked.contains(m.getId())).toList();
        if (fresh.isEmpty()) {
            throw new IllegalStateException("todos os MTRs do período já estão agregados a outro CDF");
        }

        WasteCdf cdf = new WasteCdf();
        cdf.setTenantId(tenantId);
        cdf.setDestination(destination);
        cdf.setPeriodStart(request.periodStart());
        cdf.setPeriodEnd(request.periodEnd());
        cdf.setOfficialCdfNumber(null);
        cdf.setDestinationMethod(request.destinationMethod());
        cdf.setProcessDetails(Json.orDefault(request.processDetails(), "{}"));
        cdf.setTechnicianId(request.technicianId());
        cdf.setTechnicianName(request.technicianName());
        cdf.setTechnicianCrea(request.technicianCrea());
        cdf.setIssuedAt(OffsetDateTime.now());
        cdfRepository.save(cdf);

        for (WasteMtr mtr : fresh) {
            WasteCdfMtr link = new WasteCdfMtr();
            link.setTenantId(tenantId);
            link.setCdf(cdf);
            link.setMtr(mtr);
            cdfMtrRepository.save(link);
        }

        List<String> generatorDocuments = fresh.stream()
                .map(m -> documents.revealGeneratorDocument(m.getGenerator())).toList();
        byte[] pdfBytes = pdfGenerator.generateCdfPdf(cdf, fresh, generatorDocuments);
        cdf.setPdfDocument(pdfBytes);
        cdf.setPdfChecksum(HashChain.sha256Hex(pdfBytes));
        cdf.setHashChain(HashChain.next(null,
                MtrCanonical.of(cdf, fresh.stream().map(WasteMtr::getMtrNumber).toList())));
        return cdfRepository.save(cdf);
    }

    private static UUID requireTenant() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto");
        }
        return tenantId;
    }
}
