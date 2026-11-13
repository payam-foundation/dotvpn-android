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

Needs JDK 17, Android SDK platform 37, NDK r28+ (for 16 KB page-size–aligned `libgojni.so`; still runs on older 4 KB devices), gomobile. Sibling repos live next to this one. `make check-16kb` verifies AAR ELF alignment.

## Invariants

- The Go side never configures the OS. `VpnService` owns the TUN device, routes, DNS and split tunnel; Go reads and writes packets on the descriptor (ADR-025).
- Every socket the Go client opens goes through `Protector.protect` first, or it loops into the VPN.
- Kill switch is the system's Always-on VPN with "Block connections without VPN"; the app only links to the setting.
- No analytics. Crash reporting is opt-in only (off by default; Sentry `dv-android`). No logs persisted. Go logs warnings only. Config holds the account key: `filesDir` only, backups disabled.
- Keystores and passwords never enter the repo; release signing is CI secrets.
- Protocol and wire changes belong in `dotvpn-wire`, not here.

## Docs

Design notes: README.

## Flow

Pull requests only, never push to `main`. PR title is a Conventional Commit, commits are signed off (`git commit -s`), merges are squash-only. Releases come from release-please. See `CONTRIBUTING.md`.
