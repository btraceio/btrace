# Security policy

## Reporting a vulnerability

Please do not report security vulnerabilities in public issues, pull requests, or discussions.

Use GitHub's private vulnerability reporting for the [BTrace repository](https://github.com/btraceio/btrace/security/advisories/new). Include:

- the affected BTrace version or commit;
- the impact and likely attack scenario;
- reproduction steps or a proof of concept;
- affected modules, distributions, or deployment modes; and
- any suggested mitigation.

Please remove credentials, personal data, and other sensitive information from reports unless it is required to demonstrate the issue.

The maintainers will acknowledge the report, investigate it, and coordinate disclosure and a fix when appropriate.

## Supported versions

| Version | Status |
| --- | --- |
| 3.0.x | Supported; security fixes land here first |
| 2.2.x | Security fixes only for the newest 2.2.x release, on a best-effort basis until 3.1.0 |
| < 2.2 | Not supported |

Security fixes are prioritized for the latest maintained release. When possible, upgrade to the latest BTrace release before reporting a suspected vulnerability.
