package com.ruralmarket.platform.controller;

import com.ruralmarket.platform.config.AuthUser;
import com.ruralmarket.platform.config.BizException;
import com.ruralmarket.platform.entity.*;
import com.ruralmarket.platform.repository.*;
import com.ruralmarket.platform.service.AuditService;
import com.ruralmarket.platform.service.BoundaryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 市场工作人员端：扫码入场核验、巡查记录、留样查看、边界争议现场登记 */
@RestController
@RequestMapping("/api/staff")
public class StaffController {
    private final LotteryResultRepository resultRepository;
    private final EntryCheckRepository entryCheckRepository;
    private final PatrolRecordRepository patrolRepository;
    private final FoodSampleRepository sampleRepository;
    private final MarketEventRepository eventRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BoundaryDisputeRepository boundaryDisputeRepository;
    private final BoundaryService boundaryService;
    private final AuditService audit;

    public StaffController(LotteryResultRepository resultRepository, EntryCheckRepository entryCheckRepository,
                           PatrolRecordRepository patrolRepository, FoodSampleRepository sampleRepository,
                           MarketEventRepository eventRepository, UserRepository userRepository,
                           PaymentRepository paymentRepository,
                           BoundaryDisputeRepository boundaryDisputeRepository,
                           BoundaryService boundaryService, AuditService audit) {
        this.resultRepository = resultRepository;
        this.entryCheckRepository = entryCheckRepository;
        this.patrolRepository = patrolRepository;
        this.sampleRepository = sampleRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.boundaryDisputeRepository = boundaryDisputeRepository;
        this.boundaryService = boundaryService;
        this.audit = audit;
    }

    private User me() {
        return userRepository.findById(AuthUser.current().id()).orElseThrow();
    }

    /** 当日摊位分配总览（含入场核验状态） */
    @GetMapping("/events/{eventId}/assignments")
    public List<Map<String, Object>> assignments(@PathVariable Long eventId) {
        return resultRepository.findByApplicationEventId(eventId).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("result", r);
            m.put("entryCheck", entryCheckRepository.findByResultId(r.getId()).orElse(null));
            m.put("patrolCount", patrolRepository.findByResultId(r.getId()).size());
            return m;
        }).toList();
    }

    /** 扫码（输入令牌）查询摊位分配信息 */
    @GetMapping("/entry/scan/{token}")
    public Map<String, Object> scan(@PathVariable String token) {
        LotteryResult r = resultRepository.findByQrToken(token)
                .orElseThrow(() -> new BizException("无效二维码令牌"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("result", r);
        m.put("entryCheck", entryCheckRepository.findByResultId(r.getId()).orElse(null));
        m.put("payments", paymentRepository.findByResultId(r.getId()));
        return m;
    }

    /** 入场核验登记：拍摄摊位布置、确认实际品类与是否按位摆摊 */
    public record EntryCheckReq(@NotNull Long resultId, String photos, String actualCategory,
                                Boolean atAssignedStall, String notes) {}

    @PostMapping("/entry/check")
    @Transactional
    public EntryCheck entryCheck(@RequestBody EntryCheckReq req) {
        LotteryResult r = resultRepository.findById(req.resultId())
                .orElseThrow(() -> new BizException("分配单不存在"));
        entryCheckRepository.findByResultId(r.getId()).ifPresent(c -> {
            throw new BizException("该摊位已完成入场核验，请勿重复登记");
        });
        EntryCheck check = new EntryCheck();
        check.setResult(r);
        check.setStaff(me());
        check.setPhotos(req.photos());
        check.setActualCategory(req.actualCategory());
        check.setAtAssignedStall(req.atAssignedStall() == null || req.atAssignedStall());
        check.setNotes(req.notes());
        EntryCheck saved = entryCheckRepository.save(check);
        User staff = me();
        audit.log(staff.getId(), staff.getName(), "ENTRY_CHECK",
                "摊位[" + r.getStallNo() + "] 摊主[" + r.getApplication().getVendor().getName()
                + "] 入场核验，按位摆摊=" + saved.getAtAssignedStall());
        return saved;
    }

    /** 巡查登记 */
    public record PatrolReq(@NotNull Long eventId, @NotNull Long resultId, @NotNull Integer hygieneScore,
                            Boolean priceMarked, Boolean shortWeight, Boolean occupyingLane,
                            Boolean electricSafe, String photos, String notes) {}

    @PostMapping("/patrols")
    @Transactional
    public PatrolRecord patrol(@RequestBody PatrolReq req) {
        MarketEvent event = eventRepository.findById(req.eventId())
                .orElseThrow(() -> new BizException("集市不存在"));
        LotteryResult r = resultRepository.findById(req.resultId())
                .orElseThrow(() -> new BizException("分配单不存在"));
        PatrolRecord p = new PatrolRecord();
        p.setEvent(event);
        p.setResult(r);
        p.setInspector(me());
        p.setHygieneScore(req.hygieneScore());
        p.setPriceMarked(req.priceMarked() == null || req.priceMarked());
        p.setShortWeight(Boolean.TRUE.equals(req.shortWeight()));
        p.setOccupyingLane(Boolean.TRUE.equals(req.occupyingLane()));
        p.setElectricSafe(req.electricSafe() == null || req.electricSafe());
        p.setPhotos(req.photos());
        p.setNotes(req.notes());
        PatrolRecord saved = patrolRepository.save(p);
        User inspector = me();
        audit.log(inspector.getId(), inspector.getName(), "PATROL",
                "摊位[" + r.getStallNo() + "] 巡查：卫生" + req.hygieneScore() + "分"
                + (saved.getShortWeight() ? "，发现缺斤少两" : "") + (saved.getOccupyingLane() ? "，占道" : ""));
        return saved;
    }

    @GetMapping("/events/{eventId}/patrols")
    public List<PatrolRecord> patrols(@PathVariable Long eventId) {
        return patrolRepository.findByEventIdOrderByCreatedAtDesc(eventId);
    }

    @GetMapping("/events/{eventId}/samples")
    public List<FoodSample> samples(@PathVariable Long eventId) {
        return sampleRepository.findByEventId(eventId);
    }

    // ================= 摊位边界争议（现场拍照 + 标注实际边界） =================

    /** 某集市的边界争议列表（工作人员可查看处理进度） */
    @GetMapping("/events/{eventId}/boundary-disputes")
    public List<BoundaryDispute> boundaryDisputes(@PathVariable Long eventId) {
        return boundaryDisputeRepository.findByEventIdOrderByCreatedAtDesc(eventId);
    }

    /** 登记边界争议：两个相邻摊主、现场照片、标注的实际边界 */
    public record BoundaryReq(@NotNull Long eventId, @NotNull Long resultAId, Long resultBId, Long stallId,
                              @NotBlank String description, String scenePhotos,
                              String actualBoundaryNote, String actualBoundaryData) {}

    @PostMapping("/boundary-disputes")
    @Transactional
    public BoundaryDispute registerBoundary(@RequestBody BoundaryReq req) {
        return boundaryService.register(req.eventId(), req.resultAId(), req.resultBId(), req.stallId(),
                req.description(), req.scenePhotos(), req.actualBoundaryNote(), req.actualBoundaryData(), me());
    }
}
