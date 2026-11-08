package br.com.ziipsan.reporting.exportprofile;

/**
 * Referencias e avisos LITERAIS da ED-82 - so o que o acceptance nomeia.
 * Nenhum codigo de indicador da ISO 37120, nenhuma categoria do PNQS e
 * nenhum requisito das NBR do checklist e declarado aqui: nenhum deles
 * consta das fontes disponiveis nesta VM.
 */
public final class ExportReferences {

    /** Referencia nomeada no acceptance da ED-82. */
    public static final String ISO_37120_REFERENCE = "ABNT NBR ISO 37120:2021";

    /** O regulamento do PNQS 2026 (versao 1.2) NAO define leiaute nem formato de troca de dados (acceptance). */
    public static final String PNQS_REGULATION_REFERENCE =
            "PNQS 2026 - regulamento versao 1.2 (ABES), sem leiaute nem formato de troca de dados definido";

    /** PNQS e premio e selo de reconhecimento - nao e certificacao (acceptance). */
    public static final String PNQS_NATURE_NOTICE =
            "O PNQS e premio e selo de reconhecimento, nao norma tecnica nem certificacao acreditada.";

    /**
     * A estrutura desta exportacao e NEUTRA e do produto: serve de base
     * para o preenchimento MANUAL na plataforma da ABES. Declara-la como
     * "leiaute PNQS" seria inventar um formato que o regulamento nao define.
     */
    public static final String PNQS_NEUTRAL_STRUCTURE_NOTICE =
            "Estrutura neutra de exportacao do produto (indicador + serie historica) para preenchimento "
                    + "na plataforma da ABES; nao e leiaute definido pelo regulamento do PNQS.";

    /** Normas do checklist de cadastro (acceptance), compradas na P1.28. */
    public static final java.util.List<String> CADASTRE_NORMS =
            java.util.List.of("ABNT NBR 12586", "ABNT NBR 12587", "ABNT NBR 13133", "ABNT NBR 14166");

    private ExportReferences() {
    }
}
