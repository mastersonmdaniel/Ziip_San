package br.com.ziipsan.commercial.registry.importer;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * P1.40: registro dos esquemas de importação. Todo bean {@code ImportSchema}
 * do contexto entra aqui; o cadastro novo declara o seu esquema como um bean
 * e o importador genérico passa a aceitá-lo, sem rota nova e sem importador
 * novo. Esquema desconhecido na rota é 422 (campo schemaCode), nunca 500.
 *
 * <p>Os dois esquemas embutidos provam o framework contra tabelas reais
 * (ED-12: catálogos auxiliares pelo importador, com OS de lote): categorias
 * e subcategorias. O CSV do cadastro (P1.3) e as planilhas do laboratório
 * (P2.24), dos cadastros de consistência (ED-20) e de beneficiários (ED-57)
 * entram como esquemas próprios das suas frentes, na integração.
 */
@Component
public class ImportSchemaRegistry {

    private final List<ImportSchema> schemas;

    public ImportSchemaRegistry(List<ImportSchema> schemas) {
        this.schemas = List.copyOf(schemas);
    }

    public List<ImportSchema> schemas() {
        return schemas;
    }

    public ImportSchema require(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("schemaCode e obrigatorio");
        }
        return schemas.stream()
                .filter(s -> s.code().equals(code.strip()))
                .findFirst()
                .orElseThrow(() -> new UnknownSchemaException(code.strip()));
    }

    /** Esquema pedido na rota não está declarado. O serviço traduz para 422. */
    public static final class UnknownSchemaException extends RuntimeException {
        private final String code;

        public UnknownSchemaException(String code) {
            super("Esquema de importacao desconhecido: " + code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
