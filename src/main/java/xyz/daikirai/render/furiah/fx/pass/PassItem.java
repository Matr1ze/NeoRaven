package xyz.daikirai.render.furiah.fx.pass;

public interface PassItem {
   int passes();

   void drawPass(FxPass var1);

   double passDistanceSq();

   default void passFailed() {
   }
}
