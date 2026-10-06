package com.ruralmarket.platform.repository;

import com.ruralmarket.platform.entity.Rectification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RectificationRepository extends JpaRepository<Rectification, Long> {
    List<Rectification> findByDisputeId(Long disputeId);
    List<Rectification> findByVendorIdOrderByCreatedAtDesc(Long vendorId);
    List<Rectification> findAllByOrderByCreatedAtDesc();
    List<Rectification> findByStatusOrderByCreatedAtDesc(String status);
}
