# Perdonus VPN

Простой Android VPN-клиент с:

- одной большой кнопкой подключения,
- ручным обновлением подписки,
- списком серверов с режимом `Авто`,
- виджетом включения/выключения,
- foreground-уведомлением с пингом и временем работы,
- сборкой через GitHub Actions.

По умолчанию используется подписка:

`https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/WHITE-CIDR-RU-all.txt`

Для VPN-ядра приложение использует `libv2ray.aar`, который автоматически скачивается при сборке.

