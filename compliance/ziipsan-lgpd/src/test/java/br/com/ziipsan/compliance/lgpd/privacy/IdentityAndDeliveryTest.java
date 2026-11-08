package br.com.ziipsan.compliance.lgpd.privacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Portão de identidade e entrega no portal com registro no personal_data_access_log:
 * nada é entregue sem verificação; a entrega é amarrada ao CHAMADOR (titular da
 * sessão × titular do pedido — defeito D1 da revisão independente); representante só
 * com representação ACTIVE, com documento comprobatório, vigente e com poder
 * REQUEST (P1.41 — defeito D2 da revisão independente); toda entrega gera a entrada
 * de log.
 */
class IdentityAndDeliveryTest {

    private static final UUID SUBJECT = UUID.randomUUID();
    private static final UUID OTHER_SUBJECT = UUID.randomUUID();
    private static final UUID REQUEST = UUID.randomUUID();
    private static final Instant VERIFIED = Instant.parse("2026-11-07T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);

    private static IdentityGate.Representation representation(Set<IdentityGate.RepresentationPower> powers,
                                                              LocalDate validTo) {
        return representation(powers, validTo, IdentityGate.RepresentationStatus.ACTIVE);
    }

    private static IdentityGate.Representation representation(Set<IdentityGate.RepresentationPower> powers,
                                                              LocalDate validTo,
                                                              IdentityGate.RepresentationStatus status) {
        return new IdentityGate.Representation(UUID.randomUUID(), SUBJECT, powers,
                LocalDate.of(2026, 1, 1), validTo, status, UUID.randomUUID());
    }

    @Test
    @DisplayName("sem identidade verificada, nenhuma entrega acontece")
    void unverifiedDeliveryRefused() {
        assertThatThrownBy(() -> AccessDelivery.deliverToHolder(REQUEST, SUBJECT, SUBJECT, null,
                AccessDelivery.Kind.ACCESS_FILE, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("não verificada");
    }

    @Test
    @DisplayName("titular verificado recebe e a entrega gera registro no log de acesso")
    void verifiedHolderDeliveryLogged() {
        AccessDelivery.Delivery delivery = AccessDelivery.deliverToHolder(REQUEST, SUBJECT,
                SUBJECT, VERIFIED, AccessDelivery.Kind.PORTABILITY_FILE, NOW);
        assertThat(delivery.kind()).isEqualTo(AccessDelivery.Kind.PORTABILITY_FILE);
        assertThat(delivery.logEntry().actorKind()).isEqualTo(AccessDelivery.ActorKind.HOLDER);
        assertThat(delivery.logEntry().toJson())
                .contains(REQUEST.toString())
                .contains("PORTABILITY_FILE");
    }

    @Test
    @DisplayName("entrega pedida por terceiro (chamador diferente do titular do pedido) é recusada")
    void deliveryToThirdPartyRefused() {
        // D1 da revisão independente: quem souber o requestId alheio não dispara a
        // entrega — o chamador tem de ser o titular DO PEDIDO (ou representante válido).
        assertThatThrownBy(() -> AccessDelivery.deliverToHolder(REQUEST, SUBJECT,
                OTHER_SUBJECT, VERIFIED, AccessDelivery.Kind.ACCESS_FILE, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outro titular");
        assertThatThrownBy(() -> IdentityGate.requireCallerMatchesSubject(SUBJECT, OTHER_SUBJECT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outro titular");
    }

    @Test
    @DisplayName("representante com poder REQUEST e vigência entrega, logado como representante")
    void representativeWithRequestPowerDelivers() {
        IdentityGate.Representation rep = representation(
                Set.of(IdentityGate.RepresentationPower.REQUEST), LocalDate.of(2027, 1, 1));
        AccessDelivery.Delivery delivery = AccessDelivery.deliverToRepresentative(REQUEST,
                SUBJECT, rep.representativeId(), rep,
                VERIFIED, AccessDelivery.Kind.RESPONSE, NOW, TODAY);
        assertThat(delivery.logEntry().actorKind())
                .isEqualTo(AccessDelivery.ActorKind.REPRESENTATIVE);
        assertThat(delivery.logEntry().actorId()).isEqualTo(rep.representativeId());
        assertThat(delivery.logEntry().subjectId()).isEqualTo(SUBJECT);
    }

    @Test
    @DisplayName("representação vencida não vale para entrega (P1.41)")
    void expiredRepresentationRefused() {
        IdentityGate.Representation rep = representation(
                Set.of(IdentityGate.RepresentationPower.REQUEST), LocalDate.of(2026, 6, 30));
        assertThatThrownBy(() -> AccessDelivery.deliverToRepresentative(REQUEST, SUBJECT,
                rep.representativeId(), rep,
                VERIFIED, AccessDelivery.Kind.RESPONSE, NOW, TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vencida");
    }

    @Test
    @DisplayName("representação sem o poder REQUEST não entrega pedido do titular")
    void representationWithoutRequestPowerRefused() {
        IdentityGate.Representation rep = representation(
                Set.of(IdentityGate.RepresentationPower.VIEW), null);
        assertThatThrownBy(() -> AccessDelivery.deliverToRepresentative(REQUEST, SUBJECT,
                rep.representativeId(), rep,
                VERIFIED, AccessDelivery.Kind.RESPONSE, NOW, TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REQUEST");
    }

    @Test
    @DisplayName("representante igual ao representado é recusado na construção (P1.41)")
    void samePersonRepresentationRefused() {
        assertThatThrownBy(() -> new IdentityGate.Representation(SUBJECT, SUBJECT,
                Set.of(IdentityGate.RepresentationPower.REQUEST), LocalDate.of(2026, 1, 1), null,
                IdentityGate.RepresentationStatus.ACTIVE, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diferente");
    }

    @Test
    @DisplayName("representação pendente de aprovação (PENDING_APPROVAL) não entrega (P1.41)")
    void pendingRepresentationRefused() {
        // D2 da revisão independente: na P1.41 a representação nasce PENDING_APPROVAL
        // e só vale ACTIVE, após aprovação por outra pessoa.
        IdentityGate.Representation rep = representation(
                Set.of(IdentityGate.RepresentationPower.REQUEST), LocalDate.of(2027, 1, 1),
                IdentityGate.RepresentationStatus.PENDING_APPROVAL);
        assertThatThrownBy(() -> AccessDelivery.deliverToRepresentative(REQUEST, SUBJECT,
                rep.representativeId(), rep,
                VERIFIED, AccessDelivery.Kind.RESPONSE, NOW, TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE")
                .hasMessageContaining("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("representação rejeitada ou encerrada (REJECTED/EXPIRED) não entrega (P1.41)")
    void rejectedOrClosedRepresentationRefused() {
        for (IdentityGate.RepresentationStatus status : new IdentityGate.RepresentationStatus[]{
                IdentityGate.RepresentationStatus.REJECTED, IdentityGate.RepresentationStatus.EXPIRED}) {
            IdentityGate.Representation rep = representation(
                    Set.of(IdentityGate.RepresentationPower.REQUEST), LocalDate.of(2027, 1, 1),
                    status);
            assertThatThrownBy(() -> AccessDelivery.deliverToRepresentative(REQUEST, SUBJECT,
                    rep.representativeId(), rep,
                    VERIFIED, AccessDelivery.Kind.RESPONSE, NOW, TODAY))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ACTIVE");
        }
    }

    @Test
    @DisplayName("representação sem documento comprobatório nem se constrói (proof_document_id da P1.41)")
    void representationWithoutProofDocumentRefused() {
        // D2 da revisão independente: proof_document_id é nullable = false na P1.41.
        assertThatThrownBy(() -> new IdentityGate.Representation(UUID.randomUUID(), SUBJECT,
                Set.of(IdentityGate.RepresentationPower.REQUEST), LocalDate.of(2026, 1, 1), null,
                IdentityGate.RepresentationStatus.ACTIVE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comprobatório");
    }

    @Test
    @DisplayName("representação válida de um titular não entrega o pedido de outro titular")
    void representationForOtherSubjectRefused() {
        IdentityGate.Representation rep = representation(
                Set.of(IdentityGate.RepresentationPower.REQUEST), LocalDate.of(2027, 1, 1));
        assertThatThrownBy(() -> AccessDelivery.deliverToRepresentative(REQUEST, OTHER_SUBJECT,
                rep.representativeId(), rep,
                VERIFIED, AccessDelivery.Kind.RESPONSE, NOW, TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outro titular");
    }

    @Test
    @DisplayName("quem chama não sendo o representante registado, a entrega por representação é recusada")
    void callerWhoIsNotTheRepresentativeRefused() {
        IdentityGate.Representation rep = representation(
                Set.of(IdentityGate.RepresentationPower.REQUEST), LocalDate.of(2027, 1, 1));
        assertThatThrownBy(() -> AccessDelivery.deliverToRepresentative(REQUEST, SUBJECT,
                OTHER_SUBJECT, rep,
                VERIFIED, AccessDelivery.Kind.RESPONSE, NOW, TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("representante registado");
    }
}
