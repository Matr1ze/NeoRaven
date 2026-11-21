package xyz.daikirai.render.furiah.fx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Vfx;
import net.minecraft.world.World;

public final class EffectEngine<T extends EffectInstance> {
   private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("NeoRaven/JumpCircles");
   private final String name;
   private final List<T> live = new ArrayList<>();
   private final Set<String> reported = new HashSet<>();
   private int maxTotal = 32;
   private int maxPerEntity = 3;

   public EffectEngine(String name) {
      this.name = name;
   }

   public void setLimits(int maxTotal, int maxPerEntity) {
      this.maxTotal = Math.max(1, maxTotal);
      this.maxPerEntity = Math.max(1, maxPerEntity);
   }

   public int countFor(int entityId) {
      int n = 0;

      for (int i = 0; i < this.live.size(); i++) {
         if (this.live.get(i).entityId == entityId) {
            n++;
         }
      }

      return n;
   }

   public boolean isRunning(int entityId, Lifecycle lifecycle) {
      for (int i = 0; i < this.live.size(); i++) {
         T fx = this.live.get(i);
         if (fx.entityId == entityId && fx.lifecycle == lifecycle) {
            return true;
         }
      }

      return false;
   }

   public boolean hasRoom(int entityId) {
      return this.live.size() < this.maxTotal && (entityId < 0 || this.countFor(entityId) < this.maxPerEntity);
   }

   public boolean add(T fx) {
      if (!this.hasRoom(fx.entityId)) {
         return false;
      } else {
         try {
            fx.start();
         } catch (RuntimeException | LinkageError var3) {
            this.report(fx, "start", var3);
            return false;
         }

         this.live.add(fx);
         return true;
      }
   }

   public void update(float dt, float partialTicks, World world) {
      for (int i = this.live.size() - 1; i >= 0; i--) {
         T fx = this.live.get(i);
         fx.anchor.update(partialTicks, world);
         fx.update(dt);
         if (fx.isFinished()) {
            this.live.remove(i);

            try {
               fx.finished();
            } catch (RuntimeException | LinkageError var7) {
               this.report(fx, "finish", var7);
            }
         }
      }
   }

   public void render(FxFrame f) {
      for (int i = 0; i < this.live.size(); i++) {
         T fx = this.live.get(i);

         try {
            fx.render(f);
         } catch (RuntimeException | LinkageError var5) {
            this.report(fx, "render", var5);
            fx.age = fx.life;
         }

         Vfx.flush();
         RenderState.decal(false);
         RenderState.additive();
      }
   }

   private void report(T fx, String what, Throwable e) {
      if (this.reported.add(fx.getClass().getName() + ':' + what + ':' + e.getClass().getName())) {
         LOG
            .error("{} effect {} failed during {} (further identical errors are not logged)", new Object[]{this.name, fx.getClass().getSimpleName(), what, e});
      }
   }

   public List<T> list() {
      return this.live;
   }

   public int size() {
      return this.live.size();
   }

   public boolean isEmpty() {
      return this.live.isEmpty();
   }

   public void clear() {
      this.live.clear();
   }
}
