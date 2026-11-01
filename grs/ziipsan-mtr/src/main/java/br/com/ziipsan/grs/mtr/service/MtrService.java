package br.com.ziipsan.grs.mtr.service;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.domain.*;
import br.com.ziipsan.grs.mtr.integration.GovBrSignatureService;
import br.com.ziipsan.grs.mtr.repository.WasteCdfRepository;
import br.com.ziipsan.grs.mtr.repository.WasteClassRepository;
import br.com.ziipsan.grs.mtr.repository.WasteDestinationRepository;
import br.com.ziipsan.grs.mtr.repository.WasteGeneratorRepository;
import br.com.ziipsan.grs.mtr.repository.WasteMtrRepository;
import br.com.ziipsan.grs.mtr.repository.WasteTransporterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Ciclo do MTR e do CDF (fluxo interno do ZIIPSAN).
 *
 * <p><b>Aviso regulatório:</b> o número e o CDF produzidos aqui NÃO têm valor legal. Pela Portaria
 * MMA 280/2020 o MTR só vale se emitido no SINIR ou em sistema estadual. Enquanto a integração com o
 * SINIR for um stub (devolve PENDING), este serviço é um rascunho operacional, e o status de
 * sincronização gravado em cada MTR diz isso. Ver
 * {@code docs/levantamento/12-regulatorio-residuos-mtr-sinir.md}.
 *
 * <p>A trilha de auditoria é feita pelo trigger do banco ({@code register_tenant_table}); não há
 * chamada de auditoria na aplicação.
 *
 * <p>Documentos pessoais (CPF do motorista, CPF/CNPJ do gerador, motoristas do transportador) entram
 * em claro nas requisições e são cifrados aqui, por {@link MtrDocumentProtection}, antes de tocar a
 * entidade; nenhuma entidade do módulo tem campo para o valor em claro.
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
    // P0.25: SINIR em modo assistido (ADR-0004) — SinirIntegrationService apagado e sem
    // caminho de chamada ao SINIR em coleta/destinação; o MTR segue como rascunho operacional.
    private final GovBrSignatureService govBrSignature;
    private final MtrPdfGenerator pdfGenerator;
    private final MtrDocumentProtection documents;

    /** Documento já usado por outro gerador do mesmo tenant (detectado pelo índice cego). */
    public static class DuplicateGeneratorException extends IllegalStateException {
        public DuplicateGeneratorException() {
            super("já existe gerador com este documento");
        }
    }

    /**
     * Cadastra o gerador com o documento cifrado. A duplicidade é procurada pelo índice cego, sem
     * decifrar nada; o índice único (tenant_id, document_hash) do banco cobre a corrida entre duas
     * gravações simultâneas.
     *
     * @throws IllegalArgumentException documento inválido
     * @throws DuplicateGeneratorException outro gerador do tenant já tem o documento
     */
    @Transactional
    public WasteGenerator registerGenerator(RegisterGeneratorRequest request) {
        UUID tenantId = requireTenant();
        String hash = documents.generatorDocumentHash(request.document());
        if (generatorRepository.findByTenantIdAndDocumentHash(tenantId, hash).isPresent()) {
            throw new DuplicateGeneratorException();
        }

        WasteGenerator generator = new WasteGenerator();
        generator.setTenantId(tenantId);
        documents.assignGeneratorDocument(generator, request.document());
        generator.setCorporateName(request.corporateName());
        generator.setTradeName(request.tradeName());
        generator.setAddress(request.address() == null ? null : Json.write(request.address()));
        generator.setCnaeCode(request.cnaeCode());
        generator.setCadriNumber(request.cadriNumber());
        generator.setCadriValidity(request.cadriValidity());
        generator.setProperties(Json.orDefault(request.properties(), "{}"));
        return generatorRepository.save(generator);
    }

    /** Substitui a lista de motoristas do transportador (cifrada inteira). Lista vazia limpa. */
    @Transactional
    public WasteTransporter replaceTransporterDrivers(UUID transporterId, List<MtrDocumentProtection.Driver> drivers) {
        WasteTransporter transporter = transporterRepository.findByIdAndTenantId(transporterId, requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("Transporter not found"));
        documents.assignDrivers(transporter, drivers);
        return transporterRepository.save(transporter);
    }

    @Transactional
    public WasteMtr createMtr(CreateMtrRequest request) {
        UUID tenantId = requireTenant();

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
        documents.assignDriverCpf(mtr, request.driverCpf());
        mtr.setStatus(WasteMtr.MtrStatus.EMITTED);
        mtr.setIssuedAt(OffsetDateTime.now());
        // Map.toString() gera "{a=b}", que não é JSON e o PostgreSQL recusa numa coluna jsonb.
        mtr.setProperties(Json.orDefault(request.properties(), "{}"));
        advanceChain(mtr);

        return mtrRepository.save(mtr);
    }

    @Transactional
    public WasteMtr collectMtr(UUID mtrId, CollectMtrRequest request) {
        WasteMtr mtr = load(mtrId);
        MtrStateMachine.require(mtr.getStatus(), WasteMtr.MtrStatus.COLLECTED);

        mtr.setStatus(WasteMtr.MtrStatus.COLLECTED);
        mtr.setCollectedAt(OffsetDateTime.now());
        mtr.setVehiclePlate(request.vehiclePlate());
        documents.assignDriverCpf(mtr, request.driverCpf());
        mtr.setRouteGeometry(request.routeGeometry());
        mtr.setTransporterSignature(request.transporterSignature());
        advanceChain(mtr);

        return mtrRepository.save(mtr);
    }

    @Transactional
    public WasteMtr receiveMtr(UUID mtrId, ReceiveMtrRequest request) {
        WasteMtr mtr = load(mtrId);
        MtrStateMachine.require(mtr.getStatus(), WasteMtr.MtrStatus.RECEIVED);

        mtr.setStatus(WasteMtr.MtrStatus.RECEIVED);
        mtr.setReceivedAt(OffsetDateTime.now());
        mtr.setDestinationSignature(request.destinationSignature());
        advanceChain(mtr);

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
        cdf.setProcessDetails(Json.orDefault(request.processDetails(), "{}"));
        cdf.setTechnicianId(request.technicianId());
        cdf.setTechnicianName(request.technicianName());
        cdf.setTechnicianCrea(request.technicianCrea());
        cdf.setIssuedAt(OffsetDateTime.now());

        byte[] pdfBytes = pdfGenerator.generateCdfPdf(cdf, mtr,
                documents.revealGeneratorDocument(mtr.getGenerator()));
        cdf.setPdfDocument(pdfBytes);
        cdf.setPdfChecksum(HashChain.sha256Hex(pdfBytes));
        cdf.setHashChain(HashChain.next(null, MtrCanonical.of(cdf)));
        cdfRepository.save(cdf);

        mtr.setStatus(WasteMtr.MtrStatus.DESTINED);
        mtr.setDestinedAt(OffsetDateTime.now());
        mtr.setDestinationSignature(request.destinationSignature());
        advanceChain(mtr);

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

    /**
     * Próximo elo da cadeia. O conteúdo canônico leva o CPF do motorista decifrado, não a cifra: o
     * hash significa o mesmo que antes da V2_8 e é conferível por quem tem o documento.
     */
    private void advanceChain(WasteMtr mtr) {
        mtr.setHashChain(HashChain.next(mtr.getHashChain(), MtrCanonical.of(mtr, documents.revealDriverCpf(mtr))));
    }

    private static UUID requireTenant() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto");
        }
        return tenantId;
    }

    private WasteMtr load(UUID mtrId) {
        return mtrRepository.findByIdAndTenantId(mtrId, TenantContextHolder.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("MTR not found"));
    }

    /**
     * Classes de resíduo aceitas pelo destinador: só um array JSON não vazio de códigos em texto,
     * por exemplo {@code ["A.1.1","B.2.3"]}.
     *
     * <p>Falha fechada. A versão anterior devolvia lista vazia quando o campo era nulo, vazio ou
     * ilegível, e lista vazia significava "aceita qualquer classe": um destinador sem licença
     * cadastrada recebia resíduo perigoso. Também aceitava CSV ("A.1.1,B.2.3") e números, formatos
     * que deixam de valer; o cadastro precisa ser corrigido para o array.
     */
    static List<String> parseAcceptedClasses(String destinationLabel, String json) {
        JsonNode node;
        try {
            node = Json.readTree(json);
        } catch (IllegalStateException e) {
            throw noValidClasses(destinationLabel, e);
        }
        if (!node.isArray() || node.size() == 0) {
            throw noValidClasses(destinationLabel, null);
        }
        List<String> codes = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isString() || item.asString().isBlank()) {
                throw noValidClasses(destinationLabel, null);
            }
            codes.add(item.asString().trim());
        }
        return codes;
    }

    private static IllegalStateException noValidClasses(String destinationLabel, Exception cause) {
        return new IllegalStateException("Destinador " + destinationLabel + " sem classes aceitas válidas: "
                + "cadastre um array JSON de códigos, por exemplo [\"A.1.1\"]", cause);
    }

    private void validateCompatibility(WasteClass wasteClass, WasteDestination destination) {
        List<String> accepted = parseAcceptedClasses(String.valueOf(destination.getId()),
                destination.getAcceptedWasteClasses());
        if (!accepted.contains(wasteClass.getCode())) {
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

    // Request DTOs. Os documentos chegam em claro (com ou sem pontuação) e são cifrados neste serviço.
    // O toString padrão de um record levaria o documento para qualquer log da requisição.
    public record RegisterGeneratorRequest(
            String document, String corporateName, String tradeName, Map<String, Object> address,
            String cnaeCode, String cadriNumber, LocalDate cadriValidity, Map<String, Object> properties
    ) {
        @Override
        public String toString() {
            return "RegisterGeneratorRequest[document=omitido, cnaeCode=" + cnaeCode + "]";
        }
    }

    public record CreateMtrRequest(
            UUID generatorId, UUID transporterId, UUID destinationId, UUID wasteClassId,
            BigDecimal quantityKg, String packagingType, Integer packagingCount,
            String vehiclePlate, String driverCpf, Map<String, Object> properties
    ) {
        @Override
        public String toString() {
            return "CreateMtrRequest[generatorId=" + generatorId + ", transporterId=" + transporterId
                    + ", destinationId=" + destinationId + ", wasteClassId=" + wasteClassId + ", driverCpf=omitido]";
        }
    }

    public record CollectMtrRequest(
            String vehiclePlate, String driverCpf,
            org.locationtech.jts.geom.LineString routeGeometry, String transporterSignature
    ) {
        @Override
        public String toString() {
            return "CollectMtrRequest[vehiclePlate=" + vehiclePlate + ", driverCpf=omitido]";
        }
    }

    public record ReceiveMtrRequest(String destinationSignature) {}

    public record DestinateMtrRequest(
            UUID destinationId, WasteCdf.DestinationMethod destinationMethod,
            Map<String, Object> processDetails, UUID technicianId, String technicianName,
            String technicianCrea, String destinationSignature
    ) {}
}
