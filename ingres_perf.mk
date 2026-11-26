#
# SPDX-FileCopyrightText: 2026 kingD2N
# SPDX-License-Identifier: Apache-2.0
#
# CPU/GPU per aplikasi (IngresPerf). Pakai dari device.mk:
#   $(call inherit-product, packages/apps/IngresPerf/ingres_perf.mk)

INGRES_PERF_PATH ?= packages/apps/IngresPerf

PRODUCT_SOONG_NAMESPACES += \
    $(INGRES_PERF_PATH)

PRODUCT_PACKAGES += \
    IngresPerf \
    init.ingres_perf.rc
