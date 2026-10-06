package com.ruralmarket.platform.service;

import com.ruralmarket.platform.config.BizException;
import com.ruralmarket.platform.entity.*;
import com.ruralmarket.platform.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 摊位边界争议调解服务。
 *
 * 立案时按双方实测尺寸与抽签面积自动判定：
 *  1) 超出抽签面积（含 5cm 测量容差）的一方生成整改单并扣信用分；
 *  2) 双方均未超（NONE）说明抽签图可能不清晰，转管理员复核，管理员可认定"图不清晰"，
 *     发布更新版摊位图并免除本次扣罚（撤销整改、回滚信用）；
 *  3) 同一相邻位置争议达到阈值自动标记摊位图风险点，管理员下次集市前重新测量、发版解决。
 * 所有动作写审计日志，调整依据随摊位图版本向新老摊主公开。
 */
@Service
public class DisputeService {
    /** 测量容差（米）：实测超出抽签面积该值以内不算超占 */
    public static final double TOLERANCE = 0.05;
    /** 超抽签面积单次扣信用分 */
    public static final int OVERRUN_CREDIT_DELTA = -5;
    /** 同一位置争议次数达到该值即标记为摊位图风险点 */
    public static final int RISK_THRESHOLD = 2;

    private final BoundaryDisputeRepository disputeRepository;
    private final RectificationRepository rectificationRepository;
    private final MapRiskPointRepository riskRepository;
    private final StallMapVersionRepository versionRepository;
    private final LotteryResultRepository resultRepository;
    private final CreditService creditService;
    private final AuditService auditService;

    public DisputeService(BoundaryDisputeRepository disputeRepository,
                          RectificationRepository rectificationRepository,
                          MapRiskPointRepository riskRepository,
                          StallMapVersionRepository versionRepository,
                          LotteryResultRepository resultRepository,
                          CreditService creditService, AuditService auditService) {
        this.disputeRepository = disputeRepository;
        this.rectificationRepository = rectificationRepository;
        this.riskRepository = riskRepository;
        this.versionRepository = versionRepository;
        this.resultRepository = resultRepository;
        this.creditService = creditService;
        this.auditService = auditService;
    }

    /** 单方尺寸：抽签宽/深 + 实测宽/深 */
    public record SideMeasure(Double lotWidth, Double lotDepth, Double actualWidth, Double actualDepth) {}

    /** 是否超出抽签面积（面宽或进深任一超出容差） */
    public static boolean exceeds(SideMeasure m) {
        if (m.actualWidth() == null || m.actualDepth() == null) return false;
        double lw = m.lotWidth() != null ? m.lotWidth() : m.actualWidth();
        double ld = m.lotDepth() != null ? m.lotDepth() : m.actualDepth();
        return m.actualWidth() > lw + TOLERANCE || m.actualDepth() > ld + TOLERANCE;
    }

    /** 自动判定：A / B / BOTH / NONE */
    public static String verdict(SideMeasure a, SideMeasure b) {
        boolean oa = exceeds(a), ob = exceeds(b);
        if (oa && ob) return "BOTH";
        if (oa) return "A";
        if (ob) return "B";
        return "NONE";
    }

    /**
     * 工作人员现场立案：拍照、标注实际边界、实测双方尺寸。
     * 自动判定并对超面积方生成整改单+扣分；达到风险阈值则标记风险点。
     */
    @Transactional
    public BoundaryDispute fileDispute(Long eventId, Long resultAId, Long resultBId, User reporter,
                                       String photos, String boundaryMark, String markBaseImage,
                                       Double actualWidthA, Double actualDepthA,
                                       Double actualWidthB, Double actualDepthB,
                                       Double laneClearWidth, String description) {
        LotteryResult ra = resultRepository.findById(resultAId)
                .orElseThrow(() -> new BizException("主张方分配单不存在"));
        LotteryResult rb = resultRepository.findById(resultBId)
                .orElseThrow(() -> new BizException("被诉方分配单不存在"));
        if (ra.getId().equals(rb.getId())) throw new BizException("争议双方不能为同一摊位");
        MarketEvent event = ra.getApplication().getEvent();
        if (eventId != null && !event.getId().equals(eventId)) throw new BizException("集市与分配单不一致");
        if (!event.getId().equals(rb.getApplication().getEvent().getId())) {
            throw new BizException("两个摊位不属于同一集市");
        }

        Stall sa = ra.getStall(), sb = rb.getStall();
        SideMeasure ma = new SideMeasure(sa.getLotWidth(), sa.getLotDepth(), actualWidthA, actualDepthA);
        SideMeasure mb = new SideMeasure(sb.getLotWidth(), sb.getLotDepth(), actualWidthB, actualDepthB);
        String v = verdict(ma, mb);

        BoundaryDispute d = new BoundaryDispute();
        d.setEvent(event);
        d.setResultA(ra);
        d.setResultB(rb);
        d.setReporter(reporter);
        d.setPhotos(photos);
        d.setBoundaryMark(boundaryMark);
        d.setActualWidthA(actualWidthA);
        d.setActualDepthA(actualDepthA);
        d.setLotWidthA(sa.getLotWidth());
        d.setLotDepthA(sa.getLotDepth());
        d.setActualWidthB(actualWidthB);
        d.setActualDepthB(actualDepthB);
        d.setLotWidthB(sb.getLotWidth());
        d.setLotDepthB(sb.getLotDepth());
        d.setLaneClearWidth(laneClearWidth);
        d.setDescription(description);
        d.setVerdict(v);

        StallMapVersion current = versionRepository
                .findFirstByStatusOrderByPublishedAtDesc("PUBLISHED").orElse(null);
        d.setMapVersion(current);
        d.setMarkBaseImage(markBaseImage != null && !markBaseImage.isBlank()
                ? markBaseImage : (current != null ? current.getImageUrl() : null));

        // 先持久化争议单，整改单才能引用其主键
        BoundaryDispute saved = disputeRepository.save(d);

        boolean penalized = false;
        if ("A".equals(v) || "BOTH".equals(v)) {
            createRectification(saved, ra, ma, reporter);
            penalized = true;
        }
        if ("B".equals(v) || "BOTH".equals(v)) {
            createRectification(saved, rb, mb, reporter);
            penalized = true;
        }
        saved.setStatus(penalized ? "PENALIZED" : "OPEN");
        saved = disputeRepository.save(saved);

        // 同一相邻位置争议频繁 → 标记风险点
        maybeMarkRisk(saved);

        auditService.log(reporter.getId(), reporter.getName(), "DISPUTE_FILE",
                "边界争议#" + saved.getId() + " 摊位[" + ra.getStallNo() + "]×[" + rb.getStallNo()
                        + "] 判定=" + verdictText(v) + (penalized ? "，已生成整改并扣分" : "，待管理员复核"));
        return saved;
    }

    private void createRectification(BoundaryDispute d, LotteryResult r, SideMeasure m, User operator) {
        Rectification rec = new Rectification();
        rec.setDispute(d);
        rec.setVendor(r.getApplication().getVendor());
        rec.setResult(r);
        rec.setLotWidth(m.lotWidth());
        rec.setLotDepth(m.lotDepth());
        rec.setActualWidth(m.actualWidth());
        rec.setActualDepth(m.actualDepth());
        rec.setRequirement("摊位 " + r.getStallNo() + " 超出抽签面积（实测 "
                + fmt(m.actualWidth()) + "m×" + fmt(m.actualDepth()) + "m，抽签登记 "
                + fmt(m.lotWidth()) + "m×" + fmt(m.lotDepth()) + "m），请立即退回抽签边界、让出占用通道");
        rec.setCreditDelta(OVERRUN_CREDIT_DELTA);
        String reason = "边界争议#" + d.getId() + "：摊位 " + r.getStallNo() + " 实测超出抽签面积，占用通道";
        CreditLog log = creditService.adjust(r.getApplication().getVendor(), OVERRUN_CREDIT_DELTA,
                reason, "BOUNDARY_DISPUTE", d.getId(), operator);
        rec.setCreditLog(log);
        rectificationRepository.save(rec);
    }

    /** 统计同一相邻摊位对的历史争议次数，达到阈值则生成/更新 OPEN 风险点 */
    private void maybeMarkRisk(BoundaryDispute current) {
        String pairKey = pairKey(current.getResultA(), current.getResultB());
        long count = disputeRepository.findAll().stream()
                .filter(x -> pairKey(x.getResultA(), x.getResultB()).equals(pairKey))
                .count();
        if (count < RISK_THRESHOLD) return;

        StallZone zone = current.getResultA().getStall().getZone();
        String stallNos = pairStallNos(current.getResultA(), current.getResultB());
        MapRiskPoint existing = riskRepository.findByStatusAndZoneId("OPEN", zone.getId()).stream()
                .filter(rp -> rp.getStallNos() != null && pairKeyFromNos(rp.getStallNos()).equals(pairKey))
                .findFirst().orElse(null);
        if (existing == null) {
            existing = new MapRiskPoint();
            existing.setZone(zone);
            existing.setStallNos(stallNos);
            existing.setLocation(stallNos.replace(",", " 与 ") + " 之间通道");
            existing.setStatus("OPEN");
        }
        existing.setDisputeCount((int) count);
        existing.setLatestDisputeId(current.getId());
        existing.setRemark("边界争议达 " + count + " 次，判定多为抽签图边界不清，下次集市前需重新测量发布更新图");
        riskRepository.save(existing);
        current.setRiskMarked(true);
    }

    /**
     * 管理员认定抽签图不清晰：发布更新版摊位图，免除本次扣罚——
     * 撤销相关整改单并回滚已扣信用分。
     */
    @Transactional
    public StallMapVersion waiveAsUnclearMap(Long disputeId, String versionNo, String imageUrl,
                                             String changeReason, String changeDetails,
                                             String reviewNote, User admin) {
        BoundaryDispute d = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new BizException("争议单不存在"));
        if (!Set.of("OPEN", "PENALIZED").contains(d.getStatus())) {
            throw new BizException("当前状态(" + d.getStatus() + ")不可认定图不清晰");
        }
        if (imageUrl == null || imageUrl.isBlank()) throw new BizException("请上传更新后的摊位图");
        if (changeReason == null || changeReason.isBlank()) throw new BizException("必须填写调整依据（向摊主公开）");

        StallMapVersion version = publishVersion(versionNo, imageUrl, changeReason, changeDetails, null, admin);

        // 撤销整改单并回滚扣分
        for (Rectification rec : rectificationRepository.findByDisputeId(d.getId())) {
            if ("CANCELLED_WAIVED".equals(rec.getStatus())) continue;
            rec.setStatus("CANCELLED_WAIVED");
            if (rec.getCreditLog() != null) {
                int refund = -rec.getCreditDelta();
                creditService.adjust(rec.getVendor(), refund,
                        "边界争议#" + d.getId() + " 经复核为抽签图不清晰，免除本次扣罚，回滚 " + refund + " 分",
                        "BOUNDARY_WAIVER", d.getId(), admin);
            }
            rectificationRepository.save(rec);
        }

        d.setStatus("WAIVED");
        d.setReviewer(admin);
        d.setReviewedAt(LocalDateTime.now());
        d.setReviewNote(reviewNote != null ? reviewNote : "抽签图不清晰，已发布 v" + version.getVersionNo() + " 更新图，免除本次扣罚");
        disputeRepository.save(d);

        // 图不清晰重新发版即视为对该位置风险点的重测解决
        resolveMatchingRisks(d, version);

        auditService.log(admin.getId(), admin.getName(), "DISPUTE_WAIVE",
                "边界争议#" + d.getId() + " 认定抽签图不清晰，发布摊位图[" + version.getVersionNo()
                        + "]，免除本次扣罚并回滚信用，依据：" + changeReason);
        return version;
    }

    /** 管理员直接复核办结 / 驳回 */
    @Transactional
    public BoundaryDispute review(Long disputeId, String status, String reviewNote, User admin) {
        BoundaryDispute d = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new BizException("争议单不存在"));
        if (!Set.of("RESOLVED", "REJECTED").contains(status)) throw new BizException("非法复核状态");
        d.setStatus(status);
        d.setReviewer(admin);
        d.setReviewedAt(LocalDateTime.now());
        d.setReviewNote(reviewNote);
        BoundaryDispute saved = disputeRepository.save(d);
        auditService.log(admin.getId(), admin.getName(), "DISPUTE_REVIEW",
                "边界争议#" + d.getId() + " 复核为 " + status + (reviewNote != null ? "：" + reviewNote : ""));
        return saved;
    }

    /** 工作人员现场复查整改单 */
    @Transactional
    public Rectification recheck(Long rectificationId, boolean passed, String photos, String note,
                                 Double recheckedWidth, Double recheckedDepth, User staff) {
        Rectification rec = rectificationRepository.findById(rectificationId)
                .orElseThrow(() -> new BizException("整改单不存在"));
        if ("CANCELLED_WAIVED".equals(rec.getStatus())) {
            throw new BizException("该整改单已随图不清晰豁免撤销，无需复查");
        }
        rec.setStatus(passed ? "PASSED" : "FAILED");
        rec.setRecheckPhotos(photos);
        rec.setRecheckNote(note);
        rec.setRechecker(staff);
        rec.setRecheckedAt(LocalDateTime.now());
        if (passed && rec.getRectifiedAt() == null) rec.setRectifiedAt(LocalDateTime.now());
        Rectification saved = rectificationRepository.save(rec);

        // 全部整改复查通过 → 争议办结
        boolean allPassed = rectificationRepository.findByDisputeId(rec.getDispute().getId()).stream()
                .allMatch(r -> "PASSED".equals(r.getStatus()) || "CANCELLED_WAIVED".equals(r.getStatus()));
        if (passed && allPassed && "PENALIZED".equals(rec.getDispute().getStatus())) {
            BoundaryDispute d = rec.getDispute();
            d.setStatus("RESOLVED");
            d.setReviewer(staff);
            d.setReviewedAt(LocalDateTime.now());
            d.setReviewNote("整改全部复查通过，现场退回抽签边界");
            disputeRepository.save(d);
        }
        auditService.log(staff.getId(), staff.getName(), "RECT_RECHECK",
                "整改单#" + rec.getId() + " 摊位[" + rec.getResult().getStallNo() + "] 复查"
                        + (passed ? "通过" : "不通过") + (note != null ? "：" + note : ""));
        return saved;
    }

    /** 管理员重新测量后发布新版摊位图，并解决指定风险点 */
    @Transactional
    public StallMapVersion publishVersion(String versionNo, String imageUrl, String changeReason,
                                          String changeDetails, Long triggerRiskId, User admin) {
        if (imageUrl == null || imageUrl.isBlank()) throw new BizException("请上传摊位图");
        if (changeReason == null || changeReason.isBlank()) throw new BizException("必须填写调整依据");
        StallMapVersion v = new StallMapVersion();
        v.setVersionNo(resolveVersionNo(versionNo));
        v.setImageUrl(imageUrl);
        v.setChangeReason(changeReason);
        v.setChangeDetails(changeDetails);
        v.setStatus("PUBLISHED");
        v.setPublisher(admin);
        v.setPublishedAt(LocalDateTime.now());
        v.setTriggerRiskId(triggerRiskId);
        StallMapVersion saved = versionRepository.save(v);
        auditService.log(admin.getId(), admin.getName(), "MAP_PUBLISH",
                "发布摊位图[" + saved.getVersionNo() + "]，依据：" + changeReason);
        return saved;
    }

    /** 发版并批量解决风险点 */
    @Transactional
    public StallMapVersion publishAndResolveRisks(String versionNo, String imageUrl, String changeReason,
                                                  String changeDetails, List<Long> riskIds, User admin) {
        Long trigger = riskIds != null && !riskIds.isEmpty() ? riskIds.get(0) : null;
        StallMapVersion version = publishVersion(versionNo, imageUrl, changeReason, changeDetails, trigger, admin);
        if (riskIds != null) {
            for (Long rid : riskIds) {
                MapRiskPoint rp = riskRepository.findById(rid).orElse(null);
                if (rp != null && "OPEN".equals(rp.getStatus())) {
                    rp.setStatus("RESOLVED");
                    rp.setResolvedVersion(version);
                    rp.setResolvedAt(LocalDateTime.now());
                    riskRepository.save(rp);
                }
            }
        }
        return version;
    }

    /** 忽略风险点（经核实无需重测） */
    @Transactional
    public MapRiskPoint ignoreRisk(Long riskId, String remark, User admin) {
        MapRiskPoint rp = riskRepository.findById(riskId).orElseThrow(() -> new BizException("风险点不存在"));
        rp.setStatus("IGNORED");
        rp.setRemark(remark);
        rp.setResolvedAt(LocalDateTime.now());
        MapRiskPoint saved = riskRepository.save(rp);
        auditService.log(admin.getId(), admin.getName(), "RISK_IGNORE", "忽略摊位图风险点#" + riskId);
        return saved;
    }

    private void resolveMatchingRisks(BoundaryDispute d, StallMapVersion version) {
        String pair = pairKey(d.getResultA(), d.getResultB());
        Long zoneId = d.getResultA().getStall().getZone().getId();
        for (MapRiskPoint rp : riskRepository.findByStatusAndZoneId("OPEN", zoneId)) {
            if (rp.getStallNos() != null && pairKeyFromNos(rp.getStallNos()).equals(pair)) {
                rp.setStatus("RESOLVED");
                rp.setResolvedVersion(version);
                rp.setResolvedAt(LocalDateTime.now());
                riskRepository.save(rp);
            }
        }
    }

    private String resolveVersionNo(String requested) {
        if (requested != null && !requested.isBlank()) {
            if (versionRepository.existsByVersionNo(requested)) throw new BizException("版本号已存在：" + requested);
            return requested;
        }
        long n = versionRepository.count() + 1;
        String no = "v" + n;
        while (versionRepository.existsByVersionNo(no)) { n++; no = "v" + n; }
        return no;
    }

    // ===== 摊位对工具：以摊位号排序生成稳定键，使 A-B / B-A 视为同一位置 =====
    private String pairKey(LotteryResult a, LotteryResult b) {
        List<String> nos = new ArrayList<>(List.of(a.getStallNo(), b.getStallNo()));
        Collections.sort(nos);
        return nos.get(0) + "|" + nos.get(1);
    }

    private String pairStallNos(LotteryResult a, LotteryResult b) {
        List<String> nos = new ArrayList<>(List.of(a.getStallNo(), b.getStallNo()));
        Collections.sort(nos);
        return nos.get(0) + "," + nos.get(1);
    }

    private String pairKeyFromNos(String stallNos) {
        List<String> nos = new ArrayList<>(List.of(stallNos.split(",")));
        Collections.sort(nos);
        return nos.get(0).trim() + "|" + nos.get(1).trim();
    }

    private String fmt(Double d) {
        if (d == null) return "-";
        return String.valueOf(Math.round(d * 100.0) / 100.0);
    }

    public static String verdictText(String v) {
        return switch (v) {
            case "A" -> "主张方超面积";
            case "B" -> "被诉方超面积";
            case "BOTH" -> "双方均超面积";
            default -> "双方均未超（疑图不清晰）";
        };
    }
}
