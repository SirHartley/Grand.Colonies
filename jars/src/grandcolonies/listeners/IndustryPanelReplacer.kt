package grandcolonies.listeners

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.CampaignState
import com.fs.starfarer.campaign.ui.marketinfo.IndustryListPanel
import com.fs.state.AppDriver
import grandcolonies.plugins.ModPlugin
import org.lazywizard.lazylib.MathUtils
import java.lang.Exception
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

class IndustryPanelReplacer : EveryFrameScript {

    var frames = 0
    var newIndustryPanel: CustomPanelAPI? = null
    var element: TooltipMakerAPI? = null
    var previousOffset = 0f

    var rowsCurrentFrame = 0
    var rowsLastFrame = 0
    var widgetSizeLastFrame = 0f

    companion object {
        var reset = false
    }

    init {
        reset = false
    }

    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }


    override fun advance(amount: Float) {
        frames++
        frames = MathUtils.clamp(frames, 0, 10)

        //Returns if not paused, so that this doesnt run while the player isnt in any UI screen.
        if (!Global.getSector().isPaused)
        {
            previousOffset = 0f
            return
        }

        if (ModPlugin.LEGACY_MODE && (Global.getSector().campaignUI.isShowingDialog || Global.getSector().campaignUI.currentCoreTab == null)) return

        //Wait a few frames to ensure there is no attempt at getting panels before the campaign ui loaded
        if (frames < 2) return

        var state = AppDriver.getInstance().currentState

        //Makes sure that the current state is the campaign state.
        if (state !is CampaignState) return

        //Gets the Campaigns Main UI Panel that all UI is attached to.
        // var core = state.core
        var core = invokeMethod("getCore", state)

        var managementPanel: UIPanelAPI? = null
        var industryPanel: UIPanelAPI? = null

        //The Following block gets both relevant panels by doing a deep dive through the core panel.
        //To find the required classes i used VisualVM, which made it a lot easier to find out where specificly the required panels can be accessed from.

        //Gets the main dialog panel.
        //var dialogCore = state.encounterDialog?.coreUI?.currentTab?.childrenCopy?.find { hasMethodOfName("getOutpostPanelParams", it) }

        var dialog = invokeMethod("getEncounterDialog", state)
        if (dialog != null)
        {
            var dCore = invokeMethod("getCoreUI", dialog)
            if (dCore != null)
            {
                var tab = invokeMethod("getCurrentTab", dCore!!)

                if (tab is UIPanelAPI)
                {
                    var dialogCore = tab.getChildrenCopy().find { hasMethodOfName("getOutpostPanelParams", it) }

                    if (dialogCore is UIPanelAPI)
                    {
                        //Gets the subpanel that holds the management panel
                        var dialogSubcore = dialogCore.getChildrenCopy().find { hasMethodOfName("showOverview", it) }

                        //Attempts to get the management panel
                        if (dialogSubcore is UIPanelAPI)
                        {
                            managementPanel = dialogSubcore.getChildrenCopy().find { hasMethodOfName("recreateWithEconUpdate", it) } as UIPanelAPI?
                            if (managementPanel == null)
                            {
                                previousOffset = 0f
                                return
                            }
                            if (managementPanel.getChildrenCopy().find { it == newIndustryPanel } == null)
                            {
                                industryPanel = managementPanel.getChildrenCopy().find { it is IndustryListPanel } as IndustryListPanel
                            }
                            else
                            {
                                previousOffset = 0f
                            }
                        }
                    }
                }
            }
        }


        //There is a copy of the panel that can be found from the command window, to get this one we can do the same as above, but instead going from core directly, instead of the
        //secondary core attached to a dialog.
        if (core != null)
        {
            var tab = invokeMethod("getCurrentTab", core)
            if (tab is UIPanelAPI)
            {
                var intelCore = tab.getChildrenCopy()?.find { hasMethodOfName("getOutpostPanelParams", it) }
                if (intelCore is UIPanelAPI)
                {
                    //Gets the subpanel that holds the management panel
                    var intelSubcore = intelCore.getChildrenCopy().find { hasMethodOfName("showOverview", it) }

                    //Attempts to get the management panel
                    if (intelSubcore is UIPanelAPI)
                    {
                        managementPanel = intelSubcore.getChildrenCopy().find { hasMethodOfName("recreateWithEconUpdate", it) } as UIPanelAPI?
                        if (managementPanel == null)
                        {
                            previousOffset = 0f
                            return
                        }
                        if (managementPanel.getChildrenCopy().find { it == newIndustryPanel } == null)
                        {
                            industryPanel = managementPanel.getChildrenCopy().find { it is IndustryListPanel } as IndustryListPanel
                        }
                        else
                        {
                            previousOffset = 0f
                        }
                    }
                }
            }
        }


        //Check to see if both required panels have been found
        if (industryPanel != null && managementPanel != null)
        {
            //Gets the previous scroller if there was one before, this makes sure that you dont have to scroll back down each time the panel updates.f
            if (element != null)
            {
                previousOffset = element!!.externalScroller.yOffset
            }


            var screenWidth = Global.getSettings().screenWidth
            var screenHeight = Global.getSettings().screenHeight

            //Create the replacement panel.
            newIndustryPanel = Global.getSettings().createCustom(830f, 400f, null)

            managementPanel.addComponent(newIndustryPanel)
            //newIndustryPanel!!.position.inTL(90f, 120f)
            newIndustryPanel!!.position.belowLeft(managementPanel.getChildrenCopy().get(0), 20f)

            //Create the replacement element that will hold all industry widgets
            element = newIndustryPanel!!.createUIElement(830f, 400f, true)

            //Gets the current market
            var market = getPrivateVariable("market", industryPanel) as MarketAPI

            //Gets all Industry Widgets one by one
            var widgets = getAllWidgets(market, industryPanel)

            //Removes the vanilla panel that holds all widgets. We do this so that we can steal its widgets for our own, keeping it remaining would cause issues to the widgets.
            managementPanel.removeComponent(industryPanel)

            var count = 0
            var lastElement: UIComponentAPI? = null
            var lastFirstElement: UIComponentAPI? = null
            var totalSpace = 0f
            for (widget in widgets)
            {
                //Creates a panel that will hold the widget with zero width or height, otherwise each entry would add to the scrollers spacer, but we dont want this since
                //we addd multiple widgets per row, so we instead space it manually.
                var widgetPanel = Global.getSettings().createCustom(0f, 0f, null)
                element!!.addCustom(widgetPanel, 0f)

                //Add the Widget that we took from the original panel and add it to our own.
                widgetPanel!!.addComponent(widget)
                widget.position.inTL(0f, 0f)

                //Places the first element and sets the required spacing
                if (lastElement == null)
                {
                    widgetSizeLastFrame = widget.position.height + 25f
                    lastFirstElement = widgetPanel
                    element!!.addSpacer(widget.position.height + 25f)
                    totalSpace += widget.position.height + 25f
                }

                //Every 4th widget will be placed below the first widget of the previous line
                else if (count == 3)
                {
                    rowsCurrentFrame++
                    widgetPanel.position.belowLeft(lastFirstElement, widget.position.height + 25f)
                    lastFirstElement = widgetPanel
                    element!!.addSpacer(widget.position.height + 25f)
                    totalSpace += widget.position.height + 25f

                    count = 0
                }

                //Every other Widget will be placed to the right of the previous one
                else
                {
                    widgetPanel.position.rightOfMid(lastElement, widget.position.width + 20f)
                    count++
                }

                lastElement = widgetPanel
            }

            //Re-Adds the Build Button, which was removed since we had to remove the whole industry panel.
            var lastPanel: UIComponentAPI? = null
            var build = (industryPanel as UIPanelAPI).getChildrenCopy().find { hasMethodOfName("setEnabled", it) }
            if (build is UIComponentAPI)
            {

                var buttonPanel = Global.getSettings().createCustom(200f, build.position.height, null)
                managementPanel.addComponent(buttonPanel)
                var buttonElement = buttonPanel!!.createUIElement(200f, build.position.height, false)
                buttonPanel.position.belowLeft(newIndustryPanel, 20f)
                buttonPanel.addUIElement(buttonElement)

                buttonElement.addCustom(build, 0f)
                lastPanel = buttonPanel
            }

            //Re-Adds both paragraphs to the right of the Build button
            var first = true
            for (child in (industryPanel as UIPanelAPI).getChildrenCopy().filter { hasMethodOfName("createMaxIndustriesLabel", it) })
            {
                var paragraphPanel = Global.getSettings().createCustom(child.position.width, child.position.height, null)
                managementPanel.addComponent(paragraphPanel)
                var buttonElement = paragraphPanel!!.createUIElement(child.position.width, child.position.height, false)
                if (first)
                {
                    first = false
                    paragraphPanel.position.rightOfMid(lastPanel, 200f )

                }
                else
                {
                    paragraphPanel.position.rightOfMid(lastPanel, 50f)
                }
                paragraphPanel.addUIElement(buttonElement)

                lastPanel = paragraphPanel

                buttonElement.addCustom(child, 0f)
            }

            //Finishes setting up the scroller, has to be called last as anything added to a scroller needs to be done before this call.
            newIndustryPanel!!.addUIElement(element)


            //Makes the scroller move up/down when a row has been added or removed.
            if (rowsLastFrame == 0)
            {
                element!!.externalScroller.yOffset = previousOffset
            }
            else if (rowsCurrentFrame < rowsLastFrame)
            {
                element!!.externalScroller.yOffset = MathUtils.clamp(previousOffset - widgetSizeLastFrame, 0f, 10000f)
            }
            else if (rowsCurrentFrame > rowsLastFrame)
            {
                element!!.externalScroller.yOffset = MathUtils.clamp(previousOffset + widgetSizeLastFrame, 0f, 10000f)
            }
            else
            {
                element!!.externalScroller.yOffset = previousOffset
            }
            rowsLastFrame = rowsCurrentFrame
            rowsCurrentFrame = 0

            //Set the scroller position to the value aqquired earlier
            // element!!.externalScroller.yOffset = MathUtils.clamp(previousOffset, 0f, element!!.position.height)

        }

        if (managementPanel == null)
        {
            rowsLastFrame = 0
            rowsCurrentFrame = 0
            previousOffset = 0f
            element = null
            newIndustryPanel = null
        }

    }



    //Gets all Widgets
    //This is done by stealing them from the original Industry Panel.

    //However the industry panel is limited by the fact that it will only return 12 widgets
    //To get around this, we set all industries that arent Hidden as Hidden, then one by one enable a widget, reload the panel with "panel.sizeChanged()", add the widget it generates to a list,
    //then hide the industry again and wait for the next one.

    //Could be made simpler by just instantiating the widget class myself.

    //Same principal for anything on the Construction List.

    private fun getAllWidgets(market: MarketAPI, panel: UIPanelAPI) : List<UIComponentAPI>
    {

        var industries = market.industries.filter { !it.isHidden }.distinctBy { it.spec.id }.sortedBy { it.spec.order }
        industries.forEach { it.isHidden = true }

        //var widgets: MutableMap<String, UIComponentAPI> = LinkedHashMap()
        var widgets = ArrayList<UIPanelAPI>()
        invokeMethod("sizeChanged", panel, panel.position.width, panel.position.height)
        //panel.sizeChanged(panel.position.width, panel.position.height)

        var construction = ArrayList(market.constructionQueue.items)
        market.constructionQueue.items.clear()

        for (industry in industries)
        {
            industry.isHidden = false
            invokeMethod("sizeChanged", panel, panel.position.width, panel.position.height)
            // panel.sizeChanged(panel.position.width, panel.position.height)
            widgets.addAll(invokeMethod("getWidgets", panel) as List<UIPanelAPI>)
            // widgets.addAll(panel.widgets)
            industry.isHidden = true
        }

        for (entry in construction)
        {
            market.constructionQueue.items.add(entry)
            invokeMethod("sizeChanged", panel, panel.position.width, panel.position.height)
            //panel.sizeChanged(panel.position.width, panel.position.height)
            widgets.addAll(invokeMethod("getWidgets", panel) as List<UIPanelAPI>)
            //widgets.addAll(panel.widgets)
            market.constructionQueue.items.remove(entry)
        }

        var filteredWidgets = ArrayList<UIPanelAPI>()

        var IDS = industries.map { it.spec.id } + construction.map { it.id }

        for (widget in widgets)
        {
            var widgetName = getWidgetIndustry(widget, IDS)
            var contains = false
            for (filt in filteredWidgets)
            {
                if (getWidgetIndustry(filt, IDS) == widgetName)
                {
                    contains = true
                }
            }

            if (!contains)
            {
                filteredWidgets.add(widget)
            }
        }

        market.constructionQueue.items.addAll(construction)
        industries.forEach { it.isHidden = false }

        return filteredWidgets
    }

    fun getWidgetIndustry(widget: UIPanelAPI, industries: List<String>) : String
    {
        for (child in widget.getChildrenCopy())
        {
            if (child is LabelAPI && industries.map { Global.getSettings().getIndustrySpec(it).name }.contains(child.text.trim()) )
            {
                return child.text
            }
        }
        return ""
    }

    //Used to be able to find specific files without having to reference their obfuscated class name.
    private fun hasMethodOfName(name: String, instance: Any) : Boolean {
        val methodClass = Class.forName("java.lang.reflect.Method", false, Class::class.java.classLoader)
        val getNameMethod = MethodHandles.lookup().findVirtual(methodClass, "getName", MethodType.methodType(String::class.java))

        val instancesOfMethods: Array<out Any> = instance.javaClass.getDeclaredMethods()
        return instancesOfMethods.any { getNameMethod.invoke(it) == name }
    }

    //Required to execute obfuscated methods without referencing their obfuscated class name.
    private fun invokeMethod(methodName: String, instance: Any, vararg arguments: Any?) : Any?
    {
        val methodClass = Class.forName("java.lang.reflect.Method", false, Class::class.java.classLoader)
        val getNameMethod = MethodHandles.lookup().findVirtual(methodClass, "getName", MethodType.methodType(String::class.java))
        val invokeMethod = MethodHandles.lookup().findVirtual(methodClass, "invoke", MethodType.methodType(Any::class.java, Any::class.java, Array<Any>::class.java))

        var foundMethod: Any? = null
        for (method in instance::class.java.methods as Array<Any>)
        {
            if (getNameMethod.invoke(method) == methodName)
            {
                foundMethod = method
            }
        }

        return invokeMethod.invoke(foundMethod, instance, arguments)
    }

    fun setPrivateVariable(fieldName: String, instanceToModify: Any, newValue: Any?)
    {
        val fieldClass = Class.forName("java.lang.reflect.Field", false, Class::class.java.classLoader)
        val setMethod = MethodHandles.lookup().findVirtual(fieldClass, "set", MethodType.methodType(Void.TYPE, Any::class.java, Any::class.java))
        val getNameMethod = MethodHandles.lookup().findVirtual(fieldClass, "getName", MethodType.methodType(String::class.java))
        val setAcessMethod = MethodHandles.lookup().findVirtual(fieldClass,"setAccessible", MethodType.methodType(Void.TYPE, Boolean::class.javaPrimitiveType))

        val instancesOfFields: Array<out Any> = instanceToModify.javaClass.getDeclaredFields()
        for (obj in instancesOfFields)
        {
            setAcessMethod.invoke(obj, true)
            val name = getNameMethod.invoke(obj)
            if (name.toString() == fieldName)
            {
                setMethod.invoke(obj, instanceToModify, newValue)
            }
        }
    }

    //Required to get certain variables.
    private fun getPrivateVariable(fieldName: String, instanceToGetFrom: Any): Any? {
        val fieldClass = Class.forName("java.lang.reflect.Field", false, Class::class.java.classLoader)
        val getMethod = MethodHandles.lookup().findVirtual(fieldClass, "get", MethodType.methodType(Any::class.java, Any::class.java))
        val getNameMethod = MethodHandles.lookup().findVirtual(fieldClass, "getName", MethodType.methodType(String::class.java))
        val setAcessMethod = MethodHandles.lookup().findVirtual(fieldClass,"setAccessible", MethodType.methodType(Void.TYPE, Boolean::class.javaPrimitiveType))

        val instancesOfFields: Array<out Any> = instanceToGetFrom.javaClass.getDeclaredFields()
        for (obj in instancesOfFields)
        {
            setAcessMethod.invoke(obj, true)
            val name = getNameMethod.invoke(obj)
            if (name.toString() == fieldName)
            {
                return getMethod.invoke(obj, instanceToGetFrom)
            }
        }
        return null
    }

    //Extends the UI API by adding the required method to get the child objects of a panel, only when used within this class.
    private fun UIPanelAPI.getChildrenCopy() : List<UIComponentAPI> {
        return invokeMethod("getChildrenCopy", this) as List<UIComponentAPI>
    }


}



