package com.ruralmarket.platform.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 边界争议自动判定纯逻辑测试：
 * 以抽签登记面宽/进深为基准，含 5cm 测量容差，面宽或进深任一超出即判超面积。
 */
class DisputeServiceTest {

    private DisputeService.SideMeasure side(Double lw, Double ld, Double aw, Double ad) {
        return new DisputeService.SideMeasure(lw, ld, aw, ad);
    }

    @Test
    void withinToleranceIsNotOverrun() {
        // 抽签 3.0m，实测 3.05m，恰在 5cm 容差内，不算超
        DisputeService.SideMeasure m = side(3.0, 3.0, 3.05, 3.0);
        assertFalse(DisputeService.exceeds(m));
    }

    @Test
    void beyondToleranceWidthIsOverrun() {
        // 面宽实测 3.06m，超出容差 0.01m
        assertTrue(DisputeService.exceeds(side(3.0, 3.0, 3.06, 2.9)));
    }

    @Test
    void depthOverrunCounted() {
        // 面宽合规但进深超出
        assertTrue(DisputeService.exceeds(side(3.0, 2.5, 3.0, 2.7)));
    }

    @Test
    void missingActualMeasureNotOverrun() {
        // 实测数据缺失时不误判超面积（交管理员复核）
        assertFalse(DisputeService.exceeds(side(3.0, 3.0, null, 3.0)));
    }

    @Test
    void verdictClaimantOverruns() {
        DisputeService.SideMeasure a = side(3.0, 3.0, 3.6, 3.0);  // 主张方超面宽
        DisputeService.SideMeasure b = side(3.0, 3.0, 3.0, 3.0);
        assertEquals("A", DisputeService.verdict(a, b));
    }

    @Test
    void verdictRespondentOverruns() {
        DisputeService.SideMeasure a = side(3.0, 3.0, 3.0, 3.0);
        DisputeService.SideMeasure b = side(3.0, 2.5, 3.0, 2.9); // 被诉方进深超
        assertEquals("B", DisputeService.verdict(a, b));
    }

    @Test
    void verdictBothOverrun() {
        DisputeService.SideMeasure a = side(3.0, 3.0, 3.4, 3.0);
        DisputeService.SideMeasure b = side(3.0, 3.0, 3.0, 3.4);
        assertEquals("BOTH", DisputeService.verdict(a, b));
    }

    @Test
    void verdictNoneMeansSuspectUnclearMap() {
        // 双方均未超抽签面积 → 不应开整改扣分，转管理员复核（倾向抽签图不清晰）
        DisputeService.SideMeasure a = side(3.0, 3.0, 3.02, 3.02);
        DisputeService.SideMeasure b = side(3.0, 3.0, 3.01, 3.0);
        assertEquals("NONE", DisputeService.verdict(a, b));
    }

    @Test
    void verdictTextReadable() {
        assertEquals("被诉方超面积", DisputeService.verdictText("B"));
        assertEquals("双方均未超（疑图不清晰）", DisputeService.verdictText("NONE"));
    }
}
