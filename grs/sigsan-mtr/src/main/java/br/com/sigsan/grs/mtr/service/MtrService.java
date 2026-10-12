package br.com.sigsan.grs.mtr.service;

import br.com.sigsan.core.tenant.TenantContextHolder;
import br.com.sigsan.grs.mtr.domain.*;
import br.com.sigsan.grs.mtr.integration.GovBrSignatureService;
import br.com.sigsan.grs.mtr.integration.SinirIntegrationService;
import br.com.sigsan.grs.mtr.integration.SinirIntegrationService.SinirSyncResult;
import br.com.sigsan.grs.mtr.repository.WasteCdfRepository;
import br.com.sigsan.grs.mtr.repository.WasteClassRepository;
import br.com.sigsan.grs.mtr.repository.WasteDestinationRepository;
import br.com.sigsan.grs.mtr.repository.WasteGeneratorRepository;
import br.com.sigsan.grs.mtr.repository.WasteMtrRepository;
import br.com.sigsan.grs.mtr.repository.WasteTransporterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Ciclo do MTR e do CDF (fluxo interno do SIGSAN).
 *
 * <p><b>Aviso regulatório:</b> o número e o CDF produzidos aqui NÃO têm valor legal. Pela Portaria
 * MMA 280/2020 o MTR só vale se emitido no SINIR ou em sistema estadual. Enquanto a integração com o
 * SINIR for um stub (devolve PENDING), este serviço é um rascunho operacional, e o status de
 * sincronização gravado em cada MTR diz isso. Ver
 * {@code docs/levantamento/12-regulatorio-residuos-mtr-sinir.md}.
 *
 * <p>A trilha de auditoria é feita pelo trigger do banco ({@code register_tenant_table}); não há
 * chamada de auditoria na aplicação.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtrService {

    private final WasteMtrRepository mtrRepository;
    private final WasteGeneratorRepository generatorRepository;
    private final WasteTransporterRepository transporterRepository;
    private final WasteDestinationRepository destinationRepository;
    private final WasteClassRepository wasteClassRepository;
    private final WasteCdfRepository cdfRepository;
    private final SinirIntegrationService sinirIntegration;
    private final GovBrSignatureService govBrSignature;
    private final MtrPdfGenerator pdfGenerator;

    @Transactional
    public WasteMtr createMtr(CreateMtrRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        WasteGenerator generator = generatorRepository.findByIdAndTenantId(request.generatorId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Generator not found"));
        WasteTransporter transporter = transporterRepository.findByIdAndTenantId(request.transporterId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Transporter not found"));
        WasteDestination destination = destinationRepository.findByIdAndTenantId(request.destinationId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Destination not found"));
        WasteClass wasteClass = wasteClassRepository.findByIdAndTenantId(request.wasteClassId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Waste class not found"));

        validateCompatibility(wasteClass, destination);

        WasteMtr mtr = new WasteMtr();
        mtr.setTenantId(tenantId);
        mtr.setMtrNumber(generateMtrNumber());
        mtr.setGenerator(generator);
        mtr.setTransporter(transporter);
        mtr.setDestination(destination);
        mtr.setWasteClass(wasteClass);
        mtr.setQuantityKg(request.quantityKg());
        mtr.setPackagingType(request.packagingType());
        mtr.setPackagingCount(request.packagingCount());
        mtr.setVehiclePlate(request.vehiclePlate());
        mtr.setDriverCpf(request.driverCpf());
        mtr.setStatus(WasteMtr.MtrStatus.EMITTED);
        mtr.setIssuedAt(OffsetDateTime.now());
        // Map.toString() gera "{a=b}", que não é JSON e o PostgreSQL recusa numa coluna jsonb.
        mtr.setProperties(request.properties() != null ? toJson(request.properties()) : "{}");
        mtr.setHashChain(HashChain.next(null, MtrCanonical.of(mtr)));

        return mtrRepository.save(mtr);
    }

    @Transactional
    public WasteMtr collectMtr(UUID mtrId, CollectMtrRequest request) {
        WasteMtr mtr = load(mtrId);
        MtrStateMachine.require(mtr.getStatus(), WasteMtr.MtrStatus.COLLECTED);

        mtr.setStatus(WasteMtr.MtrStatus.COLLECTED);
        mtr.setCollectedAt(OffsetDateTime.now());
        mtr.setVehiclePlate(request.vehiclePlate());
        mtr.setDriverCpf(request.driverCpf());
        mtr.setRouteGeometry(request.routeGeometry());
        mtr.setTransporterSignature(request.transporterSignature());
        mtr.setHashChain(HashChain.next(mtr.getHashChain(), MtrCanonical.of(mtr)));

        recordSinir(mtr, sinirIntegration.syncMtr(mtr));
        return mtrRepository.save(mtr);
    }

    @Transactional
    public WasteMtr receiveMtr(UUID mtrId, ReceiveMtrRequest request) {
        WasteMtr mtr = load(mtrId);
        MtrStateMachine.require(mtr.getStatus(), WasteMtr.MtrStatus.RECEIVED);

        mtr.setStatus(WasteMtr.MtrStatus.RECEIVED);
        mtr.setReceivedAt(OffsetDateTime.now());
        mtr.setDestinationSignature(request.destinationSignature());
        mtr.setHashChain(HashChain.next(mtr.getHashChain(), MtrCanonical.of(mtr)));

        return mtrRepository.save(mtr);
    }

    @Transactional
    public WasteCdf destinateMtr(UUID mtrId, DestinateMtrRequest request) {
        WasteMtr mtr = load(mtrId);
        MtrStateMachine.require(mtr.getStatus(), WasteMtr.MtrStatus.DESTINED);

        // A versão anterior buscava o destinador da requisição, ignorava o resultado e emitia o CDF
        // para o destinador do MTR: um CDF podia sair para quem não recebeu o resíduo.
        if (!mtr.getDestination().getId().equals(request.destinationId())) {
            throw new IllegalArgumentException("O destinador informado não é o destinador do MTR "
                    + mtr.getMtrNumber());
        }

        WasteCdf cdf = new WasteCdf();
        cdf.setTenantId(TenantContextHolder.getTenantId());
        cdf.setMtr(mtr);
        cdf.setDestination(mtr.getDestination());
        cdf.setDestinationMethod(request.destinationMethod());
        cdf.setProcessDetails(request.processDetails() != null ? toJson(request.processDetails()) : "{}");
        cdf.setTechnicianId(request.technicianId());
        cdf.setTechnicianName(request.technicianName());
        cdf.setTechnicianCrea(request.technicianCrea());
        cdf.setIssuedAt(OffsetDateTime.now());

        byte[] pdfBytes = pdfGenerator.generateCdfPdf(cdf, mtr);
        cdf.setPdfDocument(pdfBytes);
        cdf.setPdfChecksum(HashChain.sha256Hex(pdfBytes));
        cdf.setHashChain(HashChain.next(null, MtrCanonical.of(cdf)));
        cdfRepository.save(cdf);

        mtr.setStatus(WasteMtr.MtrStatus.DESTINED);
        mtr.setDestinedAt(OffsetDateTime.now());
        mtr.setDestinationSignature(request.destinationSignature());
        mtr.setHashChain(HashChain.next(mtr.getHashChain(), MtrCanonical.of(mtr)));

        recordSinir(mtr, sinirIntegration.syncMtr(mtr));
        mtrRepository.save(mtr);
        return cdf;
    }

    @Transactional(readOnly = true)
    public Page<WasteMtr> findMtrs(MtrSearchCriteria criteria, Pageable pageable) {
        return mtrRepository.findByCriteria(TenantContextHolder.getTenantId(), criteria, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<WasteMtr> findByMtrNumber(String mtrNumber) {
        return mtrRepository.findByMtrNumberAndTenantId(TenantContextHolder.getTenantId(), mtrNumber);
    }

    @Transactional(readOnly = true)
    public Optional<WasteCdf> getCdfByMtr(UUID mtrId) {
        return cdfRepository.findByMtrIdAndTenantId(TenantContextHolder.getTenantId(), mtrId);
    }

    @Transactional(readOnly = true)
    public byte[] downloadCdfPdf(UUID mtrId) {
        return cdfRepository.findByMtrIdAndTenantId(TenantContextHolder.getTenantId(), mtrId)
                .map(WasteCdf::getPdfDocument)
                .orElseThrow(() -> new IllegalArgumentException("CDF not found for MTR"));
    }

    // ------------------------------------------------------------------------------------

    private WasteMtr load(UUID mtrId) {
        return mtrRepository.findByIdAndTenantId(mtrId, TenantContextHolder.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("MTR not found"));
    }

    /**
     * Grava no MTR o resultado da tentativa de sincronização. A versão anterior descartava o
     * retorno, então o MTR ficava sempre com o status inicial, sem registro do motivo da pendência.
     */
    private void recordSinir(WasteMtr mtr, SinirSyncResult result) {
        mtr.setSinirSyncStatus(WasteMtr.SinirSyncStatus.valueOf(result.status().name()));
        mtr.setSinirSyncAt(result.attemptedAt());
        mtr.setSinirProtocol(result.protocol());
        mtr.setSinirResponse(toJson(Map.of(
                "status", result.status().name(),
                "reason", result.reason() == null ? "" : result.reason())));
    }

    private String toJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(value);
        } catch (Exception e) {
            // Um JSON que não se consegue produzir é erro, não "{}": gravar "{}" apagaria o dado.
            throw new IllegalStateException("Falha ao serializar para JSON", e);
        }
    }

    /**
     * Interpreta o JSON de classes aceitas do destinador como lista de codigos.
     * Aceita array JSON ["A.1.1","B.2.3"], array simples [1,2] e csv "A.1.1,B.2.3".
     */
    private List<String> parseAcceptedClasses(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .findAndRegisterModules()
                    .readTree(json);
            List<String> codes = new ArrayList<>();
            if (node.isArray()) {
                node.forEach(item -> codes.add(item.asText()));
            } else if (node.isTextual()) {
                for (String code : node.asText().split(",")) {
                    if (!code.isBlank()) {
                        codes.add(code.trim());
                    }
                }
            }
            return codes;
        } catch (Exception e) {
            log.warn("Failed to parse accepted waste classes: {}", json, e);
            return List.of();
        }
    }

    private void validateCompatibility(WasteClass wasteClass, WasteDestination destination) {
        List<String> accepted = parseAcceptedClasses(destination.getAcceptedWasteClasses());
        if (!accepted.isEmpty() && !accepted.contains(wasteClass.getCode())) {
            throw new IllegalArgumentException("Destination does not accept waste class: " + wasteClass.getCode());
        }
    }

    /**
     * Número INTERNO do MTR: {@code AAAA.NNNNNNNN.TENA.DD}. Não é o número oficial (que só o SINIR
     * ou o sistema estadual emite). O sufixo {@code DD} era sorteado ({@code new Random()}); agora é
     * determinístico (sequência módulo 97), para o número poder ser conferido. A sequência vem de
     * {@code count + 1}, o que dá o mesmo número a duas emissões simultâneas: o índice único do banco
     * recusa a segunda. Uma sequência do banco por tenant é a correção definitiva.
     */
    private String generateMtrNumber() {
        long sequence = mtrRepository.countByTenantId(TenantContextHolder.getTenantId()) + 1;
        return formatMtrNumber(OffsetDateTime.now().getYear(), sequence, TenantContextHolder.getTenant());
    }

    static String formatMtrNumber(int year, long sequence, String tenantSchema) {
        String tenantTag = tenantSchema.substring(0, Math.min(4, tenantSchema.length())).toUpperCase(Locale.ROOT);
        return String.format("%d.%08d.%s.%02d", year, sequence, tenantTag, sequence % 97);
    }

    // Request DTOs
    public record CreateMtrRequest(
            UUID generatorId, UUID transporterId, UUID destinationId, UUID wasteClassId,
            BigDecimal quantityKg, String packagingType, Integer packagingCount,
            String vehiclePlate, String driverCpf, Map<String, Object> properties
    ) {}

    public record CollectMtrRequest(
            String vehiclePlate, String driverCpf,
            org.locationtech.jts.geom.LineString routeGeometry, String transporterSignature
    ) {}

    public record ReceiveMtrRequest(String destinationSignature) {}

    public record DestinateMtrRequest(
            UUID destinationId, WasteCdf.DestinationMethod destinationMethod,
            Map<String, Object> processDetails, UUID technicianId, String technicianName,
            String technicianCrea, String destinationSignature
    ) {}
}
