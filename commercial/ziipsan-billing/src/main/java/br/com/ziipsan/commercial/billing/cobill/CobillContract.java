package br.com.ziipsan.commercial.billing.cobill;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Contrato de cofaturamento POR SERVICO (ED-25; NR 13, arts. 46 a 56,
 * conforme o acceptance): cada servico de terceiro cobrado na conta tem
 * o seu contrato, com o prestador identificado, o rotulo discriminado
 * que sai na conta, a vigencia e as datas do aviso e do inicio.
 *
 * <p>Esta entrega cobre o servico de RESIDUOS SOLIDOS
 * ({@link ServiceKind#SOLID_WASTE}); o enum deixa o desenho aberto a
 * outros servicos cofaturados sem mudar a regra.
 *
 * <p>O VALOR do servico NAO vive no contrato: ele e da tabela do
 * prestador/tenant vigente na competencia e entra resolvido no calculo
 * ({@link SolidWasteChargeCalculator}) - nenhum valor e semeado aqui.
 *
 * @param noticeDate  data do aviso ao usuario (motor de mensagens legais
 *                    da P2.32, slot {@code slot.cobilling_60d}); obrigatoria
 * @param startDate   primeiro dia de cobranca do servico; a antecedencia
 *                    minima sobre {@code noticeDate} e provada por
 *                    {@link CobillNoticeRule}, nao presumida aqui
 */
public record CobillContract(
        UUID id,
        ServiceKind serviceKind,
        UUID providerId,
        String providerName,
        String billLabel,
        LocalDate validFrom,
        LocalDate validTo,
        LocalDate noticeDate,
        LocalDate startDate) {

    /** Servicos cofaturaveis; a ED-25 entrega residuos solidos. */
    public enum ServiceKind {
        SOLID_WASTE
    }

    public CobillContract {
        Objects.requireNonNull(id, "id do contrato de cofaturamento e obrigatorio");
        Objects.requireNonNull(serviceKind, "servico do contrato e obrigatorio");
        Objects.requireNonNull(providerId, "prestador (terceiro) e obrigatorio");
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("nome do prestador e obrigatorio (valor discriminado na conta)");
        }
        if (billLabel == null || billLabel.isBlank()) {
            throw new IllegalArgumentException("rotulo discriminado da conta e obrigatorio");
        }
        Objects.requireNonNull(validFrom, "inicio de vigencia do contrato e obrigatorio");
        Objects.requireNonNull(noticeDate, "data do aviso ao usuario e obrigatoria (NR 13)");
        Objects.requireNonNull(startDate, "data de inicio da cobranca e obrigatoria");
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("vigencia invalida: fim anterior ao inicio");
        }
        if (startDate.isBefore(validFrom)) {
            throw new IllegalArgumentException("inicio da cobranca anterior a vigencia do contrato");
        }
    }

    /** O contrato esta vigente na data? (vigencia, nao a cobranca). */
    public boolean covers(LocalDate date) {
        Objects.requireNonNull(date, "data e obrigatoria");
        return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }
}
