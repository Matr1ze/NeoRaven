package xyz.daikirai.render.furiah.setting;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public abstract class Setting {
   private static final String[] NO_KEYWORDS = new String[0];
   private final String id;
   private final String name;
   private final String description;
   private String label;
   private String group;
   private BooleanSupplier visibility;
   private SettingStyle style;
   private String[] keywords = NO_KEYWORDS;
   private boolean nested;
   private boolean personal;
   private Supplier<String> unavailable;
   private Runnable preview;
   private boolean foundAnywhere;
   private final List<Runnable> listeners = new ArrayList<>();
   private boolean notSearched;
   private boolean notSaved;

   protected Setting(String id, String name, String description) {
      this.id = id;
      this.name = name;
      this.description = description;
   }

   public final String getId() {
      return this.id;
   }

   public final String getName() {
      return this.name;
   }

   public final String getLabel() {
      return this.label != null ? this.label : this.name;
   }

   public final void setLabel(String label) {
      this.label = label != null && !label.equals(this.name) ? label : null;
   }

   public final String getDescription() {
      return this.description;
   }

   public final String getGroup() {
      return this.group;
   }

   public final <S extends Setting> S group(String group) {
      this.group = group;
      return (S)this;
   }

   public final <S extends Setting> S visibleWhen(final BooleanSupplier condition) {
      final BooleanSupplier previous = this.visibility;
      this.visibility = previous == null ? condition : new BooleanSupplier() {
         @Override
         public boolean getAsBoolean() {
            return previous.getAsBoolean() && condition.getAsBoolean();
         }
      };
      return (S)this;
   }

   public final boolean isVisible() {
      return (this.style == null || this.style.isShown()) && (this.visibility == null || this.visibility.getAsBoolean());
   }

   public final boolean hasCondition() {
      return this.visibility != null;
   }

   public final <S extends Setting> S unavailableWhen(Supplier<String> reason) {
      this.unavailable = reason;
      return (S)this;
   }

   public final String unavailableReason() {
      return this.unavailable == null ? null : this.unavailable.get();
   }

   public final <S extends Setting> S preview(Runnable action) {
      this.preview = action;
      return (S)this;
   }

   public final Runnable getPreview() {
      return this.preview;
   }

   public final <S extends Setting> S foundAnywhere() {
      this.foundAnywhere = true;
      return (S)this;
   }

   public final boolean isFoundAnywhere() {
      return this.foundAnywhere;
   }

   public final boolean isVisibleIgnoringStyle() {
      return this.visibility == null || this.visibility.getAsBoolean();
   }

   public final <S extends Setting> S ownedBy(SettingStyle style) {
      this.style = style;
      return (S)this;
   }

   public final SettingStyle getOwner() {
      return this.style;
   }

   public final <S extends Setting> S keywords(String... words) {
      this.keywords = words == null ? NO_KEYWORDS : (String[])words.clone();
      return (S)this;
   }

   public final String[] getKeywords() {
      return this.keywords;
   }

   public boolean showInMenu() {
      return !this.nested;
   }

   public final <S extends Setting> S nested() {
      this.nested = true;
      return (S)this;
   }

   public final <S extends Setting> S personal() {
      this.personal = true;
      return (S)this;
   }

   public final boolean isPersonal() {
      return this.personal;
   }

   public final <S extends Setting> S notSearched() {
      this.notSearched = true;
      return (S)this;
   }

   public final boolean isSearched() {
      return !this.notSearched;
   }

   public boolean isSaved() {
      return !this.notSaved;
   }

   public final <S extends Setting> S notSaved() {
      this.notSaved = true;
      return (S)this;
   }

   public final void addListener(Runnable listener) {
      this.listeners.add(listener);
   }

   protected final void fireChanged() {
      for (int i = 0; i < this.listeners.size(); i++) {
         this.listeners.get(i).run();
      }
   }

   public abstract void reset();

   public abstract JsonElement toJson();

   public abstract void fromJson(JsonElement var1);
}
