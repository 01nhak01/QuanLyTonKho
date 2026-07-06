package com.example.quanlytonkho.controller;

import com.example.quanlytonkho.model.CartItem;
import com.example.quanlytonkho.model.Product;
import com.example.quanlytonkho.model.ProductSize;
import com.example.quanlytonkho.model.RfidEvent;
import com.example.quanlytonkho.service.StoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class StoreRestController {

    @Autowired
    private StoreService storeService;

    @GetMapping("/products")
    public List<Product> getAllProducts() {
        return storeService.getAllProducts();
    }

    @GetMapping("/product-sizes")
    public List<ProductSize> getProductSizes() {
        return storeService.getAllProductSizes();
    }

    @GetMapping("/cart")
    public Map<String, Object> getCartData(@RequestParam(required = false) String sessionCode) {
        List<CartItem> items = storeService.getCartItems(sessionCode);
        double total = items.stream().mapToDouble(item -> item.getPrice() * item.getQuantity()).sum();
        int count = items.stream().mapToInt(CartItem::getQuantity).sum();

        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        response.put("total", total);
        response.put("count", count);
        return response;
    }

    @GetMapping("/events")
    public List<RfidEvent> getRfidEvents(@RequestParam(required = false) String sessionCode) {
        return storeService.getRfidEvents(sessionCode);
    }

    @PostMapping("/events")
    public RfidEvent logEvent(
            @RequestParam String tagId,
            @RequestParam String location,
            @RequestParam String message,
            @RequestParam(required = false) String sessionCode) {
        return storeService.logEvent(tagId, location, message, sessionCode);
    }

    @PostMapping("/cart/add")
    public CartItem addToCart(@RequestParam String tagId, @RequestParam(required = false) String sessionCode) {
        return storeService.addCartItemByTag(tagId, sessionCode);
    }

    @DeleteMapping("/cart/remove/{id}")
    public Map<String, String> deleteCartItem(@PathVariable Long id) {
        storeService.removeCartItem(id);
        Map<String, String> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Cart item removed");
        return response;
    }

    @PostMapping("/checkout")
    public Map<String, String> checkout(
            @RequestParam(required = false, defaultValue = "Chuyển khoản") String paymentMethod,
            @RequestParam(required = false) String sessionCode) {
        storeService.checkout(paymentMethod, sessionCode);
        Map<String, String> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Checkout successful");
        return response;
    }

    @PostMapping("/reset")
    public Map<String, String> reset(@RequestParam(required = false) String sessionCode) {
        storeService.resetStoreState(sessionCode);
        Map<String, String> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "System state reset successfully");
        return response;
    }
}
