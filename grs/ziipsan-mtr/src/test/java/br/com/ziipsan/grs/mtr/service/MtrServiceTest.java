package br.com.ziipsan.grs.mtr.service;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.domain.WasteCdf;
import br.com.ziipsan.grs.mtr.domain.WasteClass;
import br.com.ziipsan.grs.mtr.domain.WasteDestination;
import br.com.ziipsan.grs.mtr.domain.WasteGenerator;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import br.com.ziipsan.grs.mtr.domain.WasteMtr.MtrStatus;
import br.com.ziipsan.grs.mtr.domain.WasteTransporter;
import br.com.ziipsan.grs.mtr.integration.GovBrSignatureService;
import br.com.ziipsan.grs.mtr.repository.WasteCdfRepository;
import br.com.ziipsan.grs.mtr.repository.WasteClassRepository;
import br.com.ziipsan.grs.mtr.repository.WasteDestinationRepository;
import br.com.ziipsan.grs.mtr.repository.WasteGeneratorRepository;
import br.com.ziipsan.grs.mtr.repository.WasteMtrRepository;
import br.com.ziipsan.grs.mtr.repository.WasteTransporterRepository;
import br.com.ziipsan.grs.mtr.service.MtrDocumentProtection.Driver;
import br.com.ziipsan.grs.mtr.service.MtrService.CollectMtrRequest;
import br.com.ziipsan.grs.mtr.service.MtrService.CreateMtrRequest;
import br.com.ziipsan.grs.mtr.service.MtrService.DestinateMtrRequest;
import br.com.ziipsan.grs.mtr.service.MtrService.DuplicateGeneratorException;
import br.com.ziipsan.grs.mtr.service.MtrService.ReceiveMtrRequest;
import br.com.ziipsan.grs.mtr.service.MtrService.RegisterGeneratorRequest;
import br.com.ziipsan.security.crypto.BlindIndex;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.FieldProtector;
import br.com.ziipsan.security.crypto.dev.EnvDerivedTenantKeyProvider;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MtrServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    // CPF e CNPJ válidos de exemplo (os mesmos dos testes de DocumentNumber), não de pessoas reais.
    private static final String DRIVER_CPF = "52998224725";
    private static final String GENERATOR_CNPJ = "11222333000181";
    private static final String OTHER_CPF = "11144477735";

    private WasteMtrRepository mtrRepo;
    private WasteGeneratorRepository generatorRepo;
    private WasteTransporterRepository transporterRepo;
    private WasteDestinationRepository destinationRepo;
    private WasteClassRepository classRepo;
    private WasteCdfRepository cdfRepo;
    private MtrPdfGenerator pdf;
    private MtrDocumentProtection documents;
    private MtrService service;
    /** Geradores "gravados", por índice cego: simula o índice único (tenant_id, document_hash). */
    private Map<String, WasteGenerator> generatorsByHash;

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

        // P0.25: SINIR em modo assistido — sem serviço de integração e sem chamada ao SINIR.
        // Cifra real com chave de teste (EnvDerivedTenantKeyProvider é só para desenvolvimento e teste).
        byte[] master = new byte[32];
        for (int i = 0; i < master.length; i++) {
            master[i] = (byte) (i + 7);
        }
        var keys = new EnvDerivedTenantKeyProvider(master);
        documents = new MtrDocumentProtection(new FieldProtector(new FieldCipher(keys), new BlindIndex(keys)));
        service = new MtrService(mtrRepo, generatorRepo, transporterRepo, destinationRepo, classRepo, cdfRepo,
                mock(GovBrSignatureService.class), pdf, documents);

        generator = entity(new WasteGenerator(), "00000000-0000-0000-0000-000000000001");
        documents.assignGeneratorDocument(generator, GENERATOR_CNPJ);
        transporter = entity(new WasteTransporter(), "00000000-0000-0000-0000-000000000002");
        transporter.setCnpj("12ABC34501DE35");
        destination = entity(new WasteDestination(), "00000000-0000-0000-0000-000000000003");
        destination.setCnpj("11222333000181");
        // Falha fechada: sem lista de classes aceitas, nenhum MTR sai para o destinador.
        destination.setAcceptedWasteClasses("[\"TESTE-001\",\"OUTRA-001\"]");
        wasteClass = entity(new WasteClass(), "00000000-0000-0000-0000-000000000004");
        wasteClass.setCode("TESTE-001");

        when(generatorRepo.findByIdAndTenantId(generator.getId(), TENANT)).thenReturn(Optional.of(generator));
        when(transporterRepo.findByIdAndTenantId(transporter.getId(), TENANT)).thenReturn(Optional.of(transporter));
        when(destinationRepo.findByIdAndTenantId(destination.getId(), TENANT)).thenReturn(Optional.of(destination));
        when(classRepo.findByIdAndTenantId(wasteClass.getId(), TENANT)).thenReturn(Optional.of(wasteClass));
        when(mtrRepo.countByTenantId(TENANT)).thenReturn(0L);
        when(mtrRepo.save(any(WasteMtr.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cdfRepo.save(any(WasteCdf.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pdf.generateCdfPdf(any(), any(), any())).thenReturn("pdf-de-teste".getBytes());

        generatorsByHash = new HashMap<>();
        when(generatorRepo.findByTenantIdAndDocumentHash(eq(TENANT), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(generatorsByHash.get(inv.<String>getArgument(1))));
        when(generatorRepo.save(any(WasteGenerator.class))).thenAnswer(inv -> {
            WasteGenerator g = inv.getArgument(0);
            if (generatorsByHash.putIfAbsent(g.getDocumentHash(), g) != null) {
                throw new IllegalStateException("violação do índice único idx_waste_generator_document");
            }
            return g;
        });
        when(transporterRepo.save(any(WasteTransporter.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * Nenhum campo de texto da entidade (incluindo os herdados) contém o documento em claro, com ou
     * sem pontuação. É a prova de que o valor não chega ao banco: o que não está na entidade não é
     * gravado.
     */
    private static void assertNoPlainDocument(Object entity, String digits) {
        String formatted = br.com.ziipsan.core.document.DocumentNumber.format(digits);
        List<String> leaks = new ArrayList<>();
        for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != String.class || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    String value = (String) field.get(entity);
                    if (value != null && (value.contains(digits) || value.contains(formatted))) {
                        leaks.add(type.getSimpleName() + "." + field.getName());
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        assertThat(leaks).as("campos com o documento em claro").isEmpty();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static <T extends br.com.ziipsan.core.domain.BaseEntity> T entity(T e, String uuid) {
        e.setId(UUID.fromString(uuid));
        return e;
    }

    private WasteMtr create() {
        return service.createMtr(new CreateMtrRequest(generator.getId(), transporter.getId(), destination.getId(),
                wasteClass.getId(), new BigDecimal("250.5"), "TAMBOR", 2, "ABC1D23", DRIVER_CPF,
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
        assertThat(mtr.getHashChain()).isEqualTo(HashChain.next(null, MtrCanonical.of(mtr, DRIVER_CPF)));
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

        service.collectMtr(mtr.getId(), new CollectMtrRequest("ABC1D23", DRIVER_CPF, null, "assinatura-do-transportador"));
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

        service.collectMtr(mtr.getId(), new CollectMtrRequest("ABC1D23", DRIVER_CPF, null, null));
        String h1 = mtr.getHashChain();
        service.receiveMtr(mtr.getId(), new ReceiveMtrRequest("destinador"));
        String h2 = mtr.getHashChain();

        assertThat(h0).isNotEqualTo(h1);
        assertThat(h1).isNotEqualTo(h2);
        // o último elo é recalculável a partir do anterior e do conteúdo canônico atual
        assertThat(h2).isEqualTo(HashChain.next(h1, MtrCanonical.of(mtr, DRIVER_CPF)));
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
        service.collectMtr(mtr.getId(), new CollectMtrRequest("ABC1D23", DRIVER_CPF, null, null));
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
        service.collectMtr(mtr.getId(), new CollectMtrRequest("ABC1D23", DRIVER_CPF, null, null));
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

    @ParameterizedTest(name = "classes aceitas [{0}] -> erro, nenhum MTR")
    @NullSource
    @ValueSource(strings = {"", "  ", "[]", "null", "{}", "\"TESTE-001\""})
    @DisplayName("falha fechada: lista nula, vazia ou que não é array não aceita qualquer classe")
    void missingAcceptedClassesFailsClosed(String accepted) {
        destination.setAcceptedWasteClasses(accepted);
        assertThatThrownBy(this::create).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem classes aceitas válidas")
                .hasMessageContaining(destination.getId().toString());
        Mockito.verify(mtrRepo, Mockito.never()).save(any());
    }

    @ParameterizedTest(name = "classes aceitas [{0}] -> erro")
    @ValueSource(strings = {"TESTE-001,OUTRA-001", "[TESTE-001]", "[1,2]", "[\"TESTE-001\", 7]", "[\"\"]"})
    @DisplayName("ilegível, CSV ou array com item que não é texto: erro, formato antigo não vale mais")
    void unreadableAcceptedClassesFails(String accepted) {
        destination.setAcceptedWasteClasses(accepted);
        assertThatThrownBy(this::create).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem classes aceitas válidas");
        Mockito.verify(mtrRepo, Mockito.never()).save(any());
    }

    @Test
    @DisplayName("array JSON de textos com a classe: MTR emitido")
    void acceptedClassArrayAllowsEmission() {
        destination.setAcceptedWasteClasses("[\"OUTRA-001\", \"TESTE-001\"]");
        assertThat(create().getStatus()).isEqualTo(MtrStatus.EMITTED);
    }

    @Test
    @DisplayName("destinador que não aceita a classe de resíduo é recusado")
    void incompatibleDestinationIsRejected() {
        destination.setAcceptedWasteClasses("[\"OUTRA-CLASSE\"]");
        assertThatThrownBy(this::create).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not accept waste class: TESTE-001");
    }
    // --------------------------------------------------------------- documentos pessoais (E4, V2_8)

    private RegisterGeneratorRequest generatorRequest(String document) {
        return new RegisterGeneratorRequest(document, "Fulana de Tal", null, Map.of("cidade", "Teste"),
                "3811400", null, null, null);
    }

    @Test
    @DisplayName("emitir e coletar: o MTR guarda só cifra, índice e máscara do CPF do motorista")
    void issuedMtrHoldsNoPlainDriverCpf() {
        WasteMtr mtr = create();
        assertNoPlainDocument(mtr, DRIVER_CPF);
        assertThat(mtr.getDriverCpfCiphertext()).isNotBlank();
        assertThat(mtr.getDriverCpfHash()).matches("[0-9a-f]{64}");
        assertThat(mtr.getDriverCpfMasked()).isEqualTo("***.***.247-**");
        assertThat(documents.revealDriverCpf(mtr)).isEqualTo(DRIVER_CPF);

        mtr.setId(UUID.randomUUID());
        stored(mtr);
        service.collectMtr(mtr.getId(), new CollectMtrRequest("ABC1D23", "111.444.777-35", null, null));
        assertNoPlainDocument(mtr, OTHER_CPF);
        assertThat(documents.revealDriverCpf(mtr)).as("o CPF da coleta substitui o da emissão").isEqualTo(OTHER_CPF);
    }

    @Test
    @DisplayName("cadastro de gerador: documento só cifrado, com índice e máscara; o CPF de pessoa física não fica em claro")
    void registeredGeneratorHoldsNoPlainDocument() {
        WasteGenerator pf = service.registerGenerator(generatorRequest("529.982.247-25"));
        assertNoPlainDocument(pf, DRIVER_CPF);
        assertThat(pf.getTenantId()).isEqualTo(TENANT);
        assertThat(pf.getDocumentHash()).matches("[0-9a-f]{64}");
        assertThat(pf.getDocumentMasked()).isEqualTo("***.***.247-**");
        assertThat(documents.revealGeneratorDocument(pf)).isEqualTo(DRIVER_CPF);
    }

    @Test
    @DisplayName("motoristas do transportador: a lista vai cifrada inteira e volta igual, com CPF normalizado")
    void transporterDriversAreEncrypted() {
        var drivers = List.of(new Driver("Motorista Um", "529.982.247-25", "01234567890",
                LocalDate.of(2027, 1, 31), null));
        WasteTransporter saved = service.replaceTransporterDrivers(transporter.getId(), drivers);

        assertNoPlainDocument(saved, DRIVER_CPF);
        assertThat(saved.getDriversCiphertext()).doesNotContain("Motorista Um").doesNotContain("01234567890");
        assertThat(documents.revealDrivers(saved)).containsExactly(new Driver("Motorista Um", DRIVER_CPF,
                "01234567890", LocalDate.of(2027, 1, 31), null));

        service.replaceTransporterDrivers(transporter.getId(), List.of());
        assertThat(saved.getDriversCiphertext()).as("lista vazia limpa a coluna").isNull();
        assertThat(documents.revealDrivers(saved)).isEmpty();
    }

    @Test
    @DisplayName("duplicidade de gerador detectada pelo índice cego, com ou sem pontuação, sem decifrar")
    void duplicateGeneratorIsDetectedByBlindIndex() {
        service.registerGenerator(generatorRequest("11.222.333/0001-81"));

        assertThatThrownBy(() -> service.registerGenerator(generatorRequest(GENERATOR_CNPJ)))
                .isInstanceOf(DuplicateGeneratorException.class);
        assertThatThrownBy(() -> service.registerGenerator(generatorRequest("11 222 333 0001 81")))
                .isInstanceOf(DuplicateGeneratorException.class);
        assertThat(generatorsByHash).hasSize(1);

        // outro documento passa
        service.registerGenerator(generatorRequest(DRIVER_CPF));
        assertThat(generatorsByHash).hasSize(2);
    }

    @Test
    @DisplayName("o índice cego do documento depende do tenant: o mesmo CNPJ não liga cadastros de tenants diferentes")
    void blindIndexIsPerTenant() {
        String here = documents.generatorDocumentHash(GENERATOR_CNPJ);
        TenantContextHolder.setTenantId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        assertThat(documents.generatorDocumentHash(GENERATOR_CNPJ)).isNotEqualTo(here);
    }

    @Test
    @DisplayName("hash da cadeia inalterado: calculado sobre o CPF decifrado, com ou sem pontuação na entrada")
    void hashChainUsesTheDecryptedCpf() {
        WasteMtr digits = create();
        WasteMtr punctuated = service.createMtr(new CreateMtrRequest(generator.getId(), transporter.getId(),
                destination.getId(), wasteClass.getId(), new BigDecimal("250.5"), "TAMBOR", 2, "ABC1D23",
                "529.982.247-25", Map.of("observacao", "teste", "volumes", 2)));
        // mesma emissão, só a pontuação do CPF muda
        punctuated.setIssuedAt(digits.getIssuedAt());

        assertThat(digits.getDriverCpfCiphertext()).as("a cifra muda a cada gravação (IV aleatório)")
                .isNotEqualTo(punctuated.getDriverCpfCiphertext());
        // o hash não depende da cifra: é o mesmo conteúdo canônico de antes da V2_8
        assertThat(digits.getHashChain()).isEqualTo(HashChain.next(null, MtrCanonical.of(digits, DRIVER_CPF)));
        assertThat(HashChain.next(null, MtrCanonical.of(punctuated, documents.revealDriverCpf(punctuated))))
                .isEqualTo(digits.getHashChain());
    }

    @Test
    @DisplayName("P0.25: coleta e destinação não chamam o SINIR; o PDF do CDF recebe o documento do gerador")
    void noSinirCallAndPdfReceivesGeneratorDocument() {
        WasteMtr mtr = create();
        mtr.setId(UUID.randomUUID());
        stored(mtr);
        // O status nasce PENDING por padrao da entidade (pendente de envio assistido);
        // o que a P0.25 garante e que coleta/destinacao NAO o alteram nem preenchem
        // protocolo, resposta ou data de tentativa: nenhuma sincronizacao foi tentada.
        service.collectMtr(mtr.getId(), new CollectMtrRequest("ABC1D23", DRIVER_CPF, null, null));
        assertThat(mtr.getSinirSyncStatus()).isEqualTo(WasteMtr.SinirSyncStatus.PENDING);
        assertThat(mtr.getSinirSyncAt()).isNull();
        assertThat(mtr.getSinirResponse()).isNull();
        assertThat(mtr.getSinirProtocol()).isNull();

        service.receiveMtr(mtr.getId(), new ReceiveMtrRequest("destinador"));
        service.destinateMtr(mtr.getId(), new DestinateMtrRequest(destination.getId(),
                WasteCdf.DestinationMethod.RECYCLING, Map.of(), null, "Fulana", "CREA-123", null));
        Mockito.verify(pdf).generateCdfPdf(any(), eq(mtr), eq(GENERATOR_CNPJ));
        assertThat(mtr.getSinirSyncStatus()).isEqualTo(WasteMtr.SinirSyncStatus.PENDING);
        assertThat(mtr.getSinirSyncAt()).isNull();
        assertThat(mtr.getSinirResponse()).isNull();
        assertThat(mtr.getSinirProtocol()).isNull();
    }

    @ParameterizedTest(name = "CPF do motorista [{0}] -> recusado, nenhum MTR")
    @ValueSource(strings = {"12345678900", "529.982.247-24", "11111111111", GENERATOR_CNPJ, "abc"})
    @DisplayName("CPF do motorista inválido (ou um CNPJ) é recusado na emissão e na coleta")
    void invalidDriverCpfIsRejected(String cpf) {
        assertThatThrownBy(() -> service.createMtr(new CreateMtrRequest(generator.getId(), transporter.getId(),
                destination.getId(), wasteClass.getId(), BigDecimal.ONE, null, null, "ABC1D23", cpf, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CPF do motorista inválido");
        Mockito.verify(mtrRepo, Mockito.never()).save(any());

        WasteMtr mtr = create();
        mtr.setId(UUID.randomUUID());
        stored(mtr);
        assertThatThrownBy(() -> service.collectMtr(mtr.getId(), new CollectMtrRequest("ABC1D23", cpf, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "documento do gerador [{0}] -> recusado")
    @NullSource
    @ValueSource(strings = {"", "11.222.333/0001-80", "529.982.247-24", "00000000000000"})
    @DisplayName("documento de gerador inválido é recusado e nada é gravado")
    void invalidGeneratorDocumentIsRejected(String document) {
        assertThatThrownBy(() -> service.registerGenerator(generatorRequest(document)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CPF/CNPJ do gerador inválido");
        Mockito.verify(generatorRepo, Mockito.never()).save(any());
    }

    @Test
    @DisplayName("motorista com CPF inválido é recusado e a lista não é gravada")
    void invalidDriverInListIsRejected() {
        var drivers = List.of(new Driver("Motorista Um", DRIVER_CPF, null, null, null),
                new Driver("Motorista Dois", "123", null, null, null));
        assertThatThrownBy(() -> service.replaceTransporterDrivers(transporter.getId(), drivers))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motorista 2 com CPF inválido");
        assertThat(transporter.getDriversCiphertext()).isNull();
    }

    @Test
    @DisplayName("sem tenant no contexto, nenhum documento é cifrado nem decifrado")
    void noTenantNoDocument() {
        TenantContextHolder.clear();
        assertThatThrownBy(() -> service.registerGenerator(generatorRequest(GENERATOR_CNPJ)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("sem tenant");
        assertThatThrownBy(() -> documents.revealGeneratorDocument(generator))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("sem tenant");
    }

    @Test
    @DisplayName("o toString das requisições não leva o documento para o log")
    void requestsDoNotPrintDocuments() {
        assertThat(generatorRequest("529.982.247-25").toString()).doesNotContain("529.982.247-25");
        assertThat(new CreateMtrRequest(null, null, null, null, null, null, null, null, DRIVER_CPF, null).toString())
                .doesNotContain(DRIVER_CPF);
        assertThat(new CollectMtrRequest("ABC1D23", DRIVER_CPF, null, null).toString()).doesNotContain(DRIVER_CPF);
    }
}
