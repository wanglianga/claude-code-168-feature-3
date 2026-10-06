package com.ruralmarket.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 摊位边界争议调解单：两个相邻摊主互称对方占用通道，由市场工作人员现场拍照、
 * 在平台标注实际边界并实测双方面宽/进深后立案。
 *
 * 立案即按抽签面积自动判定：
 *  - 超出抽签面积一方生成整改单并扣信用分；
 *  - 若抽签图本身不清晰（由市场管理员认定），更新摊位图并免除本次扣罚（回滚信用、撤销整改）；
 *  - 同一位置争议频繁发生时，对应区域/摊位被标记为摊位图风险点。
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

    /** 主张方（先反映被占道的摊主）分配单 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "result_a_id", nullable = false)
    private LotteryResult resultA;

    /** 被诉方分配单 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "result_b_id", nullable = false)
    private LotteryResult resultB;

    /** 现场登记的工作人员 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    /** 现场照片，逗号分隔 URL */
    @Column(length = 1000)
    private String photos;

    /**
     * 平台标注的实际边界（归一化坐标 JSON）：
     * {lane:[{x,y}...], aOutline:[...], bOutline:[...]}，坐标 0~1 相对标注底图。
     */
    @Column(length = 4000)
    private String boundaryMark;

    /** 标注底图（摊位图版本图或现场照片）URL */
    @Column(length = 500)
    private String markBaseImage;

    /** 立案时所依据的摊位图版本 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "map_version_id")
    private StallMapVersion mapVersion;

    // ===== A 方实测（米）=====
    private Double actualWidthA;
    private Double actualDepthA;
    /** A 方抽签面宽/进深（立案时快照，米） */
    private Double lotWidthA;
    private Double lotDepthA;

    // ===== B 方实测（米）=====
    private Double actualWidthB;
    private Double actualDepthB;
    private Double lotWidthB;
    private Double lotDepthB;

    /**
     * 自动判定结果（立案时生成）：
     * A 超面积 / B 超面积 / BOTH 双方均超 / NONE 均未超（待管理员复核，倾向于图不清晰）。
     */
    @Column(nullable = false, length = 10)
    private String verdict;

    /** 通道最窄处实测宽度（米），用于判断是否被占用 */
    private Double laneClearWidth;

    @Column(length = 1000)
    private String description;

    /**
     * OPEN 待处理 / PENALIZED 已生成整改扣罚 / WAIVED 图不清晰已豁免 /
     * RESOLVED 已办结 / REJECTED 驳回（误报/不成立）
     */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    /** 管理员复核意见 */
    @Column(length = 1000)
    private String reviewNote;

    /** 是否因该单产生了风险点标记 */
    @Column(nullable = false)
    private Boolean riskMarked = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
