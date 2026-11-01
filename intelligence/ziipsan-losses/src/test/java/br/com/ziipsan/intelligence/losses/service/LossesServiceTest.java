package br.com.ziipsan.intelligence.losses.service;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.losses.domain.DmaDistrict;
import br.com.ziipsan.intelligence.losses.repository.DmaDistrictRepository;
import br.com.ziipsan.intelligence.losses.repository.LeakDetectionRepository;
import br.com.ziipsan.intelligence.losses.repository.PressureAnalysisRepository;
import br.com.ziipsan.intelligence.losses.repository.WaterBalanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LossesServiceTest {

    private static final UUID TENANT = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final OffsetDateTime START = OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final OffsetDateTime END = OffsetDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.ofHours(-3));

    private WaterBalanceRepository balanceRepo;
    private DmaDistrictRepository dmaRepo;
    private LossesService service;
    private DmaDistrict dma;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        balanceRepo = mock(WaterBalanceRepository.class);
        dmaRepo = mock(DmaDistrictRepository.class);
        service = new LossesService(balanceRepo, dmaRepo, mock(LeakDetectionRepository.class),
                mock(PressureAnalysisRepository.class));

        dma = new DmaDistrict();
        dma.setId(UUID.randomUUID());
        dma.setCode("DMC-CENTRO-01");
        when(dmaRepo.findByIdAndTenantId(dma.getId(), TENANT)).thenReturn(Optional.of(dma));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private LossesService.CalculateWaterBalanceRequest request(UUID dmaId, boolean approve) {
        return new LossesService.CalculateWaterBalanceRequest(dmaId, START, END, "MONTHLY", approve, Map.of());
    }

    @Test
    @DisplayName("sem macromedição/faturamento integrado: erro explícito com o DMC e o período, nada gravado")
    void missingInputsFailWithoutSaving() {
        // Antes: volumes fixos (10000, 7000, 200, 100 m3, 2%) e um balanço em rascunho gravado.
        assertThatThrownBy(() -> service.calculateWaterBalance(request(dma.getId(), false)))
                .isInstanceOf(MissingInputDataException.class)
                .hasMessageContaining("sem macromedicao/faturamento integrado")
                .hasMessageContaining("DMC-CENTRO-01")
                .hasMessageContaining(START.toString())
                .hasMessageContaining(END.toString());
        verify(balanceRepo, never()).save(any());
    }

    @Test
    @DisplayName("sem DMC (sistema inteiro) e pedindo aprovação: o mesmo erro, nada gravado")
    void missingInputsWithoutDmaAndApproval() {
        assertThatThrownBy(() -> service.calculateWaterBalance(request(null, true)))
                .isInstanceOf(MissingInputDataException.class)
                .hasMessageContaining("sistema inteiro");
        verify(balanceRepo, never()).save(any());
    }

    @Test
    @DisplayName("período invalido é erro do chamador, antes de procurar as entradas")
    void invalidPeriodIsRejectedFirst() {
        assertThatThrownBy(() -> service.calculateWaterBalance(new LossesService.CalculateWaterBalanceRequest(
                dma.getId(), END, START, "MONTHLY", false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Periodo invalido");
        verify(balanceRepo, never()).save(any());
    }
}
