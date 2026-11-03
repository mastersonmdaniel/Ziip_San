package br.com.ziipsan.platform.infra.events;

/**
 * Catálogo sintético compartilhado pelos testes da P1.38 (unidade e IT).
 * Só de teste: nunca entra no manifesto real. Duas versões do mesmo evento,
 * para provar que carga incompatível com a versão é recusada naquela versão
 * e aceita na versão nova.
 */
public final class BusinessEventCatalogFixture {

    public static final String CATALOG_JSON = """
            {
              "declaredEventConsumers": [
                {"task": "P3.36", "name": "Central de avisos"},
                {"task": "P2.26", "name": "Webhooks de saída"}
              ],
              "events": [
                {
                  "name": "example.thing.created", "version": 1,
                  "description": "Coisa criada (sintético, só de teste)",
                  "payloadSchema": {
                    "type": "object", "additionalProperties": false,
                    "required": ["thingId", "statusCode"],
                    "properties": {
                      "thingId": {"type": "string", "format": "uuid"},
                      "statusCode": {"type": "string", "enum": ["CREATED"]}
                    }
                  },
                  "requiredPermission": "registry:read",
                  "consumers": ["P3.36"],
                  "originTask": "P1.38-test"
                },
                {
                  "name": "example.thing.created", "version": 2,
                  "description": "Coisa criada, com origem opcional (mudança incompatível: nova versão)",
                  "payloadSchema": {
                    "type": "object", "additionalProperties": false,
                    "required": ["thingId", "statusCode"],
                    "properties": {
                      "thingId": {"type": "string", "format": "uuid"},
                      "statusCode": {"type": "string", "enum": ["CREATED"]},
                      "parentThingId": {"type": "string", "format": "uuid"}
                    }
                  },
                  "requiredPermission": "registry:read",
                  "consumers": ["P3.36", "P2.26"],
                  "originTask": "P1.38-test"
                }
              ],
              "eventGaps": []
            }
            """;

    private BusinessEventCatalogFixture() {
    }
}
