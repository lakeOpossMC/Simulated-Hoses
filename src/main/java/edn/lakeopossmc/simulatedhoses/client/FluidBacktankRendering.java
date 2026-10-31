package edn.lakeopossmc.simulatedhoses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.armor.BacktankBlock;
import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import com.simibubi.create.content.equipment.armor.BacktankItem;
import com.simibubi.create.content.equipment.armor.BacktankRenderer;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import edn.lakeopossmc.simulatedhoses.HoseItems;
import edn.lakeopossmc.simulatedhoses.backtank.FluidBacktankBlockEntity;
import edn.lakeopossmc.simulatedhoses.backtank.FluidBacktankItem;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.IntFunction;

// --- FLUID IN THE FLUID BACKTANK --- //
public final class FluidBacktankRendering {
    private static final float INSET = 0.02F;
    private static final float WINDOW_X0 = 1.0F + INSET;
    private static final float CHAMBER_X0 = 2.0F;
    private static final float CHAMBER_X1 = 3.9F - INSET;
    private static final float Y0 = 2.0F + INSET;
    private static final float Y1 = 10.0F - INSET;
    private static final float WINDOW_Z0 = 6.375F + INSET;
    private static final float WINDOW_Z1 = 9.625F - INSET;
    private static final float CHAMBER_Z0 = 6.0F + INSET;
    private static final float CHAMBER_Z1 = 10.0F - INSET;
    private static final Set<Direction> WINDOW_FACES = EnumSet.complementOf(EnumSet.of(Direction.EAST));
    private static final Set<Direction> CHAMBER_FACES = EnumSet.complementOf(EnumSet.of(Direction.WEST));
    private static final Set<Direction> MIRRORED_WINDOW_FACES = EnumSet.complementOf(EnumSet.of(Direction.WEST));
    private static final Set<Direction> MIRRORED_CHAMBER_FACES = EnumSet.complementOf(EnumSet.of(Direction.EAST));
    private static final float MIN_HEIGHT = 0.5F;
    private static final float TILE = 8.0F;

    public static void renderTanks(final IntFunction<FluidStack> fluids, final int tankCount, final int capacity, final Direction facing,
                                   final PoseStack ms, final MultiBufferSource buffer, final int light) {
        for (int tank = 0; tank < tankCount; tank++) {
            renderFluid(fluids.apply(tank), capacity, facing, tank == 1, ms, buffer, light);
        }
    }

    public static void renderFluid(final FluidStack fluid, final int capacity, final Direction facing, final boolean mirrored,
                                   final PoseStack ms, final MultiBufferSource buffer, final int light) {
        if (fluid.isEmpty()) {
            return;
        }
        ms.pushPose();
        ms.translate(0.5F, 0.5F, 0.5F);
        ms.mulPose(Axis.YP.rotationDegrees(-modelTurn(facing)));
        ms.translate(-0.5F, -0.5F, -0.5F);
        if (mirrored) {
            WindowFluidRenderer.render(fluid, capacity, 16.0F - CHAMBER_X0, Y0, WINDOW_Z0, 16.0F - WINDOW_X0, Y1, WINDOW_Z1, MIN_HEIGHT, TILE,
                    MIRRORED_WINDOW_FACES, ms, buffer, light);
            WindowFluidRenderer.render(fluid, capacity, 16.0F - CHAMBER_X1, Y0, CHAMBER_Z0, 16.0F - CHAMBER_X0, Y1, CHAMBER_Z1, MIN_HEIGHT, TILE,
                    MIRRORED_CHAMBER_FACES, ms, buffer, light);
        } else {
            WindowFluidRenderer.render(fluid, capacity, WINDOW_X0, Y0, WINDOW_Z0, CHAMBER_X0, Y1, WINDOW_Z1, MIN_HEIGHT, TILE,
                    WINDOW_FACES, ms, buffer, light);
            WindowFluidRenderer.render(fluid, capacity, CHAMBER_X0, Y0, CHAMBER_Z0, CHAMBER_X1, Y1, CHAMBER_Z1, MIN_HEIGHT, TILE,
                    CHAMBER_FACES, ms, buffer, light);
        }
        ms.popPose();
    }

    private static float modelTurn(final Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    //#region // --- PLACED --- //
    public static class BlockRenderer extends BacktankRenderer {
        public BlockRenderer(final BlockEntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected void renderSafe(final BacktankBlockEntity be, final float partialTicks, final PoseStack ms, final MultiBufferSource buffer,
                                  final int light, final int overlay) {
            final BlockState state = be.getBlockState();
            final DyeColor color = HoseDyedParts.backtankColor(be);
            final SuperByteBuffer shaft = HoseDyedParts.dyeBacktank(CachedBuffers.partial(getShaftModel(state), state), color);
            renderRotatingBuffer(be, shaft, ms, buffer.getBuffer(this.getRenderType(be, state)), light);
            final SuperByteBuffer cogs = HoseDyedParts.dyeBacktank(CachedBuffers.partial(getCogsModel(state), state), color);
            cogs.center()
                    .rotateYDegrees(180 + AngleHelper.horizontalAngle(state.getValue(BacktankBlock.HORIZONTAL_FACING)))
                    .uncenter()
                    .translate(0, 6.5F / 16, 11F / 16)
                    .rotate(AngleHelper.rad(be.getSpeed() / 4F * AnimationTickHolder.getRenderTime(be.getLevel()) % 360), Direction.EAST)
                    .translate(0, -6.5F / 16, -11F / 16);
            cogs.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
            if (be instanceof final FluidBacktankBlockEntity tanks) {
                renderTanks(tanks::getFluid, tanks.tankCount(), tanks.getCapacity(), be.getBlockState().getValue(BacktankBlock.HORIZONTAL_FACING),
                        ms, buffer, light);
            }
        }
    }
    //#endregion

    //#region // --- ITEM --- //
    public static void registerExtensions(final RegisterClientExtensionsEvent event) {
        for (final Item item : new Item[]{HoseItems.COPPER_FLUID_BACKTANK.get(), HoseItems.NETHERITE_FLUID_BACKTANK.get(),
                HoseItems.NETHERITE_DUAL_FLUID_BACKTANK.get()}) {
            event.registerItem(SimpleCustomRenderer.create(item, new ItemRenderer()), item);
        }
    }

    private static final class ItemRenderer extends CustomRenderedItemModelRenderer {
        @Override
        protected void render(final ItemStack stack, final CustomRenderedItemModel model, final PartialItemModelRenderer renderer,
                              final ItemDisplayContext transformType, final PoseStack ms, final MultiBufferSource buffer, final int light,
                              final int overlay) {
            renderer.render(HoseDyedParts.dyeBacktank(model.getOriginalModel(), HoseDyedParts.backtankColor(stack)), light);
            ms.pushPose();
            ms.translate(-0.5F, -0.5F, -0.5F);
            renderTanks(tank -> FluidBacktankItem.getFluid(stack, tank), FluidBacktankItem.tankCount(stack), FluidBacktankItem.capacity(stack),
                    Direction.NORTH, ms, buffer, light);
            ms.popPose();
        }
    }
    //#endregion

    //#region // --- WORN AND DYED --- //
    public static boolean renderWornDyed(final PoseStack ms, final MultiBufferSource buffer, final int light, final LivingEntity entity,
                                         final EntityModel<?> parent) {
        final ItemStack worn = entity.getItemBySlot(EquipmentSlot.CHEST);
        final DyeColor color = HoseDyedParts.backtankColor(worn);
        if (color == null || !(worn.getItem() instanceof final BacktankItem item) || !(parent instanceof final HumanoidModel<?> model)) {
            return false;
        }
        final VertexConsumer vc = net.minecraft.client.renderer.entity.ItemRenderer.getFoilBuffer(buffer, Sheets.cutoutBlockSheet(), false, true);
        final BlockState state = item.getBlock().defaultBlockState().setValue(BacktankBlock.HORIZONTAL_FACING, Direction.SOUTH);
        final SuperByteBuffer body = HoseDyedParts.dyedBacktankBody(state, color);
        final SuperByteBuffer nob = HoseDyedParts.dyeBacktank(CachedBuffers.partial(BacktankRenderer.getShaftModel(state), state), color);
        final SuperByteBuffer cogs = HoseDyedParts.dyeBacktank(CachedBuffers.partial(BacktankRenderer.getCogsModel(state), state), color);

        ms.pushPose();
        model.body.translateAndRotate(ms);
        ms.translate(-1 / 2F, 10 / 16F, 1F);
        ms.scale(1, -1, -1);
        body.disableDiffuse().light(light).renderInto(ms, vc);
        nob.disableDiffuse().translate(0, -3F / 16, 0).light(light).renderInto(ms, vc);
        cogs.center()
                .rotateYDegrees(180)
                .uncenter()
                .translate(0, 6.5F / 16, 11F / 16)
                .rotate(AngleHelper.rad(2 * AnimationTickHolder.getRenderTime(entity.level()) % 360), Direction.EAST)
                .translate(0, -6.5F / 16, -11F / 16);
        cogs.disableDiffuse().light(light).renderInto(ms, vc);
        ms.popPose();
        return true;
    }
    //#endregion

    private FluidBacktankRendering() {
    }
}
