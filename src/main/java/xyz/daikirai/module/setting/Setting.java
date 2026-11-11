package xyz.daikirai.module.setting;

public class Setting {
    public String name;
    public boolean saved = true;

    public Setting(String name) {
        this.name = name;
    }

    public Setting notSaved() {
        this.saved = false;
        return this;
    }

    public String getName() {
        return this.name;
    }

    /** Localised setting label for display only; {@link #name} stays the save key. */
    public String localizedName() {
        return xyz.daikirai.util.Lang.setting(this.name);
    }
}
