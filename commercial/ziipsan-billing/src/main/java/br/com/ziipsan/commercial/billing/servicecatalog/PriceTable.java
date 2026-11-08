package br.com.ziipsan.commercial.billing.servicecatalog;

import java.time.LocalDate;
import java.util.List;

/**
 * Tabelas de preço de serviços e de materiais com vigência (ED-34). Vigências
 * sobrepostas para o mesmo código são ambiguidade e são recusadas na construção —
 * nunca "vence a primeira da lista". Código sem item vigente na data é erro explícito.
 */
public final class PriceTable {

    private final List<ServicePriceItem> services;
    private final List<MaterialPriceItem> materials;

    public PriceTable(List<ServicePriceItem> services, List<MaterialPriceItem> materials) {
        this.services = List.copyOf(services);
        this.materials = List.copyOf(materials);
        for (int i = 0; i < this.services.size(); i++) {
            for (int j = i + 1; j < this.services.size(); j++) {
                ServicePriceItem a = this.services.get(i), b = this.services.get(j);
                if (a.code().equals(b.code()) && overlaps(a.validFrom(), a.validTo(), b.validFrom(), b.validTo()))
                    throw new IllegalArgumentException("vigencias sobrepostas para o item de servico " + a.code());
            }
        }
        for (int i = 0; i < this.materials.size(); i++) {
            for (int j = i + 1; j < this.materials.size(); j++) {
                MaterialPriceItem a = this.materials.get(i), b = this.materials.get(j);
                if (a.code().equals(b.code()) && overlaps(a.validFrom(), a.validTo(), b.validFrom(), b.validTo()))
                    throw new IllegalArgumentException("vigencias sobrepostas para o material " + a.code());
            }
        }
    }

    private static boolean overlaps(LocalDate f1, LocalDate t1, LocalDate f2, LocalDate t2) {
        LocalDate e1 = t1 == null ? LocalDate.MAX : t1, e2 = t2 == null ? LocalDate.MAX : t2;
        return !f1.isAfter(e2) && !f2.isAfter(e1);
    }

    public ServicePriceItem service(String code, LocalDate on) {
        return services.stream().filter(i -> i.code().equals(code) && i.appliesOn(on)).findFirst()
                .orElseThrow(() -> new IllegalStateException("sem preco de servico vigente para " + code + " em " + on));
    }

    public MaterialPriceItem material(String code, LocalDate on) {
        return materials.stream().filter(i -> i.code().equals(code) && i.appliesOn(on)).findFirst()
                .orElseThrow(() -> new IllegalStateException("sem preco de material vigente para " + code + " em " + on));
    }
}
