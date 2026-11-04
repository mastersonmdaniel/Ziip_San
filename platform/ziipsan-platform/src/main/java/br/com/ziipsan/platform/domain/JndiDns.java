package br.com.ziipsan.platform.domain;

import br.com.ziipsan.platform.domain.DnsResolvers.CnameResolver;
import br.com.ziipsan.platform.domain.DnsResolvers.TxtResolver;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;

/**
 * Resolução de DNS por JNDI (P2.27), sem dependência nova: o JDK já
 * traz o provedor DNS. Usada só nas ações do admin (verificar); o
 * caminho pré-login não toca no DNS.
 *
 * <p>Detalhe do TXT: o DNS devolve cada string entre aspas e pode
 * partir um valor longo em várias strings — aqui as aspas são
 * removidas e os pedaços concatenados, para que a comparação do
 * serviço receba o valor publicado (`ziipsan-verify=<token>`).
 */
public final class JndiDns {

    private JndiDns() {
    }

    /** {@link TxtResolver} por JNDI; nome inexistente = lista vazia (não é erro). */
    public static TxtResolver txtResolver() {
        return name -> {
            try {
                Attributes attrs = attributes(name, new String[]{"TXT"});
                Attribute txt = attrs.get("TXT");
                List<String> out = new ArrayList<>();
                if (txt != null) {
                    for (int i = 0; i < txt.size(); i++) {
                        out.add(unquote(String.valueOf(txt.get(i))));
                    }
                }
                return out;
            } catch (NamingException e) {
                if (isNotFound(e)) {
                    return List.of();
                }
                throw new IllegalStateException("Falha ao consultar o TXT de " + name + ".", e);
            }
        };
    }

    /** {@link CnameResolver} por JNDI; sem CNAME = vazio. */
    public static CnameResolver cnameResolver() {
        return hostname -> {
            try {
                Attributes attrs = attributes(hostname, new String[]{"CNAME"});
                Attribute cname = attrs.get("CNAME");
                if (cname == null || cname.size() == 0) {
                    return Optional.empty();
                }
                String target = String.valueOf(cname.get(0)).strip().toLowerCase();
                if (target.endsWith(".")) {
                    target = target.substring(0, target.length() - 1);
                }
                return Optional.of(target);
            } catch (NamingException e) {
                if (isNotFound(e)) {
                    return Optional.empty();
                }
                throw new IllegalStateException("Falha ao consultar o CNAME de " + hostname + ".", e);
            }
        };
    }

    private static Attributes attributes(String name, String[] types) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put(Context.PROVIDER_URL, "dns:");
        DirContext ctx = new InitialDirContext(env);
        try {
            return ctx.getAttributes(name, types);
        } finally {
            ctx.close();
        }
    }

    private static boolean isNotFound(NamingException e) {
        String message = String.valueOf(e.getMessage());
        return message.contains("NameNotFoundException") || e instanceof javax.naming.NameNotFoundException;
    }

    /** Remove as aspas de envoltório do TXT e concatena pedaços divididos. */
    static String unquote(String raw) {
        String value = raw.strip();
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            if (value.charAt(i) == '"') {
                int end = value.indexOf('"', i + 1);
                if (end < 0) {
                    out.append(value.substring(i + 1));
                    break;
                }
                out.append(value, i + 1, end);
                i = end + 1;
            } else if (Character.isWhitespace(value.charAt(i))) {
                // Espaço entre pedaços citados ("a" "b") é separador, não conteúdo.
                int next = i;
                while (next < value.length() && Character.isWhitespace(value.charAt(next))) {
                    next++;
                }
                if (next < value.length() && value.charAt(next) == '"') {
                    i = next;
                } else {
                    out.append(value.charAt(i));
                    i++;
                }
            } else {
                out.append(value.charAt(i));
                i++;
            }
        }
        return out.toString();
    }
}
