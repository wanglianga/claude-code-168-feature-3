package com.ruralmarket.platform.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 经营品类与业务常量 */
public class Constants {
    /** 品类代码 → 中文名 */
    public static final Map<String, String> CATEGORIES = new LinkedHashMap<>() {{
        put("VEG_FRUIT", "蔬菜水果");
        put("COOKED", "熟食卤味");
        put("PASTRY", "糕点");
        put("COLD_DISH", "凉菜");
        put("LIVE_POULTRY", "活禽");
        put("MEAT", "鲜肉");
        put("DAILY_GOODS", "日用百货");
        put("CLOTHING", "服装鞋帽");
        put("OTHER", "其他");
    }};

    /** 需要食品留样的品类（卤味、糕点、凉菜等） */
    public static final Set<String> SAMPLE_REQUIRED = Set.of("COOKED", "PASTRY", "COLD_DISH", "MEAT");

    /** 老摊主认定：历史经营次数 >= 3 且信用分 >= 60 */
    public static final int VETERAN_MIN_HISTORY = 3;
    public static final int VETERAN_MIN_CREDIT = 60;

    /** 摊位图风险点：同一区域边界争议累计达到该起数即标记为风险点，下次集市前需重新测量发布更新图 */
    public static final int BOUNDARY_RISK_THRESHOLD = 2;

    /** 认定超出抽签面积时默认扣减信用分 */
    public static final int BOUNDARY_DEFAULT_PENALTY = 5;

    /** 整改通知默认期限（小时） */
    public static final int RECTIFY_DEFAULT_DEADLINE_HOURS = 24;

    public static boolean isVeteran(int historyCount, int creditScore) {
        return historyCount >= VETERAN_MIN_HISTORY && creditScore >= VETERAN_MIN_CREDIT;
    }

    public static String categoryName(String code) {
        return CATEGORIES.getOrDefault(code, code == null ? "-" : code);
    }
}
