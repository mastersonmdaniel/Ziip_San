package br.com.sigsan.platform.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAdminService {

    private final Keycloak keycloak;
    private final String masterRealm = "master";

    public void createTenantRealm(UUID tenantId, String schemaName, TenantProvisioningService.TenantProvisioningRequest request) {
        String realmName = "sigsan-" + schemaName;

        try {
            // Check if realm already exists
            if (keycloak.realms().realm(realmName).toRepresentation() != null) {
                log.info("Realm {} already exists, updating...", realmName);
                updateRealm(realmName, request);
                return;
            }

            // Create realm
            RealmRepresentation realm = new RealmRepresentation();
            realm.setRealm(realmName);
            realm.setDisplayName("SIGSAN - " + request.getDisplayName());
            realm.setEnabled(true);
            realm.setRegistrationAllowed(false);
            realm.setRememberMe(true);
            realm.setVerifyEmail(false);
            realm.setLoginWithEmailAllowed(true);
            realm.setRegistrationEmailAsUsername(false);
            realm.setLoginWithEmailAllowed(true);
            realm.setBruteForceProtected(true);
            realm.setMaxFailureWaitSeconds(900);
            realm.setMinimumQuickLoginWaitSeconds(60);
            realm.setQuickLoginCheckMilliSeconds(1000L);
            realm.setMaxDeltaTimeSeconds(43200);
            realm.setFailureFactor(30);

            // Password policies
            realm.setPasswordPolicy("length(8) and digits(1) and lowerCase(1) and upperCase(1) and specialChars(1) and notRecentlyUsed(5) and hashAlgorithm(pbkdf2-sha256) and hashIterations(100000) and expirePassword(90)");

            // Token settings
            realm.setAccessTokenLifespan(900); // 15 min
            realm.setSsoSessionMaxLifespan(36000); // 10 hours
            realm.setSsoSessionIdleTimeoutRememberMe(1209600); // 14 days
            realm.setSsoSessionIdleTimeout(1800); // 30 min
            realm.setOfflineSessionIdleTimeout(2592000); // 30 days
            realm.setOfflineSessionMaxLifespan(5184000); // 60 days
            realm.setAccessTokenLifespanForImplicitFlow(900);

            keycloak.realms().create(realm);
            log.info("Created realm: {}", realmName);

            // Create client for tenant
            createTenantClient(realmName, schemaName, request);

            // Create default roles
            createDefaultRoles(realmName);

            // Create admin user
            createAdminUser(realmName, request);

        } catch (Exception e) {
            log.error("Failed to create tenant realm: {}", realmName, e);
            throw new RuntimeException("Failed to create Keycloak realm for tenant", e);
        }
    }

    private void createTenantClient(String realmName, String schemaName, TenantProvisioningService.TenantProvisioningRequest request) {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId("sigsan-" + schemaName);
        client.setName("SIGSAN " + request.getDisplayName());
        client.setDescription("SIGSAN tenant client for " + request.getDisplayName());
        client.setEnabled(true);
        client.setPublicClient(false);
        client.setStandardFlowEnabled(true);
        client.setDirectAccessGrantsEnabled(true);
        client.setServiceAccountsEnabled(true);
        client.setAuthorizationServicesEnabled(true);
        client.setRedirectUris(List.of(
                "https://" + schemaName + ".sigsan.com.br/*",
                "https://app.sigsan.com.br/*",
                "http://localhost:3000/*"
        ));
        client.setWebOrigins(List.of(
                "https://" + schemaName + ".sigsan.com.br",
                "https://app.sigsan.com.br",
                "http://localhost:3000"
        ));
        client.setProtocolMappers(List.of(
                createMapper("tenant_id", "tenant_id", "String", schemaName),
                createMapper("tenant_schema", "tenant_schema", "String", schemaName)
        ));

        keycloak.realm(realmName).clients().create(client);
    }

    private org.keycloak.representations.idm.ProtocolMapperRepresentation createMapper(String name, String claim, String type, String value) {
        org.keycloak.representations.idm.ProtocolMapperRepresentation mapper = new org.keycloak.representations.idm.ProtocolMapperRepresentation();
        mapper.setName(name);
        mapper.setProtocol("openid-connect");
        mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
        mapper.setConfig(Map.of(
                "claim.name", claim,
                "claim.value", value,
                "jsonType.label", type,
                "access.token.claim", "true",
                "id.token.claim", "true",
                "userinfo.token.claim", "true"
        ));
        return mapper;
    }

    private void createDefaultRoles(String realmName) {
        String[] roles = {"ADMIN", "OPERATOR", "MANAGER", "VIEWER"};
        for (String roleName : roles) {
            RoleRepresentation role = new RoleRepresentation();
            role.setName(roleName);
            role.setDescription("SIGSAN " + roleName + " role");
            role.setClientRole(false);
            keycloak.realm(realmName).roles().create(role);
        }
    }

    private void createAdminUser(String realmName, TenantProvisioningService.TenantProvisioningRequest request) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.getAdminUsername());
        user.setEmail(request.getAdminEmail());
        user.setFirstName(request.getAdminName().split(" ")[0]);
        user.setLastName(request.getAdminName().substring(request.getAdminName().indexOf(" ") + 1));
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setRealmRoles(List.of("ADMIN", "OFFLINE_ACCESS"));

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getAdminPasswordHash());
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));

        try {
            keycloak.realm(realmName).users().create(user);
            log.info("Created admin user for realm: {}", realmName);
        } catch (Exception e) {
            log.warn("Admin user may already exist in realm: {}", realmName);
        }
    }

    private void updateRealm(String realmName, TenantProvisioningService.TenantProvisioningRequest request) {
        RealmRepresentation realm = keycloak.realm(realmName).toRepresentation();
        realm.setDisplayName("SIGSAN - " + request.getDisplayName());
        keycloak.realm(realmName).update(realm);
    }

    public void deleteTenantRealm(String schemaName) {
        String realmName = "sigsan-" + schemaName;
        try {
            keycloak.realm(realmName).remove();
            log.info("Deleted realm: {}", realmName);
        } catch (Exception e) {
            log.warn("Failed to delete realm: {}", realmName, e);
        }
    }

    public void disableTenantRealm(String schemaName) {
        String realmName = "sigsan-" + schemaName;
        try {
            RealmRepresentation realm = keycloak.realm(realmName).toRepresentation();
            realm.setEnabled(false);
            keycloak.realm(realmName).update(realm);
            log.info("Disabled realm: {}", realmName);
        } catch (Exception e) {
            log.warn("Failed to disable realm: {}", realmName, e);
        }
    }

    public void enableTenantRealm(String schemaName) {
        String realmName = "sigsan-" + schemaName;
        try {
            RealmRepresentation realm = keycloak.realm(realmName).toRepresentation();
            realm.setEnabled(true);
            keycloak.realm(realmName).update(realm);
            log.info("Enabled realm: {}", realmName);
        } catch (Exception e) {
            log.warn("Failed to enable realm: {}", realmName, e);
        }
    }
}