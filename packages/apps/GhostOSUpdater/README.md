# GhostOS Updater

First-party OTA client specification.

Planned responsibilities:
- check signed GhostOS update metadata
- verify release/device compatibility
- verify payload/signature metadata before installation
- hand off installation to the platform update engine
- report success/failure and reboot state

OTA is not considered production-ready until signed update artifacts are generated and tested on `larry`.
