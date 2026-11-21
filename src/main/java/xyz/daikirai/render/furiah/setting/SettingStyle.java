package xyz.daikirai.render.furiah.setting;

public interface SettingStyle {
   String styleName();

   boolean isShown();

   boolean isActive();

   boolean isMultiple();

   void show();

   default String kind() {
      return "style";
   }

   default String kinds() {
      return "styles";
   }

   default String searchGroup(String group) {
      return group;
   }

   default boolean foundElsewhereByNameOnly() {
      return false;
   }

   default float slideOffset(boolean shown, float appear) {
      return 0.0F;
   }
}
