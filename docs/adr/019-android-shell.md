# ADR-019: Android shell and the gomobile boundary

Status: accepted

## Context

Censored users are mostly on phones. The Go client already has a user-space network stack (gVisor, ADR-009) that terminates TCP and UDP from a TUN device, so Android needs a device and a few hooks, not a new protocol stack.

## Decision

- Native Kotlin app in its own repo (`dotvpn-android`), Jetpack Compose UI, minSdk 26. The Go client is consumed as an AAR built by `gomobile bind` from `dotvpn-client/mobile`.
- Boundary (gomobile-safe types only): `ImportInvite(uri) -> config`, `Start(config, dataDir, tunFd, Protector)`, `Stop()`, `Status()`, `Endpoints(config)`. `Protector.Protect(fd)` is `VpnService.protect`.
- `VpnService` owns the device, addresses, routes, DNS (198.18.0.2 = fakedns in the Go stack), MTU 1380, IPv6 routed into the tunnel and dropped by the stack, and per-app split tunnelling (allowed or disallowed application lists). Go receives the detached descriptor and wraps it with wireguard-go.
- Every engine socket and the bypass resolver go through `Protector` via `Options.Control` (`tun.NewProtectedBypass`), replacing interface binding.
- Kill switch is the system's Always-on VPN with "Block connections without VPN". The Go `kill_switch` option is ignored on Android with a warning.
- Build tags: `sys_linux.go` is `linux && !android`; `sys_android.go` supplies no-op OS hooks and `DeviceFromFD`. The shared engine construction lives in `internal/app`, used by the CLI and `mobile`.
- The engine reconnects in the background (`keepConnected`) so airplane-mode and network switches recover without a new flow; the last working endpoint is persisted in app storage (`state.json`).
- No analytics or crash reporters; Go logs warnings only, nothing is persisted.

## Consequences

- The AAR needs `-checklinkname=0`: `github.com/wlynxg/anet` (via the snowflake carrier) links against `net.zoneCache`, which Go 1.23+ restricts. Revisit when anet or pion drops the linkname.
- AAR carries arm64 and x86_64 only (about 30 MB debug); 32-bit ARM devices are not supported.
- gVisor on a phone costs CPU and battery. UDP is on by default; measure on device before tuning.
- Release signing is CI-only; the repo holds no keys.

## Rejected

WebView or Flutter wrapper: worse VpnService integration and a bigger audit surface. Reusing wireguard-go's Android tunnel: no value, the stack is ours. Configuring routes from Go: Android forbids it for unprivileged apps.
