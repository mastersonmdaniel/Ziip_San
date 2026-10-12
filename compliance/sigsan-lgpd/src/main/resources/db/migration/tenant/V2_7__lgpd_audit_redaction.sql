-- LGPD: colunas com dado pessoal que a auditoria redige (ver core V2 e platform V1_4)
-- Version: 2.7.0
--
-- Os registros de consentimento e de pedidos do titular guardam IP, user agent, geolocalização e
-- texto livre do próprio titular. A trilha registra que houve consentimento/pedido e seus estados,
-- não esses valores.

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('lgpd_consent', 'consent_text',      'REDACT', 'texto do consentimento pode conter dado do titular'),
    ('lgpd_consent', 'ip_address',        'REDACT', 'IP do titular'),
    ('lgpd_consent', 'user_agent',        'REDACT', 'user agent do titular'),
    ('lgpd_consent', 'geolocation',       'OMIT',   'localização do titular'),
    ('lgpd_consent', 'withdrawal_reason', 'REDACT', 'texto livre do titular'),
    ('lgpd_consent', 'metadata',          'REDACT', 'metadado livre: pode conter dado pessoal'),
    ('lgpd_data_subject_request', 'description',      'REDACT', 'texto livre do titular'),
    ('lgpd_data_subject_request', 'response_summary', 'REDACT', 'resposta ao titular: pode conter dado pessoal'),
    ('lgpd_data_subject_request', 'response_data',    'REDACT', 'dados entregues ao titular'),
    ('lgpd_data_subject_request', 'denial_reason',    'REDACT', 'texto livre: pode conter dado pessoal'),
    ('lgpd_data_subject_request', 'extension_reason', 'REDACT', 'texto livre: pode conter dado pessoal'),
    ('lgpd_data_subject_request', 'metadata',         'REDACT', 'metadado livre: pode conter dado pessoal'),
    ('lgpd_dpia', 'dpo_signature', 'REDACT', 'assinatura: identifica o encarregado')
ON CONFLICT (table_name, column_name) DO NOTHING;
