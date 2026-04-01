# WhiteVPN

`WhiteVPN` — Android VPN-клиент с максимально простым экраном: один центральный переключатель, два мобильных режима подписки, виджет и foreground-уведомление.

## Что уже реализовано

- большой центральный круг для старта и остановки VPN;
- автоматическая темная тема;
- два режима подписки: `1` и `2`;
- автоматический выбор сервера в авто-режиме;
- виджет включения и выключения VPN;
- foreground-уведомление со статусом, пингом, временем сессии и кнопкой остановки;
- debug APK, которые собираются и публикуются только через GitHub Actions.

## Подписки

Базовый источник в проекте:

`https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/WHITE-CIDR-RU-all.txt`

Внутри приложения режимы переключают мобильные подборки, которые сохраняются в настройках.

## Техническая база

- package name: `com.white.vpn`
- UI: Jetpack Compose + Material 3
- VPN core: `libv2ray`
- widget: App Widget
- уведомления и сервис: foreground service + `VpnService`

Основная Android-конфигурация лежит в [`app/build.gradle.kts`](app/build.gradle.kts), а GitHub workflow сборки — в [`.github/workflows/android.yml`](.github/workflows/android.yml).

## Сборки

Локальная сборка в этом процессе не используется. APK публикуются GitHub Actions workflow-ом и выкладываются в GitHub Releases.

Смотреть свежие сборки:

- `Actions` tab репозитория
- `Releases` tab репозитория
