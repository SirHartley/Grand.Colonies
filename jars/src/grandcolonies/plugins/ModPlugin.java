package grandcolonies.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import grandcolonies.listeners.CrampedConditionApplicator;
import grandcolonies.listeners.IndustryPanelReplacer;
import grandcolonies.listeners.PageMovingOptionProvider;
import grandcolonies.listeners.PlayerOpenedMarketListener;
import grandcolonies.memory.PageMemory;
import org.apache.log4j.Logger;

public class ModPlugin extends BaseModPlugin {

    public static final Logger log = Global.getLogger(ModPlugin.class);
    public static final char TARGET_CHAR = Global.getSettings().getString("GrandColonies_TogglePageButton").charAt(0);
    public static final boolean LEGACY_MODE = Global.getSettings().getBoolean("GrandColonies_LegacyMode");
    public static final String PAGE_CONDITION_ID = "GrandColonies_pageCond";
    public static final String PAGE_CONDITION_KEY = "$GrandColonies_pageCond";
    public static final String DOCKED_KEY = "$GrandColonies_docked";
    public static final String EXCLUDED_IDS = "$GrandColonies_excl";
    public static final String WAS_LEGACY_MODE = "$GrandColonies_legacy";
    
    public static void log(String s){
        log.info(s);
    }

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (LEGACY_MODE) {
            mem.set(WAS_LEGACY_MODE, true);
            PageMovingOptionProvider.register();
            Global.getSector().addTransientListener(new PlayerOpenedMarketListener(false));
            Global.getSector().addTransientScript(new IndustryPanelReplacer());
        } else {
            if (!mem.contains(WAS_LEGACY_MODE) || mem.getBoolean(WAS_LEGACY_MODE)) cleanupAll();
            Global.getSector().addTransientListener(new CrampedConditionApplicator());
            Global.getSector().addTransientScript(new IndustryPanelReplacer());
        }
    }

    public static void cleanupAll(){
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()){
            cleanupImpl(m);
        }
    }

    public static void cleanupImpl(MarketAPI market) {
        MemoryAPI mem = market.getMemoryWithoutUpdate();
        mem.unset(EXCLUDED_IDS);
        market.removeCondition(PAGE_CONDITION_ID);
        mem.unset(PAGE_CONDITION_KEY);
        PageMemory.get(market).clear();
    }
}
