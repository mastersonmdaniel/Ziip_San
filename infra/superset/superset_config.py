# ED-47 (ADR-0027): configuracao do Apache Superset embarcado do ZiipSan.
#
# Regras que este arquivo materializa (excecao do Superset no ADR-0010):
#   * SSO pelo Keycloak (OIDC, realm ziipsan);
#   * modo SINCRONO: SQLLAB_ASYNC desligado - sem Celery e sem Redis;
#   * metadados no PostgreSQL dedicado do perfil (nunca o primario dos
#     tenants); as DATABASES de dados sao registradas por tenant, com a
#     role de banco do tenant, apontando SO para a replica - a guarda
#     ReplicaOnlyGuard (Java) confere cada uma contra o endpoint do
#     primario, e este arquivo nao cadastra database alguma;
#   * nenhum segredo neste arquivo: tudo vem do ambiente (cofre/.env
#     local), injetado pelo compose.
import os

SECRET_KEY = os.environ["ZIIPSAN_SUPERSET_SECRET_KEY"]

# Metadados do Superset no PostgreSQL do perfil "bi" (servico
# superset-db do compose): definicoes e usuarios espelhados do SSO.
SQLALCHEMY_DATABASE_URI = (
    "postgresql+psycopg2://superset:"
    + os.environ.get("ZIIPSAN_SUPERSET_DB_PASSWORD", "")
    + "@superset-db:5432/superset"
)

# --- Marca do produto -------------------------------------------------
# Nome do produto (fato). Logotipo e tema visual vem do pacote de marca
# da P0.34, montados em /app/brand pela fiacao; enquanto o mount nao
# existir, APP_ICON fica no padrao e o BI nao deve ser apresentado como
# pronto (o SupersetBranding do modulo recusa habilitar sem os ativos).
APP_NAME = "ZiipSan"
APP_ICON = "/static/assets/images/superset-logo-horiz.png"  # substituir pelo ativo da P0.34 na fiacao

# --- SSO Keycloak (OIDC) ----------------------------------------------
from flask_appbuilder.security.manager import AUTH_OAUTH  # noqa: E402

AUTH_TYPE = AUTH_OAUTH
AUTH_USER_REGISTRATION = True
# O papel interno inicial e o de menor privilegio de leitura; a
# elevacao de papeis do Superset e ato administrativo registrado, e o
# TENANT do usuario vem da claim resolvida pela aplicacao (a politica
# BiAccessPolicy confere usuario x fonte; a role de banco e a barreira
# final). Nenhum papel de escrita em dataset de outro tenant existe
# porque cada database usa a role do proprio tenant.
AUTH_USER_REGISTRATION_ROLE = "Gamma"

_KEYCLOAK = os.environ["ZIIPSAN_KEYCLOAK_ISSUER"].rstrip("/")
_REALM = _KEYCLOAK + "/realms/ziipsan"

OAUTH_PROVIDERS = [
    {
        "name": "keycloak",
        "icon": "fa-key",
        "token_key": "access_token",
        "remote_app": {
            "client_id": os.environ["ZIIPSAN_SUPERSET_OIDC_CLIENT_ID"],
            "client_secret": os.environ["ZIIPSAN_SUPERSET_OIDC_CLIENT_SECRET"],
            "api_base_url": _REALM + "/protocol/openid-connect/",
            "access_token_url": _REALM + "/protocol/openid-connect/token",
            "authorize_url": _REALM + "/protocol/openid-connect/auth",
            "request_token_url": None,
            "client_kwargs": {"scope": "openid profile email"},
        },
    }
]

# --- Modo sincrono (sem Celery/Redis) ---------------------------------
SQLLAB_ASYNC = False
FEATURE_FLAGS = {
    # Alertas/relatorios agendados do Superset exigiriam Celery: ficam
    # desligados por decisao do ADR-0010. Agendamento de relatorio do
    # produto e o da P3.22 (worker proprio), nao o do Superset.
    "ALERT_REPORTS": False,
}
