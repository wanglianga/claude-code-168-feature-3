package com.ruralmarket.platform.repository;

import com.ruralmarket.platform.entity.BoundaryDispute;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BoundaryDisputeRepository extends JpaRepository<BoundaryDispute, Long> {
    List<BoundaryDispute> findByEventIdOrderByCreatedAtDesc(Long eventId);
    List<BoundaryDispute> findAllByOrderByCreatedAtDesc();
}
