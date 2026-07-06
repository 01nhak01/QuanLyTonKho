package com.example.quanlytonkho.service;

import com.example.quanlytonkho.model.CartItem;
import com.example.quanlytonkho.model.Product;
import com.example.quanlytonkho.model.ProductSize;
import com.example.quanlytonkho.model.RfidEvent;
import com.example.quanlytonkho.repository.CartItemRepository;
import com.example.quanlytonkho.repository.RfidEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class StoreService {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private RfidEventRepository rfidEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    private static final String SUPABASE_URL = "https://axexefvrpqstomhrsyhs.supabase.co/rest/v1/products";
    private static final String SUPABASE_SIZES_URL = "https://axexefvrpqstomhrsyhs.supabase.co/rest/v1/product_sizes";
    private static final String SUPABASE_KEY = "sb_publishable_w-lw5c-fZE_MVBl4lfbRlw_47yKL74-";
    private static final String DEFAULT_SESSION = "SESSION-01";

    @PostConstruct
    public void init() {
        if (rfidEventRepository.count() == 0) {
            logEvent("SYS-CTRL", "Phòng Máy Chủ", "Hệ thống RFID Trực tuyến (Bộ điều khiển chính)");
            logEvent("READER-A..D", "Khu Vực Bán Hàng", "4 cổng đọc RFID đang hoạt động (Tất cả lối đi trực tuyến)");
        }
    }

    @Transactional
    public void resetStoreState() {
        // Clear local cart and events in H2
        cartItemRepository.deleteAll();
        rfidEventRepository.deleteAll();

        // Log default start events
        logEvent("SYS-CTRL", "Phòng Máy Chủ", "Hệ thống RFID Trực tuyến (Bộ điều khiển chính)");
        logEvent("READER-A..D", "Khu Vực Bán Hàng", "4 cổng đọc RFID đang hoạt động (Tất cả lối đi trực tuyến)");
    }

    public List<Product> getAllProducts() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUPABASE_URL + "?select=*&order=id.asc"))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), new TypeReference<List<Product>>() {});
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi tải sản phẩm từ Supabase: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    public List<ProductSize> getAllProductSizes() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUPABASE_SIZES_URL + "?select=*&order=id.asc"))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), new TypeReference<List<ProductSize>>() {});
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi tải bảng size sản phẩm từ Supabase: " + e.getMessage());
        }
        return Collections.emptyList();
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
        String baseSku = sku;
        String size = "M";
        if (sku.contains("-") && (sku.endsWith("-S") || sku.endsWith("-M") || sku.endsWith("-L") || sku.endsWith("-XL"))) {
            int lastDash = sku.lastIndexOf('-');
            baseSku = sku.substring(0, lastDash);
            size = sku.substring(lastDash + 1);
        }

        Product product = getProductBySkuFromSupabase(baseSku);
        if (product != null) {
            // Check if the product has a null/empty size entry in product_sizes (Freesize)
            boolean isFreesize = false;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_SIZES_URL + "?product_id=eq." + product.getId() + "&select=*"))
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    List<ProductSize> list = objectMapper.readValue(response.body(), new TypeReference<List<ProductSize>>() {});
                    if (list != null && list.size() == 1) {
                        String s = list.get(0).getSize();
                        if (s == null || s.trim().isEmpty() || s.equalsIgnoreCase("null")) {
                            isFreesize = true;
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }

            if (isFreesize) {
                size = null;
            }

            int sizeStock = getProductSizeStockFromSupabase(product.getId(), size);
            String cartItemName = isFreesize ? product.getName() : (product.getName() + " (Size " + size + ")");

            // Fetch the quantity of this item already in the cart
            List<CartItem> cartItems = cartItemRepository.findBySessionCode(DEFAULT_SESSION);
            Optional<CartItem> existingItemOpt = cartItems.stream()
                    .filter(item -> item.getProductId().equals(product.getId()) && item.getProductName().equals(cartItemName))
                    .findFirst();
            
            int cartQty = existingItemOpt.isPresent() ? existingItemOpt.get().getQuantity() : 0;

            if (cartQty < sizeStock) {
                CartItem cartItem;
                if (existingItemOpt.isPresent()) {
                    cartItem = existingItemOpt.get();
                    cartItem.setQuantity(cartItem.getQuantity() + 1);
                } else {
                    cartItem = new CartItem(product.getId(), cartItemName, 1, product.getPrice(), DEFAULT_SESSION);
                }
                cartItemRepository.save(cartItem);

                // Map location dynamically in logs
                String location = "Khu Vực Quét";
                if (baseSku.toLowerCase().startsWith("at")) {
                    location = "Hành lang A (Áo)";
                } else if (baseSku.toLowerCase().startsWith("qj")) {
                    location = "Hành lang B (Quần)";
                } else if (baseSku.toLowerCase().startsWith("ak")) {
                    location = "Hành lang C (Áo khoác)";
                } else if (baseSku.toLowerCase().startsWith("pk")) {
                    location = "Hành lang D (Phụ kiện)";
                }

                // Log event without decrementing database stock yet
                logEvent(baseSku, location, "Quét sản phẩm: " + cartItemName + " (Thêm vào giỏ hàng)");
                return cartItem;
            } else {
                String location = "Khu Vực Quét";
                if (baseSku.toLowerCase().startsWith("at")) {
                    location = "Hành lang A (Áo)";
                } else if (baseSku.toLowerCase().startsWith("qj")) {
                    location = "Hành lang B (Quần)";
                } else if (baseSku.toLowerCase().startsWith("ak")) {
                    location = "Hành lang C (Áo khoác)";
                } else if (baseSku.toLowerCase().startsWith("pk")) {
                    location = "Hành lang D (Phụ kiện)";
                }
                
                logEvent(baseSku, location, "Quét thất bại: " + product.getName() + " (Size " + size + ") không đủ tồn kho");
            }
        }
        return null;
    }

    @Transactional
    public void checkout(String paymentMethod) {
        List<CartItem> items = cartItemRepository.findBySessionCode(DEFAULT_SESSION);
        if (!items.isEmpty()) {
            double total = items.stream().mapToDouble(item -> item.getPrice() * item.getQuantity()).sum();
            
            // Loop through each cart item and update stock on Supabase
            for (CartItem item : items) {
                Product product = getProductByIdFromSupabase(item.getProductId());
                if (product != null) {
                    // Extract size
                    String productName = item.getProductName();
                    String size = null;
                    if (productName.contains(" (Size ")) {
                        int startIdx = productName.lastIndexOf(" (Size ") + 7;
                        int endIdx = productName.lastIndexOf(")");
                        if (endIdx > startIdx) {
                            size = productName.substring(startIdx, endIdx);
                        }
                    }

                    // Decrement size stock in product_sizes on Supabase
                    int sizeStock = getProductSizeStockFromSupabase(product.getId(), size);
                    int newSizeStock = Math.max(0, sizeStock - item.getQuantity());
                    updateProductSizeStockOnSupabase(product.getId(), size, newSizeStock);

                    // Decrement overall product stock in products on Supabase
                    int newProductStock = Math.max(0, product.getStockQuantity() - item.getQuantity());
                    updateProductStockOnSupabase(product.getSku(), newProductStock);
                }
            }

            // Log event
            logEvent("CHECKOUT", "Quầy Thanh Toán", "Thanh toán hoàn tất (" + paymentMethod + ") - Đã nhận: " + String.format("%,.0f", total) + " đ");
            
            // Clear cart
            cartItemRepository.deleteBySessionCode(DEFAULT_SESSION);
        }
    }

    private Product getProductBySkuFromSupabase(String sku) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUPABASE_URL + "?sku=eq." + sku + "&select=*"))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                List<Product> list = objectMapper.readValue(response.body(), new TypeReference<List<Product>>() {});
                if (list != null && !list.isEmpty()) {
                    return list.get(0);
                }
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
            String jsonBody = objectMapper.writeValueAsString(body);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUPABASE_URL + "?sku=eq." + sku))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
            
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.err.println("Lỗi khi cập nhật tồn kho trên Supabase: " + e.getMessage());
        }
    }

    private int getProductSizeStockFromSupabase(Long productId, String size) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUPABASE_SIZES_URL + "?product_id=eq." + productId + "&select=*"))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                List<ProductSize> list = objectMapper.readValue(response.body(), new TypeReference<List<ProductSize>>() {});
                if (list != null && !list.isEmpty()) {
                    for (ProductSize ps : list) {
                        String psSize = ps.getSize();
                        if (size == null || size.equalsIgnoreCase("null") || size.trim().isEmpty()) {
                            if (psSize == null || psSize.trim().isEmpty() || psSize.equalsIgnoreCase("null")) {
                                return ps.getStockQuantity() != null ? ps.getStockQuantity() : 0;
                            }
                        } else {
                            if (psSize != null && psSize.equalsIgnoreCase(size)) {
                                return ps.getStockQuantity() != null ? ps.getStockQuantity() : 0;
                            }
                            // Fallback for default "M" if the database size is null
                            if (size.equalsIgnoreCase("M") && (psSize == null || psSize.trim().isEmpty() || psSize.equalsIgnoreCase("null"))) {
                                return ps.getStockQuantity() != null ? ps.getStockQuantity() : 0;
                            }
                        }
                    }
                    if (list.size() == 1) {
                        return list.get(0).getStockQuantity() != null ? list.get(0).getStockQuantity() : 0;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi tìm tồn kho size: " + e.getMessage());
        }
        return 0;
    }

    private void updateProductSizeStockOnSupabase(Long productId, String size, int newStock) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("stock_quantity", newStock);
            String jsonBody = objectMapper.writeValueAsString(body);
            
            String sizeFilter;
            if (size == null || size.equalsIgnoreCase("null") || size.trim().isEmpty() || (!size.equals("S") && !size.equals("M") && !size.equals("L") && !size.equals("XL"))) {
                sizeFilter = "size=is.null";
            } else {
                sizeFilter = "size=eq." + size;
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUPABASE_SIZES_URL + "?product_id=eq." + productId + "&" + sizeFilter))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
            
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.err.println("Lỗi khi cập nhật tồn kho size trên Supabase: " + e.getMessage());
        }
    }

    private Product getProductByIdFromSupabase(Long id) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUPABASE_URL + "?id=eq." + id + "&select=*"))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                List<Product> list = objectMapper.readValue(response.body(), new TypeReference<List<Product>>() {});
                if (list != null && !list.isEmpty()) {
                    return list.get(0);
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi tìm sản phẩm qua ID: " + e.getMessage());
        }
        return null;
    }

    @Transactional
    public void removeCartItem(Long id) {
        Optional<CartItem> itemOpt = cartItemRepository.findById(id);
        if (itemOpt.isPresent()) {
            CartItem item = itemOpt.get();
            if (item.getQuantity() > 1) {
                item.setQuantity(item.getQuantity() - 1);
                cartItemRepository.save(item);
                logEvent("CART-REDUCE", "Giỏ Hàng", "Giảm số lượng sản phẩm: " + item.getProductName());
            } else {
                cartItemRepository.delete(item);
                logEvent("CART-REMOVE", "Giỏ Hàng", "Loại bỏ sản phẩm khỏi giỏ: " + item.getProductName());
            }
        }
    }
}
