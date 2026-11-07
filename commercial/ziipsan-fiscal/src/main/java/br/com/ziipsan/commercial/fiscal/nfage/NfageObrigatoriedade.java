package br.com.ziipsan.commercial.fiscal.nfage;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Fato normativo da obrigatoriedade da NFAg-e, no padrão {@code NfseEtapa} da P3.6
 * (constantes com data e dispositivo + regra pura): pelo Ato Conjunto RFB/CGIBS
 * nº 4/2026, a NFAg-e é obrigatória para os fatos geradores ocorridos a partir de
 * <b>01/12/2026</b>. Antes dessa data a emissão é facultativa; a partir dela, conta
 * sem NFAg-e é conta em desconformidade — decisão de negócio de quem fatura, nunca
 * um default escondido nesta classe.
 */
public final class NfageObrigatoriedade {

    /** Primeiro dia de fato gerador que exige NFAg-e. */
    public static final LocalDate INICIO_OBRIGATORIEDADE = LocalDate.of(2026, 12, 1);

    /** Dispositivo que fixa a obrigatoriedade (rastreabilidade normativa). */
    public static final String DISPOSITIVO = "Ato Conjunto RFB/CGIBS nº 4/2026";

    private NfageObrigatoriedade() {
    }

    /**
     * Verdadeiro quando o fato gerador é de 01/12/2026 em diante (fronteira inclusiva:
     * 30/11/2026 não exige; 01/12/2026 exige).
     *
     * @throws NullPointerException se a data do fato gerador for nula: fato gerador sem
     *                              data é erro de quem chama, nunca "não obrigatório"
     */
    public static boolean exigeNfage(LocalDate dataFatoGerador) {
        Objects.requireNonNull(dataFatoGerador, "Data do fato gerador é obrigatória");
        return !dataFatoGerador.isBefore(INICIO_OBRIGATORIEDADE);
    }
}
