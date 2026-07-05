package com.example.quanlytonkho.repository;

import com.example.quanlytonkho.model.RfidEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RfidEventRepository extends JpaRepository<RfidEvent, Long> {
    List<RfidEvent> findAllByOrderByTimestampDesc();
}
