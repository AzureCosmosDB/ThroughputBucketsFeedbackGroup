import random
import string

PRODUCT_TYPES = [
    "Accessories",
    "Apparel",
    "Bags",
    "Climbing",
    "Cycling",
    "Electronics",
    "Footwear",
    "Home",
    "Jackets",
    "Navigation",
    "Ski/boarding",
    "Trekking",
]


def get_random_product_type():
    return random.choice(PRODUCT_TYPES) if PRODUCT_TYPES else None


def get_all_product_types():
    return PRODUCT_TYPES


class Product:
    def __init__(self, id, Type, Brand, Name, Description, Price, tenant, sku):
        self.id = id
        self.Type = Type
        self.Brand = Brand
        self.Name = Name
        self.Description = Description
        self.Price = Price
        self.tenant = tenant
        self.sku = sku

    @staticmethod
    def generate_product(tenant=None, sku=None):
        id = str(random.randint(100000, 1000000))
        Type = get_random_product_type()
        Brand = random.choice(
            ["UrbanX", "Acme", "Globex", "Soylent", "Initech", "Umbrella"]
        )
        Name = random.choice(
            [
                "Plant Rise Accessories",
                "Super Gadget",
                "Comfy Shirt",
                "Smart Lamp",
                "Fun Puzzle",
                "Bestseller Book",
            ]
        )
        Description = " ".join(
            random.choices(string.ascii_letters + " ", k=100)
        ).strip()
        Price = round(random.uniform(10, 500), 2)
        tenant = tenant if tenant is not None else f"tenant_{random.randint(1, 10)}"
        sku = sku if sku is not None else random.choice(["basic", "premium"])
        return Product(id, Type, Brand, Name, Description, Price, tenant, sku)

    def to_dict(self):
        return {
            "id": self.id,
            "Type": self.Type,
            "Brand": self.Brand,
            "Name": self.Name,
            "Description": self.Description,
            "Price": self.Price,
            "tenant": self.tenant,
            "sku": self.sku,
        }
