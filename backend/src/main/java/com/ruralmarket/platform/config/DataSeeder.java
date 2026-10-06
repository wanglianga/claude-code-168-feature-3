package com.ruralmarket.platform.config;

import com.ruralmarket.platform.entity.*;
import com.ruralmarket.platform.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 首次启动初始化：账号、区域、摊位、一个报名中的赶集日、首版摊位图 */
@Component
public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final StallZoneRepository zoneRepository;
    private final StallRepository stallRepository;
    private final MarketEventRepository eventRepository;
    private final StallMapVersionRepository mapVersionRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public DataSeeder(UserRepository userRepository, StallZoneRepository zoneRepository,
                      StallRepository stallRepository, MarketEventRepository eventRepository,
                      StallMapVersionRepository mapVersionRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.zoneRepository = zoneRepository;
        this.stallRepository = stallRepository;
        this.eventRepository = eventRepository;
        this.mapVersionRepository = mapVersionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) return;

        // ===== 账号 =====
        createUser("admin", "admin123", "ADMIN", "乡镇管理员", "13800000001", 0, null);
        createUser("staff1", "staff123", "STAFF", "市场工作人员-刘", "13800000002", 0, null);
        createUser("vendor1", "vendor123", "VENDOR", "王老二(熟食)", "13900000001", 5, "常年在本集市售卖卤味，证照齐全");
        createUser("vendor2", "vendor123", "VENDOR", "李大嫂(糕点)", "13900000002", 4, "连续三年经营手工糕点");
        createUser("vendor3", "vendor123", "VENDOR", "张老三(活禽)", "13900000003", 2, "近两年开始售卖活禽");
        createUser("vendor4", "vendor123", "VENDOR", "赵四(果蔬)", "13900000004", 6, "老菜农，自产自销蔬菜水果");
        createUser("vendor5", "vendor123", "VENDOR", "钱五(百货)", "13900000005", 1, "新入驻日用百货摊主");
        createUser("vendor6", "vendor123", "VENDOR", "孙六(凉菜)", "13900000006", 3, "经营凉菜三年");

        // ===== 区域 =====
        StallZone zoneA = createZone("A", "A区-综合百货区", "DAILY_GOODS,CLOTHING,OTHER", false, false, "20", 0, "靠近集市东门");
        StallZone zoneB = createZone("B", "B区-熟食冷链区", "COOKED,PASTRY,COLD_DISH,MEAT", true, false, "30", 30, "配备冷链电源");
        StallZone zoneC = createZone("C", "C区-活禽隔离区", "LIVE_POULTRY", false, true, "25", 60, "独立隔离，远离食品区");
        StallZone zoneD = createZone("D", "D区-果蔬区", "VEG_FRUIT", false, false, "15", 0, "靠近集市西门");

        // ===== 摊位（含抽签登记面宽×进深，作为边界争议判定基准，单位米）=====
        for (int i = 1; i <= 8; i++) {
            createStall(zoneA, "A-" + pad(i), i <= 4, 5.0, i == 8, true, 3.0, 3.0);
        }
        for (int i = 1; i <= 6; i++) {
            createStall(zoneB, "B-" + pad(i), true, 4.0, i == 6, true, 3.0, 2.5);
        }
        for (int i = 1; i <= 4; i++) {
            createStall(zoneC, "C-" + pad(i), false, 6.0, false, true, 3.5, 3.0);
        }
        for (int i = 1; i <= 8; i++) {
            createStall(zoneD, "D-" + pad(i), false, 5.0, i == 1, true, 3.0, 3.0);
        }
        // 消防通道：不启用、不参与分配
        createStall(zoneA, "FIRE-01", false, null, true, false, null, null);

        // ===== 赶集日 =====
        MarketEvent event = new MarketEvent();
        event.setName("十月十五大集");
        event.setMarketDate(LocalDate.of(2026, 10, 15));
        event.setStatus("OPEN");
        event.setRemark("报名后由市场管理审核，审核通过参与统一抽签");
        eventRepository.save(event);

        MarketEvent next = new MarketEvent();
        next.setName("十月廿九大集");
        next.setMarketDate(LocalDate.of(2026, 10, 29));
        next.setStatus("DRAFT");
        eventRepository.save(next);

        // ===== 首版摊位图（基线，向新老摊主公开）=====
        seedBaselineMap();
    }

    /** 生成一张基线摊位图 SVG 到上传目录，并登记为 v1 已发布版本 */
    private void seedBaselineMap() {
        try {
            Path dir = Path.of(uploadDir).toAbsolutePath();
            Files.createDirectories(dir);
            String fileName = "stallmap-v1.svg";
            if (!Files.exists(dir.resolve(fileName))) {
                String svg = """
                        <svg xmlns='http://www.w3.org/2000/svg' width='900' height='420' font-family='sans-serif' font-size='13'>
                          <rect width='900' height='420' fill='#f5f7fa'/>
                          <text x='20' y='30' font-size='18' font-weight='bold'>集市摊位图 v1（基线）</text>
                          <g fill='#e8f1ff' stroke='#409eff' stroke-width='1.5'>
                            <rect x='40' y='60' width='180' height='140' rx='6'/><text x='55' y='85' fill='#0b3d91'>A区 综合百货 A-01~A-08</text>
                            <rect x='250' y='60' width='180' height='140' rx='6'/><text x='265' y='85' fill='#7a3d00'>B区 熟食冷链 B-01~B-06</text>
                            <rect x='460' y='60' width='180' height='140' rx='6'/><text x='475' y='85' fill='#8b1a1a'>C区 活禽隔离 C-01~C-04</text>
                            <rect x='670' y='60' width='180' height='140' rx='6'/><text x='685' y='85' fill='#1d6b2c'>D区 果蔬 D-01~D-08</text>
                          </g>
                          <g stroke='#909399' stroke-dasharray='6 5' stroke-width='2'>
                            <line x1='40' y1='230' x2='850' y2='230'/><text x='40' y='252' fill='#606266'>主通道（宽 3.0m，禁止占道堆物）</text>
                          </g>
                          <text x='40' y='290' fill='#909399'>每个摊位抽签登记面积：A/D 3.0m×3.0m，B 3.0m×2.5m，C 3.5m×3.0m</text>
                          <text x='40' y='315' fill='#909399'>边界以本图为准；如图纸不清引发争议，管理员将重新测量并发布新版本，调整依据对全体摊主公开。</text>
                        </svg>
                        """;
                Files.writeString(dir.resolve(fileName), svg);
            }
            User admin = userRepository.findByUsername("admin").orElse(null);
            StallMapVersion v = new StallMapVersion();
            v.setVersionNo("v1");
            v.setImageUrl("/files/" + fileName);
            v.setChangeReason("建市基线摊位图：明确 A/B/C/D 各区摊位边界、抽签登记面积与主通道宽度。");
            v.setChangeDetails("[]");
            v.setStatus("PUBLISHED");
            v.setPublisher(admin);
            v.setPublishedAt(LocalDateTime.now());
            mapVersionRepository.save(v);
        } catch (Exception e) {
            throw new RuntimeException("初始化基线摊位图失败", e);
        }
    }

    private String pad(int i) {
        return String.format("%02d", i);
    }

    private void createUser(String username, String password, String role, String name,
                            String phone, int historyCount, String historyNotes) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(password));
        u.setRole(role);
        u.setName(name);
        u.setPhone(phone);
        u.setHistoryCount(historyCount);
        u.setHistoryNotes(historyNotes);
        userRepository.save(u);
    }

    private StallZone createZone(String code, String name, String categories, boolean cooked,
                                 boolean poultry, String fee, int offset, String remark) {
        StallZone z = new StallZone();
        z.setCode(code);
        z.setName(name);
        z.setAllowedCategories(categories);
        z.setAllowsCookedFood(cooked);
        z.setAllowsLivePoultry(poultry);
        z.setBaseFee(new BigDecimal(fee));
        z.setEntryOffsetMinutes(offset);
        z.setRemark(remark);
        return zoneRepository.save(z);
    }

    private void createStall(StallZone zone, String no, boolean power, Double maxLen,
                             boolean nearFire, boolean active, Double lotWidth, Double lotDepth) {
        Stall s = new Stall();
        s.setZone(zone);
        s.setStallNo(no);
        s.setHasPower(power);
        s.setMaxVehicleLength(maxLen);
        s.setNearFireLane(nearFire);
        s.setActive(active);
        s.setLotWidth(lotWidth);
        s.setLotDepth(lotDepth);
        stallRepository.save(s);
    }
}
