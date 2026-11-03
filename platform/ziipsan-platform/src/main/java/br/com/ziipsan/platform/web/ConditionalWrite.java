package br.com.ziipsan.platform.web;

/**
 * ETag forte por versão otimista (P1.11).
 *
 * <p>Formato: {@code "v<version>"}, onde {@code version} é o {@code @Version} do
 * {@code BaseEntity} (0 no primeiro INSERT, incrementado a cada UPDATE pelo
 * Hibernate). O controller devolve o ETag no GET do recurso e exige
 * {@code If-Match} no PUT: ausente é 428, diferente da versão atual é 412.
 * {@code If-Match: *} aceita qualquer versão existente (o recurso existe, pois o
 * controller já o carregou); qualquer outro formato conta como versão
 * desatualizada (412), nunca como erro de sintaxe do cliente.
 */
public final class ConditionalWrite {

    private ConditionalWrite() {
    }

    public static String etagFor(long version) {
        return "\"v" + version + "\"";
    }

    public static boolean matches(String ifMatch, long currentVersion) {
        if (ifMatch == null) {
            return false;
        }
        String value = ifMatch.trim();
        if (value.equals("*")) {
            return true;
        }
        String expected = "v" + currentVersion;
        if (value.equals("\"" + expected + "\"")) {
            return true;
        }
        // Forma fraca (W/"vN"): o valor é o mesmo, só a semântica de comparação muda;
        // para escrita por versão, aceitar o fraco não enfraquece a guarda (a versão
        // continua exata) e evita recusar clientes que geram ETag fraco.
        return value.equals("W/\"" + expected + "\"");
    }
}
