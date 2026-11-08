package br.com.ziipsan.intelligence.gis.export;

import java.time.Instant;
import java.util.List;

/**
 * Metadado por conjunto de dados do perfil MGB 2.0 / ISO 19115-1
 * (§3.8), que acompanha TODA exportação da GIS-16 (§9.3): título,
 * responsável, extensão, SRS, datum vertical, exatidão nominal,
 * data de referência, linhagem, restrições de acesso (LGPD e
 * infraestrutura crítica) e frequência de atualização.
 *
 * <p>Correção ED-67 (revisão independente, D10): o XML é ESTRUTURAL
 * ISO 19115-1 — hierarquia {@code gmd:} real (MD_Metadata →
 * identificationInfo/MD_DataIdentification → citation/CI_Citation,
 * extent/EX_Extent/EX_GeographicBoundingBox com os 4 limites em
 * {@code gco:Decimal}, referenceSystemInfo com RS_Identifier
 * {@code EPSG:<código>}, dataQualityInfo com lineage e relatório de
 * exatidão), com namespaces declarados. A versão anterior emitia
 * elementos planos inventados ({@code <EPSG>}, {@code <bbox>},
 * {@code <title>} solto) descartando a hierarquia. A validação
 * formal contra o esquema XSD do perfil MGB 2.0 segue como ensaio
 * de integração (pendência declarada), não é simulada aqui.
 */
public final class GisMgbMetadata {

    public record Metadata(String title, String responsibleParty, List<Double> bbox,
                           int srsEpsg, String verticalDatum, String nominalAccuracy,
                           Instant referenceDate, String lineage, String accessConstraints,
                           String updateFrequency) {}

    private GisMgbMetadata(){}

    public static String toXml(Metadata m){
        if (m.title() == null || m.title().isBlank()) throw new IllegalArgumentException("metadado MGB sem título");
        if (m.bbox() == null || m.bbox().size() != 4) throw new IllegalArgumentException("metadado MGB exige bbox de 4 números");
        StringBuilder x = new StringBuilder();
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        x.append("<gmd:MD_Metadata xmlns:gmd=\"http://www.isotc211.org/2005/gmd\""
                + " xmlns:gco=\"http://www.isotc211.org/2005/gco\""
                + " xmlns:gmdx=\"urn:ziipsan:mgb-extensao\">\n");
        // Sistema de referência: identificador EPSG + datum vertical.
        x.append("  <gmd:referenceSystemInfo><gmd:MD_ReferenceSystem>\n");
        x.append("    <gmd:referenceSystemIdentifier><gmd:RS_Identifier>\n");
        cs(x, "gmd:code", "EPSG:" + m.srsEpsg());
        x.append("    </gmd:RS_Identifier></gmd:referenceSystemIdentifier>\n");
        if (m.verticalDatum() != null)
            cs(x, "gmdx:verticalDatum", m.verticalDatum());
        x.append("  </gmd:MD_ReferenceSystem></gmd:referenceSystemInfo>\n");
        x.append("  <gmd:identificationInfo><gmd:MD_DataIdentification>\n");
        x.append("    <gmd:citation><gmd:CI_Citation>\n");
        cs(x, "gmd:title", m.title());
        if (m.referenceDate() != null) {
            x.append("      <gmd:date><gmd:CI_Date><gmd:date><gco:DateTime>")
             .append(esc(m.referenceDate().toString()))
             .append("</gco:DateTime></gmd:date><gmd:dateType><gmd:CI_DateTypeCode"
                    + " codeList=\"http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode\""
                    + " codeListValue=\"reference\">reference</gmd:CI_DateTypeCode></gmd:dateType>"
                    + "</gmd:CI_Date></gmd:date>\n");
        }
        x.append("    </gmd:CI_Citation></gmd:citation>\n");
        if (m.responsibleParty() != null) {
            x.append("    <gmd:pointOfContact><gmd:CI_ResponsibleParty>\n");
            cs(x, "gmd:organisationName", m.responsibleParty());
            x.append("    </gmd:CI_ResponsibleParty></gmd:pointOfContact>\n");
        }
        if (m.accessConstraints() != null) {
            x.append("    <gmd:resourceConstraints><gmd:MD_LegalConstraints>\n");
            x.append("      <gmd:accessConstraints><gmd:MD_RestrictionCode"
                    + " codeList=\"http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#MD_RestrictionCode\""
                    + " codeListValue=\"otherRestrictions\">otherRestrictions</gmd:MD_RestrictionCode></gmd:accessConstraints>\n");
            cs(x, "gmd:otherConstraints", m.accessConstraints());
            x.append("    </gmd:MD_LegalConstraints></gmd:resourceConstraints>\n");
        }
        if (m.updateFrequency() != null) {
            x.append("    <gmd:resourceMaintenance><gmd:MD_MaintenanceInformation>\n");
            cs(x, "gmd:maintenanceAndUpdateFrequency", m.updateFrequency());
            x.append("    </gmd:MD_MaintenanceInformation></gmd:resourceMaintenance>\n");
        }
        x.append("    <gmd:extent><gmd:EX_Extent><gmd:geographicElement><gmd:EX_GeographicBoundingBox>\n");
        dec(x, "gmd:westBoundLongitude", m.bbox().get(0));
        dec(x, "gmd:eastBoundLongitude", m.bbox().get(2));
        dec(x, "gmd:southBoundLatitude", m.bbox().get(1));
        dec(x, "gmd:northBoundLatitude", m.bbox().get(3));
        x.append("    </gmd:EX_GeographicBoundingBox></gmd:geographicElement></gmd:EX_Extent></gmd:extent>\n");
        x.append("  </gmd:MD_DataIdentification></gmd:identificationInfo>\n");
        if (m.lineage() != null || m.nominalAccuracy() != null) {
            x.append("  <gmd:dataQualityInfo><gmd:DQ_DataQuality>\n");
            if (m.nominalAccuracy() != null) {
                x.append("    <gmd:report><gmd:DQ_AbsoluteExternalPositionalAccuracy>\n");
                cs(x, "gmd:nameOfMeasure", "exatidão nominal");
                x.append("      <gmd:result><gmd:DQ_ConformanceResult>\n");
                cs(x, "gmd:explanation", m.nominalAccuracy());
                x.append("      </gmd:DQ_ConformanceResult></gmd:result>\n");
                x.append("    </gmd:DQ_AbsoluteExternalPositionalAccuracy></gmd:report>\n");
            }
            if (m.lineage() != null) {
                x.append("    <gmd:lineage><gmd:LI_Lineage>\n");
                cs(x, "gmd:statement", m.lineage());
                x.append("    </gmd:LI_Lineage></gmd:lineage>\n");
            }
            x.append("  </gmd:DQ_DataQuality></gmd:dataQualityInfo>\n");
        }
        x.append("</gmd:MD_Metadata>\n");
        return x.toString();
    }

    /** Elemento com valor em gco:CharacterString (escapado); valor null = elemento omitido. */
    private static void cs(StringBuilder x, String tag, String value){
        if (value == null) return;
        x.append("      <").append(tag).append("><gco:CharacterString>")
         .append(esc(value)).append("</gco:CharacterString></").append(tag).append(">\n");
    }

    private static void dec(StringBuilder x, String tag, Double value){
        x.append("      <").append(tag).append("><gco:Decimal>")
         .append(value).append("</gco:Decimal></").append(tag).append(">\n");
    }

    public static String esc(String s){
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&apos;");
    }
}
