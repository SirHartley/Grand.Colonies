package grandcolonies.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventType;
import grandcolonies.plugins.ModPlugin;
import grandcolonies.memory.PageMemory;

import java.awt.*;
import java.util.List;

public class ButtonPressListener implements CampaignInputListener {
    private MarketAPI market;
    private boolean listenForMove = false;

    public ButtonPressListener(MarketAPI market) {
        this.market = market;
    }

    @Override
    public int getListenerInputPriority() {
        return 0;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        if (market.isPlanetConditionMarketOnly()) {
            ModPlugin.log("Aborting Grand.Colony implementation, clearing condition market of traces");
            ModPlugin.cleanupImpl(market);
            Global.getSector().getListenerManager().removeListener(this);
            return;
        }

        for (InputEventAPI input : events) {
            if (input.isConsumed()) continue;

            if (listenForMove && !input.getEventType().equals(InputEventType.KEY_DOWN)) {
                pressM();
                listenForMove = false;
                return;
            }

            if (input.getEventType().equals(InputEventType.KEY_DOWN)) {
                if (Character.toLowerCase(input.getEventChar()) == ModPlugin.TARGET_CHAR) {

                    PageMemory.get(market).showNext();

                    pressM();
                    listenForMove = true;
                    return;
                }
            }
        }
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {

    }

    public static void trigger(){
        int keyCode = java.awt.event.KeyEvent.getExtendedKeyCodeForChar(ModPlugin.TARGET_CHAR);

        try {
            Robot r = new Robot();

            r.keyPress(keyCode);
            r.keyRelease(keyCode);

        } catch (AWTException e) {
            e.printStackTrace();
        }

    }

    public static void pressM() {
        try {
            Robot r = new Robot();

            r.keyPress(77);
            r.keyRelease(77);

        } catch (AWTException e) {
            e.printStackTrace();
        }
    }
}
