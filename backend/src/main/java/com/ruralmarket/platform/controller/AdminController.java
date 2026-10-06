package com.ruralmarket.platform.controller;

import com.ruralmarket.platform.config.AuthUser;
import com.ruralmarket.platform.config.BizException;
import com.ruralmarket.platform.entity.*;
import com.ruralmarket.platform.repository.*;
import com.ruralmarket.platform.service.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 乡镇管理端：集市管理、报名审核、抽签、投诉处理、调整、复盘看板、审计 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final MarketEventRepository eventRepository;
    private final ApplicationRepository applicationRepository;
    private final LotteryRunRepository runRepository;
    private final LotteryResultRepository resultRepository;
    private final StallZoneRepository zoneRepository;
    private final StallRepository stallRepository;
    private final ComplaintRepository complaintRepository;
    private final AdjustmentRepository adjustmentRepository;
    private final PaymentRepository paymentRepository;
    private final PatrolRecordRepository patrolRepository;
    private final FoodSampleRepository sampleRepository;
    private final EntryCheckRepository entryCheckRepository;
    private final CreditLogRepository creditLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final LotteryService lotteryService;
    private final AdjustmentService adjustmentService;
    private final CreditService creditService;
    private final AuditService auditService;
    private final DisputeService disputeService;
    private final BoundaryDisputeRepository disputeRepository;
    private final RectificationRepository rectificationRepository;
    private final MapRiskPointRepository riskRepository;
    private final StallMapVersionRepository mapVersionRepository;

    public AdminController(MarketEventRepository eventRepository, ApplicationRepository applicationRepository,
                           LotteryRunRepository runRepository, LotteryResultRepository resultRepository,
                           StallZoneRepository zoneRepository, StallRepository stallRepository,
                           ComplaintRepository complaintRepository, AdjustmentRepository adjustmentRepository,
                           PaymentRepository paymentRepository, PatrolRecordRepository patrolRepository,
                           FoodSampleRepository sampleRepository, EntryCheckRepository entryCheckRepository,
                           CreditLogRepository creditLogRepository, AuditLogRepository auditLogRepository,
                           UserRepository userRepository, LotteryService lotteryService,
                           AdjustmentService adjustmentService, CreditService creditService,
                           AuditService auditService, DisputeService disputeService,
                           BoundaryDisputeRepository disputeRepository,
                           RectificationRepository rectificationRepository,
                           MapRiskPointRepository riskRepository,
                           StallMapVersionRepository mapVersionRepository) {
        this.eventRepository = eventRepository;
        this.applicationRepository = applicationRepository;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.zoneRepository = zoneRepository;
        this.stallRepository = stallRepository;
        this.complaintRepository = complaintRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.paymentRepository = paymentRepository;
        this.patrolRepository = patrolRepository;
        this.sampleRepository = sampleRepository;
        this.entryCheckRepository = entryCheckRepository;
        this.creditLogRepository = creditLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.lotteryService = lotteryService;
        this.adjustmentService = adjustmentService;
        this.creditService = creditService;
        this.auditService = auditService;
        this.disputeService = disputeService;
        this.disputeRepository = disputeRepository;
        this.rectificationRepository = rectificationRepository;
        this.riskRepository = riskRepository;
        this.mapVersionRepository = mapVersionRepository;
    }

    private User me() {
        return userRepository.findById(AuthUser.current().id()).orElseThrow();
    }

    // ================= 集市管理 =================
    @GetMapping("/events")
    public List<MarketEvent> events() {
        return eventRepository.findAllByOrderByMarketDateDesc();
    }

    public record EventReq(@NotBlank String name, @NotNull String marketDate, String remark) {}

    @PostMapping("/events")
    @Transactional
    public MarketEvent createEvent(@RequestBody EventReq req) {
        MarketEvent e = new MarketEvent();
        e.setName(req.name());
        e.setMarketDate(java.time.LocalDate.parse(req.marketDate()));
        e.setRemark(req.remark());
        e.setStatus("DRAFT");
        MarketEvent saved = eventRepository.save(e);
        auditService.log(me().getId(), me().getName(), "EVENT_CREATE", "创建集市[" + req.name() + "] " + req.marketDate());
        return saved;
    }

    public record StatusReq(@NotBlank String status) {}

    @PostMapping("/events/{id}/status")
    @Transactional
    public MarketEvent changeStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        MarketEvent e = eventRepository.findById(id).orElseThrow(() -> new BizException("集市不存在"));
        Set<String> allowed = Set.of("DRAFT", "OPEN", "CLOSED", "IN_PROGRESS", "FINISHED");
        if (!allowed.contains(req.status())) throw new BizException("非法状态");
        String from = e.getStatus();
        e.setStatus(req.status());
        MarketEvent saved = eventRepository.save(e);
        auditService.log(me().getId(), me().getName(), "EVENT_STATUS",
                "集市[" + e.getName() + "] 状态 " + from + " → " + req.status());
        return saved;
    }

    // ================= 报名审核 =================
    @GetMapping("/events/{id}/applications")
    public List<Application> applications(@PathVariable Long id) {
        return applicationRepository.findByEventId(id);
    }

    public record ReviewReq(@NotNull Boolean approve, String note) {}

    @PostMapping("/applications/{id}/review")
    @Transactional
    public Application review(@PathVariable Long id, @RequestBody ReviewReq req) {
        Application app = applicationRepository.findById(id).orElseThrow(() -> new BizException("报名不存在"));
        if (!"PENDING".equals(app.getStatus())) throw new BizException("该报名已审核");
        app.setStatus(req.approve() ? "APPROVED" : "REJECTED");
        app.setReviewNote(req.note());
        Application saved = applicationRepository.save(app);
        auditService.log(me().getId(), me().getName(), "APPLICATION_REVIEW",
                "报名#" + id + " 摊主[" + app.getVendor().getName() + "] "
                        + (req.approve() ? "通过" : "驳回") + (req.note() != null ? "：" + req.note() : ""));
        return saved;
    }

    // ================= 抽签 =================
    public record LotteryReq(@NotNull Long seed, String remark) {}

    @PostMapping("/events/{id}/lottery")
    @Transactional
    public LotteryRun runLottery(@PathVariable Long id, @RequestBody LotteryReq req) {
        MarketEvent e = eventRepository.findById(id).orElseThrow(() -> new BizException("集市不存在"));
        LotteryRun run = lotteryService.runLottery(e, req.seed(), me());
        if (req.remark() != null && !req.remark().isBlank()) {
            run.setRemark(run.getRemark() + "；" + req.remark());
            runRepository.save(run);
        }
        return run;
    }

    @GetMapping("/events/{id}/runs")
    public List<LotteryRun> runs(@PathVariable Long id) {
        return runRepository.findByEventIdOrderByExecutedAtDesc(id);
    }

    @GetMapping("/runs/{id}")
    public Map<String, Object> runDetail(@PathVariable Long id) {
        LotteryRun run = runRepository.findById(id).orElseThrow(() -> new BizException("抽签记录不存在"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("run", run);
        m.put("results", resultRepository.findByRunId(id));
        return m;
    }

    /** 复核：用快照+种子重放，验证结果未被篡改 */
    @GetMapping("/runs/{id}/verify")
    public Map<String, Object> verifyRun(@PathVariable Long id) {
        LotteryRun run = runRepository.findById(id).orElseThrow(() -> new BizException("抽签记录不存在"));
        return lotteryService.verify(run);
    }

    @GetMapping("/events/{id}/results")
    public List<LotteryResult> eventResults(@PathVariable Long id) {
        return resultRepository.findByApplicationEventId(id);
    }

    // ================= 区域与摊位 =================
    @GetMapping("/zones")
    public List<StallZone> zones() {
        return zoneRepository.findAll();
    }

    @PostMapping("/zones")
    @Transactional
    public StallZone saveZone(@RequestBody StallZone zone) {
        StallZone saved = zoneRepository.save(zone);
        auditService.log(me().getId(), me().getName(), "ZONE_SAVE", "保存区域[" + zone.getName() + "]");
        return saved;
    }

    @GetMapping("/stalls")
    public List<Stall> stalls() {
        return stallRepository.findAll();
    }

    @PostMapping("/stalls")
    @Transactional
    public Stall saveStall(@RequestBody Stall stall) {
        if (stall.getZone() == null || stall.getZone().getId() == null) throw new BizException("必须选择所属区域");
        Stall saved = stallRepository.save(stall);
        auditService.log(me().getId(), me().getName(), "STALL_SAVE", "保存摊位[" + stall.getStallNo() + "]");
        return saved;
    }

    // ================= 投诉处理 =================
    @GetMapping("/complaints")
    public List<Complaint> complaints(@RequestParam(required = false) Long eventId) {
        if (eventId != null) return complaintRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        return complaintRepository.findAll();
    }

    public record HandleReq(@NotBlank String status, @NotBlank String resolution,
                            Integer creditDelta, String creditReason) {}

    @PostMapping("/complaints/{id}/handle")
    @Transactional
    public Complaint handleComplaint(@PathVariable Long id, @RequestBody HandleReq req) {
        Complaint c = complaintRepository.findById(id).orElseThrow(() -> new BizException("投诉不存在"));
        if (!Set.of("PROCESSING", "RESOLVED", "REJECTED").contains(req.status())) {
            throw new BizException("非法处理状态");
        }
        c.setStatus(req.status());
        c.setResolution(req.resolution());
        c.setHandler(me());
        c.setHandledAt(LocalDateTime.now());
        Complaint saved = complaintRepository.save(c);
        auditService.log(me().getId(), me().getName(), "COMPLAINT_HANDLE",
                "投诉#" + id + " 处理为 " + req.status() + "：" + req.resolution());
        // 属实投诉可联动扣信用
        if (req.creditDelta() != null && req.creditDelta() != 0 && c.getVendor() != null) {
            String reason = req.creditReason() != null ? req.creditReason()
                    : "投诉#" + id + " 处理联动";
            creditService.adjust(c.getVendor(), req.creditDelta(), reason, "COMPLAINT", c.getId(), me());
        }
        return saved;
    }

    // ================= 调整（可追溯） =================
    @GetMapping("/adjustments")
    public List<Adjustment> adjustments(@RequestParam(required = false) Long eventId) {
        if (eventId != null) return adjustmentRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        return adjustmentRepository.findAll();
    }

    public record AdjustReq(@NotBlank String type, Long eventId, Long resultId, Long paymentId,
                            Long vendorId, Long newStallId, String newCategory,
                            Boolean sellsCookedFood, Boolean sellsLivePoultry,
                            Integer creditDelta, @NotBlank String reason,
                            @NotBlank String source, Long sourceRefId) {}

    @PostMapping("/adjustments")
    @Transactional
    public Adjustment adjust(@RequestBody AdjustReq req) {
        User operator = me();
        return switch (req.type()) {
            case "RELOCATE", "RAIN_RELOCATE" -> {
                require(req.resultId(), "resultId"); require(req.newStallId(), "newStallId");
                yield adjustmentService.relocate(req.resultId(), req.newStallId(), req.type(),
                        req.reason(), req.source(), req.sourceRefId(), operator);
            }
            case "FEE_WAIVER" -> {
                require(req.paymentId(), "paymentId");
                yield adjustmentService.waiveFee(req.paymentId(), req.reason(), req.source(), req.sourceRefId(), operator);
            }
            case "DEPOSIT_PENALTY" -> {
                require(req.paymentId(), "paymentId");
                yield adjustmentService.penalizeDeposit(req.paymentId(), req.reason(), req.source(), req.sourceRefId(), operator);
            }
            case "CREDIT_ADJUST" -> {
                require(req.vendorId(), "vendorId"); require(req.creditDelta(), "creditDelta");
                yield adjustmentService.adjustCredit(req.vendorId(), req.creditDelta(),
                        req.reason(), req.source(), req.sourceRefId(), operator);
            }
            case "CATEGORY_CHANGE" -> {
                require(req.resultId(), "resultId");
                if (req.newCategory() == null || req.newCategory().isBlank()) throw new BizException("缺少 newCategory");
                yield adjustmentService.changeCategory(req.resultId(), req.newCategory(),
                        Boolean.TRUE.equals(req.sellsCookedFood()), Boolean.TRUE.equals(req.sellsLivePoultry()),
                        req.reason(), req.source(), req.sourceRefId(), operator);
            }
            default -> throw new BizException("不支持的调整类型: " + req.type());
        };
    }

    private void require(Object o, String field) {
        if (o == null) throw new BizException("缺少参数 " + field);
    }

    // ================= 摊位边界争议复核 =================
    @GetMapping("/disputes")
    public List<BoundaryDispute> disputes(@RequestParam(required = false) Long eventId) {
        if (eventId != null) return disputeRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        return disputeRepository.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/disputes/{id}")
    public Map<String, Object> disputeDetail(@PathVariable Long id) {
        BoundaryDispute d = disputeRepository.findById(id).orElseThrow(() -> new BizException("争议单不存在"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dispute", d);
        m.put("rectifications", rectificationRepository.findByDisputeId(id));
        return m;
    }

    /** 管理员复核：办结或驳回（维持超面积判定时） */
    public record DisputeReviewReq(@NotBlank String status, String reviewNote) {}

    @PostMapping("/disputes/{id}/review")
    @Transactional
    public BoundaryDispute reviewDispute(@PathVariable Long id, @RequestBody DisputeReviewReq req) {
        return disputeService.review(id, req.status(), req.reviewNote(), me());
    }

    /**
     * 认定抽签图不清晰：上传重新测量后的更新摊位图、填写调整依据并发布新版本，
     * 系统免除本次扣罚（撤销整改、回滚信用），新老摊主均可查看版本依据。
     */
    public record UnclearMapReq(String versionNo, @NotBlank String imageUrl,
                                @NotBlank String changeReason, String changeDetails, String reviewNote) {}

    @PostMapping("/disputes/{id}/unclear-map")
    @Transactional
    public StallMapVersion unclearMap(@PathVariable Long id, @RequestBody UnclearMapReq req) {
        return disputeService.waiveAsUnclearMap(id, req.versionNo(), req.imageUrl(),
                req.changeReason(), req.changeDetails(), req.reviewNote(), me());
    }

    // ================= 摊位图风险点 =================
    @GetMapping("/map-risks")
    public List<MapRiskPoint> mapRisks(@RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) return riskRepository.findByStatusOrderByCreatedAtDesc(status);
        return riskRepository.findAllByOrderByCreatedAtDesc();
    }

    public record IgnoreRiskReq(String remark) {}

    @PostMapping("/map-risks/{id}/ignore")
    @Transactional
    public MapRiskPoint ignoreRisk(@PathVariable Long id, @RequestBody(required = false) IgnoreRiskReq req) {
        return disputeService.ignoreRisk(id, req == null ? null : req.remark(), me());
    }

    // ================= 摊位图版本 =================
    @GetMapping("/map-versions")
    public List<StallMapVersion> mapVersions() {
        return mapVersionRepository.findAllByOrderByCreatedAtDesc();
    }

    /** 重新测量后发布新版摊位图（可同时解决若干风险点） */
    public record PublishMapReq(String versionNo, @NotBlank String imageUrl,
                                @NotBlank String changeReason, String changeDetails, List<Long> riskIds) {}

    @PostMapping("/map-versions")
    @Transactional
    public StallMapVersion publishMap(@RequestBody PublishMapReq req) {
        return disputeService.publishAndResolveRisks(req.versionNo(), req.imageUrl(),
                req.changeReason(), req.changeDetails(), req.riskIds(), me());
    }

    // ================= 缴费总览 =================
    @GetMapping("/payments")
    public List<Payment> payments(@RequestParam(required = false) Long eventId) {
        if (eventId != null) return paymentRepository.findByEventId(eventId);
        return paymentRepository.findAll();
    }

    // ================= 摊主管理 =================
    @GetMapping("/vendors")
    public List<User> vendors() {
        return userRepository.findAll().stream()
                .filter(u -> "VENDOR".equals(u.getRole())).toList();
    }

    // ================= 复盘看板 =================
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestParam Long eventId) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<LotteryResult> results = resultRepository.findByApplicationEventId(eventId);
        List<Complaint> complaints = complaintRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        List<PatrolRecord> patrols = patrolRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        List<Payment> payments = paymentRepository.findByEventId(eventId);
        List<FoodSample> samples = sampleRepository.findByEventId(eventId);
        List<EntryCheck> checks = entryCheckRepository.findByResultApplicationEventId(eventId);
        List<Adjustment> adjustments = adjustmentRepository.findByEventIdOrderByCreatedAtDesc(eventId);

        Map<Long, LotteryResult> resultMap = results.stream()
                .collect(Collectors.toMap(LotteryResult::getId, r -> r, (a, b) -> a));

        // 1) 高投诉品类
        Map<String, Long> complaintByCategory = new LinkedHashMap<>();
        for (Complaint c : complaints) {
            if (!"CONSUMER".equals(c.getReporterType())) continue;
            String cat = "未知";
            if (c.getResult() != null && resultMap.containsKey(c.getResult().getId())) {
                cat = Constants.categoryName(resultMap.get(c.getResult().getId()).getApplication().getCategory());
            }
            complaintByCategory.merge(cat, 1L, Long::sum);
        }
        out.put("complaintByCategory", complaintByCategory);

        // 2) 拥堵/占道区域（巡查占道记录 + 调位次数）
        Map<String, Long> laneOccupationByZone = new LinkedHashMap<>();
        for (PatrolRecord p : patrols) {
            if (Boolean.TRUE.equals(p.getOccupyingLane())) {
                LotteryResult r = resultMap.get(p.getResult().getId());
                String zone = r != null ? r.getStall().getZone().getName() : "未知";
                laneOccupationByZone.merge(zone, 1L, Long::sum);
            }
        }
        Map<String, Long> relocateByZone = new LinkedHashMap<>();
        for (Adjustment a : adjustments) {
            if (("RELOCATE".equals(a.getType()) || "RAIN_RELOCATE".equals(a.getType())) && a.getResult() != null) {
                LotteryResult r = resultMap.get(a.getResult().getId());
                String zone = r != null ? r.getStall().getZone().getName() : "未知";
                relocateByZone.merge(zone, 1L, Long::sum);
            }
        }
        out.put("laneOccupationByZone", laneOccupationByZone);
        out.put("relocateByZone", relocateByZone);

        // 3) 收费到账
        Map<String, BigDecimal> paymentByStatus = new LinkedHashMap<>();
        Map<String, BigDecimal> paymentByType = new LinkedHashMap<>();
        for (Payment p : payments) {
            if ("PAID".equals(p.getStatus())) {
                paymentByStatus.merge("已到账", p.getAmount(), BigDecimal::add);
                paymentByType.merge(p.getItemName(), p.getAmount(), BigDecimal::add);
            } else if ("UNPAID".equals(p.getStatus())) {
                paymentByStatus.merge("未缴", p.getAmount(), BigDecimal::add);
            } else if ("WAIVED".equals(p.getStatus())) {
                paymentByStatus.merge("已减免", p.getAmount(), BigDecimal::add);
            } else if ("DEDUCTED".equals(p.getStatus())) {
                paymentByStatus.merge("已扣罚", p.getAmount(), BigDecimal::add);
            }
        }
        out.put("paymentByStatus", paymentByStatus);
        out.put("paymentByType", paymentByType);

        // 4) 食品安全风险
        long sampleRequired = results.stream()
                .filter(r -> Constants.SAMPLE_REQUIRED.contains(r.getApplication().getCategory())).count();
        long diarrheaCount = complaints.stream()
                .filter(c -> "DIARRHEA".equals(c.getType())).count();
        long shortWeightCount = patrols.stream().filter(p -> Boolean.TRUE.equals(p.getShortWeight())).count();
        double avgHygiene = patrols.stream().mapToInt(PatrolRecord::getHygieneScore).average().orElse(0);
        Map<String, Object> foodSafety = new LinkedHashMap<>();
        foodSafety.put("sampleRequiredVendors", sampleRequired);
        foodSafety.put("samplesSubmitted", samples.size());
        foodSafety.put("diarrheaComplaints", diarrheaCount);
        foodSafety.put("shortWeightRecords", shortWeightCount);
        foodSafety.put("avgHygieneScore", Math.round(avgHygiene * 100.0) / 100.0);
        out.put("foodSafety", foodSafety);

        // 5) 总体概览
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("assigned", results.size());
        overview.put("checkedIn", checks.size());
        overview.put("complaints", complaints.size());
        overview.put("openComplaints", complaints.stream().filter(c -> !"RESOLVED".equals(c.getStatus()) && !"REJECTED".equals(c.getStatus())).count());
        overview.put("patrols", patrols.size());
        overview.put("adjustments", adjustments.size());
        out.put("overview", overview);

        // 6) 投诉类型分布
        Map<String, Long> complaintByType = new LinkedHashMap<>();
        for (Complaint c : complaints) {
            complaintByType.merge(c.getType(), 1L, Long::sum);
        }
        out.put("complaintByType", complaintByType);

        // 7) 摊位边界争议调解
        List<BoundaryDispute> disputes = disputeRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        Map<String, Long> disputeByVerdict = new LinkedHashMap<>();
        Map<String, Long> disputeByStatus = new LinkedHashMap<>();
        Map<String, Long> disputeByZone = new LinkedHashMap<>();
        for (BoundaryDispute d : disputes) {
            disputeByVerdict.merge(DisputeService.verdictText(d.getVerdict()), 1L, Long::sum);
            disputeByStatus.merge(d.getStatus(), 1L, Long::sum);
            String zone = d.getResultA().getStall().getZone().getName();
            disputeByZone.merge(zone, 1L, Long::sum);
        }
        long rectCount = disputes.stream()
                .flatMap(d -> rectificationRepository.findByDisputeId(d.getId()).stream()).count();
        long waivedCount = disputes.stream().filter(d -> "WAIVED".equals(d.getStatus())).count();
        long openRiskCount = riskRepository.findByStatusOrderByCreatedAtDesc("OPEN").size();
        Map<String, Object> boundary = new LinkedHashMap<>();
        boundary.put("total", disputes.size());
        boundary.put("rectifications", rectCount);
        boundary.put("waivedByUnclearMap", waivedCount);
        boundary.put("openRiskPoints", openRiskCount);
        boundary.put("byVerdict", disputeByVerdict);
        boundary.put("byStatus", disputeByStatus);
        boundary.put("byZone", disputeByZone);
        out.put("boundaryDisputes", boundary);
        return out;
    }

    // ================= 审计日志 =================
    @GetMapping("/audits")
    public List<AuditLog> audits() {
        return auditLogRepository.findTop200ByOrderByCreatedAtDesc();
    }

    @GetMapping("/events/{id}/credit-logs")
    public List<CreditLog> eventCreditLogs(@PathVariable Long id) {
        return creditLogRepository.findAll();
    }
}
