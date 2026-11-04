package br.com.ziipsan.integration.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** P2.26: pertinência de IP a allowlist de IPs/CIDRs, sem DNS reverso. */
class CidrCheckTest {

    @Test
    @DisplayName("allowlist vazia ou nula não restringe; IP fora da lista é negado")
    void emptyAllowlistAllowsAll() {
        assertThat(CidrCheck.allowed("203.0.113.5", List.of())).isTrue();
        assertThat(CidrCheck.allowed("203.0.113.5", null)).isTrue();
        assertThat(CidrCheck.allowed("203.0.113.5", List.of("198.51.100.0/24"))).isFalse();
        assertThat(CidrCheck.allowed(null, List.of("203.0.113.5"))).isFalse();
    }

    @Test
    @DisplayName("CIDR /24 aceita o intervalo e nega fora dele; IP exato exige igualdade")
    void cidrRangeAndExactIp() {
        assertThat(CidrCheck.allowed("198.51.100.1", List.of("198.51.100.0/24"))).isTrue();
        assertThat(CidrCheck.allowed("198.51.100.255", List.of("198.51.100.0/24"))).isTrue();
        assertThat(CidrCheck.allowed("198.51.101.1", List.of("198.51.100.0/24"))).isFalse();
        assertThat(CidrCheck.allowed("203.0.113.5", List.of("203.0.113.5"))).isTrue();
        assertThat(CidrCheck.allowed("203.0.113.6", List.of("203.0.113.5"))).isFalse();
    }

    @Test
    @DisplayName("entrada malformada nunca autoriza")
    void malformedEntryNeverAllows() {
        assertThat(CidrCheck.allowed("203.0.113.5", List.of("nao-um-ip"))).isFalse();
        assertThat(CidrCheck.allowed("nao-um-ip", List.of("203.0.113.0/24"))).isFalse();
    }
}
