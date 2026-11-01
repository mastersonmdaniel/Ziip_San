package br.com.ziipsan.commercial.billing.tariff;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Uma versão (vigência) de tarifa: o conjunto de regras por categoria e, se houver, a regra de
 * tarifa social, válidas de {@code validFrom} a {@code validTo} (inclusive). Vigência é por
 * <b>data</b>, não por competência: a troca pode cair no meio do período de leitura.
 *
 * @param id       identificador da versão (rastreabilidade: vai para as linhas da conta)
 * @param validTo  {@code null} = vigência aberta
 * @param socialRule {@code null} = a versão não prevê tarifa social
 */
public record TariffVersion(UUID id, LocalDate validFrom, LocalDate validTo, List<CategoryRule> rules,
                            SocialTariffRule socialRule) {

    public TariffVersion {
        Objects.requireNonNull(id, "id da versão de tarifa");
        Objects.requireNonNull(validFrom, "início da vigência");
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new TariffConfigurationException("Fim da vigência anterior ao início: " + validFrom + " > " + validTo);
        }
        if (rules == null || rules.isEmpty()) {
            throw new TariffConfigurationException("Versão de tarifa sem nenhuma regra de categoria");
        }
        rules = List.copyOf(rules);
        Set<String> seen = new HashSet<>();
        for (CategoryRule r : rules) {
            if (!seen.add(r.categoryId() + "/" + r.subcategoryId())) {
                throw new TariffConfigurationException(
                        "Regra duplicada para categoria " + r.categoryId() + " subcategoria " + r.subcategoryId());
            }
        }
    }

    /**
     * Regra da (categoria, subcategoria); se não houver regra da subcategoria, cai na da categoria.
     *
     * @throws TariffConfigurationException se nenhuma das duas existir (nunca assume valor)
     */
    public CategoryRule ruleFor(UUID categoryId, UUID subcategoryId) {
        CategoryRule fallback = null;
        for (CategoryRule r : rules) {
            if (!r.categoryId().equals(categoryId)) {
                continue;
            }
            if (subcategoryId != null && subcategoryId.equals(r.subcategoryId())) {
                return r;
            }
            if (r.subcategoryId() == null) {
                fallback = r;
            }
        }
        if (fallback == null) {
            throw new TariffConfigurationException("Sem regra de tarifa para a categoria " + categoryId
                    + (subcategoryId == null ? "" : " / subcategoria " + subcategoryId)
                    + " na versão " + id);
        }
        return fallback;
    }

    /** A versão vigora na data? */
    public boolean covers(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }
}
