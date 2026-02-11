package com.thesettler_x_create.minecolonies.client.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopPermaModuleView;
import com.thesettler_x_create.network.SetCreateShopPermaOrePayload;
import com.thesettler_x_create.network.SetCreateShopPermaWaitPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

public class CreateShopPermaModuleWindow extends AbstractModuleWindow<CreateShopPermaModuleView> {
    private final com.minecolonies.api.colony.buildings.views.IBuildingView building;
    private final CreateShopPermaModuleView moduleView;
    private final ScrollingList oreList;
    private final Button waitFullButton;

    public CreateShopPermaModuleWindow(CreateShopPermaModuleView moduleView) {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(TheSettlerXCreate.MODID, "gui/layouthuts/layoutcreateshop_perma.xml"));
        this.building = moduleView.getBuildingView();
        this.moduleView = moduleView;

        Text desc = findPaneOfTypeByID("desc", Text.class);
        if (desc != null) {
            desc.setText(moduleView.getDesc());
        }

        oreList = findPaneOfTypeByID("oreList", ScrollingList.class);
        waitFullButton = findPaneOfTypeByID("waitFull", Button.class);
        if (waitFullButton != null) {
            waitFullButton.setHandler(btn -> toggleWaitFull());
        }
        updateWaitFullLabel();
        updateOreList();
    }

    private void toggleWaitFull() {
        if (!moduleView.isEnabled()) {
            return;
        }
        boolean next = !moduleView.isWaitFullStack();
        moduleView.setWaitFullStack(next);
        PacketDistributor.sendToServer(new SetCreateShopPermaWaitPayload(building.getPosition(), next));
        updateWaitFullLabel();
    }

    private void updateWaitFullLabel() {
        if (waitFullButton == null) {
            return;
        }
        String key = moduleView.isWaitFullStack()
                ? "com.thesettler_x_create.gui.createshop.perma.wait_full_on"
                : "com.thesettler_x_create.gui.createshop.perma.wait_full_off";
        waitFullButton.setText(Component.translatable(key));
    }

    private void updateOreList() {
        if (oreList == null) {
            return;
        }
        List<CreateShopPermaModuleView.OreEntry> ores = moduleView.getOres();
        if (!moduleView.isEnabled()) {
            oreList.setEmptyText(Component.translatable("com.thesettler_x_create.gui.createshop.perma.locked"));
        } else if (ores.isEmpty()) {
            oreList.setEmptyText(Component.translatable("com.thesettler_x_create.gui.createshop.perma.empty"));
        }

        oreList.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return ores.size();
            }

            @Override
            public void updateElement(int index, Pane row) {
                if (index < 0 || index >= ores.size()) {
                    return;
                }
                CreateShopPermaModuleView.OreEntry entry = ores.get(index);
                ItemStack stack = entry.getStack();

                ItemIcon icon = row.findPaneOfTypeByID("itemIcon", ItemIcon.class);
                if (icon != null) {
                    ItemStack display = stack.copy();
                    display.setCount(1);
                    icon.setItem(display);
                }

                Text name = row.findPaneOfTypeByID("itemName", Text.class);
                if (name != null) {
                    name.setText(stack.getHoverName());
                }

                Button toggle = row.findPaneOfTypeByID("toggle", Button.class);
                if (toggle != null) {
                    String labelKey = entry.isSelected()
                            ? "com.thesettler_x_create.gui.createshop.perma.on"
                            : "com.thesettler_x_create.gui.createshop.perma.off";
                    toggle.setText(Component.translatable(labelKey));
                    toggle.setHandler(btn -> toggleEntry(entry));
                }
            }
        });
        oreList.refreshElementPanes();
    }

    private void toggleEntry(CreateShopPermaModuleView.OreEntry entry) {
        if (!moduleView.isEnabled()) {
            return;
        }
        boolean next = !entry.isSelected();
        entry.setSelected(next);
        ItemStack stack = entry.getStack();
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        PacketDistributor.sendToServer(new SetCreateShopPermaOrePayload(building.getPosition(), id, next));
        oreList.refreshElementPanes();
    }
}
