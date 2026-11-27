#
# SPDX-FileCopyrightText: 2026 kingD2N
# SPDX-License-Identifier: Apache-2.0
#
# Ingres Parts (POCO F4 GT / Redmi K50 Gaming). Pakai dari device.mk:
#   INGRES_PARTS := true
#   $(call inherit-product, packages/apps/IngresParts/ingres_parts.mk)
# CPU/GPU per Aplikasi ikut terpasang; matikan dengan INGRES_PARTS_PERF := false
# (di device.mk dan BoardConfig.mk, sebelum include).

INGRES_PARTS_PATH ?= packages/apps/IngresParts
INGRES_PARTS_PERF ?= true

PRODUCT_SOONG_NAMESPACES += \
    $(INGRES_PARTS_PATH)

PRODUCT_PACKAGES += \
    IngresParts \
    init.ingres_parts.rc

ifeq ($(INGRES_PARTS_PERF),true)
PRODUCT_PACKAGES += \
    IngresPerf \
    init.ingres_perf.rc
endif
