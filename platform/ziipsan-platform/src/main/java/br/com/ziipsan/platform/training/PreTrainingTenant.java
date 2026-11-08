package br.com.ziipsan.platform.training;

import br.com.ziipsan.platform.implantation.CloneAnonymization;
import br.com.ziipsan.platform.implantation.CloneGate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tenant de pré-treinamento (ED-11): o ambiente onde o treinamento acontece.
 *
 * <p>O aceite dá duas origens possíveis, e só duas: <b>massa sintética</b>
 * ou o <b>clone mascarado da ED-09 (PLT-39)</b>. As duas têm a mesma
 * garantia de fundo — nenhum dado pessoal real em ambiente de treinamento
 * (princípio 3 da metodologia de implantação da ED-09) — provada de formas
 * diferentes:
 * <ul>
 *   <li>massa sintética: tenant marcado como sintético e varredura da
 *       P0.45 limpa (zero violações), como no clone;</li>
 *   <li>clone mascarado: além da marca e da varredura, o portão do clone
 *       da ED-09 precisa estar liberado ({@link CloneGate}) — origem na
 *       produção, avaliação de reidentificação do RIPD anterior e viável —
 *       e a finalidade do clone precisa ser TREINAMENTO (clone gerado para
 *       homologação não vira ambiente de treinamento por reutilização).</li>
 * </ul>
 *
 * <p>Guarda adicional, do precedente do ambiente local (P0.27/P3.23): o
 * tenant de pré-treinamento roda com a emissão fiscal de produção
 * <b>desligada</b> ({@code ziipsan.fiscal.production-allowed=false}).
 * Treinamento que emite documento fiscal de verdade não é treinamento.
 *
 * <p>Esta classe é a regra pura de prontidão do ambiente; provisionar o
 * tenant, gerar a massa ou executar o clone e rodar a varredura são da
 * integração (ver o relatório da ED-11). O validador de manifesto em
 * {@code scripts/treinamento/} aplica esta mesma régua fora da JVM.
 */
public final class PreTrainingTenant {

    private PreTrainingTenant() {
    }

    public enum Source { SYNTHETIC_MASS, MASKED_CLONE }

    /**
     * Retrato de prontidão do tenant de pré-treinamento.
     *
     * @param cloneRequest    pedido do clone (obrigatório se MASKED_CLONE)
     * @param cloneAssessment avaliação do RIPD do clone (idem)
     */
    public record Spec(UUID tenantId, Source source, boolean synthetic,
                       int sweepViolationCount, boolean fiscalProductionAllowed,
                       CloneGate.CloneRequest cloneRequest,
                       CloneGate.ReidentificationAssessment cloneAssessment) {
        public Spec {
            if (tenantId == null || source == null) {
                throw new IllegalArgumentException(
                        "O tenant de pré-treinamento exige tenant e origem.");
            }
            if (sweepViolationCount < 0) {
                throw new IllegalArgumentException(
                        "A contagem da varredura não pode ser negativa.");
            }
        }
    }

    /** Motivos que impedem o tenant de receber treinamento (vazio = pronto). */
    public static List<String> blockers(Spec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("O retrato do tenant é obrigatório.");
        }
        List<String> out = new ArrayList<>();
        if (!spec.synthetic()) {
            out.add("O tenant de pré-treinamento precisa estar marcado como sintético.");
        }
        if (spec.sweepViolationCount() != 0) {
            out.add("Varredura da P0.45 com " + spec.sweepViolationCount()
                    + " violação(ões): ambiente de treinamento não recebe turma "
                    + "com resíduo de dado pessoal.");
        }
        if (spec.fiscalProductionAllowed()) {
            out.add("Emissão fiscal de produção ligada no tenant de pré-treinamento: "
                    + "o ambiente de treinamento roda com fiscal de produção desligado.");
        }
        if (spec.source() == Source.SYNTHETIC_MASS && spec.cloneRequest() != null) {
            out.add("Origem massa sintética com pedido de clone associado: massa "
                    + "sintética não tem clone — clone_id só existe em MASKED_CLONE.");
        }
        if (spec.source() == Source.MASKED_CLONE) {
            if (spec.cloneRequest() == null) {
                out.add("Origem clone mascarado sem o pedido do clone da ED-09.");
            } else {
                if (spec.cloneRequest().purpose() != CloneAnonymization.Purpose.TRAINING) {
                    out.add("O clone precisa ter a finalidade TRAINING (PLT-39): clone de "
                            + "homologação não é ambiente de treinamento.");
                }
                // O CloneGate da ED-09 confere origem-produção, clone de clone
                // e a avaliação do RIPD (tenant certo, viável e anterior).
                out.addAll(CloneGate.blockers(spec.cloneRequest(), spec.cloneAssessment()));
            }
        }
        return List.copyOf(out);
    }

    public static boolean ready(Spec spec) {
        return blockers(spec).isEmpty();
    }
}
