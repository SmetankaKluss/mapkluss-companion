package art.mapkluss.companion;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;

public final class LensRenderBridge {
    private static final int FULL_LIGHT = 0x00F000F0;
    private LensRenderBridge() {
    }

    public static void register(LensManager manager) {
        try { WorkshopHudTheme.select(CompanionConfig.load(Minecraft.getInstance().gameDirectory.toPath()).theme()); }
        catch (Exception ignored) { }
        ClientTickEvents.END_CLIENT_TICK.register(manager::tick);
        ClientTickEvents.END_CLIENT_TICK.register(SuppressionManager.instance()::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            PoseStack matrices = context.poseStack();
            SubmitNodeCollector submits = context.submitNodeCollector();
            Vec3 camera = context.levelState().cameraRenderState.pos;
            Frustum frustum = context.levelState().cameraRenderState.cullFrustum;
            matrices.pushPose();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            try {
                for (LensRenderSnapshot snapshot : manager.renderSnapshots()) {
                    renderSnapshot(snapshot, matrices, submits, frustum);
                }
                renderSuppression(matrices, submits);
            } finally {
                matrices.popPose();
            }
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MapKlussCompanionClient.MOD_ID, "lens_hud"), (context, tickCounter) -> {
            renderSuppressionHud(context);
        });
    }

    private static void renderSnapshot(LensRenderSnapshot snapshot, PoseStack matrices, SubmitNodeCollector submits, Frustum frustum) {
        if (snapshot.atlas() != null) {
            List<LensRenderSnapshot.Cell> visibleArt = new ArrayList<>();
            for (LensRenderSnapshot.CellBatch batch : snapshot.batches()) {
                if (frustum != null && !frustum.isVisible(batch.bounds())) continue;
                for (LensRenderSnapshot.Cell cell : batch.cells()) {
                    if (!cell.hasFrame()) continue;
                    visibleArt.add(cell);
                }
            }
            if (!visibleArt.isEmpty()) {
                submits.submitCustomGeometry(matrices, RenderTypes.text(snapshot.atlas()), (pose, vertices) -> {
                    for (LensRenderSnapshot.Cell cell : visibleArt) {
                        texturedQuad(vertices, pose, LensQuadGeometry.quad(cell.blockPos(), snapshot.facing()), cell);
                    }
                });
            }
        }

        List<LensRenderSnapshot.Cell> visibleMissing = new ArrayList<>();
        for (LensRenderSnapshot.CellBatch batch : snapshot.batches()) {
            if (frustum != null && !frustum.isVisible(batch.bounds())) continue;
            for (LensRenderSnapshot.Cell cell : batch.cells()) {
                if (cell.hasFrame()) continue;
                visibleMissing.add(cell);
            }
        }
        if (!visibleMissing.isEmpty()) {
            submits.submitCustomGeometry(matrices, RenderTypes.linesTranslucent(), (pose, vertices) -> {
                for (LensRenderSnapshot.Cell cell : visibleMissing) {
                    outline(vertices, pose, LensQuadGeometry.quad(cell.blockPos(), snapshot.facing()), snapshot.facing(), 255, 45, 55, 230);
                }
            });
        }
    }

    private static void texturedQuad(VertexConsumer vertices, PoseStack.Pose pose, LensQuadGeometry.Quad quad, LensRenderSnapshot.Cell cell) {
        vertex(vertices, pose, quad.bottomLeft(), cell.minU(), cell.maxV());
        vertex(vertices, pose, quad.bottomRight(), cell.maxU(), cell.maxV());
        vertex(vertices, pose, quad.topRight(), cell.maxU(), cell.minV());
        vertex(vertices, pose, quad.topLeft(), cell.minU(), cell.minV());
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, Vec3 point, float u, float v) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
            .setColor(255, 255, 255, 255).setUv(u, v).setLight(FULL_LIGHT);
    }

    private static void outline(VertexConsumer vertices, PoseStack.Pose pose, LensQuadGeometry.Quad quad, Direction normal, int r, int g, int b, int a) {
        line(vertices, pose, quad.bottomLeft(), quad.bottomRight(), normal, r, g, b, a);
        line(vertices, pose, quad.bottomRight(), quad.topRight(), normal, r, g, b, a);
        line(vertices, pose, quad.topRight(), quad.topLeft(), normal, r, g, b, a);
        line(vertices, pose, quad.topLeft(), quad.bottomLeft(), normal, r, g, b, a);
    }

    private static void line(VertexConsumer vertices, PoseStack.Pose pose, Vec3 from, Vec3 to, Direction normal, int r, int g, int b, int a) {
        float nx = normal.getStepX();
        float ny = normal.getStepY();
        float nz = normal.getStepZ();
        vertices.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
            .setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(2.0f);
        vertices.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
            .setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(2.0f);
    }

    private static void renderSuppression(PoseStack matrices, SubmitNodeCollector submits) {
        Minecraft client = Minecraft.getInstance();
        List<SuppressionHighlight> highlights = List.copyOf(SuppressionManager.instance().highlights(client));
        if (highlights.isEmpty()) return;
        submits.submitCustomGeometry(matrices, RenderTypes.textBackgroundSeeThrough(), (pose, vertices) -> {
            for (SuppressionHighlight highlight : highlights) {
                int[] color = suppressionColor(highlight);
                filledBlock(vertices, pose, highlight.blockPos(), color[0], color[1], color[2], 118);
            }
        });
        submits.submitCustomGeometry(matrices, RenderTypes.linesTranslucent(), (pose, vertices) -> {
            for (SuppressionHighlight highlight : highlights) {
                int[] color = suppressionColor(highlight);
                outlineBlock(vertices, pose, highlight.blockPos(), color[0], color[1], color[2], 255);
            }
        });
    }

    private static int[] suppressionColor(SuppressionHighlight highlight) {
        return switch (highlight.kind()) {
            case REMOVE -> new int[] {255, 66, 82};
            case STAND -> new int[] {255, 214, 92};
            case ANCHOR -> new int[] {87, 255, 110};
            case FOOTPRINT -> new int[] {76, 202, 255};
        };
    }

    private static void filledBlock(VertexConsumer vertices, PoseStack.Pose pose, net.minecraft.core.BlockPos pos, int r, int g, int b, int a) {
        double e = 0.02;
        double x0 = pos.getX() + e, y0 = pos.getY() + e, z0 = pos.getZ() + e;
        double x1 = pos.getX() + 1 - e, y1 = pos.getY() + 1 - e, z1 = pos.getZ() + 1 - e;
        Vec3 p000 = new Vec3(x0, y0, z0), p100 = new Vec3(x1, y0, z0), p010 = new Vec3(x0, y1, z0), p110 = new Vec3(x1, y1, z0);
        Vec3 p001 = new Vec3(x0, y0, z1), p101 = new Vec3(x1, y0, z1), p011 = new Vec3(x0, y1, z1), p111 = new Vec3(x1, y1, z1);
        quad(vertices, pose, p000, p100, p110, p010, r, g, b, a);
        quad(vertices, pose, p101, p001, p011, p111, r, g, b, a);
        quad(vertices, pose, p001, p000, p010, p011, r, g, b, a);
        quad(vertices, pose, p100, p101, p111, p110, r, g, b, a);
        quad(vertices, pose, p010, p110, p111, p011, r, g, b, a);
        quad(vertices, pose, p001, p101, p100, p000, r, g, b, a);
    }

    private static void quad(VertexConsumer vertices, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int r, int g, int blue, int alpha) {
        colorVertex(vertices, pose, a, r, g, blue, alpha);
        colorVertex(vertices, pose, b, r, g, blue, alpha);
        colorVertex(vertices, pose, c, r, g, blue, alpha);
        colorVertex(vertices, pose, d, r, g, blue, alpha);
    }

    private static void colorVertex(VertexConsumer vertices, PoseStack.Pose pose, Vec3 point, int r, int g, int b, int a) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
            .setColor(r, g, b, a).setLight(FULL_LIGHT);
    }

    private static void outlineBlock(VertexConsumer lines, PoseStack.Pose pose, net.minecraft.core.BlockPos pos, int r, int g, int b, int a) {
        double e = 0.003;
        double x0 = pos.getX() - e, y0 = pos.getY() - e, z0 = pos.getZ() - e;
        double x1 = pos.getX() + 1 + e, y1 = pos.getY() + 1 + e, z1 = pos.getZ() + 1 + e;
        Vec3 p000 = new Vec3(x0, y0, z0), p100 = new Vec3(x1, y0, z0), p010 = new Vec3(x0, y1, z0), p110 = new Vec3(x1, y1, z0);
        Vec3 p001 = new Vec3(x0, y0, z1), p101 = new Vec3(x1, y0, z1), p011 = new Vec3(x0, y1, z1), p111 = new Vec3(x1, y1, z1);
        line(lines, pose, p000, p100, Direction.UP, r, g, b, a); line(lines, pose, p100, p101, Direction.UP, r, g, b, a);
        line(lines, pose, p101, p001, Direction.UP, r, g, b, a); line(lines, pose, p001, p000, Direction.UP, r, g, b, a);
        line(lines, pose, p010, p110, Direction.UP, r, g, b, a); line(lines, pose, p110, p111, Direction.UP, r, g, b, a);
        line(lines, pose, p111, p011, Direction.UP, r, g, b, a); line(lines, pose, p011, p010, Direction.UP, r, g, b, a);
        line(lines, pose, p000, p010, Direction.UP, r, g, b, a); line(lines, pose, p100, p110, Direction.UP, r, g, b, a);
        line(lines, pose, p101, p111, Direction.UP, r, g, b, a); line(lines, pose, p001, p011, Direction.UP, r, g, b, a);
    }

    private static void renderSuppressionHud(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        java.util.List<String> lines = SuppressionManager.instance().hudLines();
        if (client.gui.hud.isHidden() || lines.isEmpty()) return;
        WorkshopHudDraw.twoLayer(context, lines);
    }

}
