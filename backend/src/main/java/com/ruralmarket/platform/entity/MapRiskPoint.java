package com.ruralmarket.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 摊位图风险点：边界争议频繁发生的区域/相邻摊位对。
 * 一旦在统计窗口内争议次数达到阈值自动标记，要求管理员在下一次集市前重新测量并发布更新图；
 * 发版后风险点转为 RESOLVED，并记录所依据的新版本，形成"发现风险→重测→发版→公示"闭环。
 */
@Data
@Entity
@Table(name = "map_risk_points")
public class MapRiskPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zone_id")
    private StallZone zone;

    /** 风险位置描述，如 "B-03 与 B-04 之间通道" */
    @Column(nullable = false, length = 200)
    private String location;

    /** 涉及摊位号，逗号分隔，如 B-03,B-04 */
    @Column(length = 100)
    private String stallNos;

    /** 触发阈值的争议次数 */
    @Column(nullable = false)
    private Integer disputeCount = 0;

    /** 最近一次关联争议ID */
    private Long latestDisputeId;

    /**
     * OPEN 待重测（下次集市前必须处理） / RESOLVED 已随新版摊位图解决 / IGNORED 已忽略
     */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(length = 1000)
    private String remark;

    /** 解决该风险点所发布的摊位图版本 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "resolved_version_id")
    private StallMapVersion resolvedVersion;

    private LocalDateTime resolvedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
