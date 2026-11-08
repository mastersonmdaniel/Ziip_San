package br.com.ziipsan.platform.notification.multichannel;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta para os parâmetros {@code msg.*} da ED-41 no catálogo vigente do
 * tenant (mesmo padrão das portas de parâmetros da P3.5/P3.36/ED-25):
 *
 * <ul>
 *   <li>{@code msg.monthly_cap} — teto de gasto mensal por tenant (chave do
 *       aceite da ED-41; alertas a 80% e 100%, ADR-0019). Ausente = teto NÃO
 *       configurado: o disparo pago é recusado com motivo nomeado, nunca
 *       liberado sem teto nem com teto presumido;</li>
 *   <li>{@code msg.template_category_map} — mapa modelo → categoria de preço
 *       do WhatsApp (chave do aceite). Mapa vazio = nenhum modelo
 *       classificado;</li>
 *   <li>{@code msg.sms_sender_id} — <b>chave NOVA/proposta</b>: remetente
 *       autenticado de SMS do tenant (a regra do remetente autenticado de
 *       SMS/voz, fronteira declarada da P3.5, é aplicada aqui);</li>
 *   <li>{@code msg.whatsapp_account_id} — <b>chave NOVA/proposta</b>: conta
 *       de WhatsApp do tenant (número/identidade de envio dele, nunca uma
 *       conta compartilhada da plataforma);</li>
 *   <li>{@code msg.bill_pdf_default} — <b>chave NOVA/proposta</b>: o tenant
 *       adota PDF anexo como padrão da fatura por e-mail (ADR-0028, l).
 *       Sozinha NÃO autoriza o anexo: exige o aceite formal abaixo;</li>
 *   <li>{@code msg.bill_pdf_formal_acceptance} — <b>chave NOVA/proposta</b>:
 *       o aceite formal do tenant ao padrão de PDF anexo está registrado.
 *       Sem ele, padrão ligado não anexa (ver {@link BillEmailRule}).</li>
 * </ul>
 *
 * As definições destas chaves no catálogo global vão no SQL pendente da
 * ED-41, marcadas como propostas onde a chave é nova.
 */
public interface MultichannelParameters {

    Optional<BigDecimal> monthlyCap(UUID tenantId);

    Map<String, WhatsAppCategory> templateCategoryMap(UUID tenantId);

    Optional<String> smsSenderId(UUID tenantId);

    Optional<String> whatsappAccountId(UUID tenantId);

    boolean billPdfTenantDefault(UUID tenantId);

    boolean billPdfFormalAcceptance(UUID tenantId);
}
