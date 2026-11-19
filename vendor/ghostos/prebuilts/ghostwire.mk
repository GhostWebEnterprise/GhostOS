# Development base-image builds may omit GhostWire explicitly. Never release those.
ifeq ($(GHOSTWIRE_INCLUDED),true)
PRODUCT_PACKAGES += GhostWire
endif
