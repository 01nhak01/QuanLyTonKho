package com.example.quanlytonkho.service;

import com.example.quanlytonkho.model.CartItem;
import com.example.quanlytonkho.model.Product;
import com.example.quanlytonkho.model.RfidEvent;
import com.example.quanlytonkho.repository.CartItemRepository;
import com.example.quanlytonkho.repository.RfidEventRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class StoreService {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private RfidEventRepository rfidEventRepository;

    private final RestTemplate restTemplate = new RestTemplate();
    
    private static final String SUPABASE_URL = "https://axexefvrpqstomhrsyhs.supabase.co/rest/v1/products";
    private static final String SUPABASE_KEY = "sb_publishable_w-lw5c-fZE_MVBl4lfbRlw_47yKL74-";
    private static final String DEFAULT_SESSION = "SESSION-01";

    @PostConstruct
    public void init() {
        if (rfidEventRepository.count() == 0) {
            logEvent("SYS-CTRL", "Phòng Máy Chủ", "Hệ thống RFID Trực tuyến (Bộ điều khiển chính)");
            logEvent("READER-A..D", "Khu Vực Bán Hàng", "4 cổng đọc RFID đang hoạt động (Tất cả lối đi trực tuyến)");
        }
    }

    private HttpHeaders getSupabaseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", SUPABASE_KEY);
        headers.set("Authorization", "Bearer " + SUPABASE_KEY);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Transactional
    public void resetStoreState() {
        // Clear local cart and events in H2
        cartItemRepository.deleteAll();
        rfidEventRepository.deleteAll();

        // Reset product stocks in Supabase back to their initial levels via REST API
        List<Product> products = getAllProducts();
        for (Product prod : products) {
            if ("SKU-LAP-001".equalsIgnoreCase(prod.getSku())) {
                updateProductStockOnSupabase(prod.getSku(), 10);
            } else if ("SKU-MOU-002".equalsIgnoreCase(prod.getSku())) {
                updateProductStockOnSupabase(prod.getSku(), 50);
            }
        }

        // Log default start events
        logEvent("SYS-CTRL", "Phòng Máy Chủ", "Hệ thống RFID Trực tuyến (Bộ điều khiển chính)");
        logEvent("READER-A..D", "Khu Vực Bán Hàng", "4 cổng đọc RFID đang hoạt động (Tất cả lối đi trực tuyến)");
    }

    public List<Product> getAllProducts() {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(getSupabaseHeaders());
            String url = SUPABASE_URL + "?select=*&order=id.asc";
            
            ResponseEntity<List<Product>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<Product>>() {}
            );
            
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            System.err.println("Lỗi khi tải sản phẩm từ Supabase: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<CartItem> getCartItems() {
        return cartItemRepository.findBySessionCode(DEFAULT_SESSION);
    }

    public List<RfidEvent> getRfidEvents() {
        return rfidEventRepository.findAllByOrderByTimestampDesc();
    }

    @Transactional
    public RfidEvent logEvent(String tagId, String location, String message) {
        RfidEvent event = new RfidEvent(tagId, LocalDateTime.now(), location, message);
        return rfidEventRepository.save(event);
    }

    @Transactional
    public synchronized CartItem addCartItemByTag(String sku) {
        Product product = getProductBySkuFromSupabase(sku);
        if (product != null) {
            if (product.getStockQuantity() > 0) {
                int newStock = product.getStockQuantity() - 1;
                
                // Update stock on Supabase via PATCH request
                updateProductStockOnSupabase(sku, newStock);

                // Add item to local cart
                List<CartItem> cartItems = cartItemRepository.findBySessionCode(DEFAULT_SESSION);
                Optional<CartItem> existingItemOpt = cartItems.stream()
                        .filter(item -> item.getProductId().equals(product.getId()))
                        .findFirst();

                CartItem cartItem;
                if (existingItemOpt.isPresent()) {
                    cartItem = existingItemOpt.get();
                    cartItem.setQuantity(cartItem.getQuantity() + 1);
                } else {
                    cartItem = new CartItem(product.getId(), product.getName(), 1, product.getPrice(), DEFAULT_SESSION);
                }
                cartItemRepository.save(cartItem);

                // Map location dynamically in logs
                String location = "Khu Vực Quét";
                if (sku.toLowerCase().contains("lap")) {
                    location = "Hành lang A";
                } else if (sku.toLowerCase().contains("mou")) {
                    location = "Hành lang B";
                }

                // Log event
                logEvent(sku, location, "Quét sản phẩm: " + product.getName() + " (Tồn kho còn lại: " + newStock + ")");
                return cartItem;
            } else {
                String location = "Khu Vực Quét";
                if (sku.toLowerCase().contains("lap")) location = "Hành lang A";
                else if (sku.toLowerCase().contains("mou")) location = "Hành lang B";
                
                logEvent(sku, location, "Quét thất bại: " + product.getName() + " đã HẾT HÀNG");
            }
        }
        return null;
    }

    @Transactional
    public void checkout() {
        List<CartItem> items = cartItemRepository.findBySessionCode(DEFAULT_SESSION);
        if (!items.isEmpty()) {
            double total = items.stream().mapToDouble(item -> item.getPrice() * item.getQuantity()).sum();
            
            // Log event
            logEvent("CHECKOUT", "Quầy Thanh Toán", "Thanh toán hoàn tất - Đã nhận: " + String.format("%,.0f", total) + " đ");
            
            // Clear cart
            cartItemRepository.deleteBySessionCode(DEFAULT_SESSION);
        }
    }

    private Product getProductBySkuFromSupabase(String sku) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(getSupabaseHeaders());
            String url = SUPABASE_URL + "?sku=eq." + sku + "&select=*";
            
            ResponseEntity<List<Product>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<Product>>() {}
            );
            
            List<Product> list = response.getBody();
            if (list != null && !list.isEmpty()) {
                return list.get(0);
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi tìm sản phẩm qua SKU: " + e.getMessage());
        }
        return null;
    }

    private void updateProductStockOnSupabase(String sku, int newStock) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("stock_quantity", newStock);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, getSupabaseHeaders());
            String url = SUPABASE_URL + "?sku=eq." + sku;
            
            restTemplate.exchange(
                url,
                HttpMethod.PATCH,
                entity,
                Void.class
            );
        } catch (Exception e) {
            System.err.println("Lỗi khi cập nhật tồn kho trên Supabase: " + e.getMessage());
        }
    }
}
