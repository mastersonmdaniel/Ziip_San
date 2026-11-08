package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.platform.notification.NotificationCategory;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Regras puras da classificação de modelos de WhatsApp e da estimativa de
 * custo (aceite da ED-41), calculáveis à mão:
 *
 * <ul>
 *   <li><b>classificação:</b> a categoria de preço de um modelo é a do mapa
 *       {@code msg.template_category_map} do tenant — fora do mapa, NÃO há
 *       categoria presumida ({@link #classify} devolve vazio e o disparo
 *       recusa);</li>
 *   <li><b>estimativa antes do envio:</b> preço unitário vigente (da porta
 *       {@link ChannelPricing}, do contrato do provedor) × destinatários;</li>
 *   <li><b>triagem de marketing:</b> um modelo de COBRANÇA (categoria de
 *       evento {@code BILLING} da P1.38) declarado como utilidade ou
 *       autenticação cujo texto montado contém termo promocional é
 *       sinalizado como risco de o provedor classificá-lo como MARKETING
 *       (mais caro) — o envio só sai com a aprovação registrada desse
 *       risco. A lista de termos é uma PROPOSTA declarada desta entrega
 *       ({@link #PROPOSED_MARKETING_TERMS}), substituível pelo tenant na
 *       integração; ela é triagem para a aprovação humana, não uma
 *       afirmação sobre a regra de classificação de nenhum provedor.</li>
 * </ul>
 */
public final class TemplateCategoryRule {

    /** Termos promocionais da triagem — PROPOSTA desta entrega, declarada no relatório. */
    public static final Set<String> PROPOSED_MARKETING_TERMS = Set.of(
            "promoção", "promocao", "desconto", "oferta", "% off", "grátis", "gratis",
            "aproveite", "imperdível", "imperdivel", "cashback", "sorteio",
            "prêmio", "premio", "compre agora", "assine já", "assine ja");

    private TemplateCategoryRule() {
    }

    public static Optional<WhatsAppCategory> classify(Map<String, WhatsAppCategory> categoryMap,
                                                      String templateCode) {
        if (categoryMap == null || templateCode == null || templateCode.isBlank()) {
            throw new IllegalArgumentException("mapa de categorias e código do modelo são obrigatórios");
        }
        return Optional.ofNullable(categoryMap.get(templateCode));
    }

    /** Estimativa de custo ANTES do envio: unitário × destinatários (os dois conferíveis à mão). */
    public static BigDecimal estimate(BigDecimal unitPrice, int recipients) {
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("o preço unitário é obrigatório e não pode ser negativo");
        }
        if (recipients < 1) {
            throw new IllegalArgumentException("a estimativa exige ao menos um destinatário");
        }
        return unitPrice.multiply(BigDecimal.valueOf(recipients));
    }

    /** Verdade se o texto montado contém algum termo da triagem (comparação sem caixa). */
    public static boolean containsMarketingTerm(String renderedText, Set<String> terms) {
        if (renderedText == null || terms == null) {
            throw new IllegalArgumentException("texto montado e termos da triagem são obrigatórios");
        }
        String text = renderedText.toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (term != null && !term.isBlank() && text.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Risco de classificação como marketing: modelo de cobrança declarado
     * fora de MARKETING com termo promocional no texto. Modelo já declarado
     * MARKETING não tem risco de REclassificação (o preço estimado já é o
     * de marketing); modelo que não é de cobrança está fora desta triagem
     * do aceite ("modelo de cobrança").
     */
    public static boolean marketingRisk(WhatsAppCategory declaredCategory, NotificationCategory eventCategory,
                                        String renderedText, Set<String> terms) {
        if (declaredCategory == null || eventCategory == null) {
            throw new IllegalArgumentException("categoria declarada e categoria do evento são obrigatórias");
        }
        if (declaredCategory == WhatsAppCategory.MARKETING || eventCategory != NotificationCategory.BILLING) {
            return false;
        }
        return containsMarketingTerm(renderedText, terms);
    }
}
