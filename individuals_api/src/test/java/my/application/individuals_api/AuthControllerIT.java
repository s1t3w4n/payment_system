package my.application.individuals_api;

import com.jayway.jsonpath.JsonPath;
import my.application.individuals_api.response.AuthResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static my.application.individuals_api.utils.Messages.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AuthControllerIT extends KeycloakTestBase {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Successful login should return valid access and refresh tokens")
    void login_ShouldReturnTokens() {
        // Given - подготовка тестовых данных и условий
        String loginRequest = """
        {
            "email": "user1@example.com",
            "password": "SecurePassword123"
        }
        """;

        // When - выполнение тестируемого действия
        byte[] responseBody = webTestClient.post().uri("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginRequest)
                .exchange()

                // Then - проверки результатов
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.access_token").isNotEmpty()
                .jsonPath("$.refresh_token").isNotEmpty()
                .returnResult()
                .getResponseBody();

        String responseString = new String(Objects.requireNonNull(responseBody), StandardCharsets.UTF_8);
        // Given - наличие valid access token
        String accessToken = JsonPath.parse(responseString).read("$.access_token");

        // When - запрос информации о текущем пользователе
        webTestClient.get().uri("/v1/auth/me")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()

                // Then - проверка корректности возвращаемых данных
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("user1@example.com");
    }

    @Test
    @DisplayName("Login should return 401 when email is correct but password is wrong")
    void login_ShouldReturn401_WhenPasswordWrong() {
        // Given
        String email = "user1@example.com";
        String wrongPassword = "wrongpassword";

        // When
        webTestClient.post().uri("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(String.format("""
                    {
                        "email": "%s",
                        "password": "%s"
                    }
                    """, email, wrongPassword))
                .exchange()

                // Then
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo(INVALID_EMAIL_OR_PASSWORD)
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    @DisplayName("Login should return 401 when user doesn't exist")
    void login_ShouldReturn401_WhenUserNotExist() {
        // Given
        String nonExistingEmail = "notexist@example.com";
        String anyPassword = "anypassword";

        // When
        webTestClient.post().uri("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(String.format("""
                    {
                        "email": "%s",
                        "password": "%s"
                    }
                    """, nonExistingEmail, anyPassword))
                .exchange()

                // Then
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo(INVALID_EMAIL_OR_PASSWORD)
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    @DisplayName("User registration should create new user and return auth tokens")
    void registration_ShouldCreateUser() {
        // Given
        String uniqueEmail = "testuser@example.com";
        String password = "testpassword";

        // When
        byte[] responseBody = webTestClient.post().uri("/v1/auth/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(String.format("""
                {
                    "email": "%s",
                    "password": "%s",
                    "confirm_password": "%s"
                }
                """, uniqueEmail, password, password))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.token_type").isEqualTo("Bearer")
                .jsonPath("$.access_token").exists()
                .returnResult()
                .getResponseBody();

        // Then
        String responseString = new String(Objects.requireNonNull(responseBody), StandardCharsets.UTF_8);
        String accessToken = JsonPath.parse(responseString).read("$.access_token");

        webTestClient.get().uri("/v1/auth/me")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo(uniqueEmail)
                .jsonPath("$.id").exists();
    }

    @Test
    @DisplayName("Registration should return 400 when password confirmation doesn't match")
    void registration_ShouldReturn400_WhenPasswordConfirmationMismatch() {
        // Given
        String email = "newuser@example.com";
        String password = "SecurePassword123";
        String differentConfirmation = "DifferentPassword123";

        // When
        webTestClient.post().uri("/v1/auth/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(String.format("""
                    {
                        "email": "%s",
                        "password": "%s",
                        "confirm_password": "%s"
                    }
                    """, email, password, differentConfirmation))
                .exchange()

                // Then
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo(PASSWORD_DOES_NOT_MATH)
                .jsonPath("$.status").isEqualTo(400);
    }

    @Test
    @DisplayName("Registration should return 409 when user already exists")
    void registration_ShouldReturn409_WhenUserExists() {
        // Given
        String existingEmail = "existing@example.com";
        String password = "SecurePassword123";

        // First registration
        webTestClient.post().uri("/v1/auth/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(String.format("""
                    {
                        "email": "%s",
                        "password": "%s",
                        "confirm_password": "%s"
                    }
                    """, existingEmail, password, password))
                .exchange()
                .expectStatus().isCreated();

        // When trying to register again
        webTestClient.post().uri("/v1/auth/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(String.format("""
                    {
                        "email": "%s",
                        "password": "NewPassword123",
                        "confirm_password": "NewPassword123"
                    }
                    """, existingEmail))
                .exchange()

                // Then
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(USER_ALREADY_EXISTS)
                .jsonPath("$.status").isEqualTo(409);
    }

    @Test
    @DisplayName("Refresh token endpoint should return new tokens when valid refresh token provided")
    void refreshToken_ShouldReturnNewTokens_WhenValidRefreshToken() {
        // Given - get initial refresh token
        String initialRefreshToken = webTestClient.post().uri("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                {
                    "email": "testuser@example.com",
                    "password": "testpassword"
                }
                """)
                .exchange()
                .expectStatus().isOk()
                .returnResult(AuthResponse.class)
                .getResponseBody()
                .map(AuthResponse::refreshToken)
                .blockFirst();

        assertNotNull(initialRefreshToken, "Refresh token должен быть получен при логине");

        // When - refresh token
        AuthResponse refreshedTokens = webTestClient.post().uri("/v1/auth/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                {
                    "refresh_token": "%s"
                }
                """.formatted(initialRefreshToken))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .returnResult(AuthResponse.class)
                .getResponseBody()
                .blockFirst();

        // Then
        assertNotNull(refreshedTokens, "Ответ от refresh-token не должен быть null");
        assertNotNull(refreshedTokens.accessToken(), "Новый access token должен быть в ответе");
        assertNotNull(refreshedTokens.refreshToken(), "Новый refresh token должен быть в ответе");

        webTestClient.get().uri("/v1/auth/me")
                .header("Authorization", "Bearer " + refreshedTokens.accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("testuser@example.com");

        webTestClient.get().uri("/v1/auth/me")
                .header("Authorization", "Bearer " + initialRefreshToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Refresh token should return 401 when invalid refresh token provided")
    void refreshToken_ShouldReturn401_WhenInvalidToken() {
        // Given
        String invalidToken = "invalid.refresh.token";

        // When
        webTestClient.post().uri("/v1/auth/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(String.format("""
                    {
                        "refresh_token": "%s"
                    }
                    """, invalidToken))
                .exchange()

                // Then
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo(INVALID_OR_EXPIRED_REFRESH_TOKEN)
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    @DisplayName("Current user endpoint should return authenticated user's information")
    void getCurrentUser_ShouldReturnUserInfo() {
        // Given - login to get token
        String token = webTestClient.post().uri("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "email": "testuser@example.com",
                        "password": "testpassword"
                    }
                    """)
                .exchange()
                .returnResult(AuthResponse.class)
                .getResponseBody()
                .map(AuthResponse::accessToken)
                .blockFirst();

        // When
        webTestClient.get().uri("/v1/auth/me")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()

                // Then
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("testuser@example.com");
    }

    @Test
    @DisplayName("Should return 401 when no token provided")
    void getCurrentUser_NoToken_ShouldReturnUnauthorized() {
        // Given - no token

        // When
        webTestClient.get().uri("/v1/auth/me")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()

                // Then
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo(INVALID_OR_EXPIRED_ACCESS_TOKEN)
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    @DisplayName("Should return 401 when invalid token provided")
    void getCurrentUser_InvalidToken_ShouldReturnUnauthorized() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        webTestClient.get().uri("/v1/auth/me")
                .header("Authorization", "Bearer " + invalidToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()

                // Then
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo(INVALID_OR_EXPIRED_ACCESS_TOKEN)
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    @DisplayName("Should return 401 when expired token provided")
    void getCurrentUser_ExpiredToken_ShouldReturnUnauthorized() {
        // Given
        String expiredToken = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI0WExkTTl0UzZwbHZ4MUtpd2hFUHZjQ0lscmFVSHlpOUY0ZjJTOTJnVTNBIn0.eyJleHAiOjE3NTAxODIxNTMsImlhdCI6MTc1MDE4MTg1MywianRpIjoib25ydHJ0OmRiNjhlZjExLWI2ODUtNGU3Yy1iZDJmLWZlYmM5YjJhYTczOCIsImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTA5MC9yZWFsbXMvbXktYXBwLXJlYWxtIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6IjA3NzQ0OGFkLTlkMjMtNDdiOC1iOGUxLTA5YjhlNDVlM2QwMSIsInR5cCI6IkJlYXJlciIsImF6cCI6Im15LWFwcC1jbGllbnQiLCJzaWQiOiIzYTM0NWI3OC0wZWMwLTQ1ZmUtYjE1ZS03YWI2ODU3OWUxY2QiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbIi8qIl0sInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJkZWZhdWx0LXJvbGVzLW15LWFwcC1yZWFsbSIsIm9mZmxpbmVfYWNjZXNzIiwidW1hX2F1dGhvcml6YXRpb24iXX0sInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6InByb2ZpbGUgZW1haWwiLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsInJvbGVzIjpbIlJPTEVfbWFuYWdlLWFjY291bnQiLCJST0xFX3ZpZXctcHJvZmlsZSIsIlJPTEVfbWFuYWdlLWFjY291bnQtbGlua3MiXSwicHJlZmVycmVkX3VzZXJuYW1lIjoidXNlckBleGFtcGxlLmNvbSIsImVtYWlsIjoidXNlckBleGFtcGxlLmNvbSJ9.c9dWiUZ1cb8kA4SmaAFCY4FmDnBrhC7cT7I5yTaHlOko4tCoRkCfdyNsekF139VYel9As2IeegzYbdPvTVMusuUpjgj55IV8kFNXVl-oNovfp4E4KJO43CetUjNIs5kuP_tP3KIR9LpZPn-l6E-4Bq1MktQAp2yWjNJArEdWXLT37YTZ9PoYWi-Z6-IKyfR4FbcaqLooLevVZMbn71JNjCCRYMJKGbf6gcqFdEiSo46kj8nw_fsEMbud-SgKLVBluszqGpJC63OImM8GmOogTpUF_BOOLN8P0gig-lnR4DryHme64eU2Yfxi382y7lHgEp51kNL5F9ajSLhn0woPTA";

        // When
        webTestClient.get().uri("/v1/auth/me")
                .header("Authorization", "Bearer " + expiredToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()

                // Then
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo(INVALID_OR_EXPIRED_ACCESS_TOKEN)
                .jsonPath("$.status").isEqualTo(401);
    }
}