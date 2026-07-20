## Summary

Describe the user-visible problem and the proposed change.

## Verification

- [ ] Minecraft `26.2`: `./gradlew --no-daemon clean test build -Pminecraft_version=26.2`
- [ ] Minecraft `1.21.11`: `./gradlew --no-daemon clean test build`
- [ ] Minecraft `1.21.8`: `./gradlew --no-daemon clean test build -Pminecraft_version=1.21.8`
- [ ] Minecraft `1.21.4`: `./gradlew --no-daemon clean test build -Pminecraft_version=1.21.4`
- [ ] No credentials, tokens, private user data, personal paths, logs, worlds, or local-instance files are included
- [ ] RU/EN behavior remains consistent when text or UI changes

## Rights

Do not submit third-party code or assets unless you own them or have documented permission. Contributions are accepted only after their licensing terms are agreed with the repository owner.
