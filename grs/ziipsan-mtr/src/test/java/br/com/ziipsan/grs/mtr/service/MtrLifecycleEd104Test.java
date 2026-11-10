package br.com.ziipsan.grs.mtr.service;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E13, testes 1, 6(parcial) e 8.
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.domain.WasteClass;
import br.com.ziipsan.grs.mtr.domain.WasteDestination;
import br.com.ziipsan.grs.mtr.domain.WasteGenerator;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import br.com.ziipsan.grs.mtr.domain.WasteTransporter;
import br.com.ziipsan.grs.mtr.integration.GovBrSignatureService;
import br.com.ziipsan.grs.mtr.repository.*;
import br.com.ziipsan.security.crypto.BlindIndex;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.FieldProtector;
import br.com.ziipsan.security.crypto.dev.EnvDerivedTenantKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MtrLifecycleEd104Test {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CPF = "52998224725";

    private WasteMtrRepository mtrRepo;
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
        var generatorRepo = mock(WasteGeneratorRepository.class);
        var transporterRepo = mock(WasteTransporterRepository.class);
        var destinationRepo = mock(WasteDestinationRepository.class);
        var classRepo = mock(WasteClassRepository.class);
        byte[] master = new byte[32];
        var keys = new EnvDerivedTenantKeyProvider(master);
        var documents = new MtrDocumentProtection(new FieldProtector(new FieldCipher(keys), new BlindIndex(keys)));
        service = new MtrService(mtrRepo, generatorRepo, transporterRepo, destinationRepo, classRepo,
                mock(WasteCdfRepository.class), mock(WasteTemporaryStorageRepository.class),
                mock(GovBrSignatureService.class), documents);

        generator = entity(new WasteGenerator(), "00000000-0000-0000-0000-000000000001");
        documents.assignGeneratorDocument(generator, "11222333000181");
        transporter = entity(new WasteTransporter(), "00000000-0000-0000-0000-000000000002");
        destination = entity(new WasteDestination(), "00000000-0000-0000-0000-000000000003");
        destination.setAcceptedWasteClasses("[\"190902\"]");
        wasteClass = entity(new WasteClass(), "00000000-0000-0000-0000-000000000004");
        wasteClass.setCode("190902");
        wasteClass.setHazardClass(WasteClass.HazardClass.IIA);

        when(generatorRepo.findByIdAndTenantId(generator.getId(), TENANT)).thenReturn(Optional.of(generator));
        when(transporterRepo.findByIdAndTenantId(transporter.getId(), TENANT)).thenReturn(Optional.of(transporter));
        when(destinationRepo.findByIdAndTenantId(destination.getId(), TENANT)).thenReturn(Optional.of(destination));
        when(classRepo.findByIdAndTenantId(wasteClass.getId(), TENANT)).thenReturn(Optional.of(wasteClass));
        when(mtrRepo.countByTenantId(TENANT)).thenReturn(0L);
        when(mtrRepo.save(any(WasteMtr.class))).thenAnswer(inv -> inv.getArgument(0));
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
        WasteMtr mtr = service.createMtr(new MtrService.CreateMtrRequest(generator.getId(), transporter.getId(),
                destination.getId(), wasteClass.getId(), new BigDecimal("1200.5"), "A_GRANEL", null,
                "ABC1D23", CPF, Map.of()));
        mtr.setId(UUID.randomUUID());
        when(mtrRepo.findByIdAndTenantId(mtr.getId(), TENANT)).thenReturn(Optional.of(mtr));
        return mtr;
    }

    @Test
    @DisplayName("caminho completo: emitir -> coletar -> EM TRÂNSITO -> receber -> destinar")
    void fullPathThroughInTransit() {
        WasteMtr mtr = create();
        assertThat(mtr.getStatus()).isEqualTo(WasteMtr.MtrStatus.EMITTED);
        assertThat(mtr.getSinirSyncStatus()).isEqualTo(WasteMtr.SinirSyncStatus.NOT_SUBMITTED);
        assertThat(mtr.getOfficialMtrNumber()).isNull();

        service.collectMtr(mtr.getId(), new MtrService.CollectMtrRequest("ABC1D23", CPF, null, null));
        assertThat(mtr.getStatus()).isEqualTo(WasteMtr.MtrStatus.COLLECTED);

        service.dispatchMtr(mtr.getId());
        assertThat(mtr.getStatus()).isEqualTo(WasteMtr.MtrStatus.IN_TRANSIT);

        service.receiveMtr(mtr.getId(), new MtrService.ReceiveMtrRequest("destinador"));
        assertThat(mtr.getStatus()).isEqualTo(WasteMtr.MtrStatus.RECEIVED);

        service.destinateMtr(mtr.getId(), new MtrService.DestinateMtrRequest(destination.getId(), "assinatura"));
        assertThat(mtr.getStatus()).isEqualTo(WasteMtr.MtrStatus.DESTINED);
    }

    @Test
    @DisplayName("receber sem coletar continua recusado; despachar sem coletar também")
    void invalidTransitionsRefused() {
        WasteMtr mtr = create();
        assertThatThrownBy(() -> service.dispatchMtr(mtr.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EMITTED -> IN_TRANSIT");
        assertThatThrownBy(() -> service.receiveMtr(mtr.getId(), new MtrService.ReceiveMtrRequest("x")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("cancelar exige motivo e é terminal; rejeitar só a partir de RECEIVED")
    void cancelAndRejectAreExplicit() {
        WasteMtr mtr = create();
        assertThatThrownBy(() -> service.cancelMtr(mtr.getId(), " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo");
        service.cancelMtr(mtr.getId(), "gerador desistiu");
        assertThat(mtr.getStatus()).isEqualTo(WasteMtr.MtrStatus.CANCELLED);
        assertThat(mtr.getCancelledAt()).isNotNull();
        assertThatThrownBy(() -> service.collectMtr(mtr.getId(),
                new MtrService.CollectMtrRequest("ABC1D23", CPF, null, null)))
                .isInstanceOf(IllegalStateException.class);

        WasteMtr mtr2 = create();
        service.collectMtr(mtr2.getId(), new MtrService.CollectMtrRequest("ABC1D23", CPF, null, null));
        service.receiveMtr(mtr2.getId(), new MtrService.ReceiveMtrRequest("destinador"));
        service.rejectMtr(mtr2.getId(), "quantidade divergente");
        assertThat(mtr2.getStatus()).isEqualTo(WasteMtr.MtrStatus.REJECTED);
    }

    @Test
    @DisplayName("expirar: dentro da validade recusa; fora da validade expira")
    void expiryRespectsValidity() {
        WasteMtr mtr = create();
        assertThatThrownBy(() -> service.expireMtr(mtr.getId(), OffsetDateTime.now().plusDays(10), 90))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("validade");
        service.expireMtr(mtr.getId(), OffsetDateTime.now().plusDays(120), 90);
        assertThat(mtr.getStatus()).isEqualTo(WasteMtr.MtrStatus.EXPIRED);
    }

    @Test
    @DisplayName("E8: limites do manual como regra do sistema — 45.000 kg ok; 45.000,1 e 0,05 recusam")
    void quantityLimitsAreEnforced() {
        assertThat(service.createMtr(new MtrService.CreateMtrRequest(generator.getId(), transporter.getId(),
                destination.getId(), wasteClass.getId(), new BigDecimal("45000"), null, null, null, CPF, null))
                .getStatus()).isEqualTo(WasteMtr.MtrStatus.EMITTED);
        assertThatThrownBy(() -> service.createMtr(new MtrService.CreateMtrRequest(generator.getId(),
                transporter.getId(), destination.getId(), wasteClass.getId(), new BigDecimal("45000.1"),
                null, null, null, CPF, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("limite");
        assertThatThrownBy(() -> service.createMtr(new MtrService.CreateMtrRequest(generator.getId(),
                transporter.getId(), destination.getId(), wasteClass.getId(), new BigDecimal("0.05"),
                null, null, null, CPF, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mínimo");
    }

    @Test
    @DisplayName("cadeia de hash: determinística entre execuções para o mesmo conteúdo")
    void hashChainIsDeterministic() {
        WasteMtr a = create();
        String first = a.getHashChain();
        // mesmo conteúdo canónico -> mesmo elo (a cadeia não usa aleatoriedade: regressão D-MTR-3)
        assertThat(HashChain.next(null, MtrCanonical.of(a, CPF))).isEqualTo(first);
        assertThat(HashChain.next(null, MtrCanonical.of(a, CPF)))
                .isEqualTo(HashChain.next(null, MtrCanonical.of(a, CPF)));
    }
}
