package grandcolonies.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import grandcolonies.plugins.ModPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import grandcolonies.memory.PageMemory;

import java.util.HashSet;
import java.util.Set;

import static grandcolonies.plugins.ModPlugin.*;

public class PageNumCondition extends BaseMarketConditionPlugin {
    public enum Page {
        ONE,
        TWO
    }

    private Set<String> unhideables = new HashSet<>();
    private int inCostructionQueue = 0;
    private boolean warn = false;

    public static void init(MarketAPI forMarket){
        if (!forMarket.hasCondition(ModPlugin.PAGE_CONDITION_ID)) {
            String token = forMarket.addCondition(ModPlugin.PAGE_CONDITION_ID);
            forMarket.getMemoryWithoutUpdate().set(ModPlugin.PAGE_CONDITION_KEY, token);
        }
    }

    public static PageNumCondition getCondition(MarketAPI market){
        MemoryAPI mem = market.getMemoryWithoutUpdate();
        if (!mem.contains(PAGE_CONDITION_KEY) || !market.hasCondition(PAGE_CONDITION_ID)) init(market);

        return ((PageNumCondition) market.getSpecificCondition(mem.getString(PAGE_CONDITION_KEY)).getPlugin());
    }

    public int getUnhideableCount(){
        return unhideables.size();
    }

    @Override
    public void apply(String id) {
        super.apply(id);

        Set<String> excl = PageMemory.get(market).getPermaInvisible();
        if (excl == null) {
            Global.getLogger(PageNumCondition.class).info("Grand.Colonies - Excluded Industries not initialized for " + market.getName());
            return;
        }

        Set<String> notHideable = new HashSet<>();
        clearUnhideableMessage();

        int totalInQueue = market.getConstructionQueue().getItems().size();

        //check if the industry can be hidden or not
        //skip any that are already hidden
        for (Industry ind : market.getIndustries()) {
            if (excl.contains(ind.getId())) continue;
            if (ind.isHidden()) continue;

            ind.setHidden(true);
            if (!ind.isHidden()) notHideable.add(ind.getId());
            ind.setHidden(false);
        }

        int totalPossibleOnFistPage = 12 - notHideable.size() - totalInQueue;

        //set cond todisplay warnings if the first page is filled with unhideables
        displayUnhideablesMessage(totalInQueue, notHideable, totalPossibleOnFistPage <= 0);
    }

    public void displayUnhideablesMessage(int inConstructionQueue, Set<String> unhideables, boolean warn){
        this.inCostructionQueue = inConstructionQueue;
        this.unhideables = unhideables;
        this.warn = warn;
    }

    public void clearUnhideableMessage(){
        unhideables.clear();
        inCostructionQueue = 0;
        warn = false;
    }

    private boolean isDocked(MarketAPI market){
        return market.getMemoryWithoutUpdate().getBoolean(DOCKED_KEY);
    }

    @Override
    public String getIconName() {
        if(!isDocked(market)) return Global.getSettings().getSpriteName("GrandColonies", "warn_low");
        if(warn) return Global.getSettings().getSpriteName("GrandColonies", "warn");

        String spriteName = null;

        try {
            spriteName = Global.getSettings().getSpriteName("GrandColonies", "0" + PageMemory.get(market).currentPage);
        } catch (Exception e){
            spriteName = Global.getSettings().getSpriteName("GrandColonies", "00");
        }

        return spriteName;
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        if(!isDocked(market)){
            tooltip.addPara("Unable to toggle pages while not docked at the colony!", Misc.getNegativeHighlightColor(), 10f);
            return;
        }

        String pgStr = PageMemory.get(market).currentPage + "";
        String charStr = Character.toString(ModPlugin.TARGET_CHAR).toUpperCase();

        tooltip.addPara("Currently on page " + pgStr + ".", 10f, Misc.getHighlightColor(), pgStr);
        tooltip.addPara("Press " + charStr + " to switch page.", 3f, Misc.getHighlightColor(), charStr);

        if(warn){
            tooltip.addPara("The total amount of items in the construction queue + unhideable buildings exceeds 12. Wait for some constructions " +
                    "to finish or remove an unhideable building to restore capacity.", Misc.getNegativeHighlightColor(), 10f);
        }

        if(unhideables.size() > 0) {
            String amt = unhideables.size() + " buildings";
            tooltip.addPara( amt + " can not be hidden.", 10f, Misc.getHighlightColor(), amt);
        }

        if (inCostructionQueue > 0){
            String amt = inCostructionQueue + " buildings";
            tooltip.addPara( amt + " in the construction queue can not be hidden.", unhideables.size() > 0 ? 3f : 10f, Misc.getHighlightColor(), amt);
        }
    }

    @Override
    public boolean showIcon() {
        return market.isPlayerOwned();
    }
}
