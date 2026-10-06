package com.ruralmarket.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 摊位图版本：每次市场管理员重新测量、发布更新图时生成新版本。
 * 老摊主与新摊主都能看到版本号、调整依据与变更说明，保证"图为什么改"可追溯。
 * 仅 PUBLISHED 版本向摊主公开；风险点在下一次集市前由管理员发版解决。
 */
@Data
@Entity
@Table(name = "stall_map_versions")
public class StallMapVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 版本号，如 v1、v2；同版本号唯一 */
    @Column(nullable = false, unique = true, length = 20)
    private String versionNo;

    /** 摊位图图片 URL */
    @Column(nullable = false, length = 500)
    private String imageUrl;

    /**
     * 发布原因/调整依据（向新老摊主公开）：
     * 如"十月十五大集 B-03/B-04 边界争议频繁，实测通道偏窄，重新测量后调整边界"。
     */
    @Column(nullable = false, length = 1000)
    private String changeReason;

    /** 变更明细 JSON：[{stallNo, before, after, reason}...] */
    @Column(length = 4000)
    private String changeDetails;

    /** DRAFT 草稿 / PUBLISHED 已发布 */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    /** 关联触发发版的风险点ID（可空，逗号分隔或在风险点侧反向关联） */
    private Long triggerRiskId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "publisher_id", nullable = false)
    private User publisher;

    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
