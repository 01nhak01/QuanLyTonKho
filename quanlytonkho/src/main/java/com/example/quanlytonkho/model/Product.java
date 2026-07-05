package com.example.quanlytonkho.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Product {
    private Long id;
    private String sku;
    private String name;
    private Double price;
    
    @JsonProperty("stock_quantity")
    private Integer stockQuantity;
    
    @JsonProperty("created_at")
    private String createdAt;

    public Product() {
    }

    public Product(Long id, String sku, String name, Double price, Integer stockQuantity, String createdAt) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
