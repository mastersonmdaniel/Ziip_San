package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.math.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vetores compartilhados C# x Java da regra unica de arredondamento e tributo da conta
 * (P1.16, ADR-0047). O arquivo {@code bill-rounding-vectors.txt} e o contrato: o JUnit
 * roda o lado Java aqui; o xUnit do Ziip Cloud roda o mesmo arquivo la. Divergencia
 * bloqueia o merge (FISCAL-PORTE-JAVA, secao 5).
 *
 * <p>Este teste prova o lado Java. A execucao do lado C# e externa a este repositorio e
 * fica registrada como pendencia no relatorio da frente, nao como prova aqui.
 */
class BillRoundingVectorsTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static List<BigDecimal> list(String csv) {
        List<BigDecimal> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            out.add(d(part));
        }
        return out;
    }

    @Test
    @DisplayName("todos os vetores do arquivo passam no lado Java")
    void allVectorsPass() throws IOException {
        String text;
        try (InputStream in = BillRoundingVectorsTest.class.getResourceAsStream("/bill-rounding-vectors.txt")) {
            assertThat(in).as("arquivo de vetores no classpath").isNotNull();
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int cases = 0;
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            cases++;
            String[] f = line.split("\\|");
            switch (f[1]) {
                case "ROUND_HALF_UP" -> assertThat(Money.roundHalfUp(d(f[2])))
                        .as(line).isEqualByComparingTo(f[3]);
                case "WITHHOLD" -> {
                    List<BigDecimal> rates = list(f[3]);
                    List<TaxRate> policy = new ArrayList<>();
                    for (int i = 0; i < rates.size(); i++) {
                        policy.add(new TaxRate("T-" + (i + 1), rates.get(i)));
                    }
                    List<TaxLine> lines = TaxWithholding.withhold(d(f[2]), new TaxWithholdingPolicy(policy));
                    assertThat(lines.stream().map(TaxLine::amount).toList())
                            .as(line).usingElementComparator(BigDecimal::compareTo)
                            .containsExactlyElementsOf(list(f[4]));
                    BigDecimal total = lines.stream().map(TaxLine::amount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    assertThat(total).as(line + " (soma)").isEqualByComparingTo(f[5]);
                }
                case "BILL_TOTAL" -> {
                    BigDecimal total = d(f[2]).add(d(f[3])).add(d(f[4])).subtract(d(f[5])).subtract(d(f[6]));
                    assertThat(total).as(line).isEqualByComparingTo(f[7]);
                }
                case "INSTALLMENT" -> assertThat(Installments.next(d(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4])))
                        .as(line).isEqualByComparingTo(f[5]);
                default -> throw new IllegalStateException("Vetor com operacao desconhecida: " + line);
            }
        }
        assertThat(cases).as("o teste nao pode passar por estar vazio").isEqualTo(13);
    }
}
