package br.com.sigsan.commercial.collection.returnfile;

import br.com.sigsan.commercial.collection.returnfile.Febraban150Validator.Code;
import br.com.sigsan.commercial.collection.returnfile.Febraban150Validator.Expectations;
import br.com.sigsan.commercial.collection.returnfile.Febraban150Validator.Issue;
import br.com.sigsan.commercial.collection.returnfile.Febraban150Validator.Severity;
import br.com.sigsan.commercial.collection.returnfile.ReturnFileException.Reason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Leitura e validação do retorno FEBRABAN 150 (registros A, G e Z) com o arquivo
 * {@code samples/febraban150-retorno-SINTETICO.ret}: <b>sintético</b>, gerado por script, sem
 * nenhum dado real (banco 999, convênio SINT-0001, "ARQUIVO SINTETICO" no nome da empresa).
 *
 * <p>Conteúdo: header (NSA 7, versão 07, 11/10/2026); 5 registros G (R$ 123,45, 999,99, 123,45 por
 * Pix, 50,00 sem código de barras, 125,00 com código de R$ 123,45); trailer com 7 registros (5 + header +
 * trailer) e total 123,45 + 999,99 + 123,45 + 50,00 + 125,00 = 1.421,89.
 */
class Febraban150ReturnTest {

    private static final String B8 = "82810000001234501232026111000712345678102026";

    private static List<String> sampleLines() {
        try (InputStream in = Febraban150ReturnTest.class.getResourceAsStream("/samples/febraban150-retorno-SINTETICO.ret")) {
            assertThat(in).as("arquivo de amostra sintético").isNotNull();
            String content = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            return new ArrayList<>(Arrays.asList(content.split("\r\n")));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Febraban150File parse(List<String> lines) {
        return Febraban150ReturnParser.parse(String.join("\r\n", lines) + "\r\n");
    }

    private static Expectations expected(long nsa) {
        return new Expectations("999", "SINT-0001", nsa, Set.of(7, 8));
    }

    private static List<Issue> validate(Febraban150File f, long nsa) {
        return Febraban150Validator.validate(f, expected(nsa));
    }

    // ------------------------------------------------------------------ leitura

    @Test
    @DisplayName("lê o header (registro A)")
    void parsesHeader() {
        Febraban150File.Header h = parse(sampleLines()).header();
        assertThat(h.convenioCode()).isEqualTo("SINT-0001");
        assertThat(h.companyName()).isEqualTo("ARQUIVO SINTETICO");
        assertThat(h.bankCode()).isEqualTo("999");
        assertThat(h.bankName()).isEqualTo("BANCO SINTETICO");
        assertThat(h.generatedOn()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(h.nsa()).isEqualTo(7);
        assertThat(h.layoutVersion()).isEqualTo(7);
        assertThat(h.serviceId()).isEqualTo("CÓDIGO DE BARRAS");
    }

    @Test
    @DisplayName("lê os 5 registros G com valores, datas, forma de captura e de pagamento")
    void parsesPayments() {
        Febraban150File f = parse(sampleLines());
        assertThat(f.payments()).hasSize(5);

        Febraban150File.Payment p1 = f.payments().get(0);
        assertThat(p1.lineNumber()).isEqualTo(2);
        assertThat(p1.paidOn()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(p1.creditedOn()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(p1.barcode()).isEqualTo(B8);
        assertThat(p1.amountReceived()).isEqualByComparingTo("123.45");
        assertThat(p1.fee()).isEqualByComparingTo("2.50");
        assertThat(p1.nsr()).isEqualTo(1);
        assertThat(p1.collectingBranch()).isEqualTo("00012345");
        assertThat(p1.captureForm()).isEqualTo('1');
        assertThat(p1.authentication()).isEqualTo("SINTETICO-AUT-0001");
        assertThat(p1.paymentForm()).isEqualTo(1);
        assertThat(p1.isPix()).isFalse();

        Febraban150File.Payment pix = f.payments().get(2);
        assertThat(pix.captureForm()).isEqualTo('9');
        assertThat(pix.isPix()).isTrue();
        assertThat(pix.paymentForm()).isEqualTo(3);

        Febraban150File.Payment noBarcode = f.payments().get(3);
        assertThat(noBarcode.barcode()).isEmpty();
        assertThat(noBarcode.captureForm()).isEqualTo('c');
        assertThat(noBarcode.amountReceived()).isEqualByComparingTo("50.00");
        assertThat(noBarcode.paymentForm()).isEqualTo(6);
    }

    @Test
    @DisplayName("lê o trailer e guarda as linhas cruas")
    void parsesTrailerAndKeepsRawLines() {
        Febraban150File f = parse(sampleLines());
        assertThat(f.trailer().totalRecords()).isEqualTo(7);
        assertThat(f.trailer().totalAmount()).isEqualByComparingTo("1421.89");
        assertThat(f.rawLines()).hasSize(7).allMatch(l -> l.length() == 150);
        assertThat(f.payments().get(0).rawLine()).isEqualTo(f.rawLines().get(1));
    }

    @Test
    @DisplayName("lê pelos bytes em ISO-8859-1, com CRLF ou LF, e ignora linhas em branco no fim")
    void parsesBytesAndLineEndings() throws IOException {
        byte[] bytes;
        try (InputStream in = Febraban150ReturnTest.class.getResourceAsStream("/samples/febraban150-retorno-SINTETICO.ret")) {
            bytes = in.readAllBytes();
        }
        assertThat(Febraban150ReturnParser.parse(bytes).header().serviceId()).isEqualTo("CÓDIGO DE BARRAS");
        String lf = new String(bytes, StandardCharsets.ISO_8859_1).replace("\r\n", "\n") + "\n\n";
        assertThat(Febraban150ReturnParser.parse(lf).payments()).hasSize(5);
    }

    // ------------------------------------------------------------------ validação de negócio

    @Test
    @DisplayName("arquivo íntegro: nenhum erro; um aviso para o pagamento de 125,00 contra código de 123,45")
    void validFileHasOnlyTheExpectedWarning() {
        List<Issue> issues = validate(parse(sampleLines()), 7);
        assertThat(Febraban150Validator.hasErrors(issues)).isFalse();
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).code()).isEqualTo(Code.AMOUNT_DIFFERS_FROM_BARCODE);
        assertThat(issues.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(issues.get(0).lineNumber()).isEqualTo(6);
    }

    @Test
    @DisplayName("NSA fora de sequência é erro")
    void nsaOutOfSequence() {
        List<Issue> issues = validate(parse(sampleLines()), 8);
        assertThat(issues).anyMatch(i -> i.code() == Code.NSA_OUT_OF_SEQUENCE && i.severity() == Severity.ERROR);
        assertThat(Febraban150Validator.hasErrors(issues)).isTrue();
    }

    @Test
    @DisplayName("banco, convênio e versão de layout diferentes dos do convênio cadastrado são erros")
    void wrongAgreementContext() {
        Febraban150File f = parse(sampleLines());
        List<Issue> issues = Febraban150Validator.validate(f, new Expectations("001", "OUTRO", 7, Set.of(8)));
        assertThat(issues).extracting(Issue::code).contains(Code.BANK_MISMATCH, Code.CONVENIO_MISMATCH,
                Code.LAYOUT_VERSION_NOT_ACCEPTED);
    }

    @Test
    @DisplayName("trailer com total de registros ou valor total diferente do arquivo é erro")
    void trailerMismatch() {
        List<String> lines = sampleLines();
        String z = lines.get(6);
        lines.set(6, "Z000008" + z.substring(7));
        assertThat(validate(parse(lines), 7)).extracting(Issue::code).contains(Code.TRAILER_RECORD_COUNT);

        lines = sampleLines();
        z = lines.get(6);
        lines.set(6, z.substring(0, 7) + "00000000000142190" + z.substring(24));
        List<Issue> issues = validate(parse(lines), 7);
        assertThat(issues).extracting(Issue::code).contains(Code.TRAILER_TOTAL_AMOUNT);
        assertThat(issues).filteredOn(i -> i.code() == Code.TRAILER_TOTAL_AMOUNT).first()
                .satisfies(i -> assertThat(i.message()).contains("1421.90").contains("1421.89"));
    }

    @Test
    @DisplayName("registro G removido sem acertar o trailer: contagem e total divergem")
    void missingDetailRecord() {
        List<String> lines = sampleLines();
        lines.remove(2);
        assertThat(validate(parse(lines), 7)).extracting(Issue::code)
                .contains(Code.TRAILER_RECORD_COUNT, Code.TRAILER_TOTAL_AMOUNT);
    }

    @Test
    @DisplayName("código de barras com dígito alterado e data de crédito anterior à de pagamento geram avisos")
    void barcodeAndDateWarnings() {
        List<String> lines = sampleLines();
        String g = lines.get(1);
        // troca o último algarismo do código de barras (posição 81) de 6 para 7
        lines.set(1, g.substring(0, 80) + "7" + g.substring(81));
        String g3 = lines.get(2);
        // crédito (posições 30-37) 20261008, antes do pagamento 20261008+1
        lines.set(2, g3.substring(0, 21) + "20261008" + "20261007" + g3.substring(37));
        List<Issue> issues = validate(parse(lines), 7);
        assertThat(issues).extracting(Issue::code).contains(Code.BARCODE_INVALID, Code.CREDIT_BEFORE_PAYMENT);
        assertThat(Febraban150Validator.hasErrors(issues)).isFalse();
    }

    @Test
    @DisplayName("as expectativas exigem banco, convênio e ao menos uma versão: não há padrão")
    void expectationsRequireEverything() {
        assertThatThrownBy(() -> new Expectations(null, "X", 1, Set.of(7))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Expectations("001", null, 1, Set.of(7))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Expectations("001", "X", 1, Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Expectations("001", "X", 1, null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ estrutura

    private static void assertParseFails(List<String> lines, Reason reason, int line) {
        assertThatThrownBy(() -> parse(lines)).isInstanceOfSatisfying(ReturnFileException.class, e -> {
            assertThat(e.reason()).isEqualTo(reason);
            assertThat(e.lineNumber()).isEqualTo(line);
        });
    }

    @Test
    @DisplayName("arquivo vazio")
    void emptyFile() {
        assertThatThrownBy(() -> Febraban150ReturnParser.parse(""))
                .isInstanceOfSatisfying(ReturnFileException.class, e -> assertThat(e.reason()).isEqualTo(Reason.EMPTY_FILE));
        assertThatThrownBy(() -> Febraban150ReturnParser.parse("\r\n\r\n"))
                .isInstanceOfSatisfying(ReturnFileException.class, e -> assertThat(e.reason()).isEqualTo(Reason.EMPTY_FILE));
    }

    @Test
    @DisplayName("linha com tamanho diferente de 150 aponta a linha")
    void badLineLength() {
        List<String> lines = sampleLines();
        lines.set(2, lines.get(2).substring(0, 149));
        assertParseFails(lines, Reason.BAD_LINE_LENGTH, 3);
    }

    @Test
    @DisplayName("sem header ou sem trailer")
    void missingHeaderOrTrailer() {
        List<String> noHeader = sampleLines();
        noHeader.remove(0);
        assertParseFails(noHeader, Reason.MISSING_HEADER, 1);
        List<String> noTrailer = sampleLines();
        noTrailer.remove(6);
        assertParseFails(noTrailer, Reason.MISSING_TRAILER, 6);
    }

    @Test
    @DisplayName("registro que não é G entre o header e o trailer")
    void unknownRecordType() {
        List<String> lines = sampleLines();
        lines.set(2, "X" + lines.get(2).substring(1));
        assertParseFails(lines, Reason.UNKNOWN_RECORD_TYPE, 3);
    }

    @Test
    @DisplayName("A.02 diferente de 2 (remessa, não retorno) e A.10 diferente de CÓDIGO DE BARRAS")
    void notAReturnFile() {
        List<String> lines = sampleLines();
        lines.set(0, lines.get(0).substring(0, 1) + "1" + lines.get(0).substring(2));
        assertParseFails(lines, Reason.NOT_A_RETURN_FILE, 1);

        lines = sampleLines();
        String a = lines.get(0);
        lines.set(0, a.substring(0, 81) + "OUTRO SERVICO    " + a.substring(98));
        assertParseFails(lines, Reason.BAD_SERVICE_ID, 1);
    }

    @Test
    @DisplayName("campo numérico com letra e data impossível")
    void badNumericAndDate() {
        List<String> lines = sampleLines();
        String g = lines.get(1);
        lines.set(1, g.substring(0, 81) + "00000001234X" + g.substring(93));
        assertParseFails(lines, Reason.BAD_NUMERIC_FIELD, 2);

        lines = sampleLines();
        g = lines.get(1);
        lines.set(1, g.substring(0, 21) + "20261340" + g.substring(29));
        assertParseFails(lines, Reason.BAD_DATE_FIELD, 2);
    }

    @Test
    @DisplayName("forma de captura e forma de pagamento fora dos domínios do layout")
    void badCodes() {
        List<String> lines = sampleLines();
        String g = lines.get(1);
        lines.set(1, g.substring(0, 116) + "z" + g.substring(117));
        assertParseFails(lines, Reason.BAD_CODE_FIELD, 2);

        lines = sampleLines();
        g = lines.get(1);
        lines.set(1, g.substring(0, 140) + "7" + g.substring(141));
        assertParseFails(lines, Reason.BAD_CODE_FIELD, 2);
    }

    @Test
    @DisplayName("todas as formas de captura do layout são aceitas (1-9, a-i e l)")
    void allCaptureFormsAreAccepted() {
        for (char c : "123456789abcdefghil".toCharArray()) {
            List<String> lines = sampleLines();
            String g = lines.get(1);
            lines.set(1, g.substring(0, 116) + c + g.substring(117));
            assertThat(parse(lines).payments().get(0).captureForm()).isEqualTo(c);
        }
    }

    @Test
    @DisplayName("valores monetários têm 2 casas implícitas: 000000012345 é 123,45 e a tarifa 0000250 é 2,50")
    void impliedDecimals() {
        Febraban150File.Payment p = parse(sampleLines()).payments().get(0);
        assertThat(p.amountReceived()).isEqualTo(new BigDecimal("123.45"));
        assertThat(p.fee()).isEqualTo(new BigDecimal("2.50"));
    }
}
