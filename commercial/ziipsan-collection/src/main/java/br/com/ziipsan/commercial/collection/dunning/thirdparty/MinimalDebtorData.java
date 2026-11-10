package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * E5 — Payload de MINIMO de dados (LGPD) enviado a empresa terceirizada.
 *
 * <p>WHITELIST FECHADA — os unicos campos que existem neste record sao os
 * unicos que podem sair numa remessa:
 * <ol>
 *   <li>{@code debtId} — referencia da divida;</li>
 *   <li>{@code unitId} — referencia da UC (raiz, ADR-0039);</li>
 *   <li>{@code amount} — valor da divida;</li>
 *   <li>{@code dueDate} — vencimento;</li>
 *   <li>{@code debtorIdentifier} — identificador minimo do devedor,
 *       exactamente como declarado pela superficie de origem.</li>
 * </ol>
 * Nao ha nome, endereco, contacto, documento completo nem qualquer outro
 * campo de cadastro: o teste de whitelist (reflexao sobre os componentes
 * deste record) garante que nenhum campo fora desta lista existe.
 */
public record MinimalDebtorData(UUID debtId, UUID unitId, BigDecimal amount,
                                LocalDate dueDate, String debtorIdentifier) {

    public MinimalDebtorData {
        if (debtId == null)
            throw new ThirdPartyCollectionException("MINIMAL_DATA_DEBT_REQUIRED", "payload minimo exige a referencia da divida");
        if (unitId == null)
            throw new ThirdPartyCollectionException("MINIMAL_DATA_UNIT_REQUIRED", "payload minimo exige a referencia da UC (ADR-0039)");
        if (amount == null || amount.signum() < 0)
            throw new ThirdPartyCollectionException("MINIMAL_DATA_AMOUNT_INVALID", "payload minimo exige valor nao negativo");
        if (dueDate == null)
            throw new ThirdPartyCollectionException("MINIMAL_DATA_DUE_DATE_REQUIRED", "payload minimo exige o vencimento");
        if (debtorIdentifier == null || debtorIdentifier.isBlank())
            throw new ThirdPartyCollectionException("MINIMAL_DATA_DEBTOR_ID_REQUIRED",
                    "payload minimo exige o identificador minimo do devedor declarado pela origem");
        if (debtorIdentifier.contains("|"))
            throw new ThirdPartyCollectionException("MINIMAL_DATA_DEBTOR_ID_CONTAINS_SEPARATOR",
                    "identificador minimo do devedor contem '|' — separador da linha de remessa (E6): "
                            + "aceita-lo partiria a linha em mais de 5 campos");
    }
}
