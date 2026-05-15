TENANT_SKU_MAPPING = [
    {"sku": "premium", "tenant": "tenant_7"},
    {"sku": "standard", "tenant": "tenant_3"},
    {"sku": "premium", "tenant": "tenant_4"},
    {"sku": "standard", "tenant": "tenant_6"},
    {"sku": "standard", "tenant": "tenant_5"},
    {"sku": "standard", "tenant": "tenant_2"},
    {"sku": "standard", "tenant": "tenant_1"},
]


def get_standard_sku_tenants():
    return [entry["tenant"] for entry in TENANT_SKU_MAPPING if entry["sku"] == "standard"]


def get_premium_sku_tenants():
    return [
        entry["tenant"] for entry in TENANT_SKU_MAPPING if entry["sku"] == "premium"
    ]
