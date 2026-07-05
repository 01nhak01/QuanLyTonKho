package com.example.quanlytonkho.repository;

import com.example.quanlytonkho.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findBySessionCode(String sessionCode);
    void deleteBySessionCode(String sessionCode);
}
