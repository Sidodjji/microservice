# Car sharing: микросервисы

Перенос `car_sharing_module_monolith` на Maven, MySQL и Kafka. Java 17+, Spring Boot 4.1.1 (версия исходного проекта). Исходный монолит не изменяется и не нужен для запуска.

## Запуск

Нужен работающий Docker Desktop / Docker Engine с Compose:

```powershell
Copy-Item .env.example .env
# Задайте свой JWT_SECRET (минимум 32 байта) в .env.
docker compose up -d --build
```

API: http://localhost:8080/api/cars. Swagger: http://localhost:8080/swagger-ui.html.
Полная исходная спецификация: http://localhost:8080/v3/api-docs.
Swagger также предлагает отдельные спецификации четырёх сервисов.
Сервисы запускаются после миграций; gateway ждёт их health checks.
Для другого порта задайте `HTTP_PORT` в `.env`.

```powershell
docker compose ps
docker compose logs -f fleet-service billing-service
docker compose down
```

`down` сохраняет данные MySQL и Kafka в именованных томах. Конфигурация рассчитана на локальную разработку: один брокер, один сервер MySQL с четырьмя отдельными БД и пользователями. Наружу на loopback опубликован только gateway. Пароли локальных БД находятся в `infra/mysql/01-databases.sql` и `compose.yaml`; при смене паролей существующего тома необходим `ALTER USER`, init-скрипт повторно не запускается.

## Сервисы

| Maven-модуль | Владелец данных | API |
|---|---|---|
| `fleet-service` | `fleet`: cars, bookings, outbox_events | `/api/cars`, `/api/bookings` |
| `identity-service` | `identity`: users | `/api/auth` |
| `billing-service` | `billing`: billing_records | `/api/billing` |
| `telemetry-service` | `telemetry`: car_locations | `/api/telemetry` |
| `contracts` | Только DTO события; без Spring/JPA | `BookingFinishedEvent` |

Каталог и бронирования оставлены внутри одного сервиса аренды, поскольку их изменения в монолите атомарны. Нет межсервисных JPA-ссылок или прямого доступа к чужим БД. Telemetry проверяет существование автомобиля по HTTP; billing получает завершённые аренды по Kafka.

`fleet-service` атомарно завершает аренду, освобождает автомобиль и записывает событие в outbox. Relay отправляет событие с ключом bookingId, ждёт подтверждения Kafka и помечает публикацию. После сбоя возможна повторная доставка; уникальный bookingId и проверка в billing предотвращают второй счёт. Kafka offset подтверждается после успешной транзакции billing. Ошибки обработки повторяются без потери сообщения.

**Счёт появляется асинхронно.** После успешного `/finish` клиент должен опрашивать `/api/billing/history` до появления bookingId. При недоступном брокере завершение аренды продолжает работать; событие хранится в MySQL. API-форматы и правила расчёта сохранены.

## Проверки

Без Docker, Java 17+:

```powershell
.\mvnw.cmd verify
```

На запущенном Compose-стенде, Python 3.10+ (только стандартная библиотека):

```powershell
python scripts/acceptance.py
python scripts/acceptance.py --recovery
```

`--recovery` временно останавливает Kafka и billing **этого Compose-проекта**, затем запускает их в `finally`. Проверяются HTTP-контракты, валидация, JWT, конкурентное бронирование/завершение/оплата, сохранение GPS, повтор события и восстановление после отказов. Проверки создают уникальные тестовые записи, существующие данные не удаляют. При другом порте используйте `--url http://127.0.0.1:8090`.

Сравнение с неизменённой копией монолита:

```powershell
.\scripts\start-reference.ps1 -MonolithPath C:\Users\Sidodji\IdeaProjects\car_sharing_module_monolith
python scripts/compare_monolith.py
docker compose -f target/monolith-reference/compose.yaml down
```

Копия и её PostgreSQL запускаются отдельно на порту 18080. Скрипт сравнивает HTTP-сценарии, совместимость старого JWT, все OpenAPI-операции и DTO. Генерируемые id, токены и временные метки нормализуются. `infra/openapi.json` — снимок полного контракта; при изменении API его необходимо обновить, сравнение обнаруживает рассогласование.

## Настройки и сборка

Каждый сервис — отдельный executable JAR; `mvnw.cmd package` собирает все четыре.
Переменные: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `SERVER_PORT`; для fleet/billing — `KAFKA_BOOTSTRAP_SERVERS`; для telemetry — `APP_FLEET_URL`. JWT_SECRET должен совпадать у всех сервисов и у монолита, если нужны ранее выданные JWT. Время хранится в UTC, миграции применяет Flyway, Hibernate проверяет схему (`ddl-auto=validate`).

Корневой `src/` — сохранённый исходный каркас текущего проекта; Maven-агрегатор (`packaging=pom`) его не компилирует. Рабочий код находится в перечисленных модулях.

Карта API и нюансы совместимости: [docs/migration.md](docs/migration.md).
Результаты проверки: [docs/verification.md](docs/verification.md).