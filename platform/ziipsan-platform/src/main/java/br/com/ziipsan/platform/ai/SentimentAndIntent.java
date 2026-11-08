package br.com.ziipsan.platform.ai;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** SG-685..SG-701 (recortes): deteccao de sentimento, identificacao de intencao nova e comportamento
 * anomalo (SG-699). Regras deterministicas e auditaveis — a deteccao NAO e o modelo generativo. */
public final class SentimentAndIntent {
    public enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE }
    public static final Set<String> KNOWN_INTENTS = Set.of("SEGUNDA_VIA","EXPLICAR_CONTA","STATUS_PROTOCOLO","FALTA_AGUA","VAZAMENTO"," AJUDA".trim(),"ATENDENTE");
    private static final List<String> NEG = List.of("absurdo","vergonha","processo","procon","nunca resolve","pessimo","horrivel","reclamacao");
    private static final List<String> POS = List.of("obrigado","obrigada","otimo","resolveu","agradeco");
    private SentimentAndIntent() {}
    public static Sentiment sentiment(String text) {
        if (text == null) return Sentiment.NEUTRAL;
        String t = text.toLowerCase(Locale.ROOT);
        if (NEG.stream().anyMatch(t::contains)) return Sentiment.NEGATIVE;
        if (POS.stream().anyMatch(t::contains)) return Sentiment.POSITIVE;
        return Sentiment.NEUTRAL;
    }
    /** Intencao fora do conjunto conhecido = intencao NOVA: registrada para curadoria, nao inventada pelo bot. */
    public static boolean isNewIntent(String intent) { return intent == null || !KNOWN_INTENTS.contains(intent); }
    /** SG-699 comportamento anomalo: rajada de mensagens ou repeticao identica na conversa. */
    public static boolean anomalous(int messagesInWindow, List<String> recentTexts) {
        if (messagesInWindow > 20) return true;
        if (recentTexts != null && recentTexts.size() >= 3) {
            String last = recentTexts.get(recentTexts.size()-1);
            return recentTexts.stream().filter(last::equals).count() >= 3;
        }
        return false;
    }
}
