package com.ruralmarket.platform.controller;

import com.ruralmarket.platform.entity.MarketEvent;
import com.ruralmarket.platform.entity.StallMapVersion;
import com.ruralmarket.platform.entity.StallZone;
import com.ruralmarket.platform.repository.MarketEventRepository;
import com.ruralmarket.platform.repository.StallMapVersionRepository;
import com.ruralmarket.platform.repository.StallZoneRepository;
import com.ruralmarket.platform.service.Constants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 公共元数据：品类字典、集市、区域、已发布的摊位图版本（新老摊主均可查看调整依据） */
@RestController
@RequestMapping("/api/meta")
public class MetaController {
    private final MarketEventRepository eventRepository;
    private final StallZoneRepository zoneRepository;
    private final StallMapVersionRepository stallMapVersionRepository;

    public MetaController(MarketEventRepository eventRepository, StallZoneRepository zoneRepository,
                          StallMapVersionRepository stallMapVersionRepository) {
        this.eventRepository = eventRepository;
        this.zoneRepository = zoneRepository;
        this.stallMapVersionRepository = stallMapVersionRepository;
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

    /** 已发布的更新版摊位图（向新老摊主公开调整依据）；可按区域筛选 */
    @GetMapping("/stall-map-versions")
    public List<StallMapVersion> stallMapVersions(@RequestParam(required = false) Long zoneId) {
        List<StallMapVersion> published = stallMapVersionRepository.findByStatusOrderByCreatedAtDesc("PUBLISHED");
        if (zoneId != null) {
            return published.stream()
                    .filter(v -> v.getZone() != null && zoneId.equals(v.getZone().getId()))
                    .toList();
        }
        return published;
    }
}
