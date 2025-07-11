## Запуск проекта

Для запуска всех сервисов выполните из корня проекта:

```bash
docker-compose up
```

# Состав сервисов

## 1. PostgreSQL Database

- **Образ:** `postgres:15`
- **Порт:** `5433 → 5432` (контейнер)
- **Назначение:** Хранение данных Keycloak
- **Данные:** Сохраняются в volume `postgres_data`

### Доступ

- **DB:** `keycloak`
- **User:** `keycloak`
- **Password:** `keycloak`

---

## 2. Keycloak (IAM)

- **Образ:** `quay.io/keycloak/keycloak:26.2`
- **Порт:** `9090 → 8080`
- **Режим:** `start-dev` (development)

### Доступ

- **Админ:** `admin / admin`

### Конфигурация

- Использует PostgreSQL
- Автоподключение к БД

---

## 3. Individuals API

- **Порт:** `8091`

### Технологии

- Spring Boot 3 (WebFlux)
- Spring Security + OAuth2
- JWT через Keycloak
- Lombok
- Spring Validation
- Actuator

### Тестирование

- Testcontainers
- Reactor Test
- JSONPath

