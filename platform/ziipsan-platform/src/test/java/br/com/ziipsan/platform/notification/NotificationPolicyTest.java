package br.com.ziipsan.platform.notification;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regras puras da P3.36, com casos calculados à mão (fuso America/Sao_Paulo = UTC-3,
 * sem horário de verão desde 2019):
 * 2026-03-10T13:00:00Z = 10:00 local; 2026-03-10T02:30:00Z = 2026-03-09 23:30 local.
 */
class NotificationPolicyTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final Set<NotificationCategory> NO_TENANT_ADDITIONS = Set.of();

    @Test
    @DisplayName("piso da plataforma: segurança, aprovação pendente e aviso legal são obrigatórios mesmo sem tenant declarar nada")
    void platformFloorIsMandatory() {
        assertThat(NotificationPolicy.effectiveMandatoryCategories(NO_TENANT_ADDITIONS))
                .containsExactlyInAnyOrder(NotificationCategory.SECURITY,
                        NotificationCategory.PENDING_APPROVAL, NotificationCategory.LEGAL_NOTICE);
        assertThat(NotificationPolicy.isMandatory(NotificationCategory.BILLING, NO_TENANT_ADDITIONS)).isFalse();
    }

    @Test
    @DisplayName("tenant ACRESCENTA categoria ao piso (classe c), nunca remove: BILLING declarado vira obrigatório e LEGAL_NOTICE continua")
    void tenantCanOnlyAddToTheFloor() {
        Set<NotificationCategory> effective = NotificationPolicy.effectiveMandatoryCategories(Set.of(NotificationCategory.BILLING));
        assertThat(effective).contains(NotificationCategory.BILLING, NotificationCategory.LEGAL_NOTICE,
                NotificationCategory.SECURITY, NotificationCategory.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("REGRA DO ACEITE: aviso legal NÃO é desligável — desligar LEGAL_NOTICE é recusado com 422 nomeando o campo")
    void legalNoticeCannotBeDisabled() {
        assertThatThrownBy(() -> NotificationPolicy.assertCanDisable(NotificationCategory.LEGAL_NOTICE, NO_TENANT_ADDITIONS))
                .isInstanceOf(ApiException.Validation.class)
                .hasMessageContaining("Há campos inválidos");
        // E continua recusado mesmo se o tenant tentar esvaziar o parâmetro (conjunto vazio declarado).
        assertThatThrownBy(() -> NotificationPolicy.assertCanDisable(NotificationCategory.LEGAL_NOTICE, Set.of()))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("segurança e aprovação pendente também não desligam; categoria comum desliga sem recusa")
    void otherMandatoryCategoriesCannotBeDisabledEither() {
        assertThatThrownBy(() -> NotificationPolicy.assertCanDisable(NotificationCategory.SECURITY, NO_TENANT_ADDITIONS))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> NotificationPolicy.assertCanDisable(NotificationCategory.PENDING_APPROVAL, NO_TENANT_ADDITIONS))
                .isInstanceOf(ApiException.Validation.class);
        NotificationPolicy.assertCanDisable(NotificationCategory.BILLING, NO_TENANT_ADDITIONS);
    }

    @Test
    @DisplayName("silêncio simples 22:00–07:00: 23:30 local está silenciado e o fim é 07:00 do dia seguinte (10:00Z)")
    void silenceCrossingMidnight() {
        Instant at2330Local = Instant.parse("2026-03-10T02:30:00Z"); // 2026-03-09 23:30 em SP
        assertThat(NotificationPolicy.isSilenced(at2330Local, LocalTime.of(22, 0), LocalTime.of(7, 0), SP)).isTrue();
        // Fim: 2026-03-10 07:00 local = 10:00Z.
        assertThat(NotificationPolicy.silenceEndsAt(at2330Local, LocalTime.of(22, 0), LocalTime.of(7, 0), SP))
                .isEqualTo(Instant.parse("2026-03-10T10:00:00Z"));
        Instant at1000Local = Instant.parse("2026-03-10T13:00:00Z");
        assertThat(NotificationPolicy.isSilenced(at1000Local, LocalTime.of(22, 0), LocalTime.of(7, 0), SP)).isFalse();
    }

    @Test
    @DisplayName("decisão: obrigatório ignora silêncio e resumo (agora); comum em resumo diário às 10:00 local espera o dia seguinte 00:00 local (03:00Z)")
    void deliveryDecision() {
        Instant at1000Local = Instant.parse("2026-03-10T13:00:00Z");
        assertThat(NotificationPolicy.decide(at1000Local, NotificationCategory.LEGAL_NOTICE, NO_TENANT_ADDITIONS,
                DigestMode.DAILY, LocalTime.of(22, 0), LocalTime.of(7, 0), SP).timing())
                .isEqualTo(NotificationPolicy.DeliveryTiming.NOW);
        NotificationPolicy.DeliveryDecision daily = NotificationPolicy.decide(at1000Local, NotificationCategory.BILLING,
                NO_TENANT_ADDITIONS, DigestMode.DAILY, null, null, SP);
        assertThat(daily.timing()).isEqualTo(NotificationPolicy.DeliveryTiming.HELD);
        assertThat(daily.deliverAfter()).isEqualTo(Instant.parse("2026-03-11T03:00:00Z")); // 2026-03-11 00:00 SP
        assertThat(NotificationPolicy.decide(at1000Local, NotificationCategory.BILLING, NO_TENANT_ADDITIONS,
                DigestMode.IMMEDIATE, null, null, SP).timing()).isEqualTo(NotificationPolicy.DeliveryTiming.NOW);
    }

    @Test
    @DisplayName("decisão: comum imediato dentro do silêncio 22:00–07:00 às 23:30 local é retido até 07:00 local (10:00Z), não descartado")
    void silenceHoldsNonMandatory() {
        Instant at2330Local = Instant.parse("2026-03-10T02:30:00Z");
        NotificationPolicy.DeliveryDecision d = NotificationPolicy.decide(at2330Local, NotificationCategory.BILLING,
                NO_TENANT_ADDITIONS, DigestMode.IMMEDIATE, LocalTime.of(22, 0), LocalTime.of(7, 0), SP);
        assertThat(d.timing()).isEqualTo(NotificationPolicy.DeliveryTiming.HELD);
        assertThat(d.deliverAfter()).isEqualTo(Instant.parse("2026-03-10T10:00:00Z"));
    }
}
