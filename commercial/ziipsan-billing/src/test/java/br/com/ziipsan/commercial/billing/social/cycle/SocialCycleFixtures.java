package br.com.ziipsan.commercial.billing.social.cycle;

import java.time.LocalDate;

/**
 * II-3: valores sinteticos dos testes do ciclo. Todos os indices cegos
 * sao literais 64 hex derivados de inteiros - nenhum dado real ou
 * pessoal. Datas ancoradas na data normativa da ED-57 (11/12/2026).
 */
final class SocialCycleFixtures {

    /** Data de referencia em que a regra legal ja vigora (fronteira incluida). */
    static final LocalDate REF = LocalDate.of(2026, 12, 11);
    /** Vespera da vigencia da regra legal. */
    static final LocalDate REF_BEFORE_LEGAL = LocalDate.of(2026, 12, 10);

    static String hash(int n) {
        return String.format("%064x", n);
    }

    static final String F1 = hash(0xF1);
    static final String F2 = hash(0xF2);
    static final String F3 = hash(0xF3);
    static final String F4 = hash(0xF4);
    static final String P1 = hash(0xA1);
    static final String P2 = hash(0xA2);
    static final String P5 = hash(0xA5);
    static final String P7 = hash(0xA7);
    static final String P9 = hash(0xA9);

    static String header() {
        return DeclaredSocialListFormat.proofFormat().headerLine();
    }

    /** Uma linha do formato declarado, na ordem das colunas de prova. */
    static String row(String family, String person, String role, String basis, String cadunico,
                      String validFrom, String validTo, String municipality) {
        return String.join(";", family, person, role, basis, cadunico, validFrom, validTo, municipality);
    }

    static String csv(String... rows) {
        return header() + "\n" + String.join("\n", rows) + "\n";
    }

    private SocialCycleFixtures() {
    }
}
