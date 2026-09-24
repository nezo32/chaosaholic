package dev.chaosaholic.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.chaosaholic.client.ScreenShakeClient;
import dev.chaosaholic.core.ShakeCurve;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * screen_shake camera wobble: rotates the camera pose (world projection and hand) by the angles of
 * {@link ScreenShakeClient}; the player's real rotation and aim are untouched. {@code bobHurt} has the same signature
 * in 26.2 and 26.3. HEAD, not TAIL: vanilla returns early from bobHurt whenever the player is not hurt, so a TAIL
 * injection would only run while the hurt tilt plays. {@code mulPose(Matrix4f)} exists in both versions
 * ({@code rotateDegrees} is 26.3 only).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"))
	private void chaosaholic$screenShake(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		ShakeCurve.Angles a = ScreenShakeClient.angles();
		if (a.isNone()) return;
		poseStack.mulPose(new Matrix4f().rotationXYZ((float) Math.toRadians(a.pitch()), (float) Math.toRadians(a.yaw()),
				(float) Math.toRadians(a.roll())));
	}
}
