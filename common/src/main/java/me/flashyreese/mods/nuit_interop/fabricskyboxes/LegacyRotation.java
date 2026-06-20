package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.google.common.collect.ImmutableList;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.util.Utils;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.Map;
import java.util.Optional;

public record LegacyRotation(boolean skyboxRotation, Vector3f staticRot, Vector3f axisRot, Vector3i timeShift,
                             float rotationSpeedX, float rotationSpeedY, float rotationSpeedZ,
                             Map<Long, Quaternionf> mapping, Map<Long, Quaternionf> axis, long duration,
                             float speed) {
    public static final LegacyRotation DEFAULT = new LegacyRotation(true, new Vector3f(), new Vector3f(), new Vector3i(), 0.0F, 0.0F, 0.0F);
    public static final LegacyRotation DECORATIONS = new LegacyRotation(false, new Vector3f(), new Vector3f(), new Vector3i(), 0.0F, 0.0F, 1.0F);

    private static final Codec<Vector3f> VEC_3_F = Codec.FLOAT.listOf().comapFlatMap(list -> {
        if (list.size() < 3) {
            return DataResult.error(() -> "Incomplete number of elements in vector");
        }
        return DataResult.success(new Vector3f(list.get(0), list.get(1), list.get(2)));
    }, vec -> ImmutableList.of(vec.x(), vec.y(), vec.z()));

    private static final Codec<Vector3i> VEC_3_I = Codec.INT.listOf().comapFlatMap(list -> {
        if (list.size() < 3) {
            return DataResult.error(() -> "Incomplete number of elements in vector");
        }
        return DataResult.success(new Vector3i(list.get(0), list.get(1), list.get(2)));
    }, vec -> ImmutableList.of(vec.x(), vec.y(), vec.z()));

    public static final Codec<LegacyRotation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("skyboxRotation", true).forGetter(LegacyRotation::skyboxRotation),
            VEC_3_F.optionalFieldOf("static", new Vector3f()).forGetter(LegacyRotation::staticRot),
            VEC_3_F.optionalFieldOf("axis", new Vector3f()).forGetter(LegacyRotation::axisRot),
            VEC_3_I.optionalFieldOf("timeShift", new Vector3i()).forGetter(LegacyRotation::timeShift),
            Codec.FLOAT.optionalFieldOf("rotationSpeedX", 0.0F).forGetter(LegacyRotation::rotationSpeedX),
            Codec.FLOAT.optionalFieldOf("rotationSpeedY", 0.0F).forGetter(LegacyRotation::rotationSpeedY),
            Codec.FLOAT.optionalFieldOf("rotationSpeedZ", 0.0F).forGetter(LegacyRotation::rotationSpeedZ)
    ).apply(instance, LegacyRotation::new));

    public LegacyRotation(boolean skyboxRotation, Vector3f staticRot, Vector3f axisRot, Vector3i timeShift,
                          float rotationSpeedX, float rotationSpeedY, float rotationSpeedZ) {
        this(skyboxRotation, staticRot, axisRot, timeShift, rotationSpeedX, rotationSpeedY, rotationSpeedZ, Map.of(), Map.of(), 24000L, 1.0F);
    }

    public static LegacyRotation mapped(boolean skyboxRotation, Map<Long, Quaternionf> mapping, Map<Long, Quaternionf> axis, long duration, float speed) {
        return new LegacyRotation(skyboxRotation, new Vector3f(), new Vector3f(), new Vector3i(), 0.0F, 0.0F, 0.0F, mapping, axis, Math.max(1L, duration), speed);
    }

    public void apply(Matrix4fStack stack, ClientLevel level) {
        if (!this.mapping.isEmpty() || !this.axis.isEmpty()) {
            stack.rotate(this.calculateMappedRotation(level));
            return;
        }

        float timeRotationX = (float) LegacyUtils.calculateRotation(this.rotationSpeedX, this.timeShift.x, this.skyboxRotation, level);
        float timeRotationY = (float) LegacyUtils.calculateRotation(this.rotationSpeedY, this.timeShift.y, this.skyboxRotation, level);
        float timeRotationZ = (float) LegacyUtils.calculateRotation(this.rotationSpeedZ, this.timeShift.z, this.skyboxRotation, level);

        stack.rotate(Axis.XP.rotationDegrees(this.axisRot.x()));
        stack.rotate(Axis.YP.rotationDegrees(this.axisRot.y()));
        stack.rotate(Axis.ZP.rotationDegrees(this.axisRot.z()));
        stack.rotate(Axis.XP.rotationDegrees(timeRotationX));
        stack.rotate(Axis.YP.rotationDegrees(timeRotationY));
        stack.rotate(Axis.ZP.rotationDegrees(timeRotationZ));
        stack.rotate(Axis.ZP.rotationDegrees(-this.axisRot.z()));
        stack.rotate(Axis.YP.rotationDegrees(-this.axisRot.y()));
        stack.rotate(Axis.XP.rotationDegrees(-this.axisRot.x()));
        stack.rotate(Axis.XP.rotationDegrees(this.staticRot.x()));
        stack.rotate(Axis.YP.rotationDegrees(this.staticRot.y()));
        stack.rotate(Axis.ZP.rotationDegrees(this.staticRot.z()));
    }

    public void apply(PoseStack stack, ClientLevel level) {
        if (!this.mapping.isEmpty() || !this.axis.isEmpty()) {
            stack.mulPose(this.calculateMappedRotation(level));
            return;
        }

        float timeRotationX = (float) LegacyUtils.calculateRotation(this.rotationSpeedX, this.timeShift.x, this.skyboxRotation, level);
        float timeRotationY = (float) LegacyUtils.calculateRotation(this.rotationSpeedY, this.timeShift.y, this.skyboxRotation, level);
        float timeRotationZ = (float) LegacyUtils.calculateRotation(this.rotationSpeedZ, this.timeShift.z, this.skyboxRotation, level);

        stack.mulPose(Axis.XP.rotationDegrees(this.axisRot.x()));
        stack.mulPose(Axis.YP.rotationDegrees(this.axisRot.y()));
        stack.mulPose(Axis.ZP.rotationDegrees(this.axisRot.z()));
        stack.mulPose(Axis.XP.rotationDegrees(timeRotationX));
        stack.mulPose(Axis.YP.rotationDegrees(timeRotationY));
        stack.mulPose(Axis.ZP.rotationDegrees(timeRotationZ));
        stack.mulPose(Axis.ZP.rotationDegrees(-this.axisRot.z()));
        stack.mulPose(Axis.YP.rotationDegrees(-this.axisRot.y()));
        stack.mulPose(Axis.XP.rotationDegrees(-this.axisRot.x()));
        stack.mulPose(Axis.XP.rotationDegrees(this.staticRot.x()));
        stack.mulPose(Axis.YP.rotationDegrees(this.staticRot.y()));
        stack.mulPose(Axis.ZP.rotationDegrees(this.staticRot.z()));
    }

    private Quaternionf calculateMappedRotation(ClientLevel level) {
        long currentTime = Math.floorMod(level.getDefaultClockTime(), this.duration);
        Quaternionf resultRot = new Quaternionf();
        Quaternionf mappingRot = new Quaternionf();

        Optional<Utils.KeyframePair> possibleAxisKeyframes = Utils.findClosestKeyframes(this.axis, currentTime);
        possibleAxisKeyframes.ifPresent(axisKeyframe -> {
            Quaternionf axisRot = new Quaternionf();
            mappingRot.mul(Utils.interpolateQuatKeyframes(this.axis, axisKeyframe, currentTime, this.duration), axisRot);
            resultRot.mul(axisRot);

            double timeRotation = Utils.calculateRotation(this.speed, this.skyboxRotation, level);
            resultRot.mul(Axis.YP.rotationDegrees((float) timeRotation).mul(mappingRot));
            resultRot.mul(axisRot.conjugate());
        });

        Optional<Utils.KeyframePair> possibleMappingKeyframes = Utils.findClosestKeyframes(this.mapping, currentTime);
        possibleMappingKeyframes.ifPresent(mappingKeyframe -> {
            mappingRot.set(Utils.interpolateQuatKeyframes(this.mapping, mappingKeyframe, currentTime, this.duration));
            resultRot.mul(mappingRot);
        });

        return resultRot;
    }
}
