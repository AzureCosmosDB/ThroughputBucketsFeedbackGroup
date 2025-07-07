TENANT_SKU_MAPPING = [
    {"sku": "premium", "tenant": "tenant_7"},
    {"sku": "basic", "tenant": "tenant_3"},
    {"sku": "premium", "tenant": "tenant_4"},
    {"sku": "basic", "tenant": "tenant_6"},
    {"sku": "basic", "tenant": "tenant_5"},
    {"sku": "basic", "tenant": "tenant_2"},
    {"sku": "basic", "tenant": "tenant_1"},
]


def get_basic_sku_tenants():
    return [entry["tenant"] for entry in TENANT_SKU_MAPPING if entry["sku"] == "basic"]


def get_premium_sku_tenants():
    return [
        entry["tenant"] for entry in TENANT_SKU_MAPPING if entry["sku"] == "premium"
    ]
