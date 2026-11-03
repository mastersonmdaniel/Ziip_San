package br.com.ziipsan.core.parameters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0.19: relógio simulado em tenants de America/Manaus, America/Rio_Branco e
 * America/Noronha. O mesmo instante (UTC) tem data local diferente em cada fuso:
 * 2026-01-15T03:00:00Z é 14/01 em Manaus (UTC-4) e Rio Branco (UTC-5) e 15/01 em
 * Noronha (UTC-2) e São Paulo (UTC-3, sem horário de verão).
 */
class TenantTimeTest {

    private static final Instant INSTANT = Instant.parse("2026-01-15T03:00:00Z");

    private static Clock fixed() {
        return Clock.fixed(INSTANT, ZoneOffset.UTC);
    }

    @Test
    @DisplayName("mesmo instante, datas locais diferentes por fuso (Manaus/Rio Branco no dia anterior)")
    void sameInstantDifferentLocalDates() {
        assertThat(TenantTime.of("America/Manaus", fixed()).nowLocalDate())
                .isEqualTo(LocalDate.of(2026, 1, 14));
        assertThat(TenantTime.of("America/Rio_Branco", fixed()).nowLocalDate())
                .isEqualTo(LocalDate.of(2026, 1, 14));
        assertThat(TenantTime.of("America/Noronha", fixed()).nowLocalDate())
                .isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(TenantTime.of("America/Sao_Paulo", fixed()).nowLocalDate())
                .isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    @DisplayName("instante gravado em UTC converte para a data local do tenant, sem mudar o instante")
    void instantStoredInUtcConvertedToLocalDate() {
        TenantTime manaus = TenantTime.of("America/Manaus", fixed());
        assertThat(manaus.localDateOf(INSTANT)).isEqualTo(LocalDate.of(2026, 1, 14));
        assertThat(manaus.nowInstant()).isEqualTo(INSTANT);
    }

    @Test
    @DisplayName("padrão de fábrica é America/Sao_Paulo")
    void factoryDefaultIsSaoPaulo() {
        assertThat(TenantTime.factoryDefault(fixed()).zone().getId()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    @DisplayName("identificador IANA inválido e deslocamento fixo são recusados")
    void invalidIanaIdRefused() {
        assertThatThrownBy(() -> TenantTime.of("America/SaoPaulo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantTime.of("-03:00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantTime.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
