package xyz.daikirai.render.furiah.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public class SliderSetting extends Setting {
   private final double min;
   private final double max;
   private final double step;
   private final double defaultValue;
   private final int decimals;
   private String unit = "";
   private boolean clock;
   private double value;
   private Supplier<String> info;
   private BooleanSupplier infoWarning;
   private DoubleSupplier trackMax;

   public SliderSetting(String id, String name, String description, double min, double max, double defaultValue, double step) {
      super(id, name, description);
      this.min = min;
      this.max = max;
      this.step = step;
      this.decimals = Math.max(0, BigDecimal.valueOf(step).stripTrailingZeros().scale());
      this.value = this.defaultValue = this.snap(defaultValue);
   }

   public SliderSetting unit(String unit) {
      this.unit = unit;
      return this;
   }

   public String getUnit() {
      return this.unit;
   }

   public SliderSetting clock() {
      this.clock = true;
      return this;
   }

   public boolean isClock() {
      return this.clock;
   }

   public SliderSetting info(Supplier<String> info, BooleanSupplier warning) {
      this.info = info;
      this.infoWarning = warning;
      return this;
   }

   public String getInfo() {
      return this.info == null ? null : this.info.get();
   }

   public boolean isInfoWarning() {
      return this.infoWarning != null && this.infoWarning.getAsBoolean();
   }

   public SliderSetting trackMax(DoubleSupplier trackMax) {
      this.trackMax = trackMax;
      return this;
   }

   private double trackEnd() {
      if (this.trackMax == null) {
         return this.max;
      } else {
         double t = this.trackMax.getAsDouble();
         return Double.isNaN(t) ? this.max : Math.max(this.min + this.step, Math.min(this.max, t));
      }
   }

   public double get() {
      return this.value;
   }

   public float getFloat() {
      return (float)this.value;
   }

   public int getInt() {
      return (int)Math.round(this.value);
   }

   public void set(double value) {
      double snapped = this.snap(value);
      if (snapped != this.value) {
         this.value = snapped;
         this.fireChanged();
      }
   }

   void setQuietly(double value) {
      this.value = value;
   }

   public double getMin() {
      return this.min;
   }

   public double getMax() {
      return this.max;
   }

   public double getStep() {
      return this.step;
   }

   public double getDefault() {
      return this.defaultValue;
   }

   public double getFraction() {
      double end = this.trackEnd();
      return end > this.min ? Math.min(1.0, (this.value - this.min) / (end - this.min)) : 0.0;
   }

   public void setFraction(double fraction) {
      this.set(this.min + Math.max(0.0, Math.min(1.0, fraction)) * (this.trackEnd() - this.min));
   }

   public String format() {
      if (this.clock) {
         int m = (int)Math.round(this.value);
         return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
      } else {
         return String.format(Locale.ROOT, "%." + this.decimals + "f", this.value) + this.unit;
      }
   }

   public double parse(String text) {
      if (!this.clock) {
         return Double.parseDouble(text);
      } else {
         int colon = text.indexOf(58);
         int hours = Integer.parseInt(colon < 0 ? text : text.substring(0, colon));
         int minutes = colon >= 0 && colon != text.length() - 1 ? Integer.parseInt(text.substring(colon + 1)) : 0;
         if (hours >= 0 && hours <= 24 && minutes >= 0 && minutes <= 59) {
            return hours % 24 * 60 + minutes;
         } else {
            throw new NumberFormatException(text);
         }
      }
   }

   private double snap(double v) {
      if (Double.isNaN(v)) {
         v = this.defaultValue;
      }

      v = Math.max(this.min, Math.min(this.max, v));
      double steps = Math.round((v - this.min) / this.step);
      double snapped = BigDecimal.valueOf(this.min + steps * this.step).setScale(this.decimals, 4).doubleValue();
      return Math.max(this.min, Math.min(this.max, snapped));
   }

   @Override
   public void reset() {
      this.set(this.defaultValue);
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.value);
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
         this.set(json.getAsDouble());
      }
   }
}
