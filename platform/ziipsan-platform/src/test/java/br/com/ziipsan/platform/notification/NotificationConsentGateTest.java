package br.com.ziipsan.platform.notification;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guarda de consentimento do CONSUMIDOR (correção pós-revisão da P3.36, CAD-84/DAD-30):
 * o gate confere O {@code consentId} gravado na preferência — não "algum" consentimento — e
 * sujeito ambíguo (usuário + consumidor juntos) é recusado ANTES de qualquer gate, em vez
 * de o {@code subjectKind} antigo devolver USER e pular a guarda em silêncio. As recusas
 * acontecem antes da persistência: o DataSource destes testes explode se for tocado, e o
 * caso que PASSA nas guardas é reconhecido justamente por chegar até ele.
 */
class NotificationConsentGateTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CUSTOMER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONTACT = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CONSENT_VIGENTE = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID CONSENT_ALHEIO = UUID.fromString("66666666-6666-6666-6666-666666666666");

    /** Gate que registra as chamadas e só reconhece UM consentId como vigente. */
    private static final class GateRegistrador implements ConsentGate {
        final AtomicInteger chamadas = new AtomicInteger();
        final AtomicReference<UUID> consentIdConferido = new AtomicReference<>();

        @Override
        public boolean isVerifiedContact(UUID tenantId, UUID contactId) {
            chamadas.incrementAndGet();
            return true;
        }

        @Override
        public boolean hasActiveConsent(UUID tenantId, UUID customerId, UUID contactId, UUID consentId, String purposeCode) {
            chamadas.incrementAndGet();
            consentIdConferido.set(consentId);
            return CONSENT_VIGENTE.equals(consentId);
        }
    }

    private static DataSource bancoIntocavel() {
        return (DataSource) Proxy.newProxyInstance(NotificationConsentGateTest.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (p, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        throw new AssertionError("o banco foi tocado — a guarda devia ter resolvido antes (ou a gravação começou)");
                    }
                    return null;
                });
    }

    private static NotificationCenterService service(ConsentGate gate) {
        NotificationParameters parameters = new NotificationParameters() {
            @Override
            public DigestMode digestDefault(UUID tenantId) {
                return DigestMode.IMMEDIATE;
            }

            @Override
            public Set<NotificationCategory> tenantMandatoryCategories(UUID tenantId) {
                return Set.of();
            }
        };
        BusinessEventCatalog catalog = (name, version) -> Optional.empty();
        return new NotificationCenterService(bancoIntocavel(), "test", TENANT, catalog, gate, parameters,
                ZoneId.of("America/Sao_Paulo"), Clock.fixed(Instant.parse("2026-03-10T13:00:00Z"), ZoneId.of("UTC")));
    }

    private static NotificationPreference preferencia(UUID userId, UUID customerId, UUID consentId) {
        return new NotificationPreference(null, userId, customerId,
                customerId == null ? null : CONTACT, consentId,
                "MARKETING", NotificationCategory.GENERAL, NotificationChannel.EMAIL,
                true, null, null, null);
    }

    @Test
    @DisplayName("subjectKind: exatamente um sujeito — os dois juntos ou nenhum é erro explícito, nunca USER presumido")
    void subjectKindRefusesAmbiguity() {
        assertThat(preferencia(USER, null, null).subjectKind())
                .isEqualTo(NotificationPreference.SubjectKind.USER);
        assertThat(preferencia(null, CUSTOMER, CONSENT_VIGENTE).subjectKind())
                .isEqualTo(NotificationPreference.SubjectKind.CONSUMER);
        assertThatThrownBy(() -> preferencia(USER, CUSTOMER, CONSENT_VIGENTE).subjectKind())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ambíguo");
        assertThatThrownBy(() -> preferencia(null, null, null).subjectKind())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("CORREÇÃO: preferência ambígua (userId + customerId) é recusada 422 e o gate NEM é chamado")
    void ambiguousPreferenceIsRefusedBeforeGate() {
        GateRegistrador gate = new GateRegistrador();
        assertThatThrownBy(() -> service(gate).savePreference(preferencia(USER, CUSTOMER, CONSENT_VIGENTE)))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(gate.chamadas.get()).isZero();
    }

    @Test
    @DisplayName("CORREÇÃO: o gate recebe e valida O consentId gravado — consentimento de outro id não abre a guarda")
    void gateValidatesTheRecordedConsentId() {
        GateRegistrador gate = new GateRegistrador();
        assertThatThrownBy(() -> service(gate).savePreference(preferencia(null, CUSTOMER, CONSENT_ALHEIO)))
                .isInstanceOf(ApiException.Validation.class);
        // O id conferido foi o GRAVADO na preferência (o alheio), não uma busca por "algum vigente".
        assertThat(gate.consentIdConferido.get()).isEqualTo(CONSENT_ALHEIO);
    }

    @Test
    @DisplayName("com o consentId certo e vigente, a guarda passa e a gravação é tentada (o DataSource intocável é o marco)")
    void exactActiveConsentPassesTheGate() {
        GateRegistrador gate = new GateRegistrador();
        assertThatThrownBy(() -> service(gate).savePreference(preferencia(null, CUSTOMER, CONSENT_VIGENTE)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("o banco foi tocado");
        assertThat(gate.consentIdConferido.get()).isEqualTo(CONSENT_VIGENTE);
    }

    @Test
    @DisplayName("preferência de USER nunca passa pelo gate de consentimento (e segue para a gravação)")
    void userPreferenceSkipsGate() {
        GateRegistrador gate = new GateRegistrador();
        assertThatThrownBy(() -> service(gate).savePreference(preferencia(USER, null, null)))
                .isInstanceOf(AssertionError.class);
        assertThat(gate.chamadas.get()).isZero();
    }
}
