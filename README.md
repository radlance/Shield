# Shield

Shield is an open-source Android VLESS VPN client built with Kotlin, Compose,
and sing-box.

## MVP features

- HTTPS subscriptions containing plain-text or Base64-encoded `vless://` links
- Remnawave-compatible Sing-box JSON subscriptions and device-limit headers
- Direct VLESS import from paste, manual entry, QR, Android share, or deep link
- VLESS TCP, WebSocket, and gRPC transports with TLS or REALITY
- Full-device IPv4/IPv6 VPN through Android `VpnService`
- Smart routing: blocked resources use VPN while Russian domains and networks connect directly
- Encrypted local profile storage and daily best-effort subscription refresh
- Foreground connection lifecycle and sanitized diagnostic export

## Build

The repository includes a pinned `libbox.aar`, so a normal Android build only
requires JDK 17 and the Android SDK:

```powershell
.\gradlew.bat assembleDebug
```

To rebuild the native core, install Go and Android NDK `28.0.13004108`, then run:

```powershell
.\scripts\build-libbox.ps1
```

The native build is pinned to sing-box v1.13.12 and includes only ARM64 and
x86_64. See `THIRD_PARTY_NOTICES.md` for provenance.

For subscription services with a device limit, Shield sends a random
per-installation identifier. It does not derive this value from Android ID,
IMEI, or other hardware identifiers.
