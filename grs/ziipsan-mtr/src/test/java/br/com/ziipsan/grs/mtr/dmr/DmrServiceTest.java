package br.com.ziipsan.grs.mtr.dmr;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E13, teste 3 (DMR).
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.domain.WasteClass;
import br.com.ziipsan.grs.mtr.domain.WasteDestination;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import br.com.ziipsan.grs.mtr.repository.WasteMtrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DmrServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private WasteMtrRepository mtrRepo;
    private WasteDmrRepository dmrRepo;
    private DmrService service;
    private WasteClass wasteClass;
    private WasteDestination destination;
    private WasteDmr stored;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        mtrRepo = mock(WasteMtrRepository.class);
        dmrRepo = mock(WasteDmrRepository.class);
        service = new DmrService(mtrRepo, dmrRepo);
        wasteClass = new WasteClass();
        wasteClass.setCode("190902");
        destination = new WasteDestination();
        destination.setId(UUID.randomUUID());
        when(dmrRepo.findByTenantIdAndYearAndQuarter(TENANT, 2026, 4)).thenReturn(Optional.empty());
        when(dmrRepo.save(any(WasteDmr.class))).thenAnswer(inv -> {
            stored = inv.getArgument(0);
            return stored;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private WasteMtr mtr(String number, String kg, WasteMtr.MtrStatus status, LocalDate issuedDay) {
        WasteMtr m = new WasteMtr();
        m.setId(UUID.randomUUID());
        m.setMtrNumber(number);
        m.setWasteClass(wasteClass);
        m.setDestination(destination);
        m.setQuantityKg(new BigDecimal(kg));
        m.setStatus(status);
        m.setIssuedAt(issuedDay.atStartOfDay().atOffset(ZoneOffset.UTC).plusHours(9));
        return m;
    }

    @Test
    @DisplayName("agregação do trimestre bate com a soma manual dos MTRs; destinados separados")
    void quarterlyAggregationMatchesManualSum() {
        var a = mtr("2026.00000001.TENA.01", "100", WasteMtr.MtrStatus.DESTINED, LocalDate.of(2026, 10, 3));
        var b = mtr("2026.00000002.TENA.02", "250.5", WasteMtr.MtrStatus.DESTINED, LocalDate.of(2026, 11, 11));
        var c = mtr("2026.00000003.TENA.03", "49.5", WasteMtr.MtrStatus.COLLECTED, LocalDate.of(2026, 12, 24));
        when(mtrRepo.findIssuedInPeriod(eq(TENANT), any(), any())).thenReturn(List.of(a, b, c));

        WasteDmr dmr = service.buildQuarterlyDmr(2026, 4);

        assertThat(dmr.getMtrCount()).isEqualTo(3);
        assertThat(dmr.getTotalQuantityKg()).isEqualByComparingTo("400");
        assertThat(dmr.getDestinedQuantityKg()).isEqualByComparingTo("350.5");
        assertThat(dmr.getStatus()).isEqualTo(WasteDmr.DmrStatus.DRAFT);
        assertThat(dmr.getSubmissionStatus()).isEqualTo("NOT_SUBMITTED");
        JsonNode content = Json.readTree(dmr.getContent());
        assertThat(content.get("byWasteClassCode").get("190902").asString()).isEqualTo("400");
        assertThat(content.get("mtrNumbers").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("a janela é o trimestre exacto: o trimestre vizinho não contamina")
    void quarterWindowIsExact() {
        assertThat(DmrService.quarterWindow(2026, 4))
                .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1));
        assertThat(DmrService.quarterWindow(2026, 1))
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1));
        assertThatThrownBy(() -> DmrService.quarterWindow(2026, 5))
                .isInstanceOf(IllegalArgumentException.class);
        when(mtrRepo.findIssuedInPeriod(eq(TENANT), any(), any())).thenReturn(List.of());
        service.buildQuarterlyDmr(2026, 4);
        var from = org.mockito.ArgumentCaptor.forClass(OffsetDateTime.class);
        var to = org.mockito.ArgumentCaptor.forClass(OffsetDateTime.class);
        org.mockito.Mockito.verify(mtrRepo).findIssuedInPeriod(eq(TENANT), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay().atOffset(ZoneOffset.UTC));
        assertThat(to.getValue()).isEqualTo(LocalDate.of(2027, 1, 1).atStartOfDay().atOffset(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("fechar a DMR é terminal para a agregação; submissão continua NOT_SUBMITTED")
    void closedDmrCannotBeRebuilt() {
        when(mtrRepo.findIssuedInPeriod(eq(TENANT), any(), any())).thenReturn(List.of());
        WasteDmr dmr = service.buildQuarterlyDmr(2026, 4);
        when(dmrRepo.findByTenantIdAndYearAndQuarter(TENANT, 2026, 4)).thenReturn(Optional.of(dmr));
        service.closeDmr(2026, 4);
        assertThat(dmr.getStatus()).isEqualTo(WasteDmr.DmrStatus.CLOSED);
        assertThat(dmr.getClosedAt()).isNotNull();
        assertThat(dmr.getSubmissionStatus()).isEqualTo("NOT_SUBMITTED");
        assertThatThrownBy(() -> service.buildQuarterlyDmr(2026, 4))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("já está fechada");
    }
}
