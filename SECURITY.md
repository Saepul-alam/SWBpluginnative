# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.1.x   | :white_check_mark: |
| < 0.1.0 | :x:                |

## Reporting a Vulnerability

We take the security of `swbnotif` seriously. If you discover a security vulnerability, please do NOT create a public issue.

Instead, please send an email to security@wahana.com with details including:
- Description of the vulnerability
- Steps to reproduce
- Affected platform (Android, iOS, Web) and OS versions
- Potential impact and mitigation ideas

You will receive an acknowledgement within 48 hours, followed by updates on remediation.

## Security Practices
- **No Sensitive Data Logging:** Payload contents and extra dictionaries are never printed to console logs in production builds.
- **Intent Boundary Hardening:** All PendingIntents on Android use `FLAG_IMMUTABLE` and explicit component destinations.
- **Receiver Access Control:** BroadcastReceivers (action receiver, alarm receiver, dismiss receiver) have `android:exported="false"`.
- **Payload Boundaries:** Maximum payload sizes and string lengths are strictly enforced on both native and JavaScript boundaries to prevent memory exhaustion / DoS attacks.

