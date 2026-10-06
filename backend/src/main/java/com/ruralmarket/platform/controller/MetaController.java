package com.ruralmarket.platform.controller;

import com.ruralmarket.platform.entity.MapRiskPoint;
import com.ruralmarket.platform.entity.MarketEvent;
import com.ruralmarket.platform.entity.StallMapVersion;
import com.ruralmarket.platform.entity.StallZone;
import com.ruralmarket.platform.repository.MapRiskPointRepository;
import com.ruralmarket.platform.repository.MarketEventRepository;
import com.ruralmarket.platform.repository.StallMapVersionRepository;
import com.ruralmarket.platform.repository.StallZoneRepository;
import com.ruralmarket.platform.service.Constants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 公共元数据：品类字典、报名中的集市、区域信息、已发布摊位图（新老摊主均可见调整依据） */
@RestController
@RequestMapping("/api/meta")
public class MetaController {
    private final MarketEventRepository eventRepository;
    private final StallZoneRepository zoneRepository;
    private final StallMapVersionRepository mapVersionRepository;
    private final MapRiskPointRepository riskRepository;

    public MetaController(MarketEventRepository eventRepository, StallZoneRepository zoneRepository,
                          StallMapVersionRepository mapVersionRepository, MapRiskPointRepository riskRepository) {
        this.eventRepository = eventRepository;
        this.zoneRepository = zoneRepository;
        this.mapVersionRepository = mapVersionRepository;
        this.riskRepository = riskRepository;
    }

    @GetMapping("/categories")
    public Map<String, String> categories() {
        return Constants.CATEGORIES;
    }

    @GetMapping("/events")
    public List<MarketEvent> events() {
        return eventRepository.findAllByOrderByMarketDateDesc();
    }

    @GetMapping("/zones")
    public List<StallZone> zones() {
        return zoneRepository.findAll();
    }

    /** 已发布摊位图版本历史（新老摊主都能看到每次调整依据），草稿不公开 */
    @GetMapping("/map-versions")
    public List<StallMapVersion> publishedMapVersions() {
        return mapVersionRepository.findByStatusOrderByPublishedAtDesc("PUBLISHED");
    }

    /** 当前生效摊位图 + 待处理风险点数（用于首页/报名页提示"下次集市前将重测"） */
    @GetMapping("/stall-map/current")
    public Map<String, Object> currentMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("current", mapVersionRepository.findFirstByStatusOrderByPublishedAtDesc("PUBLISHED").orElse(null));
        m.put("history", mapVersionRepository.findByStatusOrderByPublishedAtDesc("PUBLISHED"));
        m.put("openRiskCount", riskRepository.findByStatusOrderByCreatedAtDesc("OPEN").size());
        return m;
    }
}
