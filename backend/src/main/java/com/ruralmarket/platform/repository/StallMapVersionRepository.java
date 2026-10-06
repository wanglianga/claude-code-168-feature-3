package com.ruralmarket.platform.repository;

import com.ruralmarket.platform.entity.StallMapVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StallMapVersionRepository extends JpaRepository<StallMapVersion, Long> {
    List<StallMapVersion> findAllByOrderByCreatedAtDesc();
    List<StallMapVersion> findByStatusOrderByPublishedAtDesc(String status);
    Optional<StallMapVersion> findFirstByStatusOrderByPublishedAtDesc(String status);
    boolean existsByVersionNo(String versionNo);
}
