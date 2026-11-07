package br.com.ziipsan.commercial.billing.content;

import br.com.ziipsan.commercial.billing.content.BillFieldRequirement.Origin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conteudo minimo da fatura como DADO VERSIONADO (P2.32): a lista de campos
 * obrigatorios por lei e por ERI, na versao {@link #CATALOG_VERSION}.
 *
 * <p>Base federal (todo perfil):
 * <ul>
 *   <li>Lei 11.445/2007, art. 39 — o modelo da fatura e o definido pela
 *       entidade reguladora: a fatura identifica o modelo/perfil regulatorio
 *       aplicado ({@code bill.regulatory_model_identification});</li>
 *   <li>CDC, art. 42-A (Lei 12.039/2009) — nome, endereco e CPF ou CNPJ do
 *       fornecedor, em BLOCO TRAVADO ({@code supplier.identification_block},
 *       bloco LOCKED/SYSTEM da P2.30);</li>
 *   <li>Decreto 5.440/2005 — informacao MENSAL de qualidade da agua
 *       ({@code water_quality.monthly_block});</li>
 *   <li>Lei 12.007/2009 — declaracao anual de quitacao
 *       ({@code annual_discharge.declaration});</li>
 *   <li>nome do titular impresso pela regra {@code bill.holder_name_display}
 *       da P2.5 ({@code holder.printed_name}).</li>
 * </ul>
 *
 * <p>Por perfil (P2.31, so onde o texto da ERI foi LIDO no mapa de ERIs):
 * os campos de divulgacao dos encargos e da religacao usam os MESMOS atos e
 * artigos dos valores {@code fee.*} e {@code rec.sla_hours} carregados pela
 * P2.31 para o perfil. Perfis cujo texto nao foi confirmado (AGR-Tubarao,
 * CISAM-SUL — texto so digitalizado/nao localizado) recebem SO a base
 * federal nesta versao: nenhum campo de ERI e inventado para eles, e a
 * lista deles cresce quando o texto for lido (mesma regra da P2.31).
 *
 * <p>ITEM RAS-28 (aguarda parecer): o valor aproximado dos tributos
 * (Lei 12.741/2012; Decreto 8.264/2014) NAO esta neste catalogo — ver
 * {@link PendingTaxDisclosure}.
 *
 * <p>Esta classe e a fonte unica da carga repetivel
 * {@code R__invoice_required_content.sql} (conferida por
 * {@code RequiredContentCatalogConsistencyTest}).
 */
public final class RequiredBillContent {

    /** Versao do catalogo de conteudo minimo (dado versionado). */
    public static final int CATALOG_VERSION = 1;

    // Chaves estaveis dos campos federais.
    public static final String REGULATORY_MODEL_IDENTIFICATION = "bill.regulatory_model_identification";
    public static final String SUPPLIER_IDENTIFICATION_BLOCK = "supplier.identification_block";
    public static final String WATER_QUALITY_MONTHLY_BLOCK = "water_quality.monthly_block";
    public static final String ANNUAL_DISCHARGE_DECLARATION = "annual_discharge.declaration";
    public static final String HOLDER_PRINTED_NAME = "holder.printed_name";

    // Chaves por perfil (P2.31).
    public static final String CHARGES_DISCLOSURE = "charges.fine_interest_correction_disclosure";
    public static final String RECONNECTION_SLA_DISCLOSURE = "reconnection.sla_disclosure";
    public static final String CUT_NOTICE_DISCLOSURE = "cut.notice_disclosure";

    private RequiredBillContent() {
    }

    private static BillFieldRequirement federal(String fieldKey, String norm, String ref, boolean locked) {
        return new BillFieldRequirement(fieldKey, null, norm, ref, locked, Origin.FEDERAL, null, null);
    }

    private static BillFieldRequirement profile(String fieldKey, String profileKey, String act, String article) {
        return new BillFieldRequirement(fieldKey, profileKey, act, article, false,
                Origin.REGULATORY_PROFILE, null, null);
    }

    /** A base federal, identica para todos os perfis. */
    public static List<BillFieldRequirement> federalFields() {
        return List.of(
                federal(REGULATORY_MODEL_IDENTIFICATION, "Lei 11.445/2007", "art. 39", false),
                federal(SUPPLIER_IDENTIFICATION_BLOCK, "CDC (Lei 8.078/1990)",
                        "art. 42-A, incluido pela Lei 12.039/2009", true),
                federal(WATER_QUALITY_MONTHLY_BLOCK, "Decreto 5.440/2005",
                        "informacao mensal de qualidade da agua", false),
                federal(ANNUAL_DISCHARGE_DECLARATION, "Lei 12.007/2009",
                        "declaracao anual de quitacao de debitos", false),
                federal(HOLDER_PRINTED_NAME, "P2.5 (bill.holder_name_display)",
                        "regra de impressao do nome do titular", false));
    }

    /**
     * Campos do perfil, alem da base federal. So entram campos cuja norma do
     * perfil foi confirmada no texto lido (procedencia na P2.31); perfil
     * desconhecido e erro explicito, nunca lista vazia silenciosa.
     */
    public static List<BillFieldRequirement> profileFields(String profileKey) {
        if (profileKey == null || profileKey.isBlank()) {
            throw new IllegalArgumentException("profileKey e obrigatoria");
        }
        return switch (profileKey) {
            // ARIS RN 19/2019: fee.* no art. 108; rec.sla_hours nos arts. 160/161;
            // cut.min_notice_days no art. 153 (valores da P2.31).
            case "ARIS_RN19_2019" -> List.of(
                    profile(CHARGES_DISCLOSURE, profileKey, "RN 19/2019", "art. 108"),
                    profile(RECONNECTION_SLA_DISCLOSURE, profileKey, "RN 19/2019", "arts. 160 e 161"),
                    profile(CUT_NOTICE_DISCLOSURE, profileKey, "RN 19/2019", "art. 153"));
            // ARESC Res. 046/2016: fee.* no art. 104; rec.sla_hours nos arts. 87/88;
            // cut.min_notice_days no art. 79 (valores da P2.31).
            case "ARESC_RES046_2016" -> List.of(
                    profile(CHARGES_DISCLOSURE, profileKey, "Res. 046/2016", "art. 104"),
                    profile(RECONNECTION_SLA_DISCLOSURE, profileKey, "Res. 046/2016", "arts. 87 e 88"),
                    profile(CUT_NOTICE_DISCLOSURE, profileKey, "Res. 046/2016", "art. 79"));
            // AGIR RN 001/2013: fee.* no art. 129; rec.sla_hours nos arts. 151/152;
            // cut.min_notice_days no art. 143 (valores da P2.31).
            case "AGIR_RN001_2013" -> List.of(
                    profile(CHARGES_DISCLOSURE, profileKey, "RN 001/2013", "art. 129"),
                    profile(RECONNECTION_SLA_DISCLOSURE, profileKey, "RN 001/2013", "arts. 151 e 152"),
                    profile(CUT_NOTICE_DISCLOSURE, profileKey, "RN 001/2013", "art. 143"));
            // CISAM-MO RN 01/2017: so o aviso de corte tem artigo confirmado
            // (art. 104); encargos sao remetidos a legislacao municipal, sem
            // percentuais — nenhum campo de encargos entra (regra da P2.31).
            case "CISAM_MO_RN01_2017" -> List.of(
                    profile(CUT_NOTICE_DISCLOSURE, profileKey, "RN 01/2017", "art. 104"));
            // CISAM-MO RN 05/2026: aviso de suspensao de 30 dias confirmado no
            // mapa, sem artigo discriminado — a referencia declara isso.
            case "CISAM_MO_RN05_2026" -> List.of(
                    profile(CUT_NOTICE_DISCLOSURE, profileKey, "RN 05/2026",
                            "aviso de suspensao de 30 dias (artigo nao discriminado no mapa de ERIs)"));
            // AGR-Tubarao (condicoes comerciais nao localizadas) e CISAM-SUL
            // (texto so digitalizado): so a base federal nesta versao.
            case "AGR_TUBARAO_RES007_2013", "CISAM_SUL_RN03_2017" -> List.of();
            default -> throw new IllegalArgumentException("Perfil regulatorio desconhecido: " + profileKey
                    + " (perfis fora de SC trazem a propria lista quando carregados, ED-97)");
        };
    }

    /** Todos os perfis com lista nesta versao do catalogo (os 7 da P2.31). */
    public static List<String> profileKeys() {
        return List.of("ARIS_RN19_2019", "ARESC_RES046_2016", "AGIR_RN001_2013",
                "AGR_TUBARAO_RES007_2013", "CISAM_MO_RN01_2017", "CISAM_MO_RN05_2026",
                "CISAM_SUL_RN03_2017");
    }

    /** A lista completa e vigente do perfil numa data: federal + perfil. */
    public static List<BillFieldRequirement> requiredFor(String profileKey, LocalDate date) {
        List<BillFieldRequirement> all = new ArrayList<>();
        for (BillFieldRequirement f : federalFields()) {
            if (f.covers(date)) {
                all.add(f);
            }
        }
        for (BillFieldRequirement f : profileFields(profileKey)) {
            if (f.covers(date)) {
                all.add(f);
            }
        }
        return List.copyOf(all);
    }

    /** Mapa perfil -> chaves exigidas (para a carga R__ e os testes). */
    public static Map<String, List<String>> requiredFieldKeysByProfile() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (String key : profileKeys()) {
            List<String> fields = new ArrayList<>();
            for (BillFieldRequirement f : requiredFor(key, LocalDate.of(2026, 1, 1))) {
                fields.add(f.fieldKey());
            }
            map.put(key, List.copyOf(fields));
        }
        return Map.copyOf(map);
    }
}
