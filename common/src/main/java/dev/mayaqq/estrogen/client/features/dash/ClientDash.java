package dev.mayaqq.estrogen.client.features.dash;

import dev.mayaqq.estrogen.client.registry.EstrogenKeybinds;
import dev.mayaqq.estrogen.config.EstrogenConfig;
import dev.mayaqq.estrogen.features.dash.CommonDash;
import dev.mayaqq.estrogen.networking.EstrogenNetworkManager;
import dev.mayaqq.estrogen.networking.messages.c2s.DashPacket;
import dev.mayaqq.estrogen.registry.EstrogenAttributes;
import dev.mayaqq.estrogen.registry.EstrogenEffects;
import dev.mayaqq.estrogen.registry.blocks.DreamBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ClientDash {

    private static final double DASH_SPEED = 1.0;
    private static final double DASH_END_SPEED = 0.4;
    private static final double HYPER_H_SPEED = 3.0;
    private static final double HYPER_V_SPEED = 0.5;
    private static final double SUPER_H_SPEED = 0.8;
    private static final double SUPER_V_SPEED = 1.0;
    private static final double BOUNCE_H_SPEED = 0.8;
    private static final double BOUNCE_V_SPEED = 1.5;

    private static boolean isOnCooldown = false;

    private static int groundCooldown = 0;
    private static int dashCooldown = 0;
    private static int dashes = 0;
    private static int dashLevel = 0;
    private static int extraParticleTicks = 0;

    private static Vec3 dashDirection = null;
    private static double dashXRot = 0.0;
    private static double dashDeltaModifier = 0.0;

    private static BlockPos lastPos = null;

    public static void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!player.hasEffect(EstrogenEffects.ESTROGEN_EFFECT.get())) {
            reset();
            return;
        }

        // Refresh number of dashes
        if (canRefresh(player) && groundCooldown == 0) {
            groundCooldown = 4;
            refresh(player);
        }

        groundCooldown--;
        if (groundCooldown < 0) groundCooldown = 0;

        // During Dash
        Dash:
        if (dashCooldown > 0) {
            dashCooldown--;
            extraParticleTicks = 0;

            // End Dash
            if (dashCooldown == 0) {
                CommonDash.removeDashing(player.getUUID());
                if (!player.isFallFlying()) {
                    player.setDeltaMovement(dashDirection.scale(DASH_END_SPEED).scale(dashDeltaModifier));
                }
                break Dash;
            }

            player.setDeltaMovement(dashDirection.scale(DASH_SPEED).scale(dashDeltaModifier));

            // Hyper and Super Detection
            if (Minecraft.getInstance().options.keyJump.isDown()) {
                boolean doReverse = (Minecraft.getInstance().options.keyDown.isDown());
                if (player.onGround() && dashXRot > 15 && dashXRot < 60) {
                    hyperJump(player, doReverse ? player.getLookAngle().reverse() : player.getLookAngle());
                }
                else if (player.onGround() && dashXRot > 0 && dashXRot < 15) {
                    superJump(player, doReverse ? player.getLookAngle().reverse() : player.getLookAngle());
                }
                else if (dashXRot < -60) {
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        // change required distance from wall here
                        Vec3 vector = Vec3.atLowerCornerOf(direction.getNormal()).scale(0.25);
                        AABB aabb = player.getBoundingBox().expandTowards(vector);
                        if (player.level().noCollision(player, aabb)) continue;

                        Vec3 jumpDirection = Vec3.atLowerCornerOf(direction.getOpposite().getNormal());
                        wallJump(player, jumpDirection);
                        break;
                    }
                }
            }

            // Dash particles
            if (player.blockPosition() != lastPos) {
                EstrogenNetworkManager.CHANNEL.sendToServer(new DashPacket(false, dashLevel));
            }
            lastPos = player.blockPosition();
        }

        isOnCooldown = dashCooldown > 0 || dashes == 0;

        if(extraParticleTicks > 0) {
            EstrogenNetworkManager.CHANNEL.sendToServer(new DashPacket(false, dashLevel));
            extraParticleTicks--;
        }

        // Here is when the dash happens
        if (EstrogenKeybinds.DASH_KEY.consumeClick() && !isOnCooldown()) dash(player, player.getLookAngle());
    }

    private static void dash(LocalPlayer player, Vec3 dashDirection) {
        DreamBlock.lookAngle = null;
        CommonDash.setDashing(player.getUUID());

        // Set counter to duration of dash
        dashCooldown = 5;
        // Dash level of current dash (number of dashes at the beginning)
        dashLevel = dashes;
        // Decrement the dash counter
        if (dashes > 0) dashes--;

        EstrogenNetworkManager.CHANNEL.sendToServer(new DashPacket(true, dashLevel));

        // math from Entity.lookAt()
        dashXRot = Mth.wrapDegrees((float)(-(Mth.atan2(dashDirection.y, dashDirection.horizontalDistance()) * (double)(180F / (float)Math.PI))));
        ClientDash.dashDirection = dashDirection;
        dashDeltaModifier = EstrogenConfig.server().dashDeltaModifier.get();
    }

    private static void hyperJump(LocalPlayer player, Vec3 jumpDirection) {
        Vec3 hyperMotion = new Vec3(
                jumpDirection.x * HYPER_H_SPEED,
                HYPER_V_SPEED,
                jumpDirection.z * HYPER_H_SPEED
        );
        player.setDeltaMovement(hyperMotion);
        dashCooldown = 0;
        extraParticleTicks = 2;
    }

    private static void superJump(LocalPlayer player, Vec3 jumpDirection) {
        Vec3 superMotion = new Vec3(
                jumpDirection.x * SUPER_H_SPEED,
                SUPER_V_SPEED,
                jumpDirection.z * SUPER_H_SPEED
        );
        player.setDeltaMovement(superMotion);
        dashCooldown = 0;
        extraParticleTicks = 1;
    }

    private static void wallJump(LocalPlayer player, Vec3 jumpDirection) {
        player.setDeltaMovement(
                jumpDirection.x * BOUNCE_H_SPEED,
                BOUNCE_V_SPEED,
                jumpDirection.z * BOUNCE_H_SPEED
        );
        dashCooldown = 0;
        extraParticleTicks = 1;
    }

    public static void reset() {
        dashes = 0;
        dashLevel = 0;
        isOnCooldown = false;
        dashCooldown = 0;
    }

    public static void refresh(Player player) {
        dashes = (short) player.getAttributeValue(EstrogenAttributes.DASH_LEVEL.get());
    }

    private static boolean canRefresh(Player player) {
        return player.onGround() || player.level().getBlockState(player.blockPosition()).getBlock() instanceof LiquidBlock || player.onClimbable();
    }

    public static boolean isOnCooldown() {
        return isOnCooldown;
    }

    public static int getDashLevel() {
        return dashLevel;
    }
}
