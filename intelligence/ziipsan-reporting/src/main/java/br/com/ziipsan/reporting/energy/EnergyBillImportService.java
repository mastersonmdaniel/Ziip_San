package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.util.Map;

/**
 * Importacao da fatura de energia por arquivo (aceite da ED-90,
 * OPE-08). O servico e o PORTAO; o leiaute em si e da implantacao:
 * <ol>
 *   <li>sem registro de leiaute para a distribuidora, ou com registro
 *       nao verificado, a importacao RECUSA nomeada
 *       ({@code ENERGY_BILL_LAYOUT_NOT_VERIFIED}) antes de tocar o
 *       arquivo — nenhum parser presumido e executado;</li>
 *   <li>com o leiaute verificado, a leitura e delegada a porta
 *       {@link EnergyPorts.EnergyBillFilePort} (a implementacao
 *       conferida na implantacao);</li>
 *   <li>CORRECAO D6 (REVISAO-ED-90): a CHAVE do leiaute registrado tem
 *       de ser a da distribuidora pedida ({@code layout.distributorKey()
 *       == distributorKey}) — um registro achado sob outra chave nao
 *       autoriza a importacao. O que continua sendo da FIACAO,
 *       declarado: a instalacao carrega o NOME da distribuidora
 *       ({@code distributorName}, texto de cadastro), nao a chave —
 *       o vinculo nome x chave e do cadastro/fiacao que monta o mapa
 *       {@code layoutsByDistributor}; este portao amarra a chave
 *       pedida x chave do registro que a atende;</li>
 *   <li>a fatura devolvida e conferida contra o cadastro: mesmo
 *       tenant, mesma instalacao, origem IMPORTED_BILL e a versao de
 *       leiaute IGUAL a registrada — divergencia e recusa nomeada,
 *       nunca correcao silenciosa.</li>
 * </ol>
 */
public final class EnergyBillImportService {

    /** Resultado de uma importacao aceita: a fatura e o leiaute que a leu. */
    public record ImportResult(EnergyBill bill, DistributorLayout layout) {
    }

    public ImportResult importBill(EnergyInstallation installation, String distributorKey,
                                   String fileName, byte[] content,
                                   Map<String, DistributorLayout> layoutsByDistributor,
                                   EnergyPorts.EnergyBillFilePort filePort) {
        if (installation == null) {
            throw new IndicatorValidationException("ENERGY_IMPORT_INSTALLATION_REQUIRED",
                    "importacao sem a instalacao dona da fatura");
        }
        if (filePort == null) {
            throw new IndicatorValidationException("ENERGY_BILL_FILE_PORT_REQUIRED",
                    "importacao sem a porta de arquivo: o parser do leiaute conferido "
                            + "na implantacao nao foi ligado [SEM FONTE]");
        }
        DistributorLayout layout = layoutsByDistributor == null
                ? null : layoutsByDistributor.get(distributorKey);
        if (layout == null || !layout.verified()) {
            throw new IndicatorValidationException("ENERGY_BILL_LAYOUT_NOT_VERIFIED",
                    "leiaute da distribuidora " + distributorKey + " nao conferido na "
                            + "implantacao: importacao recusada [SEM FONTE]: conferir o "
                            + "leiaute contra faturas reais antes de importar");
        }
        if (!layout.distributorKey().equals(distributorKey)) {
            throw new IndicatorValidationException("ENERGY_BILL_LAYOUT_DISTRIBUTOR_MISMATCH",
                    "leiaute registrado para a distribuidora " + layout.distributorKey()
                            + " nao atende a distribuidora pedida " + distributorKey
                            + ": importacao recusada (correcao D6 da REVISAO-ED-90)");
        }
        EnergyBill bill = filePort.parse(distributorKey, fileName, content, installation);
        if (bill == null) {
            throw new IndicatorValidationException("ENERGY_BILL_IMPORT_EMPTY",
                    "o parser do leiaute " + layout.layoutVersion() + " nao devolveu fatura");
        }
        if (!bill.tenantId().equals(installation.tenantId())
                || !bill.installationId().equals(installation.id())
                || bill.source() != EnergyDataSource.IMPORTED_BILL
                || !layout.layoutVersion().equals(bill.layoutVersion())) {
            throw new IndicatorValidationException("ENERGY_BILL_IMPORT_MISMATCH",
                    "fatura importada diverge do cadastro/leiaute registrado (tenant, "
                            + "instalacao, origem ou versao de leiaute): importacao recusada");
        }
        return new ImportResult(bill, layout);
    }
}
