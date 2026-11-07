package br.com.ziipsan.commercial.billing.bill;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Tipo da origem (fonte) de um débito a cobrar ou de um crédito a realizar (DAD-20, P2.5).
 *
 * <p>É o {@code source_type} da spec: diz <i>de onde</i> o lançamento nasceu. O objeto de origem é
 * identificado por {@code source_id} no DTO/entidade; a referência textual do sistema de origem,
 * quando ela vem de fora, fica em {@code legacy_reference}. Os seis valores são os nomeados no
 * aceite da P2.5 — nenhum outro é aceito, nem no domínio ({@link #parse}) nem no banco (CHECK
 * {@code chk_drec_source_type} / {@code chk_crec_source_type} do trecho F3 da V4_4).
 *
 * <ul>
 *   <li>{@code SERVICE_ORDER}: nasceu de uma ordem de serviço; o DTO exige {@code service_order_id};</li>
 *   <li>{@code INSTALLMENT_AGREEMENT}: prestação de parcelamento (acordo); {@code source_id} é o acordo;</li>
 *   <li>{@code INFRACTION}: auto de infração; {@code source_id} é a infração;</li>
 *   <li>{@code PAYMENT_ADJUSTMENT}: ajuste/acerto sobre um pagamento já registrado;</li>
 *   <li>{@code LEGACY_IMPORT}: importado do sistema legado (P2.10); exige {@code legacy_reference};</li>
 *   <li>{@code MANUAL}: lançamento manual (backoffice), sem objeto de origem — é o único tipo em que
 *       {@code source_id} pode ser nulo sem outra exigência.</li>
 * </ul>
 */
public enum ReceivableSourceType {

    SERVICE_ORDER,
    INSTALLMENT_AGREEMENT,
    INFRACTION,
    PAYMENT_ADJUSTMENT,
    LEGACY_IMPORT,
    MANUAL;

    /** Tipos cuja origem é um objeto identificado: {@code source_id} é obrigatório neles. */
    public boolean requiresSourceId() {
        return this == SERVICE_ORDER || this == INSTALLMENT_AGREEMENT || this == INFRACTION;
    }

    /**
     * Converte o texto persistido/lido da borda para o enum, recusando qualquer valor fora dos seis
     * da spec (origem inválida é erro de quem chama, nunca um lançamento "sem tipo").
     */
    public static ReceivableSourceType parse(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("source_type ausente; valores válidos: " + validCodes());
        }
        try {
            return valueOf(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "source_type inválido: '" + code + "'; valores válidos: " + validCodes(), e);
        }
    }

    private static String validCodes() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
