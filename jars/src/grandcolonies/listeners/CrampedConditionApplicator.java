package grandcolonies.listeners;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import grandcolonies.conditions.CrampedCondition;

public class CrampedConditionApplicator extends BaseCampaignEventListener {

    public CrampedConditionApplicator() {
        super(false);
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        super.reportPlayerOpenedMarket(market);
        if (market.isPlayerOwned() || Factions.PLAYER.equals(market.getFactionId())) {
            if (!market.hasCondition(CrampedCondition.CONDITION_ID)) market.addCondition(CrampedCondition.CONDITION_ID);
        }
    }
}
