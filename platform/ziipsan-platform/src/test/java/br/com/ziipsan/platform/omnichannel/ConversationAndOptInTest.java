package br.com.ziipsan.platform.omnichannel;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationAndOptInTest {

    private Conversation open(boolean priority, Instant openedAt) {
        return Conversation.open(UUID.randomUUID(), OmnichannelTestSupport.TENANT,
                OmnichannelTestSupport.PROTOCOL, OmnichannelTestSupport.UNIT,
                OmnichannelTestSupport.CUSTOMER, OmnichannelChannel.WHATSAPP,
                "thread-" + UUID.randomUUID(), priority, openedAt);
    }

    @Test
    void conversationRequiresProtocolAndUnit() {
        assertThatThrownBy(() -> Conversation.open(UUID.randomUUID(), OmnichannelTestSupport.TENANT,
                null, OmnichannelTestSupport.UNIT, OmnichannelTestSupport.CUSTOMER,
                OmnichannelChannel.WHATSAPP, "t1", false, OmnichannelTestSupport.NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Conversation.open(UUID.randomUUID(), OmnichannelTestSupport.TENANT,
                OmnichannelTestSupport.PROTOCOL, null, OmnichannelTestSupport.CUSTOMER,
                OmnichannelChannel.WHATSAPP, "t1", false, OmnichannelTestSupport.NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conversationRequiresCustomerId() {
        // D-02 (sonda do revisor): customerId nulo NÃO constrói — a
        // invariante do modelo é a mesma do InboundService
        // (IDENTITY_UNRESOLVED): conversa sem morador não nasce.
        assertThatThrownBy(() -> Conversation.open(UUID.randomUUID(), OmnichannelTestSupport.TENANT,
                OmnichannelTestSupport.PROTOCOL, OmnichannelTestSupport.UNIT, null,
                OmnichannelChannel.WHATSAPP, "t1", false, OmnichannelTestSupport.NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("morador");
        assertThatThrownBy(() -> new Conversation(UUID.randomUUID(), OmnichannelTestSupport.TENANT,
                OmnichannelTestSupport.PROTOCOL, OmnichannelTestSupport.UNIT, null,
                OmnichannelChannel.WHATSAPP, "t1", Conversation.Status.OPEN, null, false,
                OmnichannelTestSupport.NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transitionsAndClosedIsTerminal() {
        Conversation conversation = open(false, OmnichannelTestSupport.NOW);
        Conversation assigned = conversation.assign(OmnichannelTestSupport.ATTENDANT);
        assertThat(assigned.status()).isEqualTo(Conversation.Status.ASSIGNED);
        Conversation waiting = assigned.markReplied();
        assertThat(waiting.status()).isEqualTo(Conversation.Status.WAITING_CUSTOMER);
        assertThat(waiting.markCustomerMessage().status()).isEqualTo(Conversation.Status.ASSIGNED);
        Conversation closed = waiting.close(OmnichannelTestSupport.NOW.plusSeconds(60));
        assertThat(closed.isOpen()).isFalse();
        assertThatThrownBy(() -> closed.assign(OmnichannelTestSupport.ATTENDANT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(closed::markReplied).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void queuePutsPriorityFirstThenOldestAndDropsClosed() {
        Conversation oldNormal = open(false, OmnichannelTestSupport.NOW);
        Conversation newerPriority = open(true, OmnichannelTestSupport.NOW.plusSeconds(600));
        Conversation closed = open(true, OmnichannelTestSupport.NOW.minusSeconds(600))
                .close(OmnichannelTestSupport.NOW);
        List<Conversation> ordered = ConversationQueue.order(List.of(oldNormal, newerPriority, closed));
        assertThat(ordered).containsExactly(newerPriority, oldNormal);
    }

    @Test
    void optInSemanticsMirrorEd41() {
        assertThat(OmniOptInRule.decide(List.of(true))).isEqualTo(OmniOptInRule.OptInDecision.ALLOWED);
        assertThat(OmniOptInRule.decide(List.of())).isEqualTo(OmniOptInRule.OptInDecision.OPT_IN_MISSING);
        assertThat(OmniOptInRule.decide(List.of(true, false)))
                .isEqualTo(OmniOptInRule.OptInDecision.OPT_IN_REVOKED);
        assertThat(OmniOptInRule.decide(List.of(false)))
                .isEqualTo(OmniOptInRule.OptInDecision.OPT_IN_REVOKED);
    }

    @Test
    void messageValidation() {
        UUID conversationId = UUID.randomUUID();
        assertThatThrownBy(() -> new ConversationMessage(UUID.randomUUID(), OmnichannelTestSupport.TENANT,
                conversationId, ConversationMessage.Direction.INBOUND, ConversationMessage.Author.ATTENDANT,
                "oi", null, null, null, OmnichannelTestSupport.NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConversationMessage(UUID.randomUUID(), OmnichannelTestSupport.TENANT,
                conversationId, ConversationMessage.Direction.OUTBOUND, ConversationMessage.Author.ATTENDANT,
                "oi", "p1", "template-x", null, OmnichannelTestSupport.NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("categoria");
    }

    @Test
    void coverageAndE2eReadinessAreCompleteAndHonest() {
        assertThat(OmnichannelCoverage.requirements()).hasSize(12);
        assertThat(ChannelE2eReadiness.all()).hasSize(OmnichannelChannel.values().length)
                .allSatisfy(check -> assertThat(check.status())
                        .isEqualTo(ChannelE2eReadiness.Status.PENDING_EXTERNAL));
    }
}
