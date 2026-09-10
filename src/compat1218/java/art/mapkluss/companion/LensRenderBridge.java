package art.mapkluss.companion;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class LensRenderBridge {
    private static final int FULL_LIGHT = 0x00F000F0;

    private LensRenderBridge() {
    }

    public static void register(LensManager manager) {
        try { WorkshopHudTheme.select(CompanionConfig.load(MinecraftClient.getInstance().runDirectory.toPath()).theme()); }
        catch (Exception ignored) { }
        ClientTickEvents.END_CLIENT_TICK.register(manager::tick);
        ClientTickEvents.END_CLIENT_TICK.register(SuppressionManager.instance()::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MatrixStack matrices = context.matrixStack();
            VertexConsumerProvider consumers = context.consumers();
            Vec3d camera = context.camera().getPos();
            matrices.push();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            try {
                for (LensRenderSnapshot snapshot : manager.renderSnapshots()) {
                    if (context.frustum().isVisible(snapshot.bounds())) renderSnapshot(snapshot, matrices, consumers, context);
                }
                renderSuppression(matrices, consumers);
            } finally {
                matrices.pop();
            }
        });
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            renderSuppressionHud(context);
        });
    }

    private static void renderSnapshot(
        LensRenderSnapshot snapshot,
        MatrixStack matrices,
        VertexConsumerProvider consumers,
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context
    ) {
        if (snapshot.atlas() != null) {
            VertexConsumer art = consumers.getBuffer(RenderLayer.getText(snapshot.atlas()));
            for (LensRenderSnapshot.CellBatch batch : snapshot.batches()) {
                if (!context.frustum().isVisible(batch.bounds())) continue;
                for (LensRenderSnapshot.Cell cell : batch.cells()) {
                    if (!cell.hasFrame()) continue;
                    LensQuadGeometry.Quad quad = LensQuadGeometry.quad(cell.blockPos(), snapshot.facing());
                    texturedQuad(art, matrices, quad, cell);
                }
            }
        }

        VertexConsumer missing = consumers.getBuffer(RenderLayer.getLines());
        for (LensRenderSnapshot.CellBatch batch : snapshot.batches()) {
            if (!context.frustum().isVisible(batch.bounds())) continue;
            for (LensRenderSnapshot.Cell cell : batch.cells()) {
                if (cell.hasFrame()) continue;
                LensQuadGeometry.Quad quad = LensQuadGeometry.quad(cell.blockPos(), snapshot.facing());
                outline(missing, matrices, quad, snapshot.facing(), 255, 45, 55, 230);
            }
        }
    }

    private static void texturedQuad(VertexConsumer vertices, MatrixStack matrices, LensQuadGeometry.Quad quad, LensRenderSnapshot.Cell cell) {
        vertex(vertices, matrices, quad.bottomLeft(), cell.minU(), cell.maxV());
        vertex(vertices, matrices, quad.bottomRight(), cell.maxU(), cell.maxV());
        vertex(vertices, matrices, quad.topRight(), cell.maxU(), cell.minV());
        vertex(vertices, matrices, quad.topLeft(), cell.minU(), cell.minV());
    }

    private static void vertex(VertexConsumer vertices, MatrixStack matrices, Vec3d point, float u, float v) {
        vertices.vertex(matrices.peek(), (float) point.x, (float) point.y, (float) point.z)
            .color(255, 255, 255, 255).texture(u, v).light(FULL_LIGHT);
    }

    private static void outline(VertexConsumer vertices, MatrixStack matrices, LensQuadGeometry.Quad quad, Direction normal, int r, int g, int b, int a) {
        line(vertices, matrices, quad.bottomLeft(), quad.bottomRight(), normal, r, g, b, a);
        line(vertices, matrices, quad.bottomRight(), quad.topRight(), normal, r, g, b, a);
        line(vertices, matrices, quad.topRight(), quad.topLeft(), normal, r, g, b, a);
        line(vertices, matrices, quad.topLeft(), quad.bottomLeft(), normal, r, g, b, a);
    }

    private static void line(VertexConsumer vertices, MatrixStack matrices, Vec3d from, Vec3d to, Direction normal, int r, int g, int b, int a) {
        float nx = normal.getOffsetX();
        float ny = normal.getOffsetY();
        float nz = normal.getOffsetZ();
        vertices.vertex(matrices.peek(), (float) from.x, (float) from.y, (float) from.z)
            .color(r, g, b, a).normal(nx, ny, nz);
        vertices.vertex(matrices.peek(), (float) to.x, (float) to.y, (float) to.z)
            .color(r, g, b, a).normal(nx, ny, nz);
    }

    private static void renderSuppression(MatrixStack matrices, VertexConsumerProvider consumers) {
        MinecraftClient client = MinecraftClient.getInstance();
        java.util.List<SuppressionHighlight> highlights = SuppressionManager.instance().highlights(client);
        VertexConsumer throughWalls = consumers.getBuffer(RenderLayer.getTextBackgroundSeeThrough());
        for (SuppressionHighlight highlight : highlights) {
            int[] color = suppressionColor(highlight);
            filledBlock(throughWalls, matrices, highlight.blockPos(), color[0], color[1], color[2], 118);
        }
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        for (SuppressionHighlight highlight : highlights) {
            int[] color = suppressionColor(highlight);
            outlineBlock(lines, matrices, highlight.blockPos(), color[0], color[1], color[2], 255);
        }
    }

    private static int[] suppressionColor(SuppressionHighlight highlight) {
        return switch (highlight.kind()) {
            case REMOVE -> new int[] {255, 66, 82};
            case STAND -> new int[] {255, 214, 92};
            case ANCHOR -> new int[] {87, 255, 110};
            case FOOTPRINT -> new int[] {76, 202, 255};
        };
    }

    private static void filledBlock(VertexConsumer vertices, MatrixStack matrices, net.minecraft.util.math.BlockPos pos, int r, int g, int b, int a) {
        double e = 0.02;
        double x0 = pos.getX() + e, y0 = pos.getY() + e, z0 = pos.getZ() + e;
        double x1 = pos.getX() + 1 - e, y1 = pos.getY() + 1 - e, z1 = pos.getZ() + 1 - e;
        Vec3d p000 = new Vec3d(x0, y0, z0), p100 = new Vec3d(x1, y0, z0), p010 = new Vec3d(x0, y1, z0), p110 = new Vec3d(x1, y1, z0);
        Vec3d p001 = new Vec3d(x0, y0, z1), p101 = new Vec3d(x1, y0, z1), p011 = new Vec3d(x0, y1, z1), p111 = new Vec3d(x1, y1, z1);
        quad(vertices, matrices, p000, p100, p110, p010, r, g, b, a);
        quad(vertices, matrices, p101, p001, p011, p111, r, g, b, a);
        quad(vertices, matrices, p001, p000, p010, p011, r, g, b, a);
        quad(vertices, matrices, p100, p101, p111, p110, r, g, b, a);
        quad(vertices, matrices, p010, p110, p111, p011, r, g, b, a);
        quad(vertices, matrices, p001, p101, p100, p000, r, g, b, a);
    }

    private static void quad(VertexConsumer vertices, MatrixStack matrices, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int r, int g, int blue, int alpha) {
        colorVertex(vertices, matrices, a, r, g, blue, alpha);
        colorVertex(vertices, matrices, b, r, g, blue, alpha);
        colorVertex(vertices, matrices, c, r, g, blue, alpha);
        colorVertex(vertices, matrices, d, r, g, blue, alpha);
    }

    private static void colorVertex(VertexConsumer vertices, MatrixStack matrices, Vec3d point, int r, int g, int b, int a) {
        vertices.vertex(matrices.peek(), (float) point.x, (float) point.y, (float) point.z)
            .color(r, g, b, a).light(FULL_LIGHT);
    }

    private static void outlineBlock(VertexConsumer lines, MatrixStack matrices, net.minecraft.util.math.BlockPos pos, int r, int g, int b, int a) {
        double e = 0.003;
        double x0 = pos.getX() - e, y0 = pos.getY() - e, z0 = pos.getZ() - e;
        double x1 = pos.getX() + 1 + e, y1 = pos.getY() + 1 + e, z1 = pos.getZ() + 1 + e;
        Vec3d p000 = new Vec3d(x0, y0, z0), p100 = new Vec3d(x1, y0, z0), p010 = new Vec3d(x0, y1, z0), p110 = new Vec3d(x1, y1, z0);
        Vec3d p001 = new Vec3d(x0, y0, z1), p101 = new Vec3d(x1, y0, z1), p011 = new Vec3d(x0, y1, z1), p111 = new Vec3d(x1, y1, z1);
        line(lines, matrices, p000, p100, Direction.UP, r, g, b, a); line(lines, matrices, p100, p101, Direction.UP, r, g, b, a);
        line(lines, matrices, p101, p001, Direction.UP, r, g, b, a); line(lines, matrices, p001, p000, Direction.UP, r, g, b, a);
        line(lines, matrices, p010, p110, Direction.UP, r, g, b, a); line(lines, matrices, p110, p111, Direction.UP, r, g, b, a);
        line(lines, matrices, p111, p011, Direction.UP, r, g, b, a); line(lines, matrices, p011, p010, Direction.UP, r, g, b, a);
        line(lines, matrices, p000, p010, Direction.UP, r, g, b, a); line(lines, matrices, p100, p110, Direction.UP, r, g, b, a);
        line(lines, matrices, p101, p111, Direction.UP, r, g, b, a); line(lines, matrices, p001, p011, Direction.UP, r, g, b, a);
    }

    private static void renderSuppressionHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        java.util.List<String> lines = SuppressionManager.instance().hudLines();
        if (client.options.hudHidden || lines.isEmpty()) return;
        WorkshopHudDraw.twoLayer(context, lines);
    }
}
