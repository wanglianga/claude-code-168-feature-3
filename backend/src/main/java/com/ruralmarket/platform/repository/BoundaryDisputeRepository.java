package com.ruralmarket.platform.repository;

import com.ruralmarket.platform.entity.BoundaryDispute;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BoundaryDisputeRepository extends JpaRepository<BoundaryDispute, Long> {
    List<BoundaryDispute> findByEventIdOrderByCreatedAtDesc(Long eventId);
    List<BoundaryDispute> findAllByOrderByCreatedAtDesc();
    /** 涉及某摊位（任一方）的全部争议 */
    List<BoundaryDispute> findByResultAIdOrResultBId(Long resultAId, Long resultBId);
}
