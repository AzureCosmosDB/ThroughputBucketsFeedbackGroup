package com.azure.cosmosdb.models;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Product {
    private String id;
    private String type;
    private String brand;
    private String name;
    private String description;
    private double price;

    public Product() {}

    public Product(String id, String type, String brand, String name, String description, double price) {
        this.id = id;
        this.type = type;
        this.brand = brand;
        this.name = name;
        this.description = description;
        this.price = price;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public static final List<String> TYPE_LIST = Arrays.asList(
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
        "Trekking"
    );

    public static Product generateProduct(int id) {
        Random random = new Random();
        String type = TYPE_LIST.get(random.nextInt(TYPE_LIST.size()));
        String brand = "Brand" + (random.nextInt(100) + 1);
        String name = "Product" + id;
        String description = "Description for product " + id;
        double price = 50 + (450 * random.nextDouble());
        return new Product(String.valueOf(id), type, brand, name, description, price);
    }
}
