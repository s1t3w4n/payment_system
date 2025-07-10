package my.application.individuals_api.integration;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class KeycloakAdminTokenHolder {

    private final KeycloakIntegration integration;
    private final ReentrantLock lock = new ReentrantLock();

    private final AtomicReference<String> token = new AtomicReference<>("");
    private final AtomicReference<Instant> expiredAt = new AtomicReference<>(Instant.ofEpochMilli(1));

    public KeycloakAdminTokenHolder(KeycloakIntegration integration) {
        this.integration = integration;
    }

    public Mono<String> getAdminToken() {
        if (expiredAt.get().minusSeconds(1).isAfter(Instant.now())) {
            return Mono.just(token.get());
        }
        return refreshToken();
    }

    private Mono<String> refreshToken() {
        return integration.getNewAdminAccessToken()
                .flatMap(tokenResponse -> {
                    lock.lock();
                    try {
                        token.set(tokenResponse.accessToken());
                        expiredAt.set(Instant.now().plusSeconds(tokenResponse.expiresIn()));
                        return Mono.just(tokenResponse.accessToken());
                    } finally {
                        lock.unlock();
                    }
                })
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)));
    }
}
