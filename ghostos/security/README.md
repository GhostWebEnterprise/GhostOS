# GhostOS Security Layer

Security-first foundation for GhostOS. This directory contains policy and hardening specifications that are safe to integrate incrementally into the Android build.

## Goals

- SELinux enforcing by default
- least-privilege system services
- explicit app permission policy
- secure ADB/USB defaults
- Verified Boot / AVB integration
- rollback-aware release policy
- memory and exploit mitigations where supported by the Android/device base
- privacy-preserving defaults

## Rule

Security changes must be validated against the actual device build. No policy is marked production-ready until `larry` boots and CTS/VTS/device smoke tests are evaluated.
