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
    private final BoundaryDisputeRepository boundaryDisputeRepository;
    private final RectificationRepository rectificationRepository;
    private final StallMapVersionRepository stallMapVersionRepository;
    private final LotteryService lotteryService;
    private final AdjustmentService adjustmentService;
    private final CreditService creditService;
    private final AuditService auditService;
    private final BoundaryService boundaryService;

    public AdminController(MarketEventRepository eventRepository, ApplicationRepository applicationRepository,
                           LotteryRunRepository runRepository, LotteryResultRepository resultRepository,
                           StallZoneRepository zoneRepository, StallRepository stallRepository,
                           ComplaintRepository complaintRepository, AdjustmentRepository adjustmentRepository,
                           PaymentRepository paymentRepository, PatrolRecordRepository patrolRepository,
                           FoodSampleRepository sampleRepository, EntryCheckRepository entryCheckRepository,
                           CreditLogRepository creditLogRepository, AuditLogRepository auditLogRepository,
                           UserRepository userRepository,
                           BoundaryDisputeRepository boundaryDisputeRepository,
                           RectificationRepository rectificationRepository,
                           StallMapVersionRepository stallMapVersionRepository,
                           LotteryService lotteryService,
                           AdjustmentService adjustmentService, CreditService creditService,
                           AuditService auditService, BoundaryService boundaryService) {
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
        this.boundaryDisputeRepository = boundaryDisputeRepository;
        this.rectificationRepository = rectificationRepository;
        this.stallMapVersionRepository = stallMapVersionRepository;
        this.lotteryService = lotteryService;
        this.adjustmentService = adjustmentService;
        this.creditService = creditService;
        this.auditService = auditService;
        this.boundaryService = boundaryService;
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
        return out;
    }

    // ================= 摊位边界争议调解 =================
    @GetMapping("/boundary-disputes")
    public List<BoundaryDispute> boundaryDisputes(@RequestParam(required = false) Long eventId) {
        if (eventId != null) return boundaryDisputeRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        return boundaryDisputeRepository.findAllByOrderByCreatedAtDesc();
    }

    public record BoundaryHandleReq(@NotBlank String conclusion, Long violatorResultId,
                                    String resolution, Integer penaltyPoints) {}

    /** 认定：OVER_AREA 超面积(整改+扣分) / MAP_UNCLEAR 图不清晰(免除扣罚+重新测量) / NO_VIOLATION 无违规 */
    @PostMapping("/boundary-disputes/{id}/handle")
    @Transactional
    public BoundaryService.HandleResult handleBoundary(@PathVariable Long id, @RequestBody BoundaryHandleReq req) {
        return boundaryService.handle(id, req.conclusion(), req.violatorResultId(),
                req.resolution(), req.penaltyPoints(), me());
    }

    @GetMapping("/rectifications")
    public List<Rectification> rectifications(@RequestParam(required = false) Long eventId) {
        if (eventId != null) return rectificationRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        return rectificationRepository.findAll();
    }

    public record RectifyVerifyReq(@NotNull Boolean done, String note) {}

    @PostMapping("/rectifications/{id}/verify")
    @Transactional
    public Rectification verifyRectification(@PathVariable Long id, @RequestBody RectifyVerifyReq req) {
        return boundaryService.verifyRectification(id, req.done(), req.note(), me());
    }

    /** 摊位图风险点（争议频发区域） */
    @GetMapping("/boundary-risk-points")
    public List<Map<String, Object>> boundaryRiskPoints() {
        return boundaryService.riskPoints();
    }

    // ================= 摊位图版本（重新测量后发布，新老摊主可见） =================
    @GetMapping("/stall-map-versions")
    public List<StallMapVersion> stallMapVersions() {
        return stallMapVersionRepository.findAllByOrderByCreatedAtDesc();
    }

    public record MapPublishReq(@NotBlank String scopeType, Long zoneId, String scopeLabel,
                                @NotBlank String imageUrl, @NotBlank String reason,
                                String changeSummary, String basisDisputeIds) {}

    @PostMapping("/stall-map-versions/publish")
    @Transactional
    public StallMapVersion publishMap(@RequestBody MapPublishReq req) {
        return boundaryService.publishMap(req.scopeType(), req.zoneId(), req.scopeLabel(),
                req.imageUrl(), req.reason(), req.changeSummary(), req.basisDisputeIds(), me());
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
