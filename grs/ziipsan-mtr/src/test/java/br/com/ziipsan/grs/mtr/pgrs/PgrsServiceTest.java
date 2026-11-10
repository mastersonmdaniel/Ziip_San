package br.com.ziipsan.grs.mtr.pgrs;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E4.
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PgrsServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private WastePgrsRepository repo;
    private PgrsService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        repo = mock(WastePgrsRepository.class);
        service = new PgrsService(repo);
        when(repo.save(any(WastePgrs.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private PgrsService.CreatePgrsRequest request(List<PgrsService.PgrsLine> lines) {
        return new PgrsService.CreatePgrsRequest(UUID.randomUUID(), "Fulana Técnica", "CREA-SC 123456",
                LocalDate.of(2026, 1, 1), LocalDate.of(2029, 12, 31), LocalDate.of(2029, 6, 30), lines);
    }

    @Test
    @DisplayName("PGRS nasce DRAFT com linhas codificadas serializadas em JSON válido")
    void createsDraftWithCodedLines() {
        WastePgrs pgrs = service.createPgrs(request(List.of(
                new PgrsService.PgrsLine("NBR_10004", "190902", "Lodo de ETA", "Aterro classe II"),
                new PgrsService.PgrsLine("RCC", "A", "Entulho de reparo de rede", "Reciclagem"),
                new PgrsService.PgrsLine("RSS", "B", "Resíduo químico de laboratório", "Tratamento"))));
        assertThat(pgrs.getStatus()).isEqualTo(WastePgrs.PgrsStatus.DRAFT);
        assertThat(Json.readTree(pgrs.getLines()).size()).isEqualTo(3);
    }

    @Test
    @DisplayName("linha com código fora do formato do esquema recusa o plano inteiro")
    void invalidLineRefusesPlan() {
        assertThatThrownBy(() -> new PgrsService.PgrsLine("NBR_10004", "123", "curto", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PgrsService.PgrsLine("RSS", "Z", "grupo errado", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createPgrs(request(List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sem linhas");
    }

    @Test
    @DisplayName("activar exige DRAFT e vigência completa; enquadramento jurídico nunca é presumido no código")
    void activationGates() {
        WastePgrs pgrs = service.createPgrs(request(List.of(
                new PgrsService.PgrsLine("NBR_10004", "190902", "Lodo de ETA", "Aterro"))));
        pgrs.setId(UUID.randomUUID());
        when(repo.findByIdAndTenantId(pgrs.getId(), TENANT)).thenReturn(Optional.of(pgrs));
        service.activatePgrs(pgrs.getId());
        assertThat(pgrs.getStatus()).isEqualTo(WastePgrs.PgrsStatus.ACTIVE);
        assertThatThrownBy(() -> service.activatePgrs(pgrs.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("DRAFT");
    }
}
