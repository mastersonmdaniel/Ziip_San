package br.com.ziipsan.platform.portal.selfcare;

import java.util.List;

/**
 * Cobertura de ensaio do portal ampliado (ED-38): acessibilidade
 * WCAG 2.2 AA conforme a P0.36 e o item SAMAE 2.20 (>= 90% no ensaio).
 * Esta classe CALCULA e EXIGE — nao executa o ensaio (roteiro VISAN/SAMAE
 * real e prova de integracao, declarada no relatorio):
 * <ul>
 *   <li>WCAG 2.2 AA (P0.36): NESTA classe, somente a prova automatizada
 *       (axe) e conferida — nao pode ter violacao {@code serious} ou
 *       {@code critical} (ver {@link AxeSummary} e {@link #releaseGate}).
 *       A publicacao da declaracao de acessibilidade do tenant
 *       ({@code portal.accessibility_statement} da P3.3) e regra da
 *       {@code PortalAccessibility} da P3.3 e NAO e conferida aqui:
 *       nenhum parametro desta classe a recebe (correcao D5 da revisao
 *       cruzada — o texto anterior prometia essa conferencia);</li>
 *   <li>SAMAE 2.20: percentual = itens aprovados / itens do roteiro;
 *       aprovado somente com >= 90%.</li>
 * </ul>
 * Pendencia herdada marcada (nao resolvida aqui): a P3.21 deixou
 * PENDENTE DE DECISAO DO DONO a regra de atribuicao "operado com" e
 * numero de contraste associado — nada nesta entrega decide isso.
 */
public final class PortalCoverage {

    public static final int SAMAE_220_MIN_PERCENT = 90;

    public record AxeSummary(int seriousViolations, int criticalViolations) {
        public boolean passesAaGate() {
            return seriousViolations == 0 && criticalViolations == 0;
        }
    }

    public record Samae220Result(int approvedItems, int totalItems, int percent, boolean passed) { }

    private PortalCoverage() { }

    public static Samae220Result samae220(List<Boolean> itemOutcomes) {
        if (itemOutcomes == null || itemOutcomes.isEmpty()) {
            throw new IllegalArgumentException("roteiro do ensaio SAMAE 2.20 sem itens: percentual nao calculado");
        }
        int approved = (int) itemOutcomes.stream().filter(Boolean::booleanValue).count();
        int percent = (int) Math.floor(approved * 100.0 / itemOutcomes.size());
        return new Samae220Result(approved, itemOutcomes.size(), percent, percent >= SAMAE_220_MIN_PERCENT);
    }

    /** Portao de liberacao da ampliacao: WCAG (axe limpo) E SAMAE 2.20 >= 90%. */
    public static boolean releaseGate(AxeSummary axe, Samae220Result samae) {
        return axe != null && axe.passesAaGate() && samae != null && samae.passed();
    }
}
