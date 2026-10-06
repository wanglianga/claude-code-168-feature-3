package com.ruralmarket.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 整改通知：边界争议认定一方超出抽签面积后生成，记录整改要求与期限、信用扣分。
 * 同时生成 Adjustment（信用调整，来源 DISPUTE）并写信用日志、审计日志，摊主端可见。
 */
@Data
@Entity
@Table(name = "rectifications")
public class Rectification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 来源：BOUNDARY 边界争议 / PATROL 巡查 / COMPLAINT 投诉 */
    @Column(nullable = false, length = 20)
    private String sourceType = "BOUNDARY";

    /** 来源单据 ID（边界争议 ID 等） */
    private Long sourceRefId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private MarketEvent event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "result_id", nullable = false)
    private LotteryResult result;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id", nullable = false)
    private User vendor;

    /** 摊位号（冗余便于展示） */
    @Column(nullable = false, length = 20)
    private String stallNo;

    /** 整改要求 */
    @Column(nullable = false, length = 1000)
    private String requirement;

    /** 限期完成时间 */
    private LocalDateTime deadline;

    /** 超占通道/面积的现场照片 */
    @Column(length = 1000)
    private String evidencePhotos;

    /** ISSUED 已下发 / DONE 已整改 / OVERDUE 逾期未改 */
    @Column(nullable = false, length = 20)
    private String status = "ISSUED";

    /** 本次联动扣减的信用分 */
    @Column(nullable = false)
    private Integer creditDelta = 0;

    /** 对应的信用调整 Adjustment ID（追溯用） */
    private Long adjustmentId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime doneAt;
}
