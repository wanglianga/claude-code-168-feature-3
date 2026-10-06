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
 * 处理口径：
 * 1. 工作人员现场拍照、在平台标注实际边界后登记争议；
 * 2. 管理员认定：
 *    - OVER_AREA 一方超出抽签面积 → 生成整改通知 + 信用扣分（Adjustment 来源 DISPUTE，全链路留痕，摊主可见）；
 *    - MAP_UNCLEAR 抽签图本身不清晰 → 免除本次扣罚，并对争议所在区域建立"重新测量发布更新图"任务；
 *    - NO_VIOLATION 双方均未违规 → 仅办结。
 * 3. 同一区域边界争议累计达到阈值即标记为摊位图风险点；管理员在下次集市前重新测量并发布更新版摊位图，
 *    更新原因与调整依据通过公开的摊位图版本向新老摊主公示。
 */
@Service
public class BoundaryService {
    private final BoundaryDisputeRepository disputeRepository;
    private final RectificationRepository rectificationRepository;
    private final StallMapVersionRepository mapVersionRepository;
    private final LotteryResultRepository resultRepository;
    private final StallZoneRepository zoneRepository;
    private final AdjustmentService adjustmentService;
    private final AuditService auditService;

    public BoundaryService(BoundaryDisputeRepository disputeRepository,
                           RectificationRepository rectificationRepository,
                           StallMapVersionRepository mapVersionRepository,
                           LotteryResultRepository resultRepository,
                           StallZoneRepository zoneRepository,
                           AdjustmentService adjustmentService,
                           AuditService auditService) {
        this.disputeRepository = disputeRepository;
        this.rectificationRepository = rectificationRepository;
        this.mapVersionRepository = mapVersionRepository;
        this.resultRepository = resultRepository;
        this.zoneRepository = zoneRepository;
        this.adjustmentService = adjustmentService;
        this.auditService = auditService;
    }

    /** 工作人员登记边界争议（现场拍照 + 标注实际边界） */
    @Transactional
    public BoundaryDispute register(Long eventId, Long resultAId, Long resultBId, Long stallId,
                                    String description, String scenePhotos,
                                    String actualBoundaryNote, String actualBoundaryData, User reporter) {
        LotteryResult a = resultRepository.findById(resultAId)
                .orElseThrow(() -> new BizException("争议方A分配单不存在"));
        LotteryResult b = null;
        if (resultBId != null) {
            b = resultRepository.findById(resultBId)
                    .orElseThrow(() -> new BizException("争议方B分配单不存在"));
            if (b.getId().equals(a.getId())) throw new BizException("争议两方不能为同一摊位");
            if (!b.getApplication().getEvent().getId().equals(a.getApplication().getEvent().getId())) {
                throw new BizException("争议双方必须属于同一集市");
            }
        }
        Stall stall;
        if (stallId != null && b != null && b.getStall().getId().equals(stallId)) {
            stall = b.getStall();
        } else {
            stall = a.getStall();
        }

        BoundaryDispute d = new BoundaryDispute();
        d.setEvent(a.getApplication().getEvent());
        if (eventId != null && !eventId.equals(d.getEvent().getId())) {
            throw new BizException("争议双方不属于所选集市");
        }
        d.setResultA(a);
        d.setResultB(b);
        d.setStall(stall);
        d.setDescription(description);
        d.setScenePhotos(scenePhotos);
        d.setActualBoundaryNote(actualBoundaryNote);
        d.setActualBoundaryData(actualBoundaryData);
        d.setReporter(reporter);
        BoundaryDispute saved = disputeRepository.save(d);
        auditService.log(reporter.getId(), reporter.getName(), "BOUNDARY_REGISTER",
                "登记边界争议#" + saved.getId() + "：摊位[" + a.getStallNo() + "]"
                        + (b != null ? " 与 [" + b.getStallNo() + "]" : "") + "，已标注实际边界");
        return saved;
    }

    public record HandleResult(BoundaryDispute dispute, Rectification rectification,
                               StallMapVersion remeasureTask) {}

    /** 管理员认定处理 */
    @Transactional
    public HandleResult handle(Long disputeId, String conclusion, Long violatorResultId,
                               String resolution, Integer penaltyPoints, User operator) {
        BoundaryDispute d = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new BizException("边界争议不存在"));
        if (!"SUBMITTED".equals(d.getStatus())) throw new BizException("该争议已认定处理");
        if (!Set.of("OVER_AREA", "MAP_UNCLEAR", "NO_VIOLATION").contains(conclusion)) {
            throw new BizException("非法认定结论");
        }
        d.setConclusion(conclusion);
        d.setResolution(resolution);
        d.setHandler(operator);
        d.setHandledAt(LocalDateTime.now());
        d.setStatus("RESOLVED");

        Rectification rect = null;
        StallMapVersion task = null;

        if ("OVER_AREA".equals(conclusion)) {
            // 一方超出抽签面积 → 整改 + 扣分；抽签图不清晰时禁止走此口径
            LotteryResult violator = resolveViolator(d, violatorResultId);
            int points = penaltyPoints != null && penaltyPoints > 0 ? penaltyPoints : Constants.BOUNDARY_DEFAULT_PENALTY;
            User vendor = violator.getApplication().getVendor();

            String reason = "边界争议#" + d.getId() + "：摊位[" + violator.getStallNo()
                    + "] 实际经营超出抽签面积、占用通道，责令限期整改并扣信用 " + points + " 分";
            // 复用信用调整：生成 Adjustment（来源 DISPUTE）+ 信用日志 + 审计
            Adjustment adj = adjustmentService.adjustCredit(vendor.getId(), -points, reason,
                    "DISPUTE", d.getId(), operator);

            Rectification r = new Rectification();
            r.setSourceType("BOUNDARY");
            r.setSourceRefId(d.getId());
            r.setEvent(d.getEvent());
            r.setResult(violator);
            r.setVendor(vendor);
            r.setStallNo(violator.getStallNo());
            r.setRequirement("立即退回抽签边界内经营、腾出占用通道，恢复实际边界："
                    + (d.getActualBoundaryNote() != null ? d.getActualBoundaryNote() : "以现场标注线为准"));
            r.setDeadline(LocalDateTime.now().plusHours(Constants.RECTIFY_DEFAULT_DEADLINE_HOURS));
            r.setEvidencePhotos(d.getScenePhotos());
            r.setCreditDelta(-points);
            r.setAdjustmentId(adj.getId());
            r.setOperator(operator);
            rect = rectificationRepository.save(r);

            d.setViolatorResult(violator);
            d.setPenalized(true);
            d.setWaived(false);
            d.setPenaltyPoints(points);
            auditService.log(operator.getId(), operator.getName(), "BOUNDARY_PENALIZE",
                    "边界争议#" + d.getId() + " 认定摊位[" + violator.getStallNo() + "]超抽签面积，"
                            + "下发整改#" + rect.getId() + "并扣信用 " + points + " 分");
        } else if ("MAP_UNCLEAR".equals(conclusion)) {
            // 抽签图本身不清晰 → 免除本次扣罚，建立重新测量任务
            d.setPenalized(false);
            d.setWaived(true);
            d.setPenaltyPoints(0);
            StallZone zone = d.getStall().getZone();
            task = ensureRemeasureTask(zone, "边界争议#" + d.getId() + " 认定抽签图边界不清晰："
                    + (resolution != null ? resolution : "需重新测量实际通道与摊位边界"),
                    String.valueOf(d.getId()), operator);
            auditService.log(operator.getId(), operator.getName(), "BOUNDARY_WAIVE",
                    "边界争议#" + d.getId() + " 认定抽签图不清晰，免除本次扣罚，"
                            + "区域[" + zone.getName() + "]已列入重新测量");
        } else {
            // 双方均未违规
            d.setPenalized(false);
            d.setWaived(false);
            auditService.log(operator.getId(), operator.getName(), "BOUNDARY_NOVIOLATION",
                    "边界争议#" + d.getId() + " 认定双方均未超抽签面积，办结");
        }

        // 该区域边界争议累计达到阈值 → 自动标记为摊位图风险点并建立"下次集市前重新测量"任务
        if (task == null) {
            StallZone zone = d.getStall().getZone();
            long zoneDisputes = disputeRepository.findAllByOrderByCreatedAtDesc().stream()
                    .filter(x -> x.getStall() != null && x.getStall().getZone() != null
                            && x.getStall().getZone().getId().equals(zone.getId()))
                    .count();
            if (zoneDisputes >= Constants.BOUNDARY_RISK_THRESHOLD) {
                task = ensureRemeasureTask(zone,
                        "该区域已发生 " + zoneDisputes + " 起边界争议（风险点阈值 "
                                + Constants.BOUNDARY_RISK_THRESHOLD + "），下次集市前需重新测量并发布更新图",
                        null, operator);
            }
        }

        disputeRepository.save(d);
        return new HandleResult(d, rect, task);
    }

    private LotteryResult resolveViolator(BoundaryDispute d, Long violatorResultId) {
        if (violatorResultId != null) {
            LotteryResult v = resultRepository.findById(violatorResultId)
                    .orElseThrow(() -> new BizException("超面积方分配单不存在"));
            if (!v.getId().equals(d.getResultA().getId())
                    && (d.getResultB() == null || !v.getId().equals(d.getResultB().getId()))) {
                throw new BizException("超面积方必须是争议双方之一");
            }
            return v;
        }
        // 默认取 A 方
        return d.getResultA();
    }

    /** 确保区域存在一个"待重新测量"任务（幂等：已有未完成任务则不重复建立） */
    @Transactional
    public StallMapVersion ensureRemeasureTask(StallZone zone, String reason, String basisDisputeId, User operator) {
        boolean exists = mapVersionRepository.findAllByOrderByCreatedAtDesc().stream()
                .anyMatch(v -> "MEASURING".equals(v.getStatus()) && v.getZone() != null
                        && v.getZone().getId().equals(zone.getId()));
        if (exists) return null;
        StallMapVersion v = new StallMapVersion();
        v.setScopeType("ZONE");
        v.setZone(zone);
        v.setScopeLabel(zone.getName() + " 边界通道");
        v.setImageUrl("");
        v.setVersionNo(nextVersionNo(zone.getId()));
        v.setReason(reason);
        v.setChangeSummary("等待下次集市前重新测量并发布更新图");
        v.setBasisDisputeIds(basisDisputeId);
        v.setStatus("MEASURING");
        return mapVersionRepository.save(v);
    }

    /** 整改复查通过 */
    @Transactional
    public Rectification verifyRectification(Long rectId, boolean done, String note, User operator) {
        Rectification r = rectificationRepository.findById(rectId)
                .orElseThrow(() -> new BizException("整改通知不存在"));
        if (!"ISSUED".equals(r.getStatus())) throw new BizException("该整改已复查");
        r.setStatus(done ? "DONE" : "OVERDUE");
        if (done) r.setDoneAt(LocalDateTime.now());
        if (note != null) r.setRequirement(r.getRequirement() + "；复查备注：" + note);
        Rectification saved = rectificationRepository.save(r);
        auditService.log(operator.getId(), operator.getName(), "BOUNDARY_RECTIFY_VERIFY",
                "整改#" + rectId + " 摊位[" + r.getStallNo() + "] 复查" + (done ? "通过" : "未达标"));
        return saved;
    }

    /** 发布更新版摊位图（重新测量完成），并关闭该区域待测量任务 */
    @Transactional
    public StallMapVersion publishMap(String scopeType, Long zoneId, String scopeLabel, String imageUrl,
                                      String reason, String changeSummary, String basisDisputeIds,
                                      User publisher) {
        if (imageUrl == null || imageUrl.isBlank()) throw new BizException("请上传重新测量后的摊位图");
        if (reason == null || reason.isBlank()) throw new BizException("更新原因必填（向新老摊主公开）");

        StallZone zone = null;
        if ("ZONE".equals(scopeType)) {
            if (zoneId == null) throw new BizException("请选择区域");
            zone = zoneRepository.findById(zoneId).orElseThrow(() -> new BizException("区域不存在"));
            // 关闭该区域的待测量任务
            for (StallMapVersion v : mapVersionRepository.findAllByOrderByCreatedAtDesc()) {
                if ("MEASURING".equals(v.getStatus()) && v.getZone() != null
                        && v.getZone().getId().equals(zoneId)) {
                    mapVersionRepository.delete(v);
                }
            }
        }

        StallMapVersion v = new StallMapVersion();
        v.setScopeType(scopeType == null ? "ZONE" : scopeType);
        v.setZone(zone);
        v.setScopeLabel(scopeLabel != null && !scopeLabel.isBlank() ? scopeLabel
                : zone != null ? zone.getName() : "全场摊位图");
        v.setImageUrl(imageUrl);
        v.setVersionNo(nextVersionNo(zoneId));
        v.setReason(reason);
        v.setChangeSummary(changeSummary);
        v.setBasisDisputeIds(basisDisputeIds);
        v.setStatus("PUBLISHED");
        v.setPublisher(publisher);
        v.setPublishedAt(LocalDateTime.now());
        StallMapVersion saved = mapVersionRepository.save(v);
        auditService.log(publisher.getId(), publisher.getName(), "STALL_MAP_PUBLISH",
                "发布更新版摊位图#" + saved.getId() + " v" + saved.getVersionNo()
                        + " 范围[" + saved.getScopeLabel() + "]，依据：" + reason);
        return saved;
    }

    private int nextVersionNo(Long zoneId) {
        if (zoneId == null) return 1;
        return mapVersionRepository.findByZoneIdOrderByVersionNoDesc(zoneId).stream()
                .mapToInt(StallMapVersion::getVersionNo).max().orElse(0) + 1;
    }

    /**
     * 摊位图风险点：按争议边界所在区域聚合，争议累计达到阈值的区域列为风险点。
     * 已在最近一次争议之后发布更新图的，状态为 RESOLVED；建立待测量任务的为 MEASURING；否则为 PENDING。
     */
    public List<Map<String, Object>> riskPoints() {
        List<BoundaryDispute> all = disputeRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, List<BoundaryDispute>> byZone = new LinkedHashMap<>();
        for (BoundaryDispute d : all) {
            if (d.getStall() == null || d.getStall().getZone() == null) continue;
            byZone.computeIfAbsent(d.getStall().getZone().getId(), k -> new ArrayList<>()).add(d);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, List<BoundaryDispute>> e : byZone.entrySet()) {
            List<BoundaryDispute> ds = e.getValue();
            if (ds.size() < Constants.BOUNDARY_RISK_THRESHOLD) continue;
            StallZone zone = ds.get(0).getStall().getZone();
            LocalDateTime lastDisputeAt = ds.stream().map(BoundaryDispute::getCreatedAt)
                    .max(LocalDateTime::compareTo).orElse(null);

            List<StallMapVersion> versions = mapVersionRepository.findByZoneIdOrderByVersionNoDesc(zone.getId());
            LocalDateTime lastPublishAt = versions.stream()
                    .filter(v -> "PUBLISHED".equals(v.getStatus()))
                    .map(StallMapVersion::getPublishedAt)
                    .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            boolean measuring = versions.stream().anyMatch(v -> "MEASURING".equals(v.getStatus()));

            String riskStatus;
            if (lastPublishAt != null && lastDisputeAt != null && lastPublishAt.isAfter(lastDisputeAt)) {
                riskStatus = "RESOLVED";
            } else if (measuring) {
                riskStatus = "MEASURING";
            } else {
                riskStatus = "PENDING";
            }

            long unclearCount = ds.stream().filter(d -> "MAP_UNCLEAR".equals(d.getConclusion())).count();
            long overAreaCount = ds.stream().filter(d -> "OVER_AREA".equals(d.getConclusion())).count();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("zone", zone);
            row.put("disputeCount", ds.size());
            row.put("overAreaCount", overAreaCount);
            row.put("mapUnclearCount", unclearCount);
            row.put("lastDisputeAt", lastDisputeAt);
            row.put("lastPublishAt", lastPublishAt);
            row.put("latestVersion", versions.stream().filter(v -> "PUBLISHED".equals(v.getStatus())).findFirst().orElse(null));
            row.put("riskStatus", riskStatus);
            rows.add(row);
        }
        rows.sort((a, b) -> {
            int sa = "PENDING".equals(a.get("riskStatus")) ? 0 : "MEASURING".equals(a.get("riskStatus")) ? 1 : 2;
            int sb = "PENDING".equals(b.get("riskStatus")) ? 0 : "MEASURING".equals(b.get("riskStatus")) ? 1 : 2;
            if (sa != sb) return sa - sb;
            return Integer.compare((int) b.get("disputeCount"), (int) a.get("disputeCount"));
        });
        return rows;
    }
}
