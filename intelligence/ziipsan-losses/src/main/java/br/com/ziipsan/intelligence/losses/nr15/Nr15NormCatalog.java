package br.com.ziipsan.intelligence.losses.nr15;

import java.time.LocalDate;
import java.time.Month;

/**
 * Entrada de catalogo da NR 15/ANA (ED-78) - SOMENTE fatos lidos da
 * fonte primaria nesta VM: Resolucao ANA n. 275, de 18/12/2025,
 * publicada no DOU n. 242, Secao 1, p. 156, de 19/12/2025 (processo
 * 02501.004800/2024-60), que aprova a Norma de Referencia ANA
 * n. 15/2025 (gestao da reducao progressiva e do controle de perdas
 * nos subsistemas de distribuicao de agua potavel).
 *
 * <p><b>Ligacao ao catalogo de normas (DAD-28):</b> o {@code code} e o
 * {@code cert_standard.code} da P1.34 para a NR 15 -
 * {@code ana-nr-15-res-275-2025} - lido de {@code AnaNormCatalogLink}
 * (P2.31), que mapeia NR15 para exatamente esse slug a partir do
 * catalogo gerado da mesma fonte da P1.34. Nenhum codigo e derivado
 * aqui.
 *
 * <p><b>Data de norma de maio de 2028 (item literal do aceite):</b>
 * "verificacao da adocao pela ANA a partir de maio de 2028 registrada
 * como data de norma no catalogo". No texto da NR 15, art. 14,
 * paragrafo unico: "A verificacao do cumprimento das acoes previstas
 * neste artigo se inicia em 20 de maio de 2028, devendo-se escalonar
 * os municipios de acordo com o porte e indice de perdas." Esta
 * classe REGISTRA a data ({@link #VERIFICATION_START}) como dado de
 * catalogo. Ela NAO e gatilho de comportamento: nenhum servico desta
 * entrega liga, desliga ou agenda nada por causa dela - a aplicacao
 * da NR 15 a um tenant decorre da adocao pela agencia dele (perfil
 * regulatorio, {@link Nr15Applicability}), hoje e em qualquer data.
 *
 * <p><b>Periodo de referencia (Anexo I, observacao 4):</b> 12 meses,
 * de 1 de janeiro a 31 de dezembro de cada ano, valores em media
 * anualizada. <b>Unidade de apresentacao da matriz (Anexo I,
 * observacao 1):</b> volumes em 1.000 m3/ano - conversao de
 * APRESENTACAO apenas; os volumes internos desta entrega sao os m3
 * gravados pela ED-85, sem reescala de calculo.
 *
 * <p><b>Comprovacao da adocao (art. 13):</b> segue a Resolucao ANA
 * n. 134, de 18/11/2022 - citada aqui como referencia de catalogo;
 * nenhum procedimento dela e implementado nesta entrega.
 */
public final class Nr15NormCatalog {

    /** Chave da NR no vocabulario de adocao dos perfis (P2.31). */
    public static final String NR_KEY = "NR15";

    /** cert_standard.code da P1.34 (via AnaNormCatalogLink da P2.31). */
    public static final String CATALOG_CODE = "ana-nr-15-res-275-2025";

    /** Ato que aprova a NR 15/2025. */
    public static final String RESOLUTION = "Res. ANA 275/2025";

    /** Publicacao: DOU n. 242, Secao 1, p. 156. */
    public static final LocalDate PUBLISHED_ON = LocalDate.of(2025, 12, 19);

    /** Processo administrativo citado no preambulo da Resolucao. */
    public static final String PROCESS_REF = "02501.004800/2024-60";

    /**
     * Inicio da verificacao do atendimento pela ANA (NR 15, art. 14,
     * paragrafo unico): 20 de maio de 2028. DATA DE NORMA registrada
     * no catalogo - nao condiciona comportamento algum hoje.
     */
    public static final LocalDate VERIFICATION_START = LocalDate.of(2028, Month.MAY, 20);

    /** Resolucao do procedimento de comprovacao (NR 15, art. 13). */
    public static final String ADOPTION_VERIFICATION_RESOLUTION = "Res. ANA 134/2022";

    /** Divisor da unidade de apresentacao da matriz do Anexo I (1.000 m3/ano). */
    public static final double MATRIX_UNIT_DIVISOR_M3 = 1000.0;

    private Nr15NormCatalog() {
    }

    /** O mes/dia em que a verificacao se inicia cai em maio de 2028 (aceite). */
    public static boolean verificationStartsInMay2028() {
        return VERIFICATION_START.getYear() == 2028 && VERIFICATION_START.getMonth() == Month.MAY;
    }

    /** Converte um volume em m3 para a unidade de apresentacao da matriz (mil m3). */
    public static double toMatrixUnit(double volumeM3) {
        return volumeM3 / MATRIX_UNIT_DIVISOR_M3;
    }
}
