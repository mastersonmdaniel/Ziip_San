package br.com.ziipsan.commercial.collection.ampliada;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * VAN configuravel (ED-26): a troca de arquivos de remessa/retorno com o banco
 * (caixa postal/VAN) e configurada por tenant - banco, agencia, conta, convenio
 * e leiaute - sem nenhum valor de banco presumido no codigo. A configuracao
 * incompleta e erro explicito na construcao, nao arquivo gerado pela metade.
 *
 * <p>Esta classe gera apenas o NOME deterministico do arquivo de remessa e a
 * linha de cabecalho do lote a partir da configuracao; o leiaute completo do
 * arquivo (CNAB/FEBRABAN do banco contratado) e da fiacao com o banco do tenant.
 *
 * <p><b>Atencao (correcao M4 da revisao cruzada):</b> {@link #remittanceFileName}
 * e {@link #headerLine()} sao IDENTIFICADORES INTERNOS de controle desta
 * entrega - convencao propria para nomear/agrupar remessas dentro do sistema.
 * Eles NAO sao o nome oficial nem o leiaute do arquivo VAN do banco contratado
 * e nao devem ser tratados pela integracao como se fossem o arquivo de remessa:
 * o arquivo real segue o leiaute/nomenclatura do banco/VAN do tenant.
 */
public record VanConfig(String bankCode, String agency, String account, String agreement,
                        String layoutVersion, String mailbox) {

    public VanConfig {
        requireText(bankCode, "codigo do banco");
        requireText(agency, "agencia");
        requireText(account, "conta");
        requireText(agreement, "convenio");
        requireText(layoutVersion, "versao do leiaute");
        requireText(mailbox, "caixa postal da VAN");
        if (!bankCode.matches("[0-9]{3}")) {
            throw new IllegalArgumentException("codigo do banco deve ter 3 digitos: " + bankCode);
        }
    }

    /**
     * Nome INTERNO do arquivo de remessa: REM + banco + data (AAAAMMDD) +
     * sequencia de 4. Identificador interno de controle (M4): NAO e o nome
     * oficial do arquivo VAN do banco - ver javadoc da classe.
     */
    public String remittanceFileName(LocalDate date, int sequence) {
        Objects.requireNonNull(date, "data da remessa e obrigatoria");
        if (sequence < 1 || sequence > 9999) {
            throw new IllegalArgumentException("sequencia da remessa fora de 1..9999: " + sequence);
        }
        return "REM" + bankCode + date.format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%04d", sequence) + ".REM";
    }

    /**
     * Cabecalho INTERNO do lote: banco|agencia|conta|convenio|leiaute|caixaPostal.
     * Identificador interno de controle (M4): NAO e linha do leiaute VAN/CNAB
     * do banco e nao pode ser escrita no arquivo de remessa real - ver javadoc
     * da classe.
     */
    public String headerLine() {
        return String.join("|", bankCode, agency, account, agreement, layoutVersion, mailbox);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("VAN configuravel: " + name + " e obrigatorio");
        }
    }
}
