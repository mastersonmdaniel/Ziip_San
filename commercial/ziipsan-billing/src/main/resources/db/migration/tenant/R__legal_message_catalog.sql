-- Carga repetivel do catalogo de mensagens legais da fatura (P2.32), versao 1.
-- Fonte unica: LegalMessageCatalog.java — conferida por teste de consistencia.
-- Slots entram INATIVOS (slot_only): ED-88, ED-25 e P3.5 e quem os ativa.
SELECT set_config('app.current_tenant', '${tenant_id}', true);
INSERT INTO legal_message_catalog
    (tenant_id, catalog_version, message_key, norm_key, parameter_class, trigger_condition, print_order, legal, slot_only, activating_task)
VALUES
    ('${tenant_id}', 1, 'legal.social_tariff_loss_notice', 'Lei 14.898/2024', 'C', 'SOCIAL_TARIFF_LOSS_SCHEDULED', 10, true, false, NULL),
    ('${tenant_id}', 1, 'legal.annual_discharge_declaration', 'Lei 12.007/2009', 'A', 'ANNUAL_DISCHARGE_DUE', 20, true, false, NULL),
    ('${tenant_id}', 1, 'legal.water_quality_block', 'Decreto 5.440/2005', 'A', 'WATER_QUALITY_AVAILABLE', 30, true, false, NULL),
    ('${tenant_id}', 1, 'legal.benefit_expiry_notice', 'CAD-88 (P2.5)', 'D', 'BENEFIT_EXPIRY_WINDOW', 40, true, false, NULL),
    ('${tenant_id}', 1, 'slot.mandatory_connection_90d', 'ED-88 (ligacao obrigatoria)', 'B', 'SLOT_ACTIVATION', 110, false, true, 'ED-88'),
    ('${tenant_id}', 1, 'slot.cobilling_60d', 'ED-25 (cofaturamento)', 'B', 'SLOT_ACTIVATION', 120, false, true, 'ED-25'),
    ('${tenant_id}', 1, 'slot.abnormal_consumption_alert', 'P3.5 (alerta de consumo anormal)', 'B', 'SLOT_ACTIVATION', 130, false, true, 'P3.5')
ON CONFLICT DO NOTHING;
