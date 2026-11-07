package br.com.ziipsan.commercial.fiscal.nfse;

import java.time.LocalDate;

/**
 * Etapa de obrigatoriedade dos grupos de IBS e CBS na NFS-e, pelo Ato Conjunto
 * RFB/CGIBS nº 4/2026 (aceite da P3.6; origem ADR-0003).
 *
 * <p>As três datas são as da especificação da tarefa — nenhuma outra data entra aqui:
 * <ul>
 *   <li>{@link #ISS_FORA_HIPOTESES_ESPECIAIS}: 01/10/2026 — serviços também sujeitos ao ISS
 *       fora das hipóteses especiais (art. 1º, III, "d");</li>
 *   <li>{@link #DEMAIS_HIPOTESES}: 01/12/2026 — demais hipóteses de NFS-e (art. 1º, III, "h");</li>
 *   <li>{@link #SIMPLES_NACIONAL}: 01/01/2027 — optantes do Simples Nacional (art. 1º, § 1º).</li>
 * </ul>
 *
 * <p>A etapa do Simples Nacional não é propriedade do tipo de serviço: é propriedade do
 * regime do prestador. Por isso {@link NfseEtapaRule} a aplica por cima da etapa do tipo
 * quando o prestador é optante — nunca o contrário.
 */
public enum NfseEtapa {

    ISS_FORA_HIPOTESES_ESPECIAIS(LocalDate.of(2026, 10, 1), "art. 1º, III, d"),
    DEMAIS_HIPOTESES(LocalDate.of(2026, 12, 1), "art. 1º, III, h"),
    SIMPLES_NACIONAL(LocalDate.of(2027, 1, 1), "art. 1º, § 1º");

    private final LocalDate inicioObrigatoriedade;
    private final String dispositivo;

    NfseEtapa(LocalDate inicioObrigatoriedade, String dispositivo) {
        this.inicioObrigatoriedade = inicioObrigatoriedade;
        this.dispositivo = dispositivo;
    }

    /** Primeiro dia em que a NFS-e do serviço sai com os grupos de IBS e CBS. */
    public LocalDate inicioObrigatoriedade() {
        return inicioObrigatoriedade;
    }

    /** Dispositivo do Ato Conjunto RFB/CGIBS 4/2026 que fixa esta etapa (rastreabilidade). */
    public String dispositivo() {
        return dispositivo;
    }
}
