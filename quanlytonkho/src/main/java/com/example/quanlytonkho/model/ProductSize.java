package com.example.quanlytonkho.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSize {
    private Long id;
    
    @JsonProperty("product_id")
    private Long productId;
    
    private String size;
    
    @JsonProperty("stock_quantity")
    private Integer stockQuantity;

    @JsonProperty("stock_storage")
    private Integer stockStorage;

    @JsonProperty("stock_store")
    private Integer stockStore;

    public ProductSize() {
    }

    public ProductSize(Long id, Long productId, String size, Integer stockQuantity) {
        this.id = id;
        this.productId = productId;
        this.size = size;
        this.stockQuantity = stockQuantity;
    }

    public ProductSize(Long id, Long productId, String size, Integer stockQuantity, Integer stockStorage, Integer stockStore) {
        this.id = id;
        this.productId = productId;
        this.size = size;
        this.stockQuantity = stockQuantity;
        this.stockStorage = stockStorage;
        this.stockStore = stockStore;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Integer getStockStorage() {
        return stockStorage;
    }

    public void setStockStorage(Integer stockStorage) {
        this.stockStorage = stockStorage;
    }

    public Integer getStockStore() {
        return stockStore;
    }

    public void setStockStore(Integer stockStore) {
        this.stockStore = stockStore;
    }
}
