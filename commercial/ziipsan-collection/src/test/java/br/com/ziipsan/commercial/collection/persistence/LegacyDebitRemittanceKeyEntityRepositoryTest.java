package br.com.ziipsan.commercial.collection.persistence;

import br.com.ziipsan.commercial.collection.domain.LegacyDebitEntryEntity;
import br.com.ziipsan.commercial.collection.legacydebit.LegacyDebitService;
import br.com.ziipsan.commercial.collection.repository.LegacyDebitEntryRepository;
import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Campo novo da entidade, finder do repositorio e formula viva da chave
 * (V103 / P-R2G1-1), ao nivel possivel sem banco: reflexao sobre as
 * anotacoes JPA e execucao do metodo estatico real
 * {@link LegacyDebitService#remittanceKey}.
 */
class LegacyDebitRemittanceKeyEntityRepositoryTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);

    @Test
    void entidadeTemCampoRemittanceKeyComAColunaDerivada() throws Exception {
        Field field = LegacyDebitEntryEntity.class.getDeclaredField("remittanceKey");
        assertThat(field.getType()).isEqualTo(String.class);
        Column column = field.getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("remittance_key");
        assertThat(column.nullable()).isFalse();
        // 36 (UUID) + 1 + 40 (matricula) + 1 + 15 (valor) + 1 + 10 (data).
        assertThat(column.length()).isEqualTo(104);
    }

    @Test
    void entidadeLeEDevolveAChavePelosAcessores() {
        // Mecanica adaptada ao item 46: sem construtor livre + setter
        // para a chave; constroi-se pelo construtor de aplicacao com a
        // chave derivada. A assercao e a mesma: a entidade devolve
        // EXATAMENTE a chave derivada pelo servico.
        String key = LegacyDebitService.remittanceKey(TENANT, "M1", new BigDecimal("100.00"), DATE);
        LegacyDebitEntryEntity entity = new LegacyDebitEntryEntity(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"),
                "M1", new BigDecimal("100.00"), DATE, key);
        assertThat(entity.getRemittanceKey()).isEqualTo(key);
    }

    @Test
    void tenantIdViveNaSuperclasseLogoOFinderEBemFormado() throws Exception {
        // O finder derivado resolve tenantId contra TenantAwareEntity.
        Field tenant = TenantAwareEntity.class.getDeclaredField("tenantId");
        assertThat(tenant.getType()).isEqualTo(UUID.class);
        assertThat(tenant.getAnnotation(Column.class).name()).isEqualTo("tenant_id");
    }

    @Test
    void repositorioExpoeFinderDerivadoPorTenantEChave() throws Exception {
        Method finder = LegacyDebitEntryRepository.class.getMethod(
                "findByTenantIdAndRemittanceKey", UUID.class, String.class);
        assertThat(finder.getReturnType()).isEqualTo(Optional.class);
        assertThat(finder.getGenericReturnType().getTypeName())
                .contains("LegacyDebitEntryEntity");
    }

    @Test
    void formulaJavaNormalizaOValorComoStripTrailingZeros() {
        assertThat(LegacyDebitService.remittanceKey(TENANT, "M1", new BigDecimal("100.00"), DATE))
                .isEqualTo("11111111-1111-1111-1111-111111111111|M1|100|2026-11-20");
        assertThat(LegacyDebitService.remittanceKey(TENANT, "M1", new BigDecimal("100.50"), DATE))
                .isEqualTo("11111111-1111-1111-1111-111111111111|M1|100.5|2026-11-20");
        assertThat(LegacyDebitService.remittanceKey(TENANT, "M1", new BigDecimal("100.55"), DATE))
                .isEqualTo("11111111-1111-1111-1111-111111111111|M1|100.55|2026-11-20");
        assertThat(LegacyDebitService.remittanceKey(TENANT, "M1", new BigDecimal("100.10"), DATE))
                .isEqualTo("11111111-1111-1111-1111-111111111111|M1|100.1|2026-11-20");
        assertThat(LegacyDebitService.remittanceKey(TENANT, "M1", new BigDecimal("0.00"), DATE))
                .isEqualTo("11111111-1111-1111-1111-111111111111|M1|0|2026-11-20");
        assertThat(LegacyDebitService.remittanceKey(TENANT, "M1", new BigDecimal("0.10"), DATE))
                .isEqualTo("11111111-1111-1111-1111-111111111111|M1|0.1|2026-11-20");
    }

    @Test
    void formulaJavaNoPiorCasoOcupaExatamente104Caracteres() {
        String key = LegacyDebitService.remittanceKey(TENANT, "M".repeat(40),
                new BigDecimal("999999999999.99"), DATE);
        assertThat(key).isEqualTo("11111111-1111-1111-1111-111111111111|"
                + "M".repeat(40) + "|999999999999.99|2026-11-20");
        assertThat(key).hasSize(104);
    }
}
