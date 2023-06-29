package grandcolonies.memory;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import grandcolonies.conditions.PageNumCondition;
import grandcolonies.plugins.ModPlugin;

import java.util.*;

import static grandcolonies.plugins.ModPlugin.EXCLUDED_IDS;

//I set out to make the code not shit and somehow it's still shit but less retarded
// TODO: 28/05/2023 allow manual assignment of industries to pages via industry button (don't forget allowing to delete unused pages)

public class PageMemory {
    public static final String PAGE_MEMORY_KEY = "$GrandColoniesMarketPageMemory";
    private List<Page> pages = new LinkedList<>();
    private String marketID;
    public int currentPage = 1;
    private boolean isInitialized = false;

    public PageMemory(MarketAPI market) {
        this.marketID = market.getId();
    }

    public static PageMemory get(MarketAPI forMarket){
        PageMemory pageMemory = null;
        MemoryAPI marketMemory = forMarket.getMemoryWithoutUpdate();

        if (marketMemory.contains(PAGE_MEMORY_KEY)) pageMemory = (PageMemory) marketMemory.get(PAGE_MEMORY_KEY);
        else {
            pageMemory = new PageMemory(forMarket);
            marketMemory.set(PAGE_MEMORY_KEY, pageMemory);
        }

        return pageMemory;
    }

    public void clear(){
        pages.clear();
        isInitialized = false;

        MarketAPI market = getMarket();
        if (market != null) market.getMemoryWithoutUpdate().unset(PAGE_MEMORY_KEY);
    }

    //first time setup
    private void init(){
        updatePermaInvisibleIndustries();
        MarketAPI market = getMarket();
        Set<String> excluded = getPermaInvisible();

        List<String> industries = new ArrayList<>();
        for (Industry ind : market.getIndustries()) if (!excluded.contains(ind.getId())) industries.add(ind.getId());

        int pagesRequired = (int) Math.ceil(industries.size() / 12f);

        for (int i = 0; i < pagesRequired; i++){
            Page page = newPage();

            for (String id : new ArrayList<>(industries)){
                page.add(id);
                industries.remove(id);
            }
        }

        isInitialized = true;
    }

    public void initOrUpdate(){
        if (!isInitialized) init();
        else update();
    }

    //afterwards
    public void update(){
        if (getMarket() == null) return;

        updatePermaInvisibleIndustries();
        createOrDeletePageIfNeeded();
        autoSortIndustriesToPages();
    }

    public void createOrDeletePageIfNeeded(){
        if (pages.size() < 2) newPage();

        int visibleIndAmt = getMarket().getIndustries().size() - getPermaInvisible().size() + PageNumCondition.getCondition(getMarket()).getUnhideableCount();
        if (visibleIndAmt >= (pages.size() - 1) * 12 + 8) newPage();
        else if (pages.size() > 2 && visibleIndAmt < (pages.size() - 2) * 12 + 8) pages.remove(pages.size()-1);
    }

    public int getNextIndex(int from){
        int next = from + 1;
        if (next > pages.size()) next = 1;

        return next;
    }

    public void autoSortIndustriesToPages(){
        MarketAPI market = getMarket();
        Set<String> excluded = getPermaInvisible();

        List<String> industries = new ArrayList<>();
        for (Industry ind : market.getIndustries()) if (!excluded.contains(ind.getId())) industries.add(ind.getId());

        for (Page page : pages) {
            //cull down to 12 if somehow assigned more
            int safety = 0;
            while (page.getEntriesCopy().size() > 12 || safety > 100){
                List<String> entries = page.getEntriesCopy();
                page.remove(entries.get(entries.size()-1));
                safety++;
            }

            industries.removeAll(page.getEntriesCopy());
        }

        //what remains are the industries without a page

        for (Page page : pages){
            for (String ind : new ArrayList<>(industries)) {
                if (industries.isEmpty() || page.full()) break;

                page.add(ind);
                industries.remove(ind);
            }
        }
    }

    public void showNext(){
        int current = currentPage;
        resetVisibility();
        update();
        displayPage(getNextIndex(current));
    }

    public void displayPage(int i){
        for (Page page : pages){
            page.display(page.order == i, this);
        }

        currentPage = i;
    }

    public void resetVisibility() {
        for (Page page : pages){
            page.display(true, this);
        }
    }

    public Page newPage(){
        MarketAPI market = getMarket();

        int nextIndex = pages.size() + 1;
        Page page = new Page(nextIndex, market);

        pages.add(page);
        return page;
    }

    public List<Page> getPagesCopy(){
        return new ArrayList<>(pages);
    }

    public Page getPage(int i){
        for (Page page : pages){
            if (page.order == i) return page;
        }

        return null;
    }

    public MarketAPI getMarket(){
        return Global.getSector().getEconomy().getMarket(marketID);
    }

    public Set<String> getPermaInvisible(){
        MemoryAPI mem = getMarket().getMemoryWithoutUpdate();
        if (mem.contains(EXCLUDED_IDS)) return (Set<String>) mem.get(EXCLUDED_IDS);
        else return new HashSet<>();
    }

    public void updatePermaInvisibleIndustries(){
        Set<String> excl = new HashSet<>();
        MarketAPI market = getMarket();
        MemoryAPI mem = market.getMemoryWithoutUpdate();

        for (Industry ind : market.getIndustries()) if (ind.isHidden()) excl.add(ind.getId());
        mem.set(EXCLUDED_IDS, excl);
    }
}
