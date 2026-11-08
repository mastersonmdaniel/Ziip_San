package br.com.ziipsan.core.effluent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Obrigacao legal do efluente e do lodo (JOR-16): classe
 * {@code REGULATORY}, fundada na Resolucao CONAMA 430/2011 (padroes
 * de lancamento de efluentes) e na Resolucao CONAMA 498/2020
 * (tratamento e destinacao do lodo de ETE), ATIVA para todo tenant
 * com ETA ou ETE.
 *
 * <p>Regras duras do aceite, provadas em teste nos dois sentidos:
 * <ul>
 *   <li>a obrigacao NAO depende da ISO 14001 (pacote da ED-70) nem
 *       do pacote de esgoto e agua bruta: os campos dessas ligacoes
 *       existem na {@link StationPresence} justamente para provar
 *       que eles NAO entram na decisao;</li>
 *   <li>tenant sem ETA e sem ETE nao tem a obrigacao — nem com a
 *       ISO 14001 ligada.</li>
 * </ul>
 * Desligar um pacote nunca apaga nem desativa esta obrigacao:
 * ela nasce da lei e da existencia da estacao, nao de contratacao.
 */
public final class RegulatoryObligation {

    /** Classe da obrigacao no sentido do ADR-0041: dever legal, nao pacote opcional. */
    public enum ObligationClass { REGULATORY }

    /** Componentes da obrigacao cumpridos por esta tarefa. */
    public enum Component {
        /** Automonitoramento do efluente da ETE contra o catalogo de padrao. */
        EFFLUENT_SELF_MONITORING,
        /** Geracao e destinacao registradas do lodo e dos residuos da estacao. */
        SLUDGE_GENERATION_AND_DESTINATION
    }

    /**
     * Presenca de estacoes e de pacotes no tenant. Os tres campos de
     * pacote sao lidos apenas para serem IGNORADOS pela avaliacao
     * (JOR-16) — mante-los aqui e o que torna a independencia
     * testavel, em vez de apenas declarada.
     */
    public record StationPresence(
            UUID tenantId,
            boolean hasWaterTreatmentPlant,
            boolean hasWastewaterTreatmentPlant,
            boolean iso14001Enabled,
            boolean sewerPackageEnabled,
            boolean rawWaterPackageEnabled) {
        public StationPresence {
            if (tenantId == null) throw new IllegalArgumentException("tenantId e obrigatorio");
        }

        public boolean hasStation() {
            return hasWaterTreatmentPlant || hasWastewaterTreatmentPlant;
        }
    }

    public record Obligation(
            String code,
            ObligationClass obligationClass,
            List<String> legalBases,
            List<Component> components) {
        public Obligation {
            legalBases = List.copyOf(legalBases);
            components = List.copyOf(components);
        }
    }

    /** Codigo estavel da obrigacao (referenciado pela ED-70 e pela agenda). */
    public static final String CODE = "EFFLUENT_AND_SLUDGE";

    private RegulatoryObligation() {}

    /**
     * A obrigacao existe se, e somente se, o tenant tem ETA ou ETE.
     * Nenhum pacote (ISO 14001, esgoto, agua bruta) liga, desliga ou
     * condiciona o resultado.
     */
    public static Optional<Obligation> evaluate(StationPresence presence) {
        if (presence == null) throw new IllegalArgumentException("presenca de estacoes e obrigatoria");
        if (!presence.hasStation()) return Optional.empty();
        return Optional.of(new Obligation(
                CODE,
                ObligationClass.REGULATORY,
                List.of("CONAMA 430/2011", "CONAMA 498/2020"),
                List.of(Component.EFFLUENT_SELF_MONITORING,
                        Component.SLUDGE_GENERATION_AND_DESTINATION)));
    }

    /** Atalho do portao: a operacao regulada exige a obrigacao ativa. */
    public static Obligation requireActive(StationPresence presence) {
        return evaluate(presence).orElseThrow(() -> new IllegalStateException(
                "tenant sem ETA e sem ETE: a obrigacao legal " + CODE
                        + " (CONAMA 430/2011, CONAMA 498/2020) nao se aplica (JOR-16)"));
    }
}
