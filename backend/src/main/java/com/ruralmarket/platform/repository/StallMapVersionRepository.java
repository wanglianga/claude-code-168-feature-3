package com.ruralmarket.platform.repository;

import com.ruralmarket.platform.entity.StallMapVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StallMapVersionRepository extends JpaRepository<StallMapVersion, Long> {
    List<StallMapVersion> findByStatusOrderByCreatedAtDesc(String status);
    List<StallMapVersion> findAllByOrderByCreatedAtDesc();
    List<StallMapVersion> findByZoneIdOrderByVersionNoDesc(Long zoneId);
    long countByZoneIdAndStatus(Long zoneId, String status);
}
