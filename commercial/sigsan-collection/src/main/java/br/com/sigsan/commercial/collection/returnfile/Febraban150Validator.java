package br.com.sigsan.commercial.collection.returnfile;

import br.com.sigsan.commercial.collection.barcode.CollectionBarcode;
import br.com.sigsan.commercial.collection.barcode.InvalidBarcodeException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validação de negócio de um arquivo de retorno já lido: NSA em sequência, convênio, banco,
 * versão do layout, total de registros e valor total do trailer, e coerência de cada G com o
 * código de barras. Retorna a lista de ocorrências em vez de lançar, para que quem importa decida
 * (rejeitar o arquivo inteiro com {@link Severity#ERROR}; só registrar {@link Severity#WARNING}).
 */
public final class Febraban150Validator {

    public enum Severity { ERROR, WARNING }

    public enum Code {
        BANK_MISMATCH,
        CONVENIO_MISMATCH,
        NSA_OUT_OF_SEQUENCE,
        LAYOUT_VERSION_NOT_ACCEPTED,
        TRAILER_RECORD_COUNT,
        TRAILER_TOTAL_AMOUNT,
        BARCODE_INVALID,
        AMOUNT_DIFFERS_FROM_BARCODE,
        CREDIT_BEFORE_PAYMENT
    }

    public record Issue(Severity severity, Code code, int lineNumber, String message) {
    }

    /**
     * Contexto esperado, vindo do convênio cadastrado.
     *
     * @param bankCode               banco do convênio (A.05)
     * @param convenioCode           código do convênio (A.03)
     * @param expectedNsa            NSA esperado: o último NSA aceito mais 1 (o NSA evolui de 1 em 1)
     * @param acceptedLayoutVersions versões de layout (A.09) aceitas. O manual v08 ainda cita "07"
     *                               como a nova versão e não informa o valor de A.09 para o layout 08;
     *                               por isso a lista é parâmetro do convênio, sem padrão
     */
    public record Expectations(String bankCode, String convenioCode, long expectedNsa,
                               Set<Integer> acceptedLayoutVersions) {
        public Expectations {
            if (bankCode == null || convenioCode == null || acceptedLayoutVersions == null
                    || acceptedLayoutVersions.isEmpty()) {
                throw new IllegalArgumentException(
                        "Banco, convênio e ao menos uma versão de layout aceita são obrigatórios");
            }
            acceptedLayoutVersions = Set.copyOf(acceptedLayoutVersions);
        }
    }

    private Febraban150Validator() {
    }

    public static List<Issue> validate(Febraban150File file, Expectations expected) {
        List<Issue> issues = new ArrayList<>();
        Febraban150File.Header h = file.header();

        if (!h.bankCode().equals(expected.bankCode())) {
            issues.add(error(Code.BANK_MISMATCH, 1, "Banco do arquivo " + h.bankCode()
                    + ", esperado " + expected.bankCode()));
        }
        if (!h.convenioCode().equals(expected.convenioCode().trim())) {
            issues.add(error(Code.CONVENIO_MISMATCH, 1, "Convênio do arquivo '" + h.convenioCode()
                    + "', esperado '" + expected.convenioCode().trim() + "'"));
        }
        if (h.nsa() != expected.expectedNsa()) {
            issues.add(error(Code.NSA_OUT_OF_SEQUENCE, 1, "NSA " + h.nsa() + ", esperado " + expected.expectedNsa()));
        }
        if (!expected.acceptedLayoutVersions().contains(h.layoutVersion())) {
            issues.add(error(Code.LAYOUT_VERSION_NOT_ACCEPTED, 1, "Versão de layout " + h.layoutVersion()
                    + " não está entre as aceitas " + expected.acceptedLayoutVersions()));
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (Febraban150File.Payment p : file.payments()) {
            sum = sum.add(p.amountReceived());
            if (p.creditedOn().isBefore(p.paidOn())) {
                issues.add(warning(Code.CREDIT_BEFORE_PAYMENT, p.lineNumber(),
                        "Data de crédito " + p.creditedOn() + " anterior à de pagamento " + p.paidOn()));
            }
            if (!p.barcode().isEmpty()) {
                checkBarcode(p, issues);
            }
        }

        int expectedRecords = file.payments().size() + 2; // inclui header e trailer
        int trailerLine = file.rawLines().size();
        if (file.trailer().totalRecords() != expectedRecords) {
            issues.add(error(Code.TRAILER_RECORD_COUNT, trailerLine, "Trailer informa "
                    + file.trailer().totalRecords() + " registros (com header e trailer); o arquivo tem "
                    + expectedRecords));
        }
        if (file.trailer().totalAmount().compareTo(sum) != 0) {
            issues.add(error(Code.TRAILER_TOTAL_AMOUNT, trailerLine, "Trailer informa valor total "
                    + file.trailer().totalAmount().toPlainString() + "; a soma dos registros G é "
                    + sum.toPlainString()));
        }
        return issues;
    }

    public static boolean hasErrors(List<Issue> issues) {
        return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
    }

    private static void checkBarcode(Febraban150File.Payment p, List<Issue> issues) {
        try {
            CollectionBarcode code = CollectionBarcode.parse(p.barcode());
            code.effectiveAmount().ifPresent(declared -> {
                if (declared.compareTo(p.amountReceived()) != 0) {
                    issues.add(warning(Code.AMOUNT_DIFFERS_FROM_BARCODE, p.lineNumber(),
                            "Valor recebido " + p.amountReceived().toPlainString()
                                    + " difere do valor do código de barras " + declared.toPlainString()));
                }
            });
        } catch (InvalidBarcodeException e) {
            issues.add(warning(Code.BARCODE_INVALID, p.lineNumber(), "Código de barras inválido: " + e.getMessage()));
        }
    }

    private static Issue error(Code code, int line, String message) {
        return new Issue(Severity.ERROR, code, line, message);
    }

    private static Issue warning(Code code, int line, String message) {
        return new Issue(Severity.WARNING, code, line, message);
    }
}
