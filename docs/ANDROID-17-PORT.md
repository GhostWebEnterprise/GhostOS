# Android 17 port

Android 17 is the GhostOS target, but it is a separate bring-up gate from the verified LineageOS 23.2 / Android 16 foundation.

## Migration gates

1. Establish a complete Android 17 base manifest.
2. Rebase/adapt the larry device tree and SM6375 common layer.
3. Port kernel/vendor interfaces required by the new platform.
4. Resolve SELinux, VINTF, sepolicy and compatibility changes from actual build errors.
5. Build `userdebug`.
6. Boot on larry.
7. Run device smoke tests and relevant CTS/VTS validation.
8. Only then label Android 17 support as bootable.

No Android 17 source revision is invented here; the current verified device foundation remains LineageOS 23.2.
