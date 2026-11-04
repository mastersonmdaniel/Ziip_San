package br.com.ziipsan.platform.templates;

import java.util.Map;
import java.util.UUID;

/**
 * Gancho da marca do tenant sobre o modelo (P2.30 -> P2.17 futura).
 *
 * <p>A P2.30 NÃO aplica marca nenhuma: este gancho existe para que a P2.17
 * ligue o {@code BrandProvider} da P1.19 aqui sem tocar no motor nem nas
 * tabelas. O mapa devolvido usa chaves com o prefixo {@code brand.} (ex.:
 * {@code brand.name}, {@code brand.footer}) e o renderizador as resolve
 * como qualquer variável ({@code {{brand.name}}}); sem provedor ligado, a
 * referência a {@code brand.*} na geração é recusada como variável
 * desconhecida — marca ausente nunca vira texto vazio silencioso.
 *
 * <p>Regra de precedência na geração: variável de marca NÃO sobrepõe valor
 * informado pelo chamador para o mesmo nome — o mapa do chamador vence, e
 * o provedor só preenche o que falta. Blocos de conteúdo legal (P2.32) não
 * passam por aqui: a marca nunca altera bloco LOCKED (a validação de blocos
 * travados acontece antes da renderização, sobre o modelo guardado).
 */
public interface TemplateBrandProvider {

    /** Variáveis de marca do tenant, com chaves {@code brand.*}. */
    Map<String, String> brandVariables(UUID tenantId);
}
