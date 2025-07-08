package my.application.individuals_api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank @JsonProperty("refresh_token") String refreshToken
) {}
