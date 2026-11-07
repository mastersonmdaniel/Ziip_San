package br.com.ziipsan.platform.regulatory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ligação DAD-28 entre a adoção das NR da ANA pelos perfis (P2.31,
 * {@code reg.ana_nr_adoption}) e o catálogo de normas da P1.34
 * ({@code platform.cert_standard}, core V9_8).
 *
 * <p>Formato real do catálogo, conferido nas fontes geradoras da P1.34:
 * o gerador {@code scripts/normas/generate-norm-catalog-load.py} define
 * {@code code = catalog_key = id} do JSON de normas
 * ({@code docs/normas/catalogo-normas.json}) — um slug estável, único e
 * seguro para URL, NÃO o número da norma (o JSON tem itens com o mesmo
 * número e ano, e derivar o código de número/ano colidiria no UNIQUE de
 * {@code code}). É o mesmo formato documentado em
 * {@code docs/normas/P1-34-CARGA-CATALOGO.md} ("Chave: code = catalog_key
 * = id estável do JSON").
 *
 * <p>Os códigos abaixo foram lidos, um a um, do arquivo de catálogo da
 * área 02 (ANA) gerado da mesma fonte da P1.34
 * ({@code docs/normas/catalogo/02-agencia-nacional-de-aguas-e-saneamento-basico-ana-.md},
 * coluna ID, sobre o HEAD {@code 0fbcd22}):
 * NR 1 = {@code ana-nr-1-res-79-2021} (Res. ANA 79/2021), NR 11 =
 * {@code ana-nr-11-res-230-2024} (Res. ANA 230/2024), NR 13 =
 * {@code ana-nr-13-res-271-2025} (Res. ANA 271/2025) etc. A chave de
 * adoção dos perfis continua sendo o número da NR ({@code NR11}), que é
 * o vocabulário do mapa de ERIs (§5); este mapa é a ponte NR → code, e o
 * aceite da ativação o valida contra {@code cert_standard} (ver
 * {@code RegulatoryProfileActivationService.accept}).
 */
public final class AnaNormCatalogLink {

    /** NR (chave de adoção do perfil, ex.: {@code NR11}) → {@code cert_standard.code} da P1.34. */
    public static final Map<String, String> CATALOG_CODE_BY_NR = buildMap();

    private AnaNormCatalogLink() {
    }

    private static Map<String, String> buildMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("NR1", "ana-nr-1-res-79-2021");
        map.put("NR2", "ana-nr-2-res-106-2021");
        map.put("NR3", "ana-nr-3-res-161-2023");
        map.put("NR4", "ana-nr-4-res-177-2024");
        map.put("NR5", "ana-nr-5-res-178-2024");
        map.put("NR6", "ana-nr-6-res-183-2024");
        map.put("NR7", "ana-nr-7-res-187-2024");
        map.put("NR8", "ana-nr-8-res-192-2024");
        map.put("NR9", "ana-nr-9-res-211-2024");
        map.put("NR10", "ana-nr-10-res-228-2024");
        map.put("NR11", "ana-nr-11-res-230-2024");
        map.put("NR12", "ana-nr-12-res-245-2025");
        map.put("NR13", "ana-nr-13-res-271-2025");
        map.put("NR14", "ana-nr-14-res-276-2025");
        map.put("NR15", "ana-nr-15-res-275-2025");
        map.put("NR16", "ana-nr-16-res-302-2026");
        return Map.copyOf(map);
    }

    /**
     * O {@code cert_standard.code} de uma NR, ou {@code null} se a chave
     * não é uma NR do catálogo (aceita {@code NR11} e {@code 11}).
     */
    public static String catalogCode(String nrKey) {
        if (nrKey == null) {
            return null;
        }
        String key = nrKey.trim().toUpperCase();
        if (!key.startsWith("NR")) {
            key = "NR" + key;
        }
        return CATALOG_CODE_BY_NR.get(key);
    }

    /** Códigos de catálogo das NR com ato de adoção no perfil (status com atos registrados). */
    public static List<String> adoptedCatalogCodes(RegulatoryProfileDef profile) {
        return profile.anaAdoption().entrySet().stream()
                .filter(e -> !e.getValue().acts().isEmpty())
                .map(e -> catalogCode(e.getKey()))
                .filter(code -> code != null)
                .toList();
    }

    /**
     * Toda NR presente no mapa de adoção do perfil resolve para um código
     * do catálogo da P1.34? Recusa nomeando a NR sem código: uma adoção
     * que não liga ao catálogo é exatamente o que o DAD-28 proíbe prometer.
     */
    public static void validateAdoptionLinkage(RegulatoryProfileDef profile) {
        for (String nr : profile.anaAdoption().keySet()) {
            if (catalogCode(nr) == null) {
                throw new IllegalArgumentException("Adoção da " + nr + " no perfil "
                        + profile.profileKey() + " sem código no catálogo de normas da "
                        + "P1.34 (cert_standard.code): a ligação DAD-28 não pode ser "
                        + "declarada para uma NR fora do mapa NR -> code verificado");
            }
        }
    }
}
