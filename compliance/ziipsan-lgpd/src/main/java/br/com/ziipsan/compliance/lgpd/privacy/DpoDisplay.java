package br.com.ziipsan.compliance.lgpd.privacy;

import br.com.ziipsan.core.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Encarregado de dados do tenant (aceite da P3.40): fonte ÚNICA na fonte do tenant da
 * P1.6 (DAD-32), parâmetro {@code lgpd.dpo_contact} (classe d), exibido no portal, na
 * política de privacidade e nos documentos (Res. CD/ANPD 18/2024) — o mesmo nome e o
 * mesmo contato de divulgação pública nos três lugares, porque os três leem DESTE
 * registro. O teste de exibição em cada lugar está em {@code DpoDisplayAndPolicyTest}.
 *
 * <p>Sem encarregado registrado, a exibição é RECUSADA, não inventada: não existe texto
 * de reserva para o encarregado do tenant (o texto de reserva da P3.38 é do encarregado
 * da PLATAFORMA, outro registro). Tenant sem {@code lgpd.dpo_contact} está com a
 * implantação incompleta — o checklist da P1.6 já cobra "encarregado informado" — e a
 * falha tem de aparecer, não ser maquiada.
 *
 * <p>O valor do parâmetro é JSON lido pela classe {@code Json} do core (regra do
 * projeto): {@code {"name": "...", "contact": "..."}}. Nome e contato são os de
 * DIVULGAÇÃO PÚBLICA (é o que a Res. 18/2024 manda publicar); contato interno não
 * pertence a este parâmetro.
 */
public final class DpoDisplay {

    private DpoDisplay() {
    }

    /** Chave do parâmetro na fonte única do tenant (P1.6, DAD-32). */
    public static final String PARAMETER_KEY = "lgpd.dpo_contact";

    /** Lugares de exibição exigidos pelo aceite. */
    public enum Place {
        /** Área do titular no portal. */
        PORTAL,
        /** Política de privacidade publicada do tenant. */
        PRIVACY_POLICY,
        /** Documentos emitidos (cabeçalho/rodapé com o canal do encarregado). */
        DOCUMENT
    }

    /** Encarregado de divulgação pública do tenant. */
    public record DpoContact(String name, String publicContact) {

        public DpoContact {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("nome do encarregado é obrigatório (Res. CD/ANPD 18/2024)");
            }
            if (publicContact == null || publicContact.isBlank()) {
                throw new IllegalArgumentException("contato de divulgação pública do encarregado "
                        + "é obrigatório (Res. CD/ANPD 18/2024)");
            }
        }
    }

    /**
     * Lê o encarregado do valor do parâmetro {@code lgpd.dpo_contact}.
     *
     * @throws IllegalStateException se o parâmetro estiver ausente/vazio ou sem nome/contato
     */
    public static DpoContact fromParameterValue(String parameterValueJson) {
        if (parameterValueJson == null || parameterValueJson.isBlank()) {
            throw new IllegalStateException(PARAMETER_KEY + " não registrado para o tenant: "
                    + "o encarregado de dados é exibido no portal, na política e nos documentos "
                    + "e não pode ser inventado — registre o parâmetro (classe d, P1.6/DAD-32)");
        }
        Map<String, Object> map = Json.readMap(parameterValueJson);
        Object name = map.get("name");
        Object contact = map.get("contact");
        if (!(name instanceof String nameText) || nameText.isBlank()
                || !(contact instanceof String contactText) || contactText.isBlank()) {
            throw new IllegalStateException(PARAMETER_KEY
                    + " deve declarar name e contact de divulgação pública (Res. CD/ANPD 18/2024)");
        }
        return new DpoContact(nameText, contactText);
    }

    /**
     * Texto de exibição do encarregado para um lugar. Os três lugares recebem o MESMO
     * conteúdo-fonte (nome + contato); só o rótulo do lugar muda — é isso que o teste de
     * exibição confere, um por lugar.
     */
    public static String render(DpoContact contact, Place place) {
        Objects.requireNonNull(contact, "contact é obrigatório");
        Objects.requireNonNull(place, "place é obrigatório");
        String label = switch (place) {
            case PORTAL -> "Encarregado de dados (portal)";
            case PRIVACY_POLICY -> "Encarregado de dados (política de privacidade)";
            case DOCUMENT -> "Encarregado de dados (documentos)";
        };
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("label", label);
        map.put("name", contact.name());
        map.put("contact", contact.publicContact());
        return Json.write(map);
    }
}
