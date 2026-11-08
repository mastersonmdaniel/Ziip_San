package br.com.ziipsan.intelligence.gis.asset;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Catalogo de classes de equipamento com a FRONTEIRA de cada classe
 * definida como na ISO 14224 (clausula 8.1, como analogia tecnica —
 * P1.35): cada classe declara o que INCLUI e o que EXCLUI, de modo
 * que duas classes nunca cubram o mesmo bem (sem sobreposicao).
 *
 * <p>Origem das entradas: SOMENTE tipos nomeados nas specs das
 * dependencias executadas e no proprio aceite da ED-56 —
 * ETA/ETE/elevatoria/reservatorio (ED-53/P1.35), bombas, motores e
 * paineis de ETA/ETE/elevatoria/reservatorio (aceite OPE-20),
 * valvulas e hidrantes (ED-54/OPE-09), hidrometro (P1.35) e
 * edificacoes (aceite: "equipamentos, instrumentos e edificacoes").
 * O catalogo COMPLETO de {@code ALVOS-DA-OS-E-HIERARQUIA-DE-ATIVOS}
 * nao esta entre as fontes locais desta VM: ele entra por carga do
 * tenant/curadoria sobre ESTE mecanismo (pendencia nomeada) — nenhuma
 * classe alem das nomeadas nas fontes foi inventada aqui. A FROTA e
 * da ED-59 e fica FORA deste catalogo (aceite).
 */
public final class EquipmentClassCatalog {

    private EquipmentClassCatalog() {
    }

    /**
     * Classe de equipamento: codigo, fronteira (inclui/exclui),
     * par UP/UAR correspondente e se e ativo nao linear (sujeito ao
     * plano preventivo da OPE-20). UP/UAR nulos = correspondencia
     * [A DEFINIR] ate a conferencia na tabela original do MCPSB.
     */
    public record EquipmentClass(String code, String label, String includes, String excludes,
                                 String up, String uar, boolean nonLinear,
                                 boolean confinedSpaceCapable) {
        public EquipmentClass {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("classe sem codigo");
            if (includes == null || includes.isBlank())
                throw new IllegalArgumentException("classe sem fronteira de inclusao (ISO 14224): " + code);
            if (excludes == null || excludes.isBlank())
                throw new IllegalArgumentException("classe sem fronteira de exclusao (ISO 14224): " + code);
        }
    }

    /** Classes com fonte nas specs executadas (ver javadoc da classe). */
    public static final List<EquipmentClass> SOURCED_CLASSES = List.of(
            new EquipmentClass("PUMP", "Bomba",
                    "conjunto bomba (carcaca, rotor, selo) que desloca fluido",
                    "o motor que a aciona (MOTOR) e o painel que a comanda (PANEL)",
                    null, null, true, false),
            new EquipmentClass("MOTOR", "Motor eletrico",
                    "motor eletrico de acionamento, com sua placa e rolamentos",
                    "a maquina acionada (PUMP) e o painel (PANEL)",
                    null, null, true, false),
            new EquipmentClass("PANEL", "Painel eletrico/de comando",
                    "quadro, disjuntores, partida e CLP do comando local",
                    "os motores e bombas comandados (MOTOR/PUMP)",
                    null, null, true, false),
            new EquipmentClass("VALVE", "Valvula de rede",
                    "valvula de bloqueio/manobra instalada em no da rede (GIS-03)",
                    "o atuador destacado quando inventariado a parte [A DEFINIR na carga do catalogo completo]",
                    null, null, false, false),
            new EquipmentClass("HYDRANT", "Hidrante",
                    "hidrante de coluna/solo ligado a rede de distribuicao",
                    "a valvula de manobra do ramal do hidrante (VALVE)",
                    null, null, false, false),
            new EquipmentClass("METER", "Hidrometro",
                    "medidor de agua instalado na ligacao (aponta para o ativo, P1.35)",
                    "o cavalete e o ramal (feicoes da rede, nao equipamento)",
                    null, null, false, false),
            new EquipmentClass("INSTRUMENT", "Instrumento",
                    "instrumento de medicao/controle (horimetro, sensor de vibracao, temperatura, corrente)",
                    "o equipamento medido (PUMP/MOTOR/PANEL)",
                    null, null, true, false),
            new EquipmentClass("BUILDING", "Edificacao",
                    "edificacao da instalacao (casa de bombas, laboratorio, predio administrativo)",
                    "os equipamentos nela instalados (PUMP/MOTOR/PANEL/INSTRUMENT e demais classes proprias) e o terreno",
                    null, null, false, false),
            new EquipmentClass("RESERVOIR_STRUCTURE", "Estrutura de reservatorio",
                    "estrutura civil do reservatorio (tanque, cobertura, escadas)",
                    "bombas/paineis da elevatoria anexa (PUMP/PANEL) e a valvula de saida (VALVE)",
                    null, null, false, true));

    /**
     * Prova de nao sobreposicao das fronteiras (correcao D4 da
     * revisao da ED-56). A versao original era VACUOSA: so reconhecia
     * a forma literal {@code "(CODIGO)"} e o auxiliar
     * {@code mentions(...)} devolvia sempre {@code false} — fronteiras
     * em linguagem natural nunca acusavam sobreposicao (verde falso).
     *
     * <p>Deteccao agora implementada de verdade, sobre tres formas de
     * mencao de B no texto de A (tudo normalizado: minusculas, sem
     * diacriticos, tokens por igualdade — nunca substring, para
     * "horimetro" nao casar com "hidrometro"):
     * <ol>
     *   <li>a forma explicita {@code "(CODIGO)"};</li>
     *   <li>o codigo de B como token do texto;</li>
     *   <li>o substantivo-cabeca do rotulo de B (primeiro token
     *       significativo de {@code label}, ex.: "motor" de "Motor
     *       eletrico", "bomba" de "Bomba") — com singularizacao
     *       simples do plural ("bombas" -&gt; "bomba").</li>
     * </ol>
     * Ha sobreposicao quando A MENCIONA B na inclusao sem mencionar B
     * (por codigo ou pelo substantivo) na exclusao.
     *
     * <p><b>Limite declarado (honesto):</b> a deteccao e lexical sobre
     * codigo + vocabulario dos rotulos do proprio catalogo. Sinonimos
     * livres fora desse vocabulario (ex.: "grupo motobomba") nao sao
     * detectaveis por este nucleo — a carga do catalogo completo
     * (P-ED56-02) deve declarar as fronteiras com os codigos/rotulos
     * do catalogo, que e a forma que esta verificacao examina. O que
     * nao existe mais e o verde falso: a sonda negativa da revisao
     * (classe cuja inclusao nomeia "motor eletrico ... e bomba" ao
     * lado de MOTOR) e acusada, e ha teste disso.
     */
    public static List<String> boundaryOverlaps(List<EquipmentClass> classes) {
        Map<String, EquipmentClass> byCode = classes.stream()
                .collect(Collectors.toMap(EquipmentClass::code, c -> c));
        java.util.List<String> problems = new java.util.ArrayList<>();
        for (EquipmentClass a : classes) {
            for (EquipmentClass b : classes) {
                if (a == b) continue;
                boolean aIncludesB = mentions(a.includes(), b);
                boolean aExcludesB = a.excludes().contains(b.code()) || mentions(a.excludes(), b);
                if (aIncludesB && !aExcludesB)
                    problems.add(a.code() + " inclui " + b.code() + " sem exclui-lo (sobreposicao ISO 14224)");
            }
        }
        return List.copyOf(problems);
    }

    private static boolean mentions(String text, EquipmentClass b) {
        if (text == null) return false;
        if (text.contains("(" + b.code() + ")")) return true;
        java.util.Set<String> tokens = normalizedTokens(text);
        if (tokens.contains(normalize(b.code()))) return true;
        for (String head : labelHeads(b)) {
            if (tokens.contains(head)) return true;
        }
        return false;
    }

    /** Substantivos do rotulo (tokens significativos, normalizados e singularizados). */
    private static java.util.Set<String> labelHeads(EquipmentClass b) {
        java.util.Set<String> heads = new java.util.HashSet<>();
        for (String t : normalizedTokens(b.label())) {
            if (t.length() >= 4) heads.add(t);
        }
        return heads;
    }

    private static java.util.Set<String> normalizedTokens(String text) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String raw : normalize(text).split("[^a-z0-9]+")) {
            if (raw.isBlank()) continue;
            out.add(singular(raw));
        }
        return out;
    }

    private static String normalize(String s) {
        String n = java.text.Normalizer.normalize(s.toLowerCase(java.util.Locale.ROOT),
                java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }

    /** Singularizacao simples: remove o "s" final de tokens com mais de 3 letras. */
    private static String singular(String token) {
        return token.length() > 3 && token.endsWith("s")
                ? token.substring(0, token.length() - 1) : token;
    }

    public static EquipmentClass requireClass(List<EquipmentClass> classes, String code) {
        return classes.stream().filter(c -> c.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("classe de equipamento fora do catalogo carregado: " + code));
    }
}
