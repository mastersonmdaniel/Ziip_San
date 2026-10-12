package br.com.sigsan.grs.mtr.service;

import br.com.sigsan.core.tenant.TenantContextHolder;
import br.com.sigsan.grs.mtr.domain.WasteCdf;
import br.com.sigsan.grs.mtr.domain.WasteClass;
import br.com.sigsan.grs.mtr.domain.WasteDestination;
import br.com.sigsan.grs.mtr.domain.WasteGenerator;
import br.com.sigsan.grs.mtr.domain.WasteMtr;
import br.com.sigsan.grs.mtr.domain.WasteMtr.MtrStatus;
import br.com.sigsan.grs.mtr.domain.WasteTransporter;
import br.com.sigsan.grs.mtr.integration.GovBrSignatureService;
import br.com.sigsan.grs.mtr.integration.SinirIntegrationService;
import br.com.sigsan.grs.mtr.repository.WasteCdfRepository;
import br.com.sigsan.grs.mtr.repository.WasteClassRepository;
import br.com.sigsan.grs.mtr.repository.WasteDestinationRepository;
import br.com.sigsan.grs.mtr.repository.WasteGeneratorRepository;
import br.com.sigsan.grs.mtr.repository.WasteMtrRepository;
import br.com.sigsan.grs.mtr.repository.WasteTransporterRepository;
import br.com.sigsan.grs.mtr.service.MtrService.CollectMtrRequest;
import br.com.sigsan.grs.mtr.service.MtrService.CreateMtrRequest;
import br.com.sigsan.grs.mtr.service.MtrService.DestinateMtrRequest;
import br.com.sigsan.grs.mtr.service.MtrService.ReceiveMtrRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MtrServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private WasteMtrRepository mtrRepo;
    private WasteGeneratorRepository generatorRepo;
    private WasteTransporterRepository transporterRepo;
    private WasteDestinationRepository destinationRepo;
    private WasteClassRepository classRepo;
    private WasteCdfRepository cdfRepo;
    private MtrPdfGenerator pdf;
    private MtrService service;

    private WasteGenerator generator;
    private WasteTransporter transporter;
    private WasteDestination destination;
    private WasteClass wasteClass;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenant("tenant_alpha");
        TenantContextHolder.setTenantId(TENANT);

        mtrRepo = mock(WasteMtrRepository.class);
        generatorRepo = mock(WasteGeneratorRepository.class);
        transporterRepo = mock(WasteTransporterRepository.class);
        destinationRepo = mock(WasteDestinationRepository.class);
        classRepo = mock(WasteClassRepository.class);
        cdfRepo = mock(WasteCdfRepository.class);
        pdf = mock(MtrPdfGenerator.class);

        // SINIR real, desligado: devolve PENDING com motivo, que é o comportamento de hoje
        SinirIntegrationService sinir = new SinirIntegrationService("", false);
        service = new MtrService(mtrRepo, generatorRepo, transporterRepo, destinationRepo, classRepo, cdfRepo,
                sinir, mock(GovBrSignatureService.class), pdf);

        generator = entity(new WasteGenerator(), "00000000-0000-0000-0000-000000000001");
        generator.setCnpjCpf("11222333000181");
        transporter = entity(new WasteTransporter(), "00000000-0000-0000-0000-000000000002");
        transporter.setCnpj("12ABC34501DE35");
        destination = entity(new WasteDestination(), "00000000-0000-0000-0000-000000000003");
        destination.setCnpj("11222333000181");
        wasteClass = entity(new WasteClass(), "00000000-0000-0000-0000-000000000004");
        wasteClass.setCode("TESTE-001");

        when(generatorRepo.findByIdAndTenantId(generator.getId(), TENANT)).thenReturn(Optional.of(generator));
        when(transporterRepo.findByIdAndTenantId(transporter.getId(), TENANT)).thenReturn(Optional.of(transporter));
        when(destinationRepo.findByIdAndTenantId(destination.getId(), TENANT)).thenReturn(Optional.of(destination));
        when(classRepo.findByIdAndTenantId(wasteClass.getId(), TENANT)).thenReturn(Optional.of(wasteClass));
        when(mtrRepo.countByTenantId(TENANT)).thenReturn(0L);
        when(mtrRepo.save(any(WasteMtr.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cdfRepo.save(any(WasteCdf.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pdf.generateCdfPdf(any(), any())).thenReturn("pdf-de-teste".getBytes());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static <T extends br.com.sigsan.core.domain.BaseEntity> T entity(T e, String uuid) {
        e.setId(UUID.fromString(uuid));
        return e;
    }

    private WasteMtr create() {
        return service.createMtr(new CreateMtrRequest(generator.getId(), transporter.getId(), destination.getId(),
                wasteClass.getId(), new BigDecimal("250.5"), "TAMBOR", 2, "ABC1D23", "52998224725",
                Map.of("observacao", "teste", "volumes", 2)));
    }

    private void stored(WasteMtr mtr) {
        when(mtrRepo.findByIdAndTenantId(mtr.getId(), TENANT)).thenReturn(Optional.of(mtr));
    }

    // ---------------------------------------------------------------

    @Test
    @DisplayName("createMtr: EMITTED, hash do primeiro elo e properties em JSON válido")
    void createsTheMtr() throws Exception {
        WasteMtr mtr = create();

        assertThat(mtr.getStatus()).isEqualTo(MtrStatus.EMITTED);
        assertThat(mtr.getHashChain()).matches("[0-9a-f]{64}");
        assertThat(mtr.getHashChain()).isEqualTo(HashChain.next(null, MtrCanonical.of(mtr)));
        // Map.toString() daria "{observacao=teste, volumes=2}", JSON inválido que o jsonb recusa
        assertThat(new ObjectMapper().readTree(mtr.getProperties()).get("observacao").asText()).isEqualTo("teste");
    }

    @Test
    @DisplayName("número interno: AAAA.NNNNNNNN.TENA.DD, com DD determinístico (sequência mod 97)")
    void mtrNumberIsDeterministic() {
        assertThat(MtrService.formatMtrNumber(2026, 1, "tenant_alpha")).isEqualTo("2026.00000001.TENA.01");
        assertThat(MtrService.formatMtrNumber(2026, 98, "tenant_alpha")).isEqualTo("2026.00000098.TENA.01");
        assertThat(MtrService.formatMtrNumber(2026, 97, "tenant_alpha")).isEqualTo("2026.00000097.TENA.00");
        // o mesmo número para a mesma entrada, sempre
        assertThat(MtrService.formatMtrNumber(2026, 12345, "ab")).isEqualTo(MtrService.formatMtrNumber(2026, 12345, "ab"));
        assertThat(MtrService.formatMtrNumber(2026, 5, "ab")).isEqualTo("2026.00000005.AB.05");
    }

    @Test
    @DisplayName("regressão: o MTR coletado pode ser recebido (antes receiveMtr falhava sempre)")
    void collectedMtrCanBeReceived() {
        WasteMtr mtr = create();
        mtr.setId(UUID.randomUUID());
        stored(mtr);

        service.collectMtr(mtr.getId(), new CollectMtrRequest("ABC1D23", "52998224725", null, "assinatura-do-transportador"));
        assertThat(mtr.getStatus()).isEqualTo(MtrStatus.COLLECTED);

        service.receiveMtr(mtr.getId(), new ReceiveMtrRequest("assinatura-do-destinador"));
        assertThat(mtr.getStatus()).isEqualTo(MtrStatus.RECEIVED);
        assertThat(mtr.getReceivedAt()).isNotNull();
    }

    @Test
    @DisplayName("a cadeia de hash avança a cada passo e confere do início ao fim")
    void hashChainAdvancesAndVerifies() {
        WasteMtr mtr = create();
        mtr.setId(UUID.randomUUID());
        stored(mtr);
        String h0 = mtr.getHashChain();

        service.collectMtr(mtr.getId(), new CollectMtrRequest("ABC1D23", "52998224725", null, null));
        String h1 = mtr.getHashChain();
        service.receiveMtr(mtr.getId(), new ReceiveMtrRequest("destinador"));
        String h2 = mtr.getHashChain();

        assertThat(h0).isNotEqualTo(h1);
        assertThat(h1).isNotEqualTo(h2);
        // o último elo é recalculável a partir do anterior e do conteúdo canônico atual
        assertThat(h2).isEqualTo(HashChain.next(h1, MtrCanonical.of(mtr)));
    }

    @Test
    @DisplayName("o resultado da sincronização com o SINIR é gravado no MTR: PENDING, com o motivo")
    void sinirResultIsStored() throws Exception {
        WasteMtr mtr = create();
        mtr.setId(UUID.randomUUID());
        stored(mtr);

        service.collectMtr(mtr.getId(), new CollectMtrRequest("ABC1D23", "52998224725", null, null));

        assertThat(mtr.getSinirSyncStatus()).isEqualTo(WasteMtr.SinirSyncStatus.PENDING);
        assertThat(mtr.getSinirSyncAt()).isNotNull();
        assertThat(mtr.getSinirProtocol()).isNull();
        assertThat(new ObjectMapper().readTree(mtr.getSinirResponse()).get("reason").asText())
                .contains("SINIR não configurado");
    }

    @Test
    @DisplayName("transições inválidas são recusadas: destinar sem receber")
    void cannotDestinateWithoutReceiving() {
        WasteMtr mtr = create();
        mtr.setId(UUID.randomUUID());
        stored(mtr);

        assertThatThrownBy(() -> service.destinateMtr(mtr.getId(), new DestinateMtrRequest(
                destination.getId(), WasteCdf.DestinationMethod.RECYCLING, Map.of(), null, "Fulana", "CREA-123", null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EMITTED -> DESTINED");
    }

    @Test
    @DisplayName("destinar: gera o CDF com checksum do PDF e fecha o MTR em DESTINED")
    void destinationIssuesTheCdf() {
        WasteMtr mtr = create();
        mtr.setId(UUID.randomUUID());
        stored(mtr);
        service.collectMtr(mtr.getId(), new CollectMtrRequest("ABC1D23", "52998224725", null, null));
        service.receiveMtr(mtr.getId(), new ReceiveMtrRequest("destinador"));

        WasteCdf cdf = service.destinateMtr(mtr.getId(), new DestinateMtrRequest(destination.getId(),
                WasteCdf.DestinationMethod.RECYCLING, Map.of("processo", "triagem"), UUID.randomUUID(),
                "Fulana", "CREA-123", "assinatura"));

        assertThat(mtr.getStatus()).isEqualTo(MtrStatus.DESTINED);
        assertThat(mtr.getDestinedAt()).isNotNull();
        assertThat(cdf.getPdfChecksum()).isEqualTo(HashChain.sha256Hex("pdf-de-teste".getBytes()));
        assertThat(cdf.getHashChain()).isEqualTo(HashChain.next(null, MtrCanonical.of(cdf)));
        assertThat(cdf.getDestination().getId()).isEqualTo(destination.getId());
    }

    @Test
    @DisplayName("regressão: CDF para destinador diferente do MTR é recusado")
    void cdfForAnotherDestinationIsRejected() {
        WasteMtr mtr = create();
        mtr.setId(UUID.randomUUID());
        stored(mtr);
        service.collectMtr(mtr.getId(), new CollectMtrRequest("ABC1D23", "52998224725", null, null));
        service.receiveMtr(mtr.getId(), new ReceiveMtrRequest("destinador"));

        assertThatThrownBy(() -> service.destinateMtr(mtr.getId(), new DestinateMtrRequest(UUID.randomUUID(),
                WasteCdf.DestinationMethod.RECYCLING, Map.of(), null, "Fulana", "CREA-123", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não é o destinador do MTR");
        assertThat(mtr.getStatus()).as("o MTR continua RECEIVED").isEqualTo(MtrStatus.RECEIVED);
        Mockito.verify(cdfRepo, Mockito.never()).save(any());
    }

    @Test
    @DisplayName("entidades de outro tenant ou inexistentes não são aceitas")
    void unknownEntitiesAreRejected() {
        assertThatThrownBy(() -> service.createMtr(new CreateMtrRequest(UUID.randomUUID(), transporter.getId(),
                destination.getId(), wasteClass.getId(), BigDecimal.ONE, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Generator not found");
    }

    @Test
    @DisplayName("destinador que não aceita a classe de resíduo é recusado")
    void incompatibleDestinationIsRejected() {
        destination.setAcceptedWasteClasses("[\"OUTRA-CLASSE\"]");
        assertThatThrownBy(this::create).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not accept waste class: TESTE-001");
    }
}
