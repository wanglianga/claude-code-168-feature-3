package com.ruralmarket.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 摊位边界争议调解：两个摊主互称对方占用通道，工作人员现场拍照并在平台标注实际边界，
 * 由市场管理员认定：一方超抽签面积→整改+扣分；抽签图本身不清晰→更新摊位图并免除本次扣罚。
 */
@Data
@Entity
@Table(name = "boundary_disputes")
public class BoundaryDispute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private MarketEvent event;

    /** 争议涉及的两个摊位分配单（B 可空，如对方为无位流动摊贩） */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "result_a_id", nullable = false)
    private LotteryResult resultA;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "result_b_id")
    private LotteryResult resultB;

    /** 争议边界所在摊位（风险点按其所属区域聚合），默认取 A 侧摊位 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stall_id", nullable = false)
    private Stall stall;

    @Column(nullable = false, length = 1000)
    private String description;

    /** 现场照片（逗号分隔 URL） */
    @Column(length = 1000)
    private String scenePhotos;

    /** 工作人员在平台标注的实际边界说明（如：A-01 实际右边界较抽签线内缩 0.3m，通道实测 2.1m） */
    @Column(length = 1000)
    private String actualBoundaryNote;

    /** 实际边界标注数据（JSON，坐标/线段，可空） */
    @Column(length = 4000)
    private String actualBoundaryData;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    /** SUBMITTED 待认定 / RESOLVED 已认定 */
    @Column(nullable = false, length = 20)
    private String status = "SUBMITTED";

    /** OVER_AREA 一方超出抽签面积 / MAP_UNCLEAR 抽签图不清晰 / NO_VIOLATION 双方均未违规 */
    @Column(length = 20)
    private String conclusion;

    /** 认定超面积的一方分配单 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "violator_result_id")
    private LotteryResult violatorResult;

    @Column(length = 2000)
    private String resolution;

    /** 是否已作出扣分/整改 */
    @Column(nullable = false)
    private Boolean penalized = false;

    /** 是否因抽签图不清晰免除本次扣罚 */
    @Column(nullable = false)
    private Boolean waived = false;

    /** 本次扣分值（0 表示未扣分） */
    @Column(nullable = false)
    private Integer penaltyPoints = 0;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "handler_id")
    private User handler;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime handledAt;
}
