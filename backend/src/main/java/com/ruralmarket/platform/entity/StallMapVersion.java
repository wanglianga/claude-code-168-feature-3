package com.ruralmarket.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 摊位图版本：边界争议频发区域被标记为摊位图风险点后，市场管理员在下一次集市前
 * 重新测量并发布更新版摊位图。更新原因与调整依据向老摊主、新摊主公开。
 */
@Data
@Entity
@Table(name = "stall_map_versions")
public class StallMapVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ZONE 区域图 / GLOBAL 全场图 */
    @Column(nullable = false, length = 20)
    private String scopeType = "ZONE";

    /** 区域图时关联的区域 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zone_id")
    private StallZone zone;

    /** 覆盖范围说明，如：D区 D-03~D-04 边界通道 */
    @Column(nullable = false, length = 200)
    private String scopeLabel;

    /** 摊位图图片 URL */
    @Column(nullable = false, length = 500)
    private String imageUrl;

    /** 版本号，从 1 递增 */
    @Column(nullable = false)
    private Integer versionNo = 1;

    /** 更新原因（为何重新测量，向摊主公开） */
    @Column(nullable = false, length = 1000)
    private String reason;

    /** 调整依据：测量数据/争议单号等（向新老摊主公开） */
    @Column(length = 2000)
    private String changeSummary;

    /** 关联的边界争议 ID（逗号分隔） */
    @Column(length = 500)
    private String basisDisputeIds;

    /** MEASURING 待重新测量 / PUBLISHED 已发布 */
    @Column(nullable = false, length = 20)
    private String status = "MEASURING";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "publisher_id")
    private User publisher;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime publishedAt;
}
