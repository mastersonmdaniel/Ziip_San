package br.com.ziipsan.platform.omnichannel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrityLogTest {

    private List<IntegrityLog.Entry> chainOf3() {
        List<IntegrityLog.Entry> chain = new ArrayList<>();
        String prev = null;
        for (int i = 1; i <= 3; i++) {
            IntegrityLog.Entry entry = IntegrityLog.append(i, OmnichannelTestSupport.TENANT,
                    IntegrityLog.EventType.MESSAGE_RECORDED, "ref-" + i, "atendente",
                    OmnichannelTestSupport.NOW.plusSeconds(i), prev);
            chain.add(entry);
            prev = entry.hash();
        }
        return chain;
    }

    @Test
    void intactChainVerifies() {
        assertThat(IntegrityLog.verifyChain(chainOf3())).isTrue();
        assertThat(IntegrityLog.firstBrokenIndex(chainOf3())).isEqualTo(-1);
        assertThat(IntegrityLog.verifyChain(List.of())).isTrue();
    }

    @Test
    void tamperedEntryBreaksChain() {
        List<IntegrityLog.Entry> chain = new ArrayList<>(chainOf3());
        IntegrityLog.Entry original = chain.get(1);
        chain.set(1, new IntegrityLog.Entry(original.sequence(), original.tenantId(), original.eventType(),
                "ref-ADULTERADA", original.actor(), original.occurredAt(), original.previousHash(),
                original.hash()));
        assertThat(IntegrityLog.verifyChain(chain)).isFalse();
        assertThat(IntegrityLog.firstBrokenIndex(chain)).isEqualTo(1);
    }

    @Test
    void removedEntryBreaksChain() {
        List<IntegrityLog.Entry> chain = new ArrayList<>(chainOf3());
        chain.remove(1);
        assertThat(IntegrityLog.verifyChain(chain)).isFalse();
    }

    @Test
    void credentialEntryCarriesReferenceNeverValue() {
        IntegrityLog.Entry entry = IntegrityLog.append(1, OmnichannelTestSupport.TENANT,
                IntegrityLog.EventType.CREDENTIAL_ACCESSED, "vault://omni/WHATSAPP", "webhook",
                OmnichannelTestSupport.NOW, null);
        assertThat(entry.reference()).isEqualTo("vault://omni/WHATSAPP");
        assertThat(entry.previousHash()).isEqualTo(IntegrityLog.GENESIS_HASH);
        assertThat(entry.hash()).hasSize(64);
    }
}
