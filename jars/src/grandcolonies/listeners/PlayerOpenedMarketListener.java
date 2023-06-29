package grandcolonies.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import grandcolonies.conditions.CrampedCondition;
import grandcolonies.conditions.PageNumCondition;
import grandcolonies.plugins.ModPlugin;
import grandcolonies.memory.PageMemory;

import static grandcolonies.plugins.ModPlugin.EXCLUDED_IDS;

public class PlayerOpenedMarketListener extends BaseCampaignEventListener {
    public PlayerOpenedMarketListener(boolean permaRegister) {
        super(permaRegister);
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        if (market == null || !market.isPlayerOwned()) return;

        if (market.isPlanetConditionMarketOnly()) removeImpl(market);
        else setupImpl(market);
    }

    private void setupImpl(MarketAPI forMarket){
        Global.getSector().getListenerManager().addListener(new ButtonPressListener(forMarket), true);

        PageMemory memory = PageMemory.get(forMarket);
        setDocked(forMarket, true);
        memory.initOrUpdate();
        PageNumCondition.init(forMarket);
        CrampedCondition.init(forMarket);
        memory.displayPage(1);
    }

    private void removeImpl(MarketAPI fromMarket){
        ModPlugin.log("Clearing a condition market of Grand.Colony Traces");
        ModPlugin.cleanupImpl(fromMarket);
        Global.getSector().getListenerManager().removeListenerOfClass(ButtonPressListener.class);
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        if (!market.hasCondition(ModPlugin.PAGE_CONDITION_ID)) return;

        setDocked(market, false);
        Global.getSector().getListenerManager().removeListenerOfClass(ButtonPressListener.class);

        //reset everything visible and unset excluded
        PageMemory.get(market).resetVisibility();
        market.getMemoryWithoutUpdate().unset(EXCLUDED_IDS);
        if (!market.isPlayerOwned()) ModPlugin.cleanupImpl(market);
    }

    private void setDocked(MarketAPI market, boolean docked){
        if (docked) market.getMemoryWithoutUpdate().set(ModPlugin.DOCKED_KEY, true);
        else market.getMemoryWithoutUpdate().unset(ModPlugin.DOCKED_KEY);
    }
}
