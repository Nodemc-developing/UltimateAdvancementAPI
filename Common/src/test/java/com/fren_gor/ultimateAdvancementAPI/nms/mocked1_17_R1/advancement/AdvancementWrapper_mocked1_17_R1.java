package com.fren_gor.ultimateAdvancementAPI.nms.mocked1_17_R1.advancement;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.MinecraftKeyWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.*;
public final class AdvancementWrapper_mocked1_17_R1 extends AdvancementWrapper {
    private final MinecraftKeyWrapper key;
    private final AdvancementWrapper parent;
    private final AdvancementDisplayWrapper display;
    private final int maximum;
    public AdvancementWrapper_mocked1_17_R1(MinecraftKeyWrapper key, AdvancementDisplayWrapper display, int maximum) { this(key,null,display,maximum); }
    public AdvancementWrapper_mocked1_17_R1(MinecraftKeyWrapper key, AdvancementWrapper parent, AdvancementDisplayWrapper display, int maximum) {
        this.key=key; this.parent=parent; this.display=display; this.maximum=maximum;
    }
    public MinecraftKeyWrapper getKey() { return key; }
    public AdvancementWrapper getParent() { return parent; }
    public AdvancementDisplayWrapper getDisplay() { return display; }
    public int getMaxProgression() { return maximum; }
    public Object toNMS() { throw new UnsupportedOperationException(); }
}
