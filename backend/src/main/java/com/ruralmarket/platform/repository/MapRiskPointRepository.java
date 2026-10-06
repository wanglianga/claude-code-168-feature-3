package com.ruralmarket.platform.repository;

import com.ruralmarket.platform.entity.MapRiskPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MapRiskPointRepository extends JpaRepository<MapRiskPoint, Long> {
    List<MapRiskPoint> findByStatusOrderByCreatedAtDesc(String status);
    List<MapRiskPoint> findAllByOrderByCreatedAtDesc();
    List<MapRiskPoint> findByStatusAndZoneId(String status, Long zoneId);
}
