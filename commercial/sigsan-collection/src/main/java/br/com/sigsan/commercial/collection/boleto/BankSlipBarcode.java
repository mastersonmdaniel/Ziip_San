package br.com.sigsan.commercial.collection.boleto;

import br.com.sigsan.commercial.collection.barcode.CheckDigits;
import br.com.sigsan.commercial.collection.barcode.InvalidBarcodeException;
import br.com.sigsan.commercial.collection.barcode.InvalidBarcodeException.Reason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Código de barras do boleto de cobrança bancária (ficha de compensação), 44 posições, e sua
 * linha digitável de 47.
 *
 * <pre>
 * posição  tam  conteúdo
 *  01-03    3   código do banco (compensação)
 *  04       1   código da moeda (9 = real)
 *  05       1   dígito verificador geral (módulo 11, {@link CheckDigits#mod11Billing})
 *  06-09    4   fator de vencimento ({@link DueDateFactor})
 *  10-19   10   valor nominal em centavos
 *  20-44   25   campo livre (definido por cada banco)
 * </pre>
 *
 * <p>Linha digitável (47): campo 1 = banco + moeda + 5 primeiras do campo livre + DV módulo 10;
 * campo 2 = 10 posições seguintes do campo livre + DV; campo 3 = 10 últimas + DV; campo 4 = DV
 * geral; campo 5 = fator (4) + valor (10), sem DV.
 *
 * <p>O campo livre é por banco: aqui é só uma sequência de 25 algarismos. Quem o monta é o
 * {@link BankSlipLayout}, configurado por convênio (dado do tenant). A especificação oficial
 * usada para a estrutura geral e os exemplos numéricos é o manual do Banco Santander
 * (v36, abril/2025); o layout do campo livre dos demais bancos precisa do manual de cada banco
 * (ver README).
 *
 * @param bankCode    3 algarismos
 * @param currency    {@code '9'} (real) ou {@code '8'} (outras moedas)
 * @param factor      fator de vencimento 0000-9999 (0000 = sem vencimento)
 * @param amountCents valor nominal em centavos (10 posições)
 * @param freeField   25 algarismos
 */
public record BankSlipBarcode(String bankCode, char currency, int factor, long amountCents, String freeField) {

    public static final int LENGTH = 44;
    public static final int DIGITABLE_LENGTH = 47;
    public static final long MAX_AMOUNT_CENTS = 9_999_999_999L;

    public BankSlipBarcode {
        if (!isDigits(bankCode) || bankCode.length() != 3) {
            throw new InvalidBarcodeException(Reason.FIELD_OUT_OF_RANGE, "Código do banco: 3 algarismos");
        }
        if (currency != '9' && currency != '8') {
            throw new InvalidBarcodeException(Reason.FIELD_OUT_OF_RANGE,
                    "Código da moeda esperado 9 (real) ou 8 (outras): '" + currency + "'");
        }
        if (factor < 0 || factor > DueDateFactor.MAX_FACTOR) {
            throw new InvalidBarcodeException(Reason.FIELD_OUT_OF_RANGE, "Fator de vencimento fora de 0000-9999");
        }
        if (amountCents < 0 || amountCents > MAX_AMOUNT_CENTS) {
            throw new InvalidBarcodeException(Reason.FIELD_OUT_OF_RANGE, "O valor precisa caber em 10 posições");
        }
        if (!isDigits(freeField) || freeField.length() != 25) {
            throw new InvalidBarcodeException(Reason.FIELD_OUT_OF_RANGE, "Campo livre: 25 algarismos");
        }
    }

    public BigDecimal amount() {
        return BigDecimal.valueOf(amountCents, 2);
    }

    /**
     * Vencimento resolvido pela referência e janela (ver {@link DueDateFactor#decode}); vazio se
     * o fator for 0000.
     */
    public Optional<LocalDate> dueDate(LocalDate reference, int maxDaysBefore, int maxDaysAfter) {
        return DueDateFactor.decode(factor, reference, maxDaysBefore, maxDaysAfter);
    }

    private String body() {
        return pad(factor, 4) + pad(amountCents, 10) + freeField;
    }

    /** Os 44 algarismos, com o DV geral calculado. */
    public String digits() {
        String withoutDv = bankCode + currency + body();
        return bankCode + currency + CheckDigits.mod11Billing(withoutDv) + body();
    }

    /** Linha digitável de 47 algarismos. */
    public String digitableLine() {
        String f1 = bankCode + currency + freeField.substring(0, 5);
        String f2 = freeField.substring(5, 15);
        String f3 = freeField.substring(15, 25);
        int dv = Character.digit(digits().charAt(4), 10);
        return f1 + CheckDigits.mod10(f1) + f2 + CheckDigits.mod10(f2) + f3 + CheckDigits.mod10(f3)
                + dv + pad(factor, 4) + pad(amountCents, 10);
    }

    /** Linha digitável com a pontuação impressa: {@code AAAAA.AAAAAD BBBBB.BBBBBD CCCCC.CCCCCD V FFFFVVVVVVVVVV}. */
    public String digitableLineFormatted() {
        String l = digitableLine();
        return l.substring(0, 5) + "." + l.substring(5, 10) + " " + l.substring(10, 15) + "." + l.substring(15, 21)
                + " " + l.substring(21, 26) + "." + l.substring(26, 32) + " " + l.charAt(32) + " " + l.substring(33);
    }

    /**
     * Lê um código de barras de 44 posições (aceita separadores) e confere o DV geral.
     */
    public static BankSlipBarcode parse(String raw) {
        String d = normalize(raw);
        if (d.length() != LENGTH) {
            throw new InvalidBarcodeException(Reason.BAD_LENGTH,
                    "O código de barras do boleto tem 44 posições, recebido: " + d.length());
        }
        String withoutDv = d.substring(0, 4) + d.substring(5);
        int expected = CheckDigits.mod11Billing(withoutDv);
        int found = d.charAt(4) - '0';
        if (expected != found) {
            throw new InvalidBarcodeException(Reason.BAD_CHECK_DIGIT,
                    "DV geral incorreto: esperado " + expected + ", recebido " + found);
        }
        return new BankSlipBarcode(d.substring(0, 3), d.charAt(3), Integer.parseInt(d.substring(5, 9)),
                Long.parseLong(d.substring(9, 19)), d.substring(19));
    }

    /** Lê uma linha digitável de 47 algarismos, conferindo os 3 DV de campo e o DV geral. */
    public static BankSlipBarcode fromDigitableLine(String raw) {
        String l = normalize(raw);
        if (l.length() != DIGITABLE_LENGTH) {
            throw new InvalidBarcodeException(Reason.BAD_LENGTH,
                    "A linha digitável do boleto tem 47 posições, recebido: " + l.length());
        }
        String[] fields = {l.substring(0, 9), l.substring(10, 20), l.substring(21, 31)};
        int[] dvs = {l.charAt(9) - '0', l.charAt(20) - '0', l.charAt(31) - '0'};
        for (int i = 0; i < 3; i++) {
            int expected = CheckDigits.mod10(fields[i]);
            if (expected != dvs[i]) {
                throw new InvalidBarcodeException(Reason.BAD_BLOCK_CHECK_DIGIT,
                        "DV do campo " + (i + 1) + " incorreto: esperado " + expected + ", recebido " + dvs[i]);
            }
        }
        String barcode = l.substring(0, 4) + l.charAt(32) + l.substring(33) + l.substring(4, 9)
                + l.substring(10, 20) + l.substring(21, 31);
        return parse(barcode);
    }

    private static String normalize(String raw) {
        if (raw == null) {
            throw new InvalidBarcodeException(Reason.NOT_NUMERIC, "Código ausente");
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            if (c == ' ' || c == '.' || c == '-' || c == '\t') {
                continue;
            }
            if (c < '0' || c > '9') {
                throw new InvalidBarcodeException(Reason.NOT_NUMERIC, "Caractere não numérico: '" + c + "'");
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isDigits(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (char c : s.toCharArray()) {
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static String pad(long value, int width) {
        String s = Long.toString(value);
        return "0".repeat(width - s.length()) + s;
    }
}
