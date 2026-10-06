package com.ruralmarket.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 整改单：边界争议中超出抽签面积的一方，由系统自动生成。
 * 限期退回抽签边界，复查通过后关闭；复查不通过可升级扣罚。
 * 若争议被认定为"抽签图不清晰"，整改单随豁免一并撤销（CANCELLED_WAIVED）。
 */
@Data
@Entity
@Table(name = "rectifications")
public class Rectification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dispute_id", nullable = false)
    private BoundaryDispute dispute;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id", nullable = false)
    private User vendor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "result_id", nullable = false)
    private LotteryResult result;

    /** 整改要求（如：退回抽签边界，面宽由实测 3.6m 退回 3.0m） */
    @Column(nullable = false, length = 1000)
    private String requirement;

    /** 抽签面积快照（米，宽×深） */
    private Double lotWidth;
    private Double lotDepth;
    /** 实测面积快照（米） */
    private Double actualWidth;
    private Double actualDepth;

    /** 本次整改联动扣分值 */
    @Column(nullable = false)
    private Integer creditDelta;

    /** 扣分对应的信用流水，豁免时用于回滚 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "credit_log_id")
    private CreditLog creditLog;

    /**
     * PENDING 待整改 / RECTIFIED 已整改待复查 / PASSED 复查通过 /
     * FAILED 复查不通过 / CANCELLED_WAIVED 图不清晰已豁免撤销
     */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    private LocalDateTime deadline;

    @Column(length = 1000)
    private String rectifyNote;
    private LocalDateTime rectifiedAt;

    /** 复查照片 */
    @Column(length = 1000)
    private String recheckPhotos;
    @Column(length = 1000)
    private String recheckNote;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rechecker_id")
    private User rechecker;
    private LocalDateTime recheckedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
