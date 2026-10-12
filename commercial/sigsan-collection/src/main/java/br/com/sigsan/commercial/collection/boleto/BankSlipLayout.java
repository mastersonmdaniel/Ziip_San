package br.com.sigsan.commercial.collection.boleto;

import br.com.sigsan.commercial.collection.barcode.FreeFieldLayout;
import br.com.sigsan.commercial.collection.barcode.InvalidBarcodeException;
import br.com.sigsan.commercial.collection.barcode.InvalidBarcodeException.Reason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Layout do boleto de cobrança de <b>um convênio</b> (um banco e um jeito de preencher os 25
 * algarismos do campo livre). É dado do tenant, não uma subclasse por banco: o campo livre de cada
 * banco é descrito por um {@link FreeFieldLayout}, que deve ser escrito a partir do manual
 * oficial do banco no onboarding do convênio.
 *
 * @param bankCode  código de compensação do banco emissor (3 algarismos)
 * @param freeField descrição dos 25 algarismos do campo livre
 */
public record BankSlipLayout(String bankCode, FreeFieldLayout freeField) {

    /** Boleto lido, segundo este layout. */
    public record ParsedSlip(BankSlipBarcode barcode, BigDecimal amount, Optional<LocalDate> dueDate,
                             Map<String, String> fields) {
    }

    public BankSlipLayout {
        if (bankCode == null || !bankCode.matches("[0-9]{3}")) {
            throw new IllegalArgumentException("Código do banco: 3 algarismos");
        }
        if (freeField == null || freeField.length() != 25) {
            throw new IllegalArgumentException("O campo livre do boleto tem 25 posições");
        }
        if (freeField.hasDueDate()) {
            throw new IllegalArgumentException(
                    "No boleto de cobrança o vencimento vai no fator, não no campo livre");
        }
    }

    /** Gera o código de barras de um boleto em reais com vencimento. */
    public BankSlipBarcode build(BigDecimal amount, LocalDate dueDate, Map<String, String> values) {
        if (dueDate == null) {
            throw new IllegalArgumentException("Boleto sem vencimento não é gerado por este módulo");
        }
        long cents = toCents(amount);
        return new BankSlipBarcode(bankCode, '9', DueDateFactor.encode(dueDate), cents,
                freeField.encode(null, values));
    }

    /**
     * Lê um código de barras exigindo o banco deste layout e campo livre coerente com ele.
     *
     * @param reference     referência para resolver o fator de vencimento (ver {@link DueDateFactor#decode})
     * @param maxDaysBefore janela para trás, em dias
     * @param maxDaysAfter  janela para frente, em dias
     */
    public ParsedSlip parse(String barcode, LocalDate reference, int maxDaysBefore, int maxDaysAfter) {
        return interpret(BankSlipBarcode.parse(barcode), reference, maxDaysBefore, maxDaysAfter);
    }

    public ParsedSlip parseDigitableLine(String line, LocalDate reference, int maxDaysBefore, int maxDaysAfter) {
        return interpret(BankSlipBarcode.fromDigitableLine(line), reference, maxDaysBefore, maxDaysAfter);
    }

    private ParsedSlip interpret(BankSlipBarcode code, LocalDate reference, int before, int after) {
        if (!code.bankCode().equals(bankCode)) {
            throw new InvalidBarcodeException(Reason.LAYOUT_MISMATCH,
                    "Boleto do banco " + code.bankCode() + " em convênio do banco " + bankCode);
        }
        FreeFieldLayout.Decoded decoded = freeField.decode(code.freeField());
        return new ParsedSlip(code, code.amount(), code.dueDate(reference, before, after), decoded.values());
    }

    private static long toCents(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new InvalidBarcodeException(Reason.FIELD_OUT_OF_RANGE, "Valor ausente ou negativo");
        }
        BigDecimal cents = amount.movePointRight(2);
        if (cents.stripTrailingZeros().scale() > 0) {
            throw new InvalidBarcodeException(Reason.FIELD_OUT_OF_RANGE,
                    "Valor com mais de 2 casas decimais: " + amount.toPlainString());
        }
        try {
            return cents.longValueExact();
        } catch (ArithmeticException e) {
            throw new InvalidBarcodeException(Reason.FIELD_OUT_OF_RANGE, "Valor grande demais");
        }
    }
}
