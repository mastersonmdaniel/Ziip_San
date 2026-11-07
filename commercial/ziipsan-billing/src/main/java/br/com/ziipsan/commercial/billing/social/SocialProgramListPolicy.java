package br.com.ziipsan.commercial.billing.social;

import java.time.LocalDate;

/**
 * ED-57: origem da lista de beneficiarios conforme a natureza juridica do
 * prestador (PLT-36, consumido da P1.5) e a data normativa da regra legal.
 *
 * <p>A lista de beneficiarios de programa social nao tem API publica: ela
 * chega em arquivo, pelo importador generico da P1.40, declarando a origem
 * em {@code social.list_source} (dominios da V4_2). Esta classe e a regra
 * pura dessa origem - ela NAO importa arquivo, NAO classifica e NAO cria
 * inscricao: quem importa e o esquema da ED-57 no importador, quem
 * classifica e {@link SocialClassification} (P1.5) e quem decide a
 * elegibilidade e {@link SocialEligibility} (P1.5). Nada disso e duplicado
 * aqui.
 *
 * <p>Trava registrada no plano (P1.5, classe a): a combinacao de
 * {@code social.base_import} e {@code social.list_source} com
 * {@code reg.legal_nature} que a lei nao permite fica travada, porque a
 * cessao direta de dados do CadUnico so alcanca orgao ou entidade de
 * direito publico (Decreto 11.016/2022). O canal de cessao direta e o
 * {@code CONECTA_API}; os demais canais (lista entregue pela agencia
 * reguladora e base municipal) nao sao cessao direta do CadUnico e nao
 * recebem trava nesta regra. No dominio de {@code reg.legal_nature} (core
 * V9: PRIVATE, AUTARCHY, PUBLIC_COMPANY, SEM), ente de direito publico e a
 * autarquia (AUTARCHY): empresa publica e sociedade de economia mista sao
 * pessoas juridicas de direito privado. A coerencia de parametros do
 * tenant continua validada onde os parametros vivem (plataforma), como a
 * V4_2 registra; esta regra e a mesma trava, reutilizavel na conferencia
 * da lista recebida.
 *
 * <p>Data normativa: a regra legal da tarifa social e aplicavel a partir
 * de 11/12/2026, data de conclusao da implementacao fixada pela Lei
 * 14.898/2024 e pela NR 13, art. 26, paragrafo 1o (texto da P1.5 no plano:
 * data normativa, nao marco do projeto). Antes dessa data a conferencia
 * marca a lista como fora da vigencia da regra; a data nao e inventada
 * nem configuravel aqui.
 */
public final class SocialProgramListPolicy {

    /** Origens da lista ({@code social.list_source} na V4_2; {@code social_base_imports.list_source}). */
    public enum ListSource {
        REGULATOR_LIST, MUNICIPAL_BASE, CONECTA_API
    }

    /** Naturezas juridicas do prestador ({@code reg.legal_nature}; dominio da core V9). */
    public enum LegalNature {
        PRIVATE, AUTARCHY, PUBLIC_COMPANY, SEM
    }

    /** A regra legal da tarifa social e aplicavel a partir desta data (Lei 14.898/2024; NR 13, art. 26, paragrafo 1o). */
    public static final LocalDate LEGAL_RULE_APPLIES_FROM = LocalDate.of(2026, 12, 11);

    private SocialProgramListPolicy() {
    }

    /** Ente de direito publico, para a cessao direta do CadUnico (Decreto 11.016/2022). */
    public static boolean isPublicLawBody(LegalNature legalNature) {
        if (legalNature == null) {
            throw new IllegalArgumentException("Natureza juridica do prestador e obrigatoria");
        }
        return legalNature == LegalNature.AUTARCHY;
    }

    /** A origem declarada da lista e permitida para a natureza juridica do prestador? */
    public static boolean isAllowed(ListSource listSource, LegalNature legalNature) {
        if (listSource == null || legalNature == null) {
            throw new IllegalArgumentException("Origem da lista e natureza juridica sao obrigatorias");
        }
        return listSource != ListSource.CONECTA_API || isPublicLawBody(legalNature);
    }

    /** Recusa explicita da combinacao que a lei nao permite (a mesma trava da P1.5). */
    public static void requireAllowed(ListSource listSource, LegalNature legalNature) {
        if (!isAllowed(listSource, legalNature)) {
            throw new IllegalArgumentException("Origem CONECTA_API (cessao direta do CadUnico) nao"
                    + " alcanca prestador de natureza " + legalNature
                    + ": a cessao direta so alcanca orgao ou entidade de direito publico"
                    + " (Decreto 11.016/2022)");
        }
    }

    /** A regra legal da tarifa social ja e aplicavel na data de referencia? */
    public static boolean legalRuleAppliesOn(LocalDate referenceDate) {
        if (referenceDate == null) {
            throw new IllegalArgumentException("Data de referencia e obrigatoria");
        }
        return !referenceDate.isBefore(LEGAL_RULE_APPLIES_FROM);
    }
}
