package br.com.ziipsan.intelligence.losses.nr15;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Equacoes de fechamento do Anexo I da NR 15/ANA (Res. ANA 275/2025),
 * transcritas VERBATIM da observacao 3 do Anexo I no DOU:
 * <pre>
 *   GTA 1014 = GTA 1001 + GTA 1009
 *   GTA 1211 = GT 1209 + GTA 1210
 *   GTA 1207 = GTA 1204 + GTA 1205 + GTA 1206
 *   GTA 1217 = GTA 1215 + GTA 1216
 *   GTA 1218 = GTA 1014 - GTA 1211 - GTA 1203 - GTA 1207 - GTA 1217
 * </pre>
 * A observacao 2 do mesmo Anexo remete variaveis e glossario ao
 * SINISA em sua versao mais atualizada.
 *
 * <p><b>Limite honesto [SEM FONTE]:</b> a matriz grafica do Anexo I
 * (imagem no DOU) nao e legivel nesta VM e o Glossario de
 * Informacoes do SINISA nao foi carregado aqui; logo, o significado
 * componente a componente de cada codigo GTA alem destas equacoes
 * NAO e afirmado nesta entrega. As equacoes sao guardadas como DADO
 * de catalogo e verificadas aritmeticamente sobre valores GTA
 * declarados por quem integra (ciclo SINISA da P4.2/ED-48): variavel
 * ausente recusa nomeando-a; residuo diferente de zero e reportado,
 * nunca corrigido em silencio. O codigo {@code GT1209} e mantido
 * como impresso no DOU [sic - provavelmente GTA 1209 no glossario;
 * confirmar na carga do glossario, pendencia P-ED78-02].
 *
 * <p>Esta classe NAO calcula o balanco da NR 15: o balanco vem da
 * ED-85 ({@link Nr15BalanceAssembler}); as equacoes sao a conferencia
 * de fechamento do formulario SINISA quando os valores GTA existem.
 */
public final class SinisaClosureEquations {

    /** Uma equacao: alvo = soma dos termos com sinal (+1/-1). */
    public record Equation(String target, List<Term> terms) {
        public Equation {
            Objects.requireNonNull(target, "alvo da equacao obrigatorio");
            terms = List.copyOf(terms);
            if (terms.isEmpty()) throw new IllegalArgumentException("equacao sem termos: " + target);
        }
    }

    public record Term(String variable, int sign) {
        public Term {
            Objects.requireNonNull(variable, "variavel do termo obrigatoria");
            if (sign != 1 && sign != -1) throw new IllegalArgumentException("sinal do termo deve ser +1/-1");
        }
    }

    /** Resultado da verificacao de uma equacao sobre valores declarados. */
    public record Residual(String target, double declared, double computed, double residual) {
        public boolean closed() {
            return Math.abs(residual) <= Nr15Reconciler.TOLERANCE_M3;
        }
    }

    private static final List<Equation> EQUATIONS = List.of(
            eq("GTA1014", t("GTA1001", 1), t("GTA1009", 1)),
            eq("GTA1211", t("GT1209", 1), t("GTA1210", 1)),
            eq("GTA1207", t("GTA1204", 1), t("GTA1205", 1), t("GTA1206", 1)),
            eq("GTA1217", t("GTA1215", 1), t("GTA1216", 1)),
            eq("GTA1218", t("GTA1014", 1), t("GTA1211", -1), t("GTA1203", -1),
                    t("GTA1207", -1), t("GTA1217", -1)));

    private SinisaClosureEquations() {
    }

    private static Term t(String variable, int sign) {
        return new Term(variable, sign);
    }

    private static Equation eq(String target, Term... terms) {
        return new Equation(target, List.of(terms));
    }

    /** As 5 equacoes do Anexo I, na ordem do DOU. */
    public static List<Equation> equations() {
        return EQUATIONS;
    }

    /**
     * Verifica as 5 equacoes sobre os valores GTA declarados.
     * Variavel necessaria ausente (ou NaN) = recusa nomeada - o
     * fechamento nao e avaliado com marcador no lugar do dado.
     */
    public static List<Residual> verify(Map<String, Double> gtaValues) {
        Objects.requireNonNull(gtaValues, "valores GTA declarados obrigatorios");
        return EQUATIONS.stream().map(equation -> {
            double declared = required(gtaValues, equation.target());
            double computed = 0.0;
            for (Term term : equation.terms()) {
                computed += term.sign() * required(gtaValues, term.variable());
            }
            return new Residual(equation.target(), declared, computed, declared - computed);
        }).toList();
    }

    /** Verdadeiro quando as 5 equacoes fecham dentro da tolerancia aritmetica. */
    public static boolean allClosed(Map<String, Double> gtaValues) {
        return verify(gtaValues).stream().allMatch(Residual::closed);
    }

    private static double required(Map<String, Double> values, String variable) {
        Double value = values.get(variable);
        if (value == null || Double.isNaN(value)) {
            throw new IllegalArgumentException("variavel SINISA " + variable
                    + " ausente: o fechamento do Anexo I nao e avaliado sem ela declarada");
        }
        return value;
    }
}
