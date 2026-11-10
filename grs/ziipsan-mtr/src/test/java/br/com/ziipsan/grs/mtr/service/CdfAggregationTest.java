package br.com.ziipsan.grs.mtr.service;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E13, teste 2 (CDF).
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.domain.WasteCdf;
import br.com.ziipsan.grs.mtr.domain.WasteDestination;
import br.com.ziipsan.grs.mtr.domain.WasteGenerator;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import br.com.ziipsan.grs.mtr.repository.WasteCdfMtrRepository;
import br.com.ziipsan.grs.mtr.repository.WasteCdfRepository;
import br.com.ziipsan.grs.mtr.repository.WasteDestinationRepository;
import br.com.ziipsan.grs.mtr.repository.WasteMtrRepository;
import br.com.ziipsan.security.crypto.BlindIndex;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.FieldProtector;
import br.com.ziipsan.security.crypto.dev.EnvDerivedTenantKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CdfAggregationTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private WasteMtrRepository mtrRepo;
    private WasteCdfMtrRepository linkRepo;
    private CdfService service;
    private WasteDestination destination;
    private WasteGenerator generator;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenant("tenant_alpha");
        TenantContextHolder.setTenantId(TENANT);
        mtrRepo = mock(WasteMtrRepository.class);
        linkRepo = mock(WasteCdfMtrRepository.class);
        var destinationRepo = mock(WasteDestinationRepository.class);
        var cdfRepo = mock(WasteCdfRepository.class);
        var keys = new EnvDerivedTenantKeyProvider(new byte[32]);
        var documents = new MtrDocumentProtection(new FieldProtector(new FieldCipher(keys), new BlindIndex(keys)));
        service = new CdfService(mtrRepo, destinationRepo, cdfRepo, linkRepo, new MtrPdfGenerator(), documents);

        destination = new WasteDestination();
        destination.setId(UUID.randomUUID());
        destination.setCorporateName("Destinador");
        destination.setCnpj("11222333000181");
        generator = new WasteGenerator();
        generator.setId(UUID.randomUUID());
        generator.setCorporateName("Gerador");
        documents.assignGeneratorDocument(generator, "11222333000181");

        when(destinationRepo.findByIdAndTenantId(destination.getId(), TENANT)).thenReturn(Optional.of(destination));
        when(cdfRepo.save(any(WasteCdf.class))).thenAnswer(inv -> inv.getArgument(0));
        when(linkRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(linkRepo.findAlreadyLinkedMtrIds(eq(TENANT), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private WasteMtr destined(String number, String kg, LocalDate destinedDay) {
        WasteMtr m = new WasteMtr();
        m.setId(UUID.randomUUID());
        m.setTenantId(TENANT);
        m.setMtrNumber(number);
        m.setGenerator(generator);
        m.setDestination(destination);
        m.setQuantityKg(new BigDecimal(kg));
        m.setStatus(WasteMtr.MtrStatus.DESTINED);
        m.setDestinedAt(destinedDay.atStartOfDay().atOffset(ZoneOffset.UTC).plusHours(10));
        m.setIssuedAt(OffsetDateTime.now());
        return m;
    }

    private CdfService.IssueCdfRequest request() {
        return new CdfService.IssueCdfRequest(destination.getId(), LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), WasteCdf.DestinationMethod.LANDFILL,
                Map.of("processo", "aterro"), null, "Fulana", "CREA-123");
    }

    @Test
    @DisplayName("um CDF agrega 2+ MTRs do período e não é 1:1")
    void cdfAggregatesSeveralMtrsOfThePeriod() {
        var m1 = destined("2026.00000001.TENA.01", "100", LocalDate.of(2026, 10, 5));
        var m2 = destined("2026.00000002.TENA.02", "250.5", LocalDate.of(2026, 10, 20));
        when(mtrRepo.findDestinedInPeriod(eq(TENANT), eq(destination.getId()), any(), any()))
                .thenReturn(List.of(m1, m2));

        WasteCdf cdf = service.issueCdf(request());

        assertThat(cdf.getPeriodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(cdf.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(cdf.getOfficialCdfNumber()).as("oficial é do sistema emissor — nunca fabricado").isNull();
        assertThat(cdf.getPdfChecksum()).isEqualTo(HashChain.sha256Hex(cdf.getPdfDocument()));
        var captor = ArgumentCaptor.forClass(br.com.ziipsan.grs.mtr.domain.WasteCdfMtr.class);
        verify(linkRepo, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(l -> l.getMtr().getMtrNumber())
                .containsExactly("2026.00000001.TENA.01", "2026.00000002.TENA.02");
    }

    @Test
    @DisplayName("período vazio recusa: CDF sem MTR não certifica massa inexistente")
    void emptyPeriodIsRefused() {
        when(mtrRepo.findDestinedInPeriod(eq(TENANT), eq(destination.getId()), any(), any()))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.issueCdf(request()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("nenhum MTR destinado");
    }

    @Test
    @DisplayName("MTR já agregado a outro CDF não entra duas vezes")
    void alreadyAggregatedMtrIsSkippedAndAllLinkedRefuses() {
        var m1 = destined("2026.00000001.TENA.01", "100", LocalDate.of(2026, 10, 5));
        when(mtrRepo.findDestinedInPeriod(eq(TENANT), eq(destination.getId()), any(), any()))
                .thenReturn(List.of(m1));
        when(linkRepo.findAlreadyLinkedMtrIds(eq(TENANT), any())).thenReturn(List.of(m1.getId()));
        assertThatThrownBy(() -> service.issueCdf(request()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("já estão agregados");
    }

    @Test
    @DisplayName("a janela do período exclui o MTR de fora (fronteira tratada no repositório, conferida aqui)")
    void periodWindowIsPassedToRepository() {
        var m1 = destined("2026.00000001.TENA.01", "100", LocalDate.of(2026, 10, 5));
        when(mtrRepo.findDestinedInPeriod(eq(TENANT), eq(destination.getId()), any(), any()))
                .thenReturn(List.of(m1));
        service.issueCdf(request());
        var from = ArgumentCaptor.forClass(OffsetDateTime.class);
        var to = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(mtrRepo).findDestinedInPeriod(eq(TENANT), eq(destination.getId()), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay().atOffset(ZoneOffset.UTC));
        assertThat(to.getValue()).isEqualTo(LocalDate.of(2026, 11, 1).atStartOfDay().atOffset(ZoneOffset.UTC));
    }
}
