package br.com.ziipsan.core.chemicals;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Catalogo GLOBAL (sem tenant) da lista de produtos sujeitos ao controle
 * da Policia Federal — Lei 10.357/2001; Portaria MJSP 204/2022,
 * alterada pela Portaria MJSP 223/2022 (ED-89, OPE-06).
 *
 * <p>A lista e mantida e atualizada PELO ZIIP (carga normativa
 * versionada, com vigencia): este nucleo NAO semeia nenhuma entrada —
 * nenhum produto e declarado controlado por presuncao do codigo. A
 * chave do tenant {@code chem.controlled_products_list} identifica a
 * versao vigente adotada; a conferencia do tenant roda contra a versao
 * vigente na data ({@link #currentOn}).
 *
 * <p>Chave de casamento: o identificador do produto na lista
 * ({@code productKey}) casa com o codigo do item do tenant OU com o
 * nome normalizado do produto — o de-para concreto da carga e da
 * integracao (pendencia nomeada); o nucleo casa por igualdade exata
 * das duas chaves declaradas, sem fuzzy matching inventado.
 */
public final class ControlledCatalog {
    private ControlledCatalog() {}

    /** Fonte normativa da lista, conforme o acceptance da ED-89. */
    public static final String NORM_SOURCE = "Lei 10.357/2001; Portaria MJSP 204/2022, alterada pela Portaria MJSP 223/2022";

    public record ListEntry(String productKey, String productName) {
        public ListEntry {
            if (productKey == null || productKey.isBlank()) throw new IllegalArgumentException("chave do produto controlado e obrigatoria");
            if (productName == null || productName.isBlank()) throw new IllegalArgumentException("nome do produto controlado e obrigatorio: " + productKey);
        }
    }

    public record ListVersion(String version, String normReference, LocalDate effectiveFrom,
                              LocalDate effectiveTo, List<ListEntry> entries) {
        public ListVersion {
            if (version == null || version.isBlank()) throw new IllegalArgumentException("versao da lista de controlados e obrigatoria");
            if (normReference == null || normReference.isBlank())
                throw new IllegalArgumentException("a lista de controlados exige a referencia normativa (versao " + version + ")");
            Objects.requireNonNull(effectiveFrom, "inicio de vigencia da lista e obrigatorio");
            if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom))
                throw new IllegalArgumentException("fim de vigencia anterior ao inicio: " + version);
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
        public boolean inForceOn(LocalDate day) {
            return !day.isBefore(effectiveFrom) && (effectiveTo == null || !day.isAfter(effectiveTo));
        }
    }

    /** Versao vigente na data; nenhuma vigente = erro nomeado, nunca lista presumida. */
    public static ListVersion currentOn(List<ListVersion> versions, LocalDate day) {
        return versions.stream().filter(v -> v.inForceOn(day)).findFirst()
                .orElseThrow(() -> new ChemExceptions.ValidationException("CONTROLLED_LIST_NOT_IN_FORCE",
                        "nenhuma versao da lista de produtos controlados (Policia Federal) vigente em " + day
                                + " [SEM FONTE]: a lista e carga normativa do Ziip, nao presuncao do codigo"));
    }

    /** Casamento exato: chave da lista x codigo do item ou nome do produto do tenant. */
    public static boolean matches(ListEntry entry, ChemProduct product) {
        return entry.productKey().equals(product.inventoryItemCode())
                || entry.productKey().equalsIgnoreCase(product.name().trim());
    }

    public static List<ChemProduct> matchedProducts(ListVersion version, List<ChemProduct> tenantProducts) {
        return tenantProducts.stream()
                .filter(p -> version.entries().stream().anyMatch(e -> matches(e, p)))
                .toList();
    }
}
