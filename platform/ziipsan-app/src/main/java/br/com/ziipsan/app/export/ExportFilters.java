package br.com.ziipsan.app.export;

import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos;
import br.com.ziipsan.commercial.registry.application.CustomerDtos;
import br.com.ziipsan.commercial.registry.domain.Customer;
import br.com.ziipsan.core.api.ApiException;

import java.util.Map;
import java.util.UUID;

/**
 * P1.12: filtros da exportação (o mesmo corpo do search da lista) convertidos
 * para os registros de busca. O controlador recebe o corpo como mapa para não
 * duplicar os DTOs de busca; a validação de formato vive aqui (UUID inválido,
 * tipo de pessoa inválido e texto acima de 200 caracteres dão 400, como o
 * {@code @Valid} do search). O texto da busca nunca vai para a URL nem para o
 * log: ele só alimenta a consulta, como no search.
 */
final class ExportFilters {

    private ExportFilters() {
    }

    static CustomerDtos.CustomerSearch customers(Map<String, Object> filters, int page, int size) {
        String q = text(filters, "q");
        validateQ(q);
        return new CustomerDtos.CustomerSearch(
                q,
                text(filters, "nameMatch"),
                personType(filters),
                uuid(filters, "customerTypeId"),
                text(filters, "city"),
                bool(filters, "includeDeleted"),
                page, size, text(filters, "sort"));
    }

    static ConsumerUnitDtos.UnitSearch units(Map<String, Object> filters, int page, int size) {
        String q = text(filters, "q");
        validateQ(q);
        return new ConsumerUnitDtos.UnitSearch(
                q,
                uuid(filters, "localityId"),
                uuid(filters, "sectorId"),
                uuid(filters, "blockId"),
                uuid(filters, "waterStatusId"),
                uuid(filters, "sewerStatusId"),
                uuid(filters, "categoryId"),
                uuid(filters, "routeId"),
                text(filters, "cep"),
                uuid(filters, "profileId"),
                text(filters, "numberFrom"),
                text(filters, "numberTo"),
                bool(filters, "includeDeleted"),
                page, size, text(filters, "sort"));
    }

    private static void validateQ(String q) {
        if (q != null && q.length() > 200) {
            throw new ApiException.BadRequest("invalid-filter", "A busca aceita no máximo 200 caracteres.");
        }
    }

    private static String text(Map<String, Object> filters, String key) {
        Object value = filters.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static Boolean bool(Map<String, Object> filters, String key) {
        Object value = filters.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static UUID uuid(Map<String, Object> filters, String key) {
        String text = text(filters, key);
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("invalid-filter", "Filtro " + key + " não é um identificador válido.");
        }
    }

    private static Customer.PersonType personType(Map<String, Object> filters) {
        String text = text(filters, "personType");
        if (text == null) {
            return null;
        }
        try {
            return Customer.PersonType.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("invalid-filter", "Tipo de pessoa inválido: " + text);
        }
    }
}
