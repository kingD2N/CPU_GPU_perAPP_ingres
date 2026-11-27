#
# SPDX-FileCopyrightText: 2026 kingD2N
# SPDX-License-Identifier: Apache-2.0
#
# SELinux untuk Ingres Parts dan CPU/GPU per Aplikasi. Pakai dari BoardConfig.mk (paling bawah):
#   -include packages/apps/IngresParts/BoardConfigParts.mk

INGRES_PARTS_PATH ?= packages/apps/IngresParts
INGRES_PARTS_APP ?= true
INGRES_PARTS_PERF ?= true

# Android 17: app platform dengan target SDK < 37 berjalan sebagai platform_app_36.
# Deteksi dari sepolicy sistem supaya ROM Android 16 tidak terpengaruh.
INGRES_PARTS_A17 := $(shell grep -qs 'domain=platform_app_36' system/sepolicy/private/seapp_contexts && echo true)

INGRES_PARTS_SEPOLICY_SRC := $(wildcard device/qcom device/xiaomi hardware/qcom-caf)
ingres-parts-type = $(if $(INGRES_PARTS_SEPOLICY_SRC),$(shell grep -rqsE --include=*.te "^type[[:space:]]+$(1)[[:space:],;]" $(INGRES_PARTS_SEPOLICY_SRC) && echo true))

ifeq ($(INGRES_PARTS_APP),true)
# Properti sys.ingres.parts.* (app IngresParts -> init rc) dan sconfig thermal (sysfs_thermal)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(INGRES_PARTS_PATH)/sepolicy/parts/private
ifeq ($(INGRES_PARTS_A17),true)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(INGRES_PARTS_PATH)/sepolicy/parts/private-a17
endif

# Node vendor (baterai, sentuhan, layar, FPS): labelnya beda per ROM, jadi aturannya dipecah
# per tipe dan tiap folder hanya dipasang bila tipenya didefinisikan di source (aturan dengan
# tipe yang tidak ada merusak build sepolicy vendor).

# Baterai + vendor_sysfs_graphics (overlay FPS; disp_param di AOSPA)
ifeq ($(call ingres-parts-type,vendor_sysfs_battery_supply)$(call ingres-parts-type,vendor_sysfs_graphics),truetrue)
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_PARTS_PATH)/sepolicy/parts/vendor
ifeq ($(INGRES_PARTS_A17),true)
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_PARTS_PATH)/sepolicy/parts/vendor-a17
endif
else
$(warning IngresParts: tipe vendor_sysfs_battery_supply/vendor_sysfs_graphics tidak ada, batas cas, Flat dan overlay FPS tidak akan jalan)
endif

# Sentuhan 480/240 Hz (bump_sample_rate). Bila device tree belum memberi label pada node ini
# (mis. AOSPA calcite), label vendor_sysfs_touch ditambahkan dari sepolicy/parts/vendor-touch-genfs.
ifeq ($(call ingres-parts-type,vendor_sysfs_touch),true)
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_PARTS_PATH)/sepolicy/parts/vendor-touch
INGRES_PARTS_TOUCH_LABELED := $(shell grep -rqs --include=genfs_contexts 'touch_dev/bump_sample_rate' device/xiaomi && echo true)
ifneq ($(INGRES_PARTS_TOUCH_LABELED),true)
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_PARTS_PATH)/sepolicy/parts/vendor-touch-genfs
endif
else
$(warning IngresParts: tipe vendor_sysfs_touch tidak ada, sentuhan 480 Hz tidak akan jalan)
endif

# Mode Flat di LineageOS: disp_param berlabel vendor_sysfs_displayfeature
ifeq ($(call ingres-parts-type,vendor_sysfs_displayfeature),true)
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_PARTS_PATH)/sepolicy/parts/vendor-displayfeature
endif
endif

# CPU/GPU per Aplikasi: properti sys.ingres.perf.* (app IngresPerf -> init rc)
ifeq ($(INGRES_PARTS_PERF),true)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(INGRES_PARTS_PATH)/sepolicy/perf/private
ifeq ($(INGRES_PARTS_A17),true)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(INGRES_PARTS_PATH)/sepolicy/perf/private-a17
endif
# Node GPU kgsl (tipe vendor)
INGRES_PERF_KGSL_TYPE := $(call ingres-parts-type,vendor_sysfs_kgsl)
ifeq ($(INGRES_PERF_KGSL_TYPE),true)
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_PARTS_PATH)/sepolicy/perf/vendor
ifeq ($(INGRES_PARTS_A17),true)
BOARD_VENDOR_SEPOLICY_DIRS += $(INGRES_PARTS_PATH)/sepolicy/perf/vendor-a17
endif
endif
endif
