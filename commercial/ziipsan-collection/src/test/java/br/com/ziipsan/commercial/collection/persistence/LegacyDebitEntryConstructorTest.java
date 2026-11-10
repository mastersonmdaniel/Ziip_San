package br.com.ziipsan.commercial.collection.persistence;

import br.com.ziipsan.commercial.collection.domain.LegacyDebitEntryEntity;
import br.com.ziipsan.commercial.collection.legacydebit.LegacyDebitService;
import br.com.ziipsan.commercial.collection.repository.LegacyDebitEntryRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Construtor obrigatorio da entidade (item 46): a construcao usada
 * pela aplicacao exige a chave derivada pelo servico, sem setter
 * livre, e o round-trip pelo repositorio resolve pela chave.
 *
 * <p>O round-trip exercita a interface REAL
 * {@link LegacyDebitEntryRepository} sobre um duplo em memoria via
 * {@link Proxy} dinamico: e prova ao nivel do contrato (assinatura e
 * enderecamento por tenant + chave), SEM contexto Spring/JPA e sem
 * banco - a mesma classe de limitacao da observacao O-1 da revisao
 * original (o finder derivado so e exercitado contra banco na
 * integracao JPA que fecha a P-V103-01).
 */
class LegacyDebitEntryConstructorTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AUTH = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);

    @Test
    void construcaoPelaAplicacaoCarregaExatamenteAChaveDerivadaEmTodoOVetor() {
        // Vetor de normalizacao ja provado (servico x backfill SQL),
        // tal como o futuro adaptador fara: chave = debit.remittanceKey(),
        // aqui derivada pelo metodo real do servico.
        String[][] vetor = {
                {"100.00", "M1"}, {"100.50", "M1"}, {"100.55", "M1"},
                {"100.10", "M1"}, {"0.10", "M1"},
        };
        for (String[] caso : vetor) {
            BigDecimal amount = new BigDecimal(caso[0]);
            String key = LegacyDebitService.remittanceKey(TENANT, caso[1], amount, DATE);
            LegacyDebitEntryEntity entity = new LegacyDebitEntryEntity(AUTH, caso[1], amount, DATE, key);
            assertThat(entity.getRemittanceKey()).isEqualTo(key);
            assertThat(entity.getAuthorizationId()).isEqualTo(AUTH);
            assertThat(entity.getEnrollmentId()).isEqualTo(caso[1]);
            assertThat(entity.getAmount()).isEqualByComparingTo(amount);
            assertThat(entity.getDebitDate()).isEqualTo(DATE);
            // Estado inicial coerente com os inicializadores vigentes.
            assertThat(entity.getStatus()).isEqualTo("REMETIDO");
            assertThat(entity.getRemittanceCount()).isEqualTo(1);
        }
        // Pior caso: matricula de 40 + maior valor ocupam exatamente 104.
        String enroll40 = "M".repeat(40);
        BigDecimal max = new BigDecimal("999999999999.99");
        String key = LegacyDebitService.remittanceKey(TENANT, enroll40, max, DATE);
        assertThat(key).hasSize(104);
        LegacyDebitEntryEntity entity = new LegacyDebitEntryEntity(AUTH, enroll40, max, DATE, key);
        assertThat(entity.getRemittanceKey()).isEqualTo(key);
    }

    @Test
    void construtorRejeitaRemittanceKeyNula() {
        assertThatThrownBy(() -> new LegacyDebitEntryEntity(
                AUTH, "M1", new BigDecimal("100.00"), DATE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("chave de remessa");
    }

    @Test
    void naoExisteSetterPublicoParaRemittanceKey() {
        boolean temSetter = java.util.Arrays.stream(LegacyDebitEntryEntity.class.getMethods())
                .anyMatch(m -> m.getName().equals("setRemittanceKey"));
        assertThat(temSetter).as("setter publico de remittanceKey").isFalse();
        assertThatThrownBy(() -> LegacyDebitEntryEntity.class
                .getMethod("setRemittanceKey", String.class))
                .isInstanceOf(NoSuchMethodException.class);
        // O acessor de leitura continua a existir.
        assertThat(LegacyDebitEntryEntity.class.getMethods())
                .extracting("name")
                .contains("getRemittanceKey");
    }

    @Test
    void construtorSemArgumentosEProtectedEODeAplicacaoEPublico() throws Exception {
        Constructor<LegacyDebitEntryEntity> semArgs =
                LegacyDebitEntryEntity.class.getDeclaredConstructor();
        assertThat(Modifier.isProtected(semArgs.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(semArgs.getModifiers())).isFalse();
        Constructor<LegacyDebitEntryEntity> aplicacao = LegacyDebitEntryEntity.class
                .getConstructor(UUID.class, String.class, BigDecimal.class, LocalDate.class, String.class);
        assertThat(Modifier.isPublic(aplicacao.getModifiers())).isTrue();
    }

    @Test
    void roundTripPeloRepositorioDevolveAChaveEOsCampos() {
        // Duplo em memoria sobre a interface REAL do repositorio
        // (contrato apenas - sem Spring/JPA; ver javadoc da classe).
        Map<String, LegacyDebitEntryEntity> gravados = new HashMap<>();
        LegacyDebitEntryRepository repository = (LegacyDebitEntryRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{LegacyDebitEntryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        LegacyDebitEntryEntity e = (LegacyDebitEntryEntity) args[0];
                        gravados.put(e.getTenantId() + "|" + e.getRemittanceKey(), e);
                        yield e;
                    }
                    case "findByTenantIdAndRemittanceKey" ->
                            Optional.ofNullable(gravados.get(args[0] + "|" + args[1]));
                    case "toString" -> "duplo-em-memoria-de-LegacyDebitEntryRepository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(
                            "duplo em memoria nao implementa " + method.getName());
                });

        BigDecimal amount = new BigDecimal("100.50");
        // Tal como o futuro adaptador: a chave vem de debit.remittanceKey().
        String key = LegacyDebitService.remittanceKey(TENANT, "M1", amount, DATE);
        LegacyDebitEntryEntity entity = new LegacyDebitEntryEntity(AUTH, "M1", amount, DATE, key);
        entity.setTenantId(TENANT); // mecanismo herdado de TenantAwareEntity
        repository.save(entity);

        Optional<LegacyDebitEntryEntity> lido = repository.findByTenantIdAndRemittanceKey(TENANT, key);
        assertThat(lido).isPresent();
        assertThat(lido.get().getRemittanceKey()).isEqualTo(key);
        assertThat(lido.get().getAuthorizationId()).isEqualTo(AUTH);
        assertThat(lido.get().getEnrollmentId()).isEqualTo("M1");
        assertThat(lido.get().getAmount()).isEqualByComparingTo(amount);
        assertThat(lido.get().getDebitDate()).isEqualTo(DATE);
        assertThat(lido.get().getTenantId()).isEqualTo(TENANT);
        // O enderecamento e pela chave: outra chave nao resolve.
        assertThat(repository.findByTenantIdAndRemittanceKey(TENANT, "chave-inexistente")).isEmpty();
    }
}
