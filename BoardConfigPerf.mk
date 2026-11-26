#
# SPDX-FileCopyrightText: 2026 kingD2N
# SPDX-License-Identifier: Apache-2.0
#
# SELinux untuk IngresPerf. Pakai dari BoardConfig.mk (paling bawah):
#   -include packages/apps/IngresPerf/BoardConfigPerf.mk

INGRES_PERF_PATH ?= packages/apps/IngresPerf

# Android 17: app platform dengan target SDK < 37 berjalan sebagai platform_app_36.
# Deteksi dari sepolicy sistem supaya ROM Android 16 tidak terpengaruh.
INGRES_PERF_A17 := $(shell grep -qs 'domain=platform_app_36' system/sepolicy/private/seapp_contexts && echo true)

# Properti sys.ingres.perf.* (app IngresPerf -> init rc)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(INGRES_PERF_PATH)/sepolicy/private
ifeq ($(INGRES_PERF_A17),true)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(INGRES_PERF_PATH)/sepolicy/private-a17
endif

# Node GPU kgsl (tipe vendor): hanya dipasang bila tipenya didefinisikan di source,
# karena aturan dengan tipe yang tidak ada merusak build sepolicy vendor.
INGRES_PERF_KGSL_DIRS := $(wildcard device/qcom device/xiaomi hardware/qcom-caf)
INGRES_PERF_KGSL_TYPE := $(if $(INGRES_PERF_KGSL_DIRS),$(shell grep -rqs --include=*.te -e '^type vendor_sysfs_kgsl' $(INGRES_PERF_KGSL_DIRS) && echo true))
ifeq ($(INGRES_PERF_KGSL_TYPE),true)
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_PERF_PATH)/sepolicy/vendor
ifeq ($(INGRES_PERF_A17),true)
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_PERF_PATH)/sepolicy/vendor-a17
endif
endif
