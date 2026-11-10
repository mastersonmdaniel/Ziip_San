package br.com.ziipsan.grs.mtr.service;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E13, teste 4 (lodo -> MTR).
import br.com.ziipsan.core.effluent.MtrLink;
import br.com.ziipsan.core.effluent.SludgeRecord;
import br.com.ziipsan.core.stationops.StationUnit;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.domain.WasteGenerator;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import br.com.ziipsan.grs.mtr.repository.WasteGeneratorRepository;
import br.com.ziipsan.grs.mtr.repository.WasteMtrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SludgeMtrEmissionTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private MtrService mtrService;
    private WasteMtrRepository mtrRepo;
    private SludgeMtrEmissionService service;
    private WasteGenerator tenantGenerator;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        mtrService = mock(MtrService.class);
        mtrRepo = mock(WasteMtrRepository.class);
        var generatorRepo = mock(WasteGeneratorRepository.class);
        service = new SludgeMtrEmissionService(mtrService, mtrRepo, generatorRepo);
        tenantGenerator = new WasteGenerator();
        tenantGenerator.setId(UUID.randomUUID());
        when(generatorRepo.findByIdAndTenantId(tenantGenerator.getId(), TENANT))
                .thenReturn(Optional.of(tenantGenerator));
        when(mtrRepo.findBySludgeRecordIdAndTenantId(any(), any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private SludgeRecord record(String unit, BigDecimal qty) {
        return new SludgeRecord(UUID.randomUUID(), TENANT, UUID.randomUUID(), StationUnit.UnitKind.WTP,
                SludgeRecord.ResidueKind.SLUDGE, "19 09 02", qty, unit,
                SludgeRecord.QuantityMethod.WEIGHED, Instant.parse("2026-10-05T10:00:00Z"), null);
    }

    private SludgeMtrEmissionService.EmissionRoute route() {
        return new SludgeMtrEmissionService.EmissionRoute(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("registo de lodo gera MTR com gerador = tenant e ligação automática no MtrLink da ED-105")
    void sludgeRecordEmitsMtrWithTenantAsGenerator() {
        SludgeRecord record = record("kg", new BigDecimal("800"));
        MtrLink link = MtrLink.pendingManual(UUID.randomUUID(), TENANT, record.id(), "ED98-ITEM-1");
        WasteMtr created = new WasteMtr();
        created.setMtrNumber("2026.00000007.TENA.07");
        when(mtrService.createMtr(any())).thenReturn(created);

        WasteMtr mtr = service.emitFromSludge(record, link, tenantGenerator.getId(), route());

        assertThat(mtr).isSameAs(created);
        var captor = ArgumentCaptor.forClass(MtrService.CreateMtrRequest.class);
        verify(mtrService).createMtr(captor.capture());
        assertThat(captor.getValue().generatorId()).isEqualTo(tenantGenerator.getId());
        assertThat(captor.getValue().sludgeRecordId()).isEqualTo(record.id());
        assertThat(captor.getValue().quantityKg()).isEqualByComparingTo("800");
        assertThat(link.status()).isEqualTo(MtrLink.Status.LINKED_AUTOMATIC);
        assertThat(link.source()).isEqualTo(MtrLink.Source.AUTOMATIC_ED104);
        assertThat(link.mtrNumber()).isEqualTo("2026.00000007.TENA.07");
    }

    @Test
    @DisplayName("sem registo de lodo não há emissão por esta via; unidade fora de kg recusa sem conversão inventada")
    void noRecordNoEmissionAndUnitGate() {
        assertThatThrownBy(() -> service.emitFromSludge(null, null, tenantGenerator.getId(), route()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sem registo de lodo");
        SludgeRecord inCubicMeters = record("m3", new BigDecimal("2"));
        assertThatThrownBy(() -> service.emitFromSludge(inCubicMeters, null, tenantGenerator.getId(), route()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("kg");
    }

    @Test
    @DisplayName("vínculo EXEMPT da ED-105 é recusado: isenção não vira emissão")
    void exemptLinkIsRefused() {
        SludgeRecord record = record("kg", new BigDecimal("10"));
        MtrLink exempt = MtrLink.exempt(UUID.randomUUID(), TENANT, record.id());
        when(mtrService.createMtr(any())).thenReturn(new WasteMtr());
        assertThatThrownBy(() -> service.emitFromSludge(record, exempt, tenantGenerator.getId(), route()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EXEMPT");
    }

    @Test
    @DisplayName("registo já com MTR ligado devolve o existente (idempotência)")
    void alreadyEmittedReturnsExisting() {
        SludgeRecord record = record("kg", new BigDecimal("10"));
        WasteMtr existing = new WasteMtr();
        existing.setMtrNumber("2026.00000009.TENA.09");
        when(mtrRepo.findBySludgeRecordIdAndTenantId(TENANT, record.id())).thenReturn(Optional.of(existing));
        assertThat(service.emitFromSludge(record, null, tenantGenerator.getId(), route())).isSameAs(existing);
    }
}
