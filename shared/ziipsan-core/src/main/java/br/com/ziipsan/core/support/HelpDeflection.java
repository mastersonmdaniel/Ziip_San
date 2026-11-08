package br.com.ziipsan.core.support;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * P4.8: deflexão do suporte pela base de ajuda (ADR-0020; artigos da
 * P1.21 em {@code support.article}). Defletir é responder citando o
 * artigo que resolve — ou transferir para humano. Nunca é inventar uma
 * resposta sem artigo.
 *
 * <p>Regras do aceite, literais:
 * <ul>
 *   <li>"deflexão CITA o artigo da base de ajuda": toda resposta
 *       defletida carrega slug e título do artigo
 *       ({@link DeflectionAnswer#citation()} é montada do artigo, não de
 *       texto livre de quem chama);</li>
 *   <li>"transfere para atendimento humano A PEDIDO": o pedido do
 *       usuário (flag explícita da interface ou frase de pedido) transfere
 *       SEMPRE, mesmo havendo artigo que responderia — deflexão não é
 *       barreira de saída;</li>
 *   <li>sem artigo encontrado, transfere: a ausência de artigo não vira
 *       resposta fabricada.</li>
 * </ul>
 *
 * <p>A busca nos artigos (tsvector da P1.21) é de quem chama: este
 * serviço recebe o artigo já localizado (ou {@code null}) e decide.
 */
public final class HelpDeflection {

    /** Artigo da base de ajuda localizado para a pergunta (P1.21). */
    public record HelpArticle(String slug, String title, String summary) {
        public HelpArticle {
            if (slug == null || slug.isBlank()) {
                throw new IllegalArgumentException("slug do artigo é obrigatório: sem slug não há citação");
            }
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("título do artigo é obrigatório");
            }
        }

        /** Citação no formato único da deflexão: título + slug estável do artigo. */
        public String citation() {
            return title + " (artigo: " + slug + ")";
        }
    }

    public enum Outcome { ANSWERED_WITH_ARTICLE, TRANSFERRED_TO_HUMAN }

    /**
     * Resultado da deflexão. Em {@code ANSWERED_WITH_ARTICLE},
     * {@code answerText} e {@code article} são obrigatórios; em
     * {@code TRANSFERRED_TO_HUMAN}, {@code answerText} é {@code null} —
     * transferência não finge resposta.
     */
    public record DeflectionAnswer(Outcome outcome, String answerText, HelpArticle article,
                                   String transferReason) {
        public DeflectionAnswer {
            Objects.requireNonNull(outcome, "outcome é obrigatório");
            if (outcome == Outcome.ANSWERED_WITH_ARTICLE) {
                Objects.requireNonNull(article, "resposta defletida exige o artigo citado");
                if (answerText == null || !answerText.contains(article.slug())) {
                    throw new IllegalArgumentException(
                            "resposta defletida precisa citar o artigo (slug " + article.slug() + " no texto)");
                }
            }
        }
    }

    /**
     * Frases que são pedido de humano, comparadas na forma normalizada
     * de {@link #normalizeHumanRequest(String)} (minúsculas, sem acento
     * e sem artigos). Remover os artigos — definidos (o/a/os/as) e
     * indefinidos (um/uma/uns/umas) — do texto do usuário E das frases
     * desta lista faz a forma natural "quero falar com um humano"
     * casar com a entrada "falar com humano", sem enumerar cada
     * combinação com/sem artigo ("falar com uma pessoa",
     * "falar com a pessoa" e "falar com pessoa" colapsam na mesma
     * forma). O alcance no resto NÃO muda: continua sendo casamento de
     * frase inteira desta lista — a menção solta a "humano"/"pessoa"
     * (ex.: "isso é humano") não casa com nenhuma entrada e não
     * transfere.
     */
    private static final List<String> HUMAN_REQUEST_PHRASES = List.of(
            "falar com humano", "falar com uma pessoa", "atendimento humano",
            "atendente humano", "quero um humano", "quero uma pessoa",
            "transferir para humano", "chamar um atendente");

    /** As mesmas frases, já normalizadas (sem artigos) para a comparação. */
    private static final List<String> HUMAN_REQUEST_PHRASES_NORMALIZED =
            HUMAN_REQUEST_PHRASES.stream().map(HelpDeflection::normalizeHumanRequest).toList();

    private HelpDeflection() {
    }

    private static String normalizeHumanRequest(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[áàâã]", "a").replaceAll("[éê]", "e")
                .replaceAll("[í]", "i").replaceAll("[óôõ]", "o").replaceAll("[ú]", "u")
                .replaceAll("[ç]", "c");
        // Artigo como palavra inteira sai; o espaço resultante é colapsado
        // para a forma normalizada continuar comparável por substring.
        normalized = normalized.replaceAll("\\b(um|uma|uns|umas|o|a|os|as)\\b", " ");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    /** O texto do usuário pede atendimento humano? */
    public static boolean requestsHuman(String userText) {
        if (userText == null) {
            return false;
        }
        String normalized = normalizeHumanRequest(userText);
        return HUMAN_REQUEST_PHRASES_NORMALIZED.stream().anyMatch(normalized::contains);
    }

    /**
     * Decide a deflexão de uma pergunta.
     *
     * @param articleOrNull artigo localizado pela busca da P1.21, ou null
     * @param humanRequested pedido explícito de humano (botão/flag da interface)
     * @param userText texto da pergunta (também varrido à procura de pedido de humano)
     */
    public static DeflectionAnswer deflect(HelpArticle articleOrNull, boolean humanRequested,
                                           String userText) {
        if (humanRequested || requestsHuman(userText)) {
            return new DeflectionAnswer(Outcome.TRANSFERRED_TO_HUMAN, null, articleOrNull,
                    "human-requested");
        }
        if (articleOrNull == null) {
            return new DeflectionAnswer(Outcome.TRANSFERRED_TO_HUMAN, null, null,
                    "no-article-found");
        }
        String answer = articleOrNull.summary() != null && !articleOrNull.summary().isBlank()
                ? articleOrNull.summary() + " Fonte: " + articleOrNull.citation() + "."
                : "Consulte o artigo da base de ajuda: " + articleOrNull.citation() + ".";
        return new DeflectionAnswer(Outcome.ANSWERED_WITH_ARTICLE, answer, articleOrNull, null);
    }
}
