package grandcolonies.memory;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.apache.log4j.Logger;

import java.util.*;

public class Page {
    int order;
    String marketID;
    private Set<String> entries = new LinkedHashSet<>();

    public Page(int order, MarketAPI market) {
        this.order = order;
        this.marketID = market.getId();
    }

    public void add(String industry){
        entries.add(industry);
    }

    public void remove(String industry){
        entries.remove(industry);
    }

    public List<String> getEntriesCopy(){
        return new ArrayList<>(entries);
    }

    public void display(boolean setVisible, PageMemory memory){
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketID);

        if (market == null) return;

        Logger log = Global.getLogger(Page.class);

        for (String id : getEntriesCopy()){
            if (!market.hasIndustry(id) || memory.getPermaInvisible().contains(id)){
                //industry no longer exists on market or is forbidden, auto-remove and move on
                remove(id);
                continue;
            }

            Industry ind = market.getIndustry(id);

            ind.setHidden(!setVisible);
        }
    }

    public boolean full(){
        return entries.size() > 11;
    }
}
