package br.com.ziipsan.core.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4.8: deflexão cita o artigo da base de ajuda e transfere para humano
 * a pedido — sempre, mesmo havendo artigo.
 */
class HelpDeflectionTest {

    private static final HelpDeflection.HelpArticle ARTICLE =
            new HelpDeflection.HelpArticle("como-emitir-segunda-via",
                    "Como emitir a segunda via da conta",
                    "A segunda via fica na tela de contas, botão Emitir.");

    @Test
    @DisplayName("P4.8: com artigo, a resposta defletida CITA o artigo (slug e título no texto)")
    void deflectsCitingTheArticle() {
        HelpDeflection.DeflectionAnswer a =
                HelpDeflection.deflect(ARTICLE, false, "como tiro a segunda via?");
        assertThat(a.outcome()).isEqualTo(HelpDeflection.Outcome.ANSWERED_WITH_ARTICLE);
        assertThat(a.answerText()).contains("como-emitir-segunda-via");
        assertThat(a.answerText()).contains("Como emitir a segunda via da conta");
        assertThat(a.article()).isEqualTo(ARTICLE);
    }

    @Test
    @DisplayName("P4.8: pedido de humano transfere SEMPRE — por flag e por frase — mesmo com artigo")
    void humanRequestAlwaysTransfers() {
        assertThat(HelpDeflection.deflect(ARTICLE, true, "como tiro a segunda via?").outcome())
                .isEqualTo(HelpDeflection.Outcome.TRANSFERRED_TO_HUMAN);
        HelpDeflection.DeflectionAnswer byPhrase =
                HelpDeflection.deflect(ARTICLE, false, "quero falar com um humano, por favor");
        assertThat(byPhrase.outcome()).isEqualTo(HelpDeflection.Outcome.TRANSFERRED_TO_HUMAN);
        assertThat(byPhrase.transferReason()).isEqualTo("human-requested");
        assertThat(byPhrase.answerText()).isNull();
    }

    @Test
    @DisplayName("P4.8 regressão D1: formas naturais COM artigo sondadas pelo revisor pedem humano")
    void humanRequestWithArticleTransfers() {
        // As duas sondas executadas na revisão cruzada (REVISAO-P4.8, D1):
        assertThat(HelpDeflection.requestsHuman("quero falar com um humano")).isTrue();
        assertThat(HelpDeflection.requestsHuman("quero falar com um humano, por favor")).isTrue();
        // Variações naturais da mesma intenção, com artigo definido/indefinido:
        assertThat(HelpDeflection.requestsHuman("falar com um humano")).isTrue();
        assertThat(HelpDeflection.requestsHuman("quero falar com uma pessoa")).isTrue();
        assertThat(HelpDeflection.requestsHuman("quero falar com a pessoa")).isTrue();
        assertThat(HelpDeflection.requestsHuman("transferir para um humano")).isTrue();
        // E a transferência de ponta a ponta, mesmo havendo artigo que responderia:
        assertThat(HelpDeflection.deflect(ARTICLE, false, "quero falar com um humano").outcome())
                .isEqualTo(HelpDeflection.Outcome.TRANSFERRED_TO_HUMAN);
    }

    @Test
    @DisplayName("P4.8 regressão D1: formas SEM artigo e as frases já cobertas continuam transferindo")
    void humanRequestWithoutArticleStillTransfers() {
        assertThat(HelpDeflection.requestsHuman("quero falar com humano")).isTrue();
        assertThat(HelpDeflection.requestsHuman("falar com humano")).isTrue();
        assertThat(HelpDeflection.requestsHuman("falar com pessoa")).isTrue();
        assertThat(HelpDeflection.requestsHuman("quero um humano")).isTrue();
        assertThat(HelpDeflection.requestsHuman("quero uma pessoa")).isTrue();
        assertThat(HelpDeflection.requestsHuman("quero atendimento humano")).isTrue();
    }

    @Test
    @DisplayName("P4.8 regressão D1: menção solta a humano/pessoa NÃO é pedido — sem falso positivo grosseiro")
    void mereMentionOfHumanDoesNotTransfer() {
        assertThat(HelpDeflection.requestsHuman("isso é humano")).isFalse();
        assertThat(HelpDeflection.requestsHuman("o erro foi humano")).isFalse();
        assertThat(HelpDeflection.requestsHuman("quero saber como a pessoa usa a segunda via")).isFalse();
        assertThat(HelpDeflection.requestsHuman("como tiro a segunda via?")).isFalse();
        assertThat(HelpDeflection.deflect(ARTICLE, false, "isso é humano").outcome())
                .isEqualTo(HelpDeflection.Outcome.ANSWERED_WITH_ARTICLE);
    }

    @Test
    @DisplayName("P4.8: sem artigo encontrado, transfere — nunca fabrica resposta sem fonte")
    void noArticleTransfers() {
        HelpDeflection.DeflectionAnswer a =
                HelpDeflection.deflect(null, false, "pergunta sem artigo na base");
        assertThat(a.outcome()).isEqualTo(HelpDeflection.Outcome.TRANSFERRED_TO_HUMAN);
        assertThat(a.transferReason()).isEqualTo("no-article-found");
        assertThat(a.answerText()).isNull();
    }
}
