package br.com.ziipsan.commercial.billing.saaspricing;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Uma VERSAO da planilha de custo e preco por porte (aceite da ED-10).
 * Agrega faixas de porte, componentes de custo (tabela da P0.26) e
 * perfis/homem-hora. Nasce INVALIDA se as faixas nao cobrirem a partir
 * de zero sem buraco nem sobreposicao: preco por porte nao pode deixar
 * um porte sem preco nem com dois precos.
 */
public record PriceSheet(String version, LocalDate validFrom, LocalDate validTo,
                         List<SizeBand> bands, List<CostComponent> costComponents,
                         List<ProfessionalProfile> profiles) {
    public PriceSheet {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("versao da planilha e obrigatoria");
        if (validFrom == null) throw new IllegalArgumentException("vigencia inicial da planilha e obrigatoria");
        if (validTo != null && validTo.isBefore(validFrom)) throw new IllegalArgumentException("vigencia final anterior a inicial");
        if (bands == null || bands.isEmpty()) throw new IllegalArgumentException("planilha sem faixa de porte nao precifica");
        if (costComponents == null) throw new IllegalArgumentException("componentes de custo sao obrigatorios");
        if (profiles == null) throw new IllegalArgumentException("perfis profissionais sao obrigatorios");
        bands = List.copyOf(bands); costComponents = List.copyOf(costComponents); profiles = List.copyOf(profiles);
        List<SizeBand> sorted = bands.stream().sorted(Comparator.comparingLong(SizeBand::minConnections)).toList();
        if (sorted.get(0).minConnections() != 0)
            throw new IllegalArgumentException("faixas de porte devem comecar em zero ligacoes");
        for (int i = 1; i < sorted.size(); i++) {
            long expected = sorted.get(i - 1).maxConnections() + 1;
            if (sorted.get(i).minConnections() != expected)
                throw new IllegalArgumentException("faixas de porte com buraco ou sobreposicao em: " + sorted.get(i).code());
        }
    }
    public SizeBand bandFor(long activeConnections) {
        if (activeConnections < 0) throw new IllegalArgumentException("ligacoes ativas invalidas: " + activeConnections);
        return bands.stream().filter(b -> b.contains(activeConnections)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("nenhuma faixa de porte cobre " + activeConnections + " ligacoes [A DEFINIR]"));
    }
    public boolean validOn(LocalDate date) {
        return date != null && !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }
}
