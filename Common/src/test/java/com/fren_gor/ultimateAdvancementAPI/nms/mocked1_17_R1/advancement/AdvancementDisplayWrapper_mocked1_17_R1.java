package com.fren_gor.ultimateAdvancementAPI.nms.mocked1_17_R1.advancement;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.*;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.inventory.ItemStack;
public final class AdvancementDisplayWrapper_mocked1_17_R1 extends AdvancementDisplayWrapper {
    private final ItemStack icon;
    private final String title, description, background;
    private final AdvancementFrameTypeWrapper frame;
    private final float x, y;
    private final boolean toast, chat, hidden;
    public AdvancementDisplayWrapper_mocked1_17_R1(ItemStack icon, String title, String description, AdvancementFrameTypeWrapper frame,
            float x, float y, boolean toast, boolean chat, boolean hidden, String background) {
        this.icon=icon; this.title=title; this.description=description; this.frame=frame;
        this.x=x; this.y=y; this.toast=toast; this.chat=chat; this.hidden=hidden; this.background=background;
    }
    public AdvancementDisplayWrapper_mocked1_17_R1(ItemStack icon, BaseComponent title, BaseComponent description, AdvancementFrameTypeWrapper frame,
            float x, float y, boolean toast, boolean chat, boolean hidden, String background) {
        this(icon,title.toPlainText(),description.toPlainText(),frame,x,y,toast,chat,hidden,background);
    }
    public ItemStack getIcon() { return icon; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public AdvancementFrameTypeWrapper getAdvancementFrameType() { return frame; }
    public float getX() { return x; }
    public float getY() { return y; }
    public boolean doesShowToast() { return toast; }
    public boolean doesAnnounceToChat() { return chat; }
    public boolean isHidden() { return hidden; }
    public String getBackgroundTexture() { return background; }
    public Object toNMS() { throw new UnsupportedOperationException(); }
}
