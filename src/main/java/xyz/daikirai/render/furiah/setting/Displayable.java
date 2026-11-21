package xyz.daikirai.render.furiah.setting;

public interface Displayable {
   String displayName();

   String description();

   default String tag() {
      return null;
   }

   default String[] keywords() {
      return null;
   }

   default boolean isNew() {
      return false;
   }
}
