# Android 17 port

Android 17 is the GhostOS target, but it is a separate bring-up gate from the repository's LineageOS 23.2 / Android 16 reference baseline.

**Status boundary:** the LineageOS 23.2 manifest is a tracked reference baseline, not proof of a bootable larry build. The candidate LineageOS 24.0 / Android 17 manifest and pinned source revisions are tracked in [the larry bring-up PR](https://github.com/GhostWebEnterprise/GhostOS/pull/9), but a full source sync and product build have not been verified. No Android 17 bootability or device compatibility is claimed.

## Migration gates

1. Establish and review the complete Android 17 base manifest.
2. Rebase/adapt the larry device tree and SM6375 common layer.
3. Port kernel/vendor interfaces required by the new platform.
4. Resolve SELinux, VINTF, sepolicy and compatibility changes from actual build errors.
5. Complete a real LineageOS source sync and verify all six pinned project worktrees.
6. Run `lunch ghostos_larry-userdebug` and the full Soong/product build.
7. Inspect target-files and produce a flashable development image.
8. Boot on the actual larry handset.
9. Run device smoke tests and relevant CTS/VTS validation.
10. Only then label Android 17 support as bootable.

No Android 17 source revision is invented here. The candidate revisions in the larry bring-up PR must be reviewed against a successful full sync and product build before they are treated as an integrated foundation.

## Evidence still required

- Full LineageOS 24.0 `repo sync` and project-worktree verification.
- `lunch ghostos_larry-userdebug` and a successful Soong/product build.
- target-files and flashable image inspection.
- Production AVB/signing configuration and verification, separate from development test keys.
- Compatibility checks against the actual handset firmware.
- Physical larry boot and hardware validation.

Host tests, Android SDK resource linking, Java compilation, manifest checks and mocked bootstrap tests do not substitute for these gates.
