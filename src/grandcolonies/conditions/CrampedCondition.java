package grandcolonies.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class CrampedCondition extends BaseMarketConditionPlugin {
    public static final String CONDITION_ID = "GrandColonies_CrampedCondition";

    private float currentPenalty = 0f;
    private int currentBuildingsOverLimit = 0;

    public static void init(MarketAPI forMarket){
        if (!forMarket.hasCondition(CrampedCondition.CONDITION_ID))
            forMarket.addCondition(CrampedCondition.CONDITION_ID);
    }

    @Override
    public void apply(String id) {
        super.apply(id);

        if(Global.getSettings().getBoolean("GrandColonies_AddPenalty")) applyOverCapPenalty();
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        market.getUpkeepMult().unmodify(getModId());
    }

    private int getColonyBuildingAmt(){
        int total = 0;

        for (Industry ind : market.getIndustries()){
            if (ind.isHidden()){
                ind.setHidden(false);
                if(ind.isHidden()) continue;
                else {
                    total++;
                    ind.setHidden(true);
                }

            } else total++;
        }

        return total;
    }

    private void applyOverCapPenalty(){
        int amt = getColonyBuildingAmt();
        float penaltyPerBuilding = Global.getSettings().getFloat("GrandColonies_UpkeepPenaltyPerBuilding");
        int limit = Global.getSettings().getInt("GrandColonies_PenaltyThreshold");

        if(amt <= limit) {
            currentPenalty = 0;
            currentBuildingsOverLimit = 0;
            return;
        }

        float penalty = (float) Math.pow(penaltyPerBuilding, amt - limit);
        market.getUpkeepMult().modifyMult(getModId(), penalty, "Cramped Infrastructure");

        currentBuildingsOverLimit = amt - limit;
        currentPenalty = penalty;
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        if(currentBuildingsOverLimit > 0){
            tooltip.addPara("Too many buildings on this planet, limit exceeded by " + currentBuildingsOverLimit + ".", 10f, Misc.getHighlightColor(), currentBuildingsOverLimit + "");
            tooltip.addPara("All upkeep increased by %s" , 10f, Misc.getNegativeHighlightColor(), Math.round((currentPenalty-1)*100) + "%");
        }
    }

    @Override
    public boolean showIcon() {
        return Global.getSettings().getBoolean("GrandColonies_AddPenalty") && currentBuildingsOverLimit > 0;
    }
}
