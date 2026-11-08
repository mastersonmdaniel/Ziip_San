package br.com.ziipsan.intelligence.gis.asset;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Risco por probabilidade x consequencia e plano de renovacao —
 * aceite da GIS-19 (a matriz de risco da ISO 55000 citada no
 * GIS-CADASTRO-TECNICO §GIS-19). A PROBABILIDADE e alimentada pelo
 * grau de condicao (inspecao/manuntencao) e pelos rompimentos das
 * OS de reparo do ativo/trecho; a CONSEQUENCIA pelo isolamento da
 * GIS-11 (ligacoes e clientes sensiveis afetados) e pela criticidade
 * — os insumos chegam calculados pelas donas (ED-54/ED-36); aqui so
 * a combinacao e a ordenacao do plano.
 *
 * <p>Escala declarada do produto (1..5 x 1..5), registrada no doc:
 * probabilidade deriva de grau de condicao 1..5 (5 = pior) somado a
 * rompimentos recentes com teto 5; consequencia e entregue pelo
 * chamador na mesma escala (a fiacao a deriva do isolamento). Sem
 * grau de condicao conhecido, a probabilidade NAO e presumida: o
 * ativo sai do plano com o motivo {@code SEM_GRAU_DE_CONDICAO}.
 */
public final class RiskRenewalService {

    private RiskRenewalService() {
    }

    public enum RiskBand { LOW, MEDIUM, HIGH, CRITICAL }

    public record RiskInput(UUID assetId, Integer conditionGrade, long recentBreaks,
                            int consequence, String criticalityClass) {
        public RiskInput {
            Objects.requireNonNull(assetId, "risco sem ativo");
            if (conditionGrade != null && (conditionGrade < 1 || conditionGrade > 5))
                throw new IllegalArgumentException("grau de condicao fora de 1..5");
            if (recentBreaks < 0) throw new IllegalArgumentException("rompimentos negativos");
            if (consequence < 1 || consequence > 5)
                throw new IllegalArgumentException("consequencia fora da escala 1..5");
        }
    }

    public record RiskAssessment(UUID assetId, Integer probability, int consequence,
                                 Integer score, RiskBand band, String excludedReason) {
        public boolean assessed() { return score != null; }
    }

    /** Probabilidade 1..5: grau de condicao + 1 ponto a cada 2 rompimentos recentes, teto 5. */
    public static int probabilityOf(RiskInput in) {
        if (in.conditionGrade() == null)
            throw new IllegalArgumentException("probabilidade exige o grau de condicao (nunca presumida)");
        long bonus = in.recentBreaks() / 2;
        return (int) Math.min(5, in.conditionGrade() + bonus);
    }

    public static RiskBand bandOf(int score) {
        if (score >= 20) return RiskBand.CRITICAL;
        if (score >= 12) return RiskBand.HIGH;
        if (score >= 6) return RiskBand.MEDIUM;
        return RiskBand.LOW;
    }

    public static RiskAssessment assess(RiskInput in) {
        if (in.conditionGrade() == null)
            return new RiskAssessment(in.assetId(), null, in.consequence(), null, null, "SEM_GRAU_DE_CONDICAO");
        int p = probabilityOf(in);
        int score = p * in.consequence();
        return new RiskAssessment(in.assetId(), p, in.consequence(), score, bandOf(score), null);
    }

    /**
     * Plano de renovacao: avaliados por risco decrescente (desempate
     * por criticidade CRITICAL primeiro e id), seguidos dos nao
     * avaliados (com motivo) — ninguem some do plano.
     */
    public static List<RiskAssessment> renewalPlan(List<RiskInput> inputs) {
        List<RiskAssessment> assessed = new java.util.ArrayList<>();
        List<RiskAssessment> excluded = new java.util.ArrayList<>();
        java.util.Map<UUID, String> crit = new java.util.HashMap<>();
        for (RiskInput in : inputs) {
            crit.put(in.assetId(), in.criticalityClass());
            RiskAssessment a = assess(in);
            (a.assessed() ? assessed : excluded).add(a);
        }
        assessed.sort((x, y) -> {
            int c = Integer.compare(y.score(), x.score());
            if (c != 0) return c;
            boolean xc = "CRITICAL".equals(crit.get(x.assetId()));
            boolean yc = "CRITICAL".equals(crit.get(y.assetId()));
            if (xc != yc) return xc ? -1 : 1;
            return x.assetId().compareTo(y.assetId());
        });
        java.util.List<RiskAssessment> out = new java.util.ArrayList<>(assessed);
        out.addAll(excluded);
        return List.copyOf(out);
    }
}
