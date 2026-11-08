package br.com.ziipsan.core.transparency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Transparencia ATIVA (ED-99): o conjunto publicado proativamente pelo
 * tenant, nas SEIS secoes da spec:
 * <pre>
 *   COMPETENCIES    competencias do prestador
 *   SERVICE_CHANNELS canais de atendimento
 *   TARIFFS         tarifas
 *   CONTRACTS       contratos
 *   SERVICE_CHARTER Carta de Servicos
 *   INDICATORS      indicadores da ED-48
 * </pre>
 * A completude e verificavel: {@link #missingSections()} nomeia cada
 * secao ausente — publicar "portal da transparencia" sem uma secao nao
 * vira completude presumida. Os indicadores NAO sao calculados aqui:
 * entram como instantaneos ({@link IndicatorSnapshot}) lidos da ED-48
 * pela fiacao (a ED-48 apura pelo registro da P3.41; o core nao depende
 * do modulo reporting — a porta e de leitura, como a
 * {@code ManagerialCostPort} da ED-48).
 *
 * <p>Nenhum item carrega dado pessoal: item e titulo + referencia
 * publica (URL ou identificador do documento publicado).
 */
public final class ActiveTransparency {

    /** Secoes da transparencia ativa da spec da ED-99. */
    public enum Section { COMPETENCIES, SERVICE_CHANNELS, TARIFFS, CONTRACTS, SERVICE_CHARTER, INDICATORS }

    /** Item publicado de uma secao: titulo e referencia publica, sem dado pessoal. */
    public record PublishedItem(Section section, String title, String reference) {
        public PublishedItem {
            Objects.requireNonNull(section, "secao obrigatoria");
            if (title == null || title.isBlank())
                throw new IllegalArgumentException("titulo do item de transparencia obrigatorio");
            if (reference == null || reference.isBlank())
                throw new IllegalArgumentException("referencia publica do item obrigatoria");
        }
    }

    /**
     * Instantaneo de indicador lido da ED-48 (valor apurado pelo registro
     * da P3.41 na origem — transportado, nunca recalculado aqui).
     */
    public record IndicatorSnapshot(String code, String label,
                                    LocalDate periodStart, LocalDate periodEnd,
                                    BigDecimal value) {
        public IndicatorSnapshot {
            if (code == null || code.isBlank())
                throw new IllegalArgumentException("codigo do indicador obrigatorio");
            if (label == null || label.isBlank())
                throw new IllegalArgumentException("rotulo do indicador obrigatorio");
            Objects.requireNonNull(periodStart, "inicio do periodo obrigatorio");
            Objects.requireNonNull(periodEnd, "fim do periodo obrigatorio");
            Objects.requireNonNull(value, "valor do indicador obrigatorio");
        }
    }

    /** Porta de leitura dos indicadores da ED-48 para a secao INDICATORS. */
    public interface IndicatorPort {
        List<IndicatorSnapshot> indicators(java.util.UUID tenantId, LocalDate periodStart, LocalDate periodEnd);
    }

    private final Map<Section, List<PublishedItem>> items = new EnumMap<>(Section.class);
    private final List<IndicatorSnapshot> indicators = new ArrayList<>();

    public ActiveTransparency publish(PublishedItem item) {
        Objects.requireNonNull(item, "item obrigatorio");
        if (item.section() == Section.INDICATORS) throw new IllegalArgumentException(
                "secao INDICATORS e alimentada por publishIndicators (instantaneos da ED-48)");
        items.computeIfAbsent(item.section(), s -> new ArrayList<>()).add(item);
        return this;
    }

    /** Alimenta a secao INDICATORS com os instantaneos lidos da ED-48 pela porta. */
    public ActiveTransparency publishIndicators(IndicatorPort port, java.util.UUID tenantId,
                                                LocalDate periodStart, LocalDate periodEnd) {
        Objects.requireNonNull(port, "porta de indicadores (ED-48) obrigatoria");
        List<IndicatorSnapshot> read = port.indicators(tenantId, periodStart, periodEnd);
        if (read != null) indicators.addAll(read);
        return this;
    }

    public List<PublishedItem> items(Section section) {
        return List.copyOf(items.getOrDefault(section, List.of()));
    }

    public List<IndicatorSnapshot> indicators() { return List.copyOf(indicators); }

    /** Secoes ainda sem nenhum item publicado (INDICATORS conta pelos instantaneos). */
    public List<Section> missingSections() {
        List<Section> missing = new ArrayList<>();
        for (Section s : Section.values()) {
            boolean present = s == Section.INDICATORS
                    ? !indicators.isEmpty()
                    : !items.getOrDefault(s, List.of()).isEmpty();
            if (!present) missing.add(s);
        }
        return List.copyOf(missing);
    }

    /** Transparencia ativa completa = as seis secoes da spec publicadas. */
    public boolean complete() { return missingSections().isEmpty(); }
}
