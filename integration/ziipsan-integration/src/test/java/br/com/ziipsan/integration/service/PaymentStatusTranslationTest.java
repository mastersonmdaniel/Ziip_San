package br.com.ziipsan.integration.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Convergência V112 (decisão 75, PD-2/PD-3): tradução do {@code status} entre o
 * vocabulário canónico e o FEBRABAN exposto pela API pública. Teste puro, sem banco.
 */
class PaymentStatusTranslationTest {

    @Test
    @DisplayName("os três pares normalizam-se nos dois sentidos, no filtro e na exposição")
    void pairsTranslateBothWays() {
        assertThat(PaymentStatusTranslation.canonicalStatusesForApiFilter("CLASSIFIED"))
                .containsExactlyInAnyOrder("CLASSIFIED", "CLASSIFICADO");
        assertThat(PaymentStatusTranslation.canonicalStatusesForApiFilter("CLASSIFICADO"))
                .containsExactlyInAnyOrder("CLASSIFIED", "CLASSIFICADO");
        assertThat(PaymentStatusTranslation.canonicalStatusesForApiFilter("DUPLICATE"))
                .containsExactlyInAnyOrder("DUPLICATE", "DUPLICIDADE");
        assertThat(PaymentStatusTranslation.canonicalStatusesForApiFilter("DUPLICIDADE"))
                .containsExactlyInAnyOrder("DUPLICATE", "DUPLICIDADE");
        assertThat(PaymentStatusTranslation.canonicalStatusesForApiFilter("DOC_NOT_FOUND"))
                .containsExactlyInAnyOrder("DOC_NOT_FOUND", "NAO_IDENTIFICADO");
        assertThat(PaymentStatusTranslation.canonicalStatusesForApiFilter("NAO_IDENTIFICADO"))
                .containsExactlyInAnyOrder("DOC_NOT_FOUND", "NAO_IDENTIFICADO");

        assertThat(PaymentStatusTranslation.apiStatusForCanonical("CLASSIFICADO")).isEqualTo("CLASSIFIED");
        assertThat(PaymentStatusTranslation.apiStatusForCanonical("DUPLICIDADE")).isEqualTo("DUPLICATE");
        assertThat(PaymentStatusTranslation.apiStatusForCanonical("NAO_IDENTIFICADO")).isEqualTo("DOC_NOT_FOUND");
    }

    @Test
    @DisplayName("os valores sem par passam tal como estão (sem tradução inventada)")
    void unpairedValuesPassThrough() {
        // Os 12 FEBRABAN sem correspondente P3.1 são valores legítimos do unificado.
        for (String febraban : new String[]{"EXCESS", "TO_WRITE_DOWN", "AMOUNT_MISMATCH", "OPEN",
                "DUPLICATE_REFUNDED", "TO_ACCOUNT", "DOC_PRESCRIBED", "DOC_PARCELED", "DOC_CANCELED",
                "DOC_PROCESSING_ERROR", "CREDIT_RECOVERY_CANCELED", "CREDIT_RECOVERY_DUPLICATE"}) {
            assertThat(PaymentStatusTranslation.canonicalStatusesForApiFilter(febraban))
                    .containsExactly(febraban);
            assertThat(PaymentStatusTranslation.apiStatusForCanonical(febraban)).isEqualTo(febraban);
        }
        // Os P3.1 sem correspondente FEBRABAN (BAIXADO incluído) não se traduzem:
        // BAIXADO como CLASSIFIED seria inventar a equivalência que o diagnóstico recusa.
        for (String p31 : new String[]{"RECEBIDO", "BAIXADO", "TRANSFERIDO"}) {
            assertThat(PaymentStatusTranslation.canonicalStatusesForApiFilter(p31)).containsExactly(p31);
            assertThat(PaymentStatusTranslation.apiStatusForCanonical(p31)).isEqualTo(p31);
        }
        // Os membros FEBRABAN dos pares expõem-se a si próprios.
        assertThat(PaymentStatusTranslation.apiStatusForCanonical("CLASSIFIED")).isEqualTo("CLASSIFIED");
        assertThat(PaymentStatusTranslation.apiStatusForCanonical("DUPLICATE")).isEqualTo("DUPLICATE");
        assertThat(PaymentStatusTranslation.apiStatusForCanonical("DOC_NOT_FOUND")).isEqualTo("DOC_NOT_FOUND");
    }

    @Test
    @DisplayName("valor desconhecido passa tal como está; null não filtra / não traduz")
    void unknownAndNull() {
        assertThat(PaymentStatusTranslation.canonicalStatusesForApiFilter("INVENTADO"))
                .containsExactly("INVENTADO");
        assertThat(PaymentStatusTranslation.canonicalStatusesForApiFilter(null)).isEmpty();
        assertThat(PaymentStatusTranslation.apiStatusForCanonical(null)).isNull();
    }
}
