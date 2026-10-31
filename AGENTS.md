# dotvpn-android

Native Android app (Kotlin, Jetpack Compose, minSdk 26) around the Go client. Package `dot.vpn.payam`. Depends on `dotvpn-client` (the `mobile` package, built with gomobile into `app/libs/dotvpn.aar`) and, through it, `dotvpn-wire`.

## Commands

```sh
make vet      # GOOS=android go vet + mobile tests (Go only, no SDK)
make aar      # gomobile bind -> app/libs/dotvpn.aar
make apk      # debug APK
make test     # JVM unit tests
make release  # signed with DOTVPN_KEYSTORE* env vars (CI secrets)
```

Needs JDK 17, Android SDK platform 35, NDK r26+, gomobile. Sibling repos live next to this one.

## Invariants

- The Go side never configures the OS. `VpnService` owns the TUN device, routes, DNS and split tunnel; Go reads and writes packets on the descriptor (ADR-019).
- Every socket the Go client opens goes through `Protector.protect` first, or it loops into the VPN.
- Kill switch is the system's Always-on VPN with "Block connections without VPN"; the app only links to the setting.
- No analytics, no crash reporting, no logs persisted. Go logs warnings only. Config holds the account key: `filesDir` only, backups disabled.
- Keystores and passwords never enter the repo; release signing is CI secrets.
- Protocol and wire changes belong in `dotvpn-wire`, not here.

## Docs

`docs/adr/019-android-shell.md`. Architecture and ADR index: `../dotvpn-lab/docs/`.
