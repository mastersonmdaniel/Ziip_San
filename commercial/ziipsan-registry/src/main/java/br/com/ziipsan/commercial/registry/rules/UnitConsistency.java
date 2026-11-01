package br.com.ziipsan.commercial.registry.rules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Regras de consistência do cadastro de uma unidade consumidora (imóvel), sem persistência.
 *
 * <p>Estas regras vivem no domínio, e não na tela, porque o cadastro entra por mais de um caminho
 * (digitação, importação de legado, recadastramento em campo, API). No sistema legado de referência
 * várias delas estavam escondidas num controlador de 40 mil linhas. Aqui cada uma tem código estável
 * ({@link Violation#code()}) e teste.
 *
 * <p>Duas severidades: {@link Severity#ERROR} bloqueia a gravação; {@link Severity#WARNING} pode ser
 * confirmada pelo usuário (a API devolve um {@code confirmToken}).
 */
public final class UnitConsistency {

    public enum Severity { ERROR, WARNING }

    public enum Role { OWNER, USER, RESPONSIBLE }

    public record Violation(String code, Severity severity, String field, String message) {
    }

    /** Vínculo cliente-imóvel. {@code endDate == null} significa vínculo vigente. */
    public record Relation(UUID customerId, Role role, LocalDate startDate, LocalDate endDate,
                           boolean showOnBill, boolean hasDocument) {
        boolean current() {
            return endDate == null;
        }
    }

    public record SubcategoryLine(UUID subcategoryId, int economies) {
    }

    /** Situação de ligação (água ou esgoto) e se ela exige rede na quadra. */
    public record Status(String code, boolean requiresNetwork) {
    }

    public record BlockNetwork(boolean hasWaterNetwork, boolean hasSewerNetwork) {
    }

    /** Parâmetros do tenant que mudam as regras. */
    public record Policy(boolean requireUserDocument, int economiesSoftLimit) {
        public static Policy defaults() {
            return new Policy(true, 50);
        }
    }

    public record UnitSnapshot(
            boolean provisional,
            int physicalAddressCount,
            BlockNetwork block,
            Status water,
            Status sewer,
            List<Relation> relations,
            List<SubcategoryLine> subcategories,
            LocalDate today) {
    }

    private UnitConsistency() {
    }

    public static List<Violation> check(UnitSnapshot unit, Policy policy) {
        List<Violation> out = new ArrayList<>();

        // ---- endereço físico: exatamente um ----
        if (unit.physicalAddressCount() != 1) {
            out.add(error("UNIT_PHYSICAL_ADDRESS_COUNT", "address",
                    "A unidade deve ter exatamente 1 endereço físico (tem " + unit.physicalAddressCount() + ")."));
        }

        // ---- quadra e rede ----
        if (!unit.provisional()) {
            if (unit.block() == null) {
                out.add(error("UNIT_BLOCK_REQUIRED", "blockId", "Informe a quadra da unidade."));
            } else {
                if (unit.water() != null && unit.water().requiresNetwork() && !unit.block().hasWaterNetwork()) {
                    out.add(error("WATER_STATUS_REQUIRES_NETWORK", "waterStatusId",
                            "A situação de água '" + unit.water().code() + "' exige rede de água na quadra, e a quadra não tem."));
                }
                if (unit.sewer() != null && unit.sewer().requiresNetwork() && !unit.block().hasSewerNetwork()) {
                    out.add(error("SEWER_STATUS_REQUIRES_NETWORK", "sewerStatusId",
                            "A situação de esgoto '" + unit.sewer().code() + "' exige rede de esgoto na quadra, e a quadra não tem."));
                }
            }
        }

        // ---- vínculos com clientes ----
        List<Relation> current = unit.relations().stream().filter(Relation::current).toList();
        for (Relation r : unit.relations()) {
            if (r.startDate() == null) {
                out.add(error("RELATION_START_REQUIRED", "relations", "Todo vínculo precisa de data de início."));
            } else {
                if (r.startDate().isAfter(unit.today())) {
                    out.add(error("RELATION_START_IN_FUTURE", "relations", "A data de início do vínculo não pode ser futura."));
                }
                if (r.endDate() != null && r.endDate().isBefore(r.startDate())) {
                    out.add(error("RELATION_END_BEFORE_START", "relations", "A data de fim do vínculo é anterior à de início."));
                }
            }
        }

        List<Relation> users = current.stream().filter(r -> r.role() == Role.USER).toList();
        if (users.isEmpty()) {
            out.add(error("UNIT_NO_CURRENT_USER", "relations", "A unidade precisa ter ao menos 1 usuário vigente."));
        } else if (policy.requireUserDocument() && users.stream().anyMatch(r -> !r.hasDocument())) {
            out.add(error("UNIT_USER_DOCUMENT_REQUIRED", "relations", "O usuário da unidade precisa ter CPF ou CNPJ informado."));
        }

        long billNames = current.stream().filter(Relation::showOnBill).count();
        if (billNames != 1) {
            out.add(error("UNIT_BILL_NAME_COUNT", "relations",
                    "Exatamente 1 vínculo vigente deve ser o nome na conta (há " + billNames + ")."));
        }

        Set<String> seen = new HashSet<>();
        for (Relation r : current) {
            if (!seen.add(r.customerId() + "|" + r.role())) {
                out.add(error("RELATION_DUPLICATE", "relations", "O mesmo cliente aparece duas vezes com o mesmo papel."));
            }
        }

        // ---- categorias e economias ----
        if (unit.subcategories().isEmpty()) {
            out.add(error("UNIT_NO_SUBCATEGORY", "subcategories", "Informe ao menos 1 subcategoria."));
        } else {
            Set<UUID> subs = new HashSet<>();
            int total = 0;
            for (SubcategoryLine s : unit.subcategories()) {
                if (s.economies() < 1) {
                    out.add(error("SUBCATEGORY_ECONOMIES_INVALID", "subcategories", "A quantidade de economias deve ser ao menos 1."));
                }
                if (!subs.add(s.subcategoryId())) {
                    out.add(error("SUBCATEGORY_DUPLICATE", "subcategories", "A mesma subcategoria foi informada duas vezes."));
                }
                total += Math.max(0, s.economies());
            }
            if (policy.economiesSoftLimit() > 0 && total > policy.economiesSoftLimit()) {
                out.add(new Violation("UNIT_ECONOMIES_OVER_LIMIT", Severity.WARNING, "subcategories",
                        "O total de " + total + " economias passa do limite de " + policy.economiesSoftLimit()
                                + ". Confirme se está correto."));
            }
        }
        return out;
    }

    public static boolean hasErrors(List<Violation> violations) {
        return violations.stream().anyMatch(v -> v.severity() == Severity.ERROR);
    }

    private static Violation error(String code, String field, String message) {
        return new Violation(code, Severity.ERROR, field, message);
    }
}
