package my.application.individuals_api.integration;

import my.application.individuals_api.exception.AuthException;
import my.application.individuals_api.model.KeycloakRoleRepresentation;
import my.application.individuals_api.model.KeycloakUserCreateBody;
import my.application.individuals_api.model.KeycloakUserRepresentation;
import my.application.individuals_api.response.AuthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static my.application.individuals_api.utils.Messages.USER_ALREADY_EXISTS;
import static my.application.individuals_api.utils.Messages.USER_NOT_FOUND;

@Component
public class KeycloakIntegration {

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    private final WebClient webClient;
    private final KeycloakAdminTokenHolder adminTokenHolder;

    public KeycloakIntegration(@Value("${keycloak.auth-server-url}") String authServerUrl,
                               @Lazy KeycloakAdminTokenHolder adminTokenHolder) {
        this.webClient = WebClient.builder()
                .baseUrl(authServerUrl)
                .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
        this.adminTokenHolder = adminTokenHolder;
    }

    public Mono<AuthResponse> getUserToken(MultiValueMap<String, String> formData) {
        formData.add(KeycloakConstants.CLIENT_ID, clientId);
        formData.add(KeycloakConstants.CLIENT_SECRET, clientSecret);
        return webClient
                .post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(AuthResponse.class);
    }

    public Mono<Void> createUser(String email, String password) {
        return getAdminAccessToken()
                .flatMap(adminAccessToken -> webClient.post()
                        .uri("/admin/realms/{realm}/users", realm)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(KeycloakUserCreateBody.from(email, password))
                        .exchangeToMono(clientResponse -> {
                            if (clientResponse.statusCode() == HttpStatus.CONFLICT) {
                                return Mono.error(new AuthException(USER_ALREADY_EXISTS, HttpStatus.CONFLICT));
                            }

                            return clientResponse.bodyToMono(Void.class);
                        })
                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                                .filter(e -> Boolean.FALSE.equals(e instanceof AuthException))));
    }

    public Mono<KeycloakUserRepresentation> getUserById(String userId) {
        return getAdminAccessToken().flatMap(adminAccessToken -> webClient.get()
                        .uri("/admin/realms/{realm}/users/{userId}", realm, userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .retrieve()
                        .onRawStatus(status -> status == HttpStatus.NOT_FOUND.value(),
                                response -> Mono.error(new AuthException(USER_NOT_FOUND, HttpStatus.NOT_FOUND)))
                        .bodyToMono(KeycloakUserRepresentation.class))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(e -> Boolean.FALSE.equals(e instanceof AuthException)));
    }

    public Mono<List<String>> getRolesByUserId(String userId) {
        return getAdminAccessToken().flatMap(adminAccessToken -> webClient.get()
                        .uri("/admin/realms/{realm}/users/{userId}/role-mappings/realm", realm, userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<KeycloakRoleRepresentation>>() {
                        })
                        .map(roles -> roles.stream()
                                .map(KeycloakRoleRepresentation::name)
                                .collect(Collectors.toList())))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(e -> Boolean.FALSE.equals(e instanceof AuthException)));
    }

    protected Mono<AuthResponse> getNewAdminAccessToken() {
        return getUserToken(createAdminFormData());
    }

    private Mono<String> getAdminAccessToken() {
        return adminTokenHolder.getAdminToken();
    }

    private MultiValueMap<String, String> createAdminFormData() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add(KeycloakConstants.GRANT_TYPE, KeycloakConstants.PASSWORD);
        formData.add(KeycloakConstants.USERNAME, adminUsername);
        formData.add(KeycloakConstants.PASSWORD, adminPassword);
        return formData;
    }
}
