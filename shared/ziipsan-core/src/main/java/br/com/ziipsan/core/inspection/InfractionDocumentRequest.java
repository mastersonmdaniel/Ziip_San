package br.com.ziipsan.core.inspection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pedido de emissao do auto de infracao ao motor unico de modelos da P2.30
 * (aceite da ED-24: auto "emitido pelo motor de modelos da P2.30"). Esta
 * frente NAO cria leiaute proprio: monta as variaveis do tipo de documento
 * {@code INFRACTION_NOTICE} e delega a renderizacao ao motor pela porta
 * {@link DocumentEngineGateway}.
 *
 * <p>As variaveis sao EXATAMENTE as do catalogo da P2.30 para
 * {@code INFRACTION_NOTICE} (TemplateVariableCatalog da plataforma,
 * conferido no espelho da P2.32): {@code customer_name},
 * {@code registration_number}, {@code protocol_number},
 * {@code infraction_description} e {@code appeal_days}. Variavel fora do
 * catalogo ou faltante recusa o pedido aqui, antes de chamar o motor — o
 * motor tambem recusa na validacao do modelo (aceite da P2.30), e as duas
 * recusas sao complementares, nao substitutas.
 */
public final class InfractionDocumentRequest {

    /** Tipo de documento no motor da P2.30 (TemplateDocumentType.INFRACTION_NOTICE). */
    public static final String DOCUMENT_TYPE = "INFRACTION_NOTICE";

    /** Variaveis do catalogo da P2.30 para INFRACTION_NOTICE, na ordem do catalogo. */
    public static final Set<String> CATALOG_VARIABLES = Set.of(
            "customer_name", "registration_number", "protocol_number",
            "infraction_description", "appeal_days");

    private InfractionDocumentRequest() {}

    public record Request(String documentType, Map<String, String> variables) {
        public Request {
            if (!DOCUMENT_TYPE.equals(documentType))
                throw new IllegalArgumentException("tipo de documento do auto deve ser " + DOCUMENT_TYPE + ": " + documentType);
            Objects.requireNonNull(variables, "variaveis do auto sao obrigatorias");
            for (String required : CATALOG_VARIABLES) {
                String value = variables.get(required);
                if (value == null || value.isBlank())
                    throw new IllegalArgumentException("variavel obrigatoria do catalogo P2.30 ausente no auto: " + required);
            }
            for (String provided : variables.keySet()) {
                if (!CATALOG_VARIABLES.contains(provided))
                    throw new IllegalArgumentException("variavel fora do catalogo P2.30 de " + DOCUMENT_TYPE + ": " + provided);
            }
            variables = Map.copyOf(variables);
        }
    }

    /**
     * Monta o pedido a partir dos fatos do auto. {@code appealDays} e o valor
     * resolvido de {@code infraction.appeal_days} (ver
     * {@link InspectionParameters}) — o mesmo prazo que rege a defesa.
     */
    public static Request build(String customerName, String registrationNumber, String protocolNumber,
                                String infractionDescription, int appealDays) {
        if (appealDays <= 0) throw new IllegalArgumentException("appeal_days deve ser maior que zero: " + appealDays);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("customer_name", requireText(customerName, "customer_name"));
        variables.put("registration_number", requireText(registrationNumber, "registration_number"));
        variables.put("protocol_number", requireText(protocolNumber, "protocol_number"));
        variables.put("infraction_description", requireText(infractionDescription, "infraction_description"));
        variables.put("appeal_days", Integer.toString(appealDays));
        return new Request(DOCUMENT_TYPE, variables);
    }

    private static String requireText(String value, String variable) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("variavel obrigatoria do catalogo P2.30 ausente no auto: " + variable);
        return value;
    }

    /**
     * Porta do motor de modelos da P2.30. O adaptador real chama o servico
     * de modelos da plataforma (modelo vigente do tenant para
     * INFRACTION_NOTICE, com previa/aprovacao do proprio motor) e devolve o
     * id do documento gerado, que grava modelo e versao usados (aceite da
     * P2.30) — nada disso e reimplementado aqui.
     */
    public interface DocumentEngineGateway {
        UUID renderInfractionNotice(Request request);
    }
}
