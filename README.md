<p align="center">
  <img src="https://mapkluss.art/logo.png" alt="MapKluss" width="104">
</p>

# MapKluss Companion

Клиентский Fabric-мод для [MapKluss](https://mapkluss.art): облачная библиотека артов, Lens, AutoFrame, скан карт, трекер строительства и пошаговый Two-layer Builder.

Client-side Fabric companion for [MapKluss](https://mapkluss.art): cloud art library, Lens, AutoFrame, map scanning, build tracking, and the guided Two-layer Builder.

## Русский

### Возможности

- вход в аккаунт MapKluss через безопасное подтверждение устройства;
- просмотр облачных артов, версий, коллекций и файлов прямо в Minecraft;
- установка совместимых Litematic-файлов без ручного поиска загрузок;
- Lens — клиентское отображение актуального арта поверх рамок;
- AutoFrame и точное распознавание частей многостраничного арта;
- локальное сохранение превью, привязок и прогресса;
- Two-layer Builder для артов `1×1` и больших bundle-архивов с выбором части;
- скан карты и трекер необходимых материалов.

### Поддерживаемые версии

| Minecraft | Платформа | Java |
| --- | --- | --- |
| `1.21.11` | Fabric | `21+` |
| `1.21.8` | Fabric | `21+` |

Fabric Loader и Fabric API обязательны. Litematica и MaLiLib необязательны: без них Companion продолжает показывать этапы и подсветку, но не управляет размещением схематики.

### Установка

1. Скачайте JAR для своей версии Minecraft из [официальных Releases](https://github.com/SmetankaKluss/mapkluss-companion/releases).
2. Установите Fabric Loader и Fabric API.
3. Поместите JAR в папку `mods` своего экземпляра Minecraft.
4. Откройте меню MapKluss Companion в игре и подтвердите вход через сайт при необходимости.

Не устанавливайте JAR для `1.21.8` в `1.21.11` и наоборот.

### Сборка

Нужна Java 21. Сборки выполняются последовательно:

```bash
./gradlew --no-daemon clean test build

./gradlew --no-daemon clean test build \
  -Pminecraft_version=1.21.8 \
  -Pyarn_mappings=1.21.8+build.1 \
  -Ploader_version=0.19.3
```

Готовые JAR появляются в `build/libs`. CI проверяет обе версии Minecraft на каждом изменении.

## English

### Features

- secure MapKluss device authorization;
- cloud arts, revisions, collections, and files inside Minecraft;
- installation of compatible Litematic files;
- Lens client-side previews over item frames;
- AutoFrame and exact multi-tile map recognition;
- local preview, binding, and progress persistence;
- guided Two-layer Builder for `1×1` and multi-map bundles with tile selection;
- map scanning and material build tracking.

### Requirements

Minecraft `1.21.11` and `1.21.8` are supported as separate Fabric builds. Java 21, Fabric Loader, and Fabric API are required. Litematica and MaLiLib are optional.

Use the commands in the Russian build section to build both targets. The same commands run in CI.

## Privacy and network access

Companion is client-only. Account, Cloud, scan, and Lens actions communicate with MapKluss services only when their respective features are used. Two-layer coordinates, map bindings, cached previews, and local progress remain on the player's device. See [PRIVACY.md](PRIVACY.md) for the complete summary.

## Support and security

- Bugs and feature requests: [GitHub Issues](https://github.com/SmetankaKluss/mapkluss-companion/issues)
- Project news and guides: [Telegram](https://t.me/mapkluss)
- Security reports: [SECURITY.md](SECURITY.md)
- Support boundaries: [SUPPORT.md](SUPPORT.md)
- Web editor and account: [mapkluss.art](https://mapkluss.art)

## License

The source is public for transparency and review, but it is not open source. Copying, modification, redistribution, derivative works, and commercial use are not permitted without prior written permission. Unmodified official binary releases may be used for personal, non-commercial Minecraft gameplay. See [LICENSE](LICENSE).
