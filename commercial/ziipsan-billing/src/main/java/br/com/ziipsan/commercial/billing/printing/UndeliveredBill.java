package br.com.ziipsan.commercial.billing.printing;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Registro de conta nao entregue (P2.8, JOR-35): a conta fisica nao chegou
 * ao destinatario e o retorno foi registrado com motivo de catalogo, data e
 * origem. E o dado que alimenta a qualidade cadastral continua da P3.32
 * (regra "conta nao entregue" sobre estes registros) e o contato verificado
 * (o endereco so e marcado como nao verificado quando o motivo implica —
 * ver {@link UndeliveredReason#impliesUnverifiedAddress()}).
 *
 * <p>Nenhum campo e opcional: retorno sem motivo, sem data ou sem origem
 * nao alimenta qualidade cadastral nenhuma e e recusado na origem, nao
 * gravado pela metade.
 *
 * @param billId     conta nao entregue
 * @param unitId     unidade consumidora da conta
 * @param reason     motivo do catalogo fechado {@link UndeliveredReason}
 * @param returnDate data do retorno (a do arquivo de retorno ou do registro)
 * @param origin     origem do retorno {@link ReturnOrigin}
 */
public record UndeliveredBill(
        UUID billId,
        UUID unitId,
        UndeliveredReason reason,
        LocalDate returnDate,
        ReturnOrigin origin) {

    public UndeliveredBill {
        if (billId == null) {
            throw new IllegalArgumentException("billId e obrigatorio no registro de nao entrega");
        }
        if (unitId == null) {
            throw new IllegalArgumentException("unitId e obrigatorio no registro de nao entrega da conta " + billId);
        }
        if (reason == null) {
            throw new IllegalArgumentException("motivo de catalogo e obrigatorio no registro de nao entrega da conta " + billId);
        }
        if (returnDate == null) {
            throw new IllegalArgumentException("data do retorno e obrigatoria no registro de nao entrega da conta " + billId);
        }
        if (origin == null) {
            throw new IllegalArgumentException("origem do retorno e obrigatoria no registro de nao entrega da conta " + billId);
        }
    }

    /** Toda nao entrega registrada e sinal da regra de qualidade da P3.32. */
    public boolean feedsCadastralQuality() {
        return true;
    }

    /** O endereco de entrega deve ser tratado como nao verificado apos este retorno? */
    public boolean marksAddressUnverified() {
        return reason.impliesUnverifiedAddress();
    }
}
