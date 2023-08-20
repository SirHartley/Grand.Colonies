package grandcolonies.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.IndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import grandcolonies.memory.Page;
import grandcolonies.memory.PageMemory;
import grandcolonies.plugins.ModPlugin;

import java.util.ArrayList;
import java.util.List;

public class PageMovingOptionProvider extends BaseIndustryOptionProvider {
    public Object PAGE_FULL = new Object();
    public Object MOVE_ALLOWED = new Object();

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(PageMovingOptionProvider.class)) {
            listeners.addListener(new PageMovingOptionProvider(), true);
        }
    }

    @Override
    public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
        boolean notPlayerOwned = !ind.getMarket().isPlayerOwned();
        boolean notLegacy = !ModPlugin.LEGACY_MODE;

        return super.isUnsuitable(ind, allowUnderConstruction) || notPlayerOwned || notLegacy;
    }

    public List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (isUnsuitable(ind, false)) return null;

        List<IndustryOptionProvider.IndustryOptionData> result = new ArrayList<IndustryOptionData>();

        PageMemory memory = PageMemory.get(ind.getMarket());
        Page page = memory.getPage(memory.getNextIndex(memory.currentPage));
        boolean isFull = page == null || page.full();

        IndustryOptionData opt;
        if (!isFull) {
            opt = new IndustryOptionData("Move to next Page", MOVE_ALLOWED, ind, this);
            opt.color = Misc.getBasePlayerColor();
        } else {
            opt = new IndustryOptionData("Move to next Page", PAGE_FULL, ind, this);
            opt.color = Misc.getGrayColor();
        }

        result.add(opt);

        return result;
    }

    @Override
    public void createTooltip(IndustryOptionProvider.IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id == MOVE_ALLOWED) tooltip.addPara("Moves this building to the next page.", 0f);
        else if (opt.id == PAGE_FULL) tooltip.addPara("The next page is full - move some buildings to make space.", 0f);
    }

    @Override
    public void optionSelected(IndustryOptionProvider.IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id == MOVE_ALLOWED) {
            PageMemory memory = PageMemory.get(opt.ind.getMarket());
            Industry ind = opt.ind;

            if (memory != null) {
                int next = memory.getNextIndex(memory.currentPage);
                memory.getPage(memory.currentPage).remove(ind.getId());
                memory.getPage(next).add(ind.getId());
                memory.getPage(memory.currentPage).display(true, memory);

                ButtonPressListener.trigger();
            }
        }
    }
}
