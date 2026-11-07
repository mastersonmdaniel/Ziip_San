-- Carga repetivel do conteudo minimo da fatura (P2.32), versao 1 do catalogo.
-- Fonte unica: RequiredBillContent.java — esta carga NAO se edita a mao sem
-- editar a fonte; RequiredContentCatalogConsistencyTest confere os dois lados.
-- profile_key NULL = base federal (vale para todo perfil).
-- Guarda de contagem no fim: 16 linhas (5 federais + 11 de perfil).
INSERT INTO invoice_required_fields
    (tenant_id, catalog_version, profile_key, field_key, source_norm, source_reference, locked_block, origin)
VALUES
    ('${tenant_id}', 1, NULL, 'bill.regulatory_model_identification', 'Lei 11.445/2007', 'art. 39', false, 'FEDERAL'),
    ('${tenant_id}', 1, NULL, 'supplier.identification_block', 'CDC (Lei 8.078/1990)', 'art. 42-A, incluido pela Lei 12.039/2009', true, 'FEDERAL'),
    ('${tenant_id}', 1, NULL, 'water_quality.monthly_block', 'Decreto 5.440/2005', 'informacao mensal de qualidade da agua', false, 'FEDERAL'),
    ('${tenant_id}', 1, NULL, 'annual_discharge.declaration', 'Lei 12.007/2009', 'declaracao anual de quitacao de debitos', false, 'FEDERAL'),
    ('${tenant_id}', 1, NULL, 'holder.printed_name', 'P2.5 (bill.holder_name_display)', 'regra de impressao do nome do titular', false, 'FEDERAL'),
    ('${tenant_id}', 1, 'ARIS_RN19_2019', 'charges.fine_interest_correction_disclosure', 'RN 19/2019', 'art. 108', false, 'REGULATORY_PROFILE'),
    ('${tenant_id}', 1, 'ARIS_RN19_2019', 'reconnection.sla_disclosure', 'RN 19/2019', 'arts. 160 e 161', false, 'REGULATORY_PROFILE'),
    ('${tenant_id}', 1, 'ARIS_RN19_2019', 'cut.notice_disclosure', 'RN 19/2019', 'art. 153', false, 'REGULATORY_PROFILE'),
    ('${tenant_id}', 1, 'ARESC_RES046_2016', 'charges.fine_interest_correction_disclosure', 'Res. 046/2016', 'art. 104', false, 'REGULATORY_PROFILE'),
    ('${tenant_id}', 1, 'ARESC_RES046_2016', 'reconnection.sla_disclosure', 'Res. 046/2016', 'arts. 87 e 88', false, 'REGULATORY_PROFILE'),
    ('${tenant_id}', 1, 'ARESC_RES046_2016', 'cut.notice_disclosure', 'Res. 046/2016', 'art. 79', false, 'REGULATORY_PROFILE'),
    ('${tenant_id}', 1, 'AGIR_RN001_2013', 'charges.fine_interest_correction_disclosure', 'RN 001/2013', 'art. 129', false, 'REGULATORY_PROFILE'),
    ('${tenant_id}', 1, 'AGIR_RN001_2013', 'reconnection.sla_disclosure', 'RN 001/2013', 'arts. 151 e 152', false, 'REGULATORY_PROFILE'),
    ('${tenant_id}', 1, 'AGIR_RN001_2013', 'cut.notice_disclosure', 'RN 001/2013', 'art. 143', false, 'REGULATORY_PROFILE'),
    ('${tenant_id}', 1, 'CISAM_MO_RN01_2017', 'cut.notice_disclosure', 'RN 01/2017', 'art. 104', false, 'REGULATORY_PROFILE'),
    ('${tenant_id}', 1, 'CISAM_MO_RN05_2026', 'cut.notice_disclosure', 'RN 05/2026', 'aviso de suspensao de 30 dias (artigo nao discriminado no mapa de ERIs)', false, 'REGULATORY_PROFILE')
ON CONFLICT DO NOTHING;
