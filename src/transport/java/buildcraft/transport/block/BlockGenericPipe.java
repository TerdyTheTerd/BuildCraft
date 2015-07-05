/** Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL. Please check the contents
 * of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt */
package buildcraft.transport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import buildcraft.api.blocks.IColorRemovable;
import buildcraft.api.core.BCLog;
import buildcraft.api.events.PipePlacedEvent;
import buildcraft.api.items.IMapLocation;
import buildcraft.api.tools.IToolWrench;
import buildcraft.api.transport.IPipe;
import buildcraft.api.transport.IPipeTile;
import buildcraft.api.transport.PipeWire;
import buildcraft.api.transport.pluggable.IPipePluggableItem;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.core.BCCreativeTab;
import buildcraft.core.CoreConstants;
import buildcraft.core.lib.TileBuffer;
import buildcraft.core.lib.block.BlockBuildCraft;
import buildcraft.core.lib.utils.MatrixTranformations;
import buildcraft.core.lib.utils.Utils;
import buildcraft.transport.BuildCraftTransport;
import buildcraft.transport.Gate;
import buildcraft.transport.ISolidSideTile;
import buildcraft.transport.PipeIconProvider;
import buildcraft.transport.TileGenericPipe;
import buildcraft.transport.TransportProxy;
import buildcraft.transport.gates.GatePluggable;
import buildcraft.transport.item.ItemGateCopier;
import buildcraft.transport.item.ItemPipe;
import buildcraft.transport.render.PipeRendererWorld;

public class BlockGenericPipe extends BlockBuildCraft implements IColorRemovable {

    public static Map<Item, Class<? extends Pipe>> pipes = new HashMap<Item, Class<? extends Pipe>>();
    public static Map<BlockPos, Pipe<?>> pipeRemoved = new HashMap<BlockPos, Pipe<?>>();

    private static long lastRemovedDate = -1;

    private static final EnumFacing[] DIR_VALUES = EnumFacing.values();

    public static enum Part {
        Pipe,
        Pluggable
    }

    public static class RaytraceResult {
        public final Part hitPart;
        public final MovingObjectPosition movingObjectPosition;
        public final AxisAlignedBB boundingBox;
        public final EnumFacing sideHit;

        RaytraceResult(Part hitPart, MovingObjectPosition movingObjectPosition, AxisAlignedBB boundingBox, EnumFacing side) {
            this.hitPart = hitPart;
            this.movingObjectPosition = movingObjectPosition;
            this.boundingBox = boundingBox;
            this.sideHit = side;
        }

        @Override
        public String toString() {
            return String.format("RayTraceResult: %s, %s", hitPart == null ? "null" : hitPart.name(), boundingBox == null ? "null" : boundingBox
                .toString());
        }
    }

    /* Defined subprograms ************************************************* */
    public BlockGenericPipe() {
        super(Material.glass);
        setCreativeTab(null);
    }

    @Override
    public float getBlockHardness(World par1World, int par2, int par3, int par4) {
        return BuildCraftTransport.pipeDurability;
    }

    /* Rendering Delegation Attributes ************************************* */
    @Override
    public int getRenderType() {
        return TransportProxy.pipeModel;
    }

    @Override
    public boolean canRenderInPass(int pass) {
        PipeRendererWorld.renderPass = pass;
        return true;
    }

    @Override
    public int getRenderBlockPass() {
        return 1;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean canBeReplacedByLeaves(IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Override
    public boolean isSideSolid(IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity tile = world.getTileEntity(pos);

        if (tile instanceof ISolidSideTile) {
            return ((ISolidSideTile) tile).isSolidOnSide(side);
        }

        return false;
    }

    @Override
    public boolean isNormalCube() {
        return false;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void addCollisionBoxesToList(World world, BlockPos pos, AxisAlignedBB axisalignedbb, List arraylist, Entity par7Entity) {
        setBlockBounds(CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MAX_POS,
            CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS);
        super.addCollisionBoxesToList(world, pos, axisalignedbb, arraylist, par7Entity);

        TileEntity tile1 = world.getTileEntity(pos);
        if (tile1 instanceof TileGenericPipe) {
            TileGenericPipe tileG = (TileGenericPipe) tile1;

            if (tileG.isPipeConnected(EnumFacing.WEST)) {
                setBlockBounds(0.0F, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS,
                    CoreConstants.PIPE_MAX_POS);
                super.addCollisionBoxesToList(world, pos, axisalignedbb, arraylist, par7Entity);
            }

            if (tileG.isPipeConnected(EnumFacing.EAST)) {
                setBlockBounds(CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, 1.0F, CoreConstants.PIPE_MAX_POS,
                    CoreConstants.PIPE_MAX_POS);
                super.addCollisionBoxesToList(world, pos, axisalignedbb, arraylist, par7Entity);
            }

            if (tileG.isPipeConnected(EnumFacing.DOWN)) {
                setBlockBounds(CoreConstants.PIPE_MIN_POS, 0.0F, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS,
                    CoreConstants.PIPE_MAX_POS);
                super.addCollisionBoxesToList(world, pos, axisalignedbb, arraylist, par7Entity);
            }

            if (tileG.isPipeConnected(EnumFacing.UP)) {
                setBlockBounds(CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MAX_POS, 1.0F,
                    CoreConstants.PIPE_MAX_POS);
                super.addCollisionBoxesToList(world, pos, axisalignedbb, arraylist, par7Entity);
            }

            if (tileG.isPipeConnected(EnumFacing.NORTH)) {
                setBlockBounds(CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, 0.0F, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS,
                    CoreConstants.PIPE_MAX_POS);
                super.addCollisionBoxesToList(world, pos, axisalignedbb, arraylist, par7Entity);
            }

            if (tileG.isPipeConnected(EnumFacing.SOUTH)) {
                setBlockBounds(CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MAX_POS,
                    CoreConstants.PIPE_MAX_POS, 1.0F);
                super.addCollisionBoxesToList(world, pos, axisalignedbb, arraylist, par7Entity);
            }

            float facadeThickness = TransportConstants.FACADE_THICKNESS;

            if (tileG.hasEnabledFacade(EnumFacing.EAST)) {
                setBlockBounds(1 - facadeThickness, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
                super.addCollisionBoxesToList(world, pos, axisalignedbb, arraylist, par7Entity);
            }

            if (tileG.hasEnabledFacade(EnumFacing.WEST)) {
                setBlockBounds(0.0F, 0.0F, 0.0F, facadeThickness, 1.0F, 1.0F);
                super.addCollisionBoxesToList(world, pos, axisalignedbb, arraylist, par7Entity);
            }

            if (tileG.hasEnabledFacade(EnumFacing.UP)) {
                setBlockBounds(0.0F, 1 - facadeThickness, 0.0F, 1.0F, 1.0F, 1.0F);
                super.addCollisionBoxesToList(world, pos, axisalignedbb, arraylist, par7Entity);
            }

            if (tileG.hasEnabledFacade(EnumFacing.DOWN)) {
                setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, facadeThickness, 1.0F);
                super.addCollisionBoxesToList(world, pos, axisalignedbb, arraylist, par7Entity);
            }

            if (tileG.hasEnabledFacade(EnumFacing.SOUTH)) {
                setBlockBounds(0.0F, 0.0F, 1 - facadeThickness, 1.0F, 1.0F, 1.0F);
                super.addCollisionBoxesToList(world, pos, axisalignedbb, arraylist, par7Entity);
            }

            if (tileG.hasEnabledFacade(EnumFacing.NORTH)) {
                setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, facadeThickness);
                super.addCollisionBoxesToList(world, pos, axisalignedbb, arraylist, par7Entity);
            }
        }
        setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, BlockPos pos) {
        RaytraceResult rayTraceResult = doRayTrace(world, pos, Minecraft.getMinecraft().thePlayer);

        if (rayTraceResult != null && rayTraceResult.boundingBox != null) {
            AxisAlignedBB box = rayTraceResult.boundingBox;
            switch (rayTraceResult.hitPart) {
                case Pluggable: {
                    float scale = 0.001F;
                    box = box.expand(scale, scale, scale);
                    break;
                }
                case Pipe: {
                    float scale = 0.08F;
                    box = box.expand(scale, scale, scale);
                    break;
                }
            }
            return box.getOffsetBoundingBox(pos);
        }
        return super.getSelectedBoundingBoxFromPool(world, pos).expand(-0.85F, -0.85F, -0.85F);
    }

    @Override
    public MovingObjectPosition collisionRayTrace(World world, BlockPos pos, Vec3 origin, Vec3 direction) {
        RaytraceResult raytraceResult = doRayTrace(world, pos, origin, direction);

        if (raytraceResult == null) {
            return null;
        } else {
            return raytraceResult.movingObjectPosition;
        }
    }

    public RaytraceResult doRayTrace(World world, BlockPos pos, EntityPlayer player) {
        double reachDistance = 5;

        if (player instanceof EntityPlayerMP) {
            reachDistance = ((EntityPlayerMP) player).theItemInWorldManager.getBlockReachDistance();
        }

        double eyeHeight = world.isRemote ? player.getEyeHeight() - player.getDefaultEyeHeight() : player.getEyeHeight();
        Vec3 lookVec = player.getLookVec();
        Vec3 origin = Vec3.createVectorHelper(player.posX, player.posY + eyeHeight, player.posZ);
        Vec3 direction = origin.addVector(lookVec.xCoord * reachDistance, lookVec.yCoord * reachDistance, lookVec.zCoord * reachDistance);

        return doRayTrace(world, pos, origin, direction);
    }

    private RaytraceResult doRayTrace(World world, BlockPos pos, Vec3 origin, Vec3 direction) {
        Pipe<?> pipe = getPipe(world, pos);

        if (!isValid(pipe)) {
            return null;
        }

        TileGenericPipe tileG = pipe.container;

        if (tileG == null) {
            return null;
        }

        /** pipe hits along x, y, and z axis, gate (all 6 sides) [and wires+facades] */
        MovingObjectPosition[] hits = new MovingObjectPosition[31];
        AxisAlignedBB[] boxes = new AxisAlignedBB[31];
        EnumFacing[] sideHit = new EnumFacing[31];
        Arrays.fill(sideHit, null);

        // pipe

        for (EnumFacing side : DIR_VALUES) {
            if (side == null || tileG.isPipeConnected(side)) {
                AxisAlignedBB bb = getPipeBoundingBox(side);
                setBlockBounds(bb);
                boxes[side.ordinal()] = bb;
                hits[side.ordinal()] = super.collisionRayTrace(world, pos, origin, direction);
                sideHit[side.ordinal()] = side;
            }
        }

        // pluggables

        for (EnumFacing side : EnumFacing.VALID_DIRECTIONS) {
            if (tileG.getPipePluggable(side) != null) {
                AxisAlignedBB bb = tileG.getPipePluggable(side).getBoundingBox(side);
                setBlockBounds(bb);
                boxes[7 + side.ordinal()] = bb;
                hits[7 + side.ordinal()] = super.collisionRayTrace(world, pos, origin, direction);
                sideHit[7 + side.ordinal()] = side;
            }
        }

        // TODO: check wires

        // get closest hit

        double minLengthSquared = Double.POSITIVE_INFINITY;
        int minIndex = -1;

        for (int i = 0; i < hits.length; i++) {
            MovingObjectPosition hit = hits[i];
            if (hit == null) {
                continue;
            }

            double lengthSquared = hit.hitVec.squareDistanceTo(origin);

            if (lengthSquared < minLengthSquared) {
                minLengthSquared = lengthSquared;
                minIndex = i;
            }
        }

        // reset bounds

        setBlockBounds(0, 0, 0, 1, 1, 1);

        if (minIndex == -1) {
            return null;
        } else {
            Part hitPart;

            if (minIndex < 7) {
                hitPart = Part.Pipe;
            } else {
                hitPart = Part.Pluggable;
            }

            return new RaytraceResult(hitPart, hits[minIndex], boxes[minIndex], sideHit[minIndex]);
        }
    }

    private void setBlockBounds(AxisAlignedBB bb) {
        setBlockBounds((float) bb.minX, (float) bb.minY, (float) bb.minZ, (float) bb.maxX, (float) bb.maxY, (float) bb.maxZ);
    }

    private AxisAlignedBB getPipeBoundingBox(EnumFacing side) {
        float min = CoreConstants.PIPE_MIN_POS;
        float max = CoreConstants.PIPE_MAX_POS;

        if (side == null) {
            return new AxisAlignedBB(min, min, min, max, max, max);
        }

        float[][] bounds = new float[3][2];
        // X START - END
        bounds[0][0] = min;
        bounds[0][1] = max;
        // Y START - END
        bounds[1][0] = 0;
        bounds[1][1] = min;
        // Z START - END
        bounds[2][0] = min;
        bounds[2][1] = max;

        MatrixTranformations.transform(bounds, side);
        return new AxisAlignedBB(bounds[0][0], bounds[1][0], bounds[2][0], bounds[0][1], bounds[1][1], bounds[2][1]);
    }

    public static void removePipe(Pipe<?> pipe) {
        if (!isValid(pipe)) {
            return;
        }

        World world = pipe.container.getWorldObj();

        if (world == null) {
            return;
        }

        int x = pipe.container.xCoord;
        int y = pipe.container.yCoord;
        int z = pipe.container.zCoord;

        if (lastRemovedDate != world.getTotalWorldTime()) {
            lastRemovedDate = world.getTotalWorldTime();
            pipeRemoved.clear();
        }

        pipeRemoved.put(new BlockPos(pos), pipe);
        world.removeTileEntity(pos);
        if (pipe != null) {
            updateNeighbourSignalState(pipe);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, Block block, int par6) {
        Utils.preDestroyBlock(world, pos);
        removePipe(getPipe(world, pos));
        super.breakBlock(world, pos, block, par6);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, BlockPos pos, int metadata, int fortune) {
        if (world.isRemote) {
            return null;
        }

        ArrayList<ItemStack> list = new ArrayList<ItemStack>();
        Pipe<?> pipe = getPipe(world, pos);

        if (pipe == null) {
            pipe = pipeRemoved.get(new BlockPos(pos));
        }

        if (pipe != null) {
            if (pipe.item != null) {
                list.add(new ItemStack(pipe.item, 1, pipe.container.getItemMetadata()));
                list.addAll(pipe.computeItemDrop());
                list.addAll(pipe.getDroppedItems());
            }
        }
        return list;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileGenericPipe();
    }

    @Override
    public void dropBlockAsItemWithChance(World world, BlockPos pos, int l, float f, int dmg) {
        if (world.isRemote) {
            return;
        }
        Pipe<?> pipe = getPipe(world, pos);

        if (pipe == null) {
            pipe = pipeRemoved.get(new BlockPos(pos));
        }

        if (pipe != null) {
            Item k1 = pipe.item;

            if (k1 != null) {
                pipe.dropContents();
                for (ItemStack is : pipe.computeItemDrop()) {
                    dropBlockAsItem(world, pos, is);
                }
                dropBlockAsItem(world, pos, new ItemStack(k1, 1, pipe.container.getItemMetadata()));
            }
        }
    }

    @Override
    public Item getItemDropped(int meta, Random rand, int dmg) {
        // Returns null to be safe - the id does not depend on the meta
        return null;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, BlockPos pos) {
        RaytraceResult rayTraceResult = doRayTrace(world, pos, Minecraft.getMinecraft().thePlayer);

        if (rayTraceResult != null && rayTraceResult.boundingBox != null) {
            switch (rayTraceResult.hitPart) {
                case Pluggable: {
                    Pipe<?> pipe = getPipe(world, pos);
                    PipePluggable pluggable = pipe.container.getPipePluggable(rayTraceResult.sideHit);
                    ItemStack[] drops = pluggable.getDropItems(pipe.container);
                    if (drops != null && drops.length > 0) {
                        return drops[0];
                    }
                }
                case Pipe:
                    return new ItemStack(getPipe(world, pos).item, 1, getPipe(world, pos).container.getItemMetadata());
            }
        }
        return null;
    }

    /* Wrappers ************************************************************ */
    @Override
    public void onNeighborBlockChange(World world, BlockPos pos, Block block) {
        super.onNeighborBlockChange(world, pos, block);

        Pipe<?> pipe = getPipe(world, pos);

        if (isValid(pipe)) {
            pipe.container.scheduleNeighborChange();
            pipe.container.redstoneInput = 0;

            for (int i = 0; i < EnumFacing.VALID_DIRECTIONS.length; i++) {
                EnumFacing d = EnumFacing.getOrientation(i);
                pipe.container.redstoneInputSide[i] = getRedstoneInputToPipe(world, pos, d);
                if (pipe.container.redstoneInput < pipe.container.redstoneInputSide[i]) {
                    pipe.container.redstoneInput = pipe.container.redstoneInputSide[i];
                }
            }
        }
    }

    private int getRedstoneInputToPipe(World world, BlockPos pos, EnumFacing d) {
        int i = d.ordinal();
        int input = world.isBlockProvidingPowerTo(x + d.offsetX, y + d.offsetY, z + d.offsetZ, i);
        if (input == 0) {
            input = world.getIndirectPowerLevelTo(x + d.offsetX, y + d.offsetY, z + d.offsetZ, i);
            if (input == 0 && d != EnumFacing.DOWN) {
                Block block = world.getBlock(x + d.offsetX, y + d.offsetY, z + d.offsetZ);
                if (block instanceof BlockRedstoneWire) {
                    return world.getBlockMetadata(x + d.offsetX, y + d.offsetY, z + d.offsetZ);
                }
            }
        }
        return input;
    }

    @Override
    public int onBlockPlaced(World world, BlockPos pos, int side, float par6, float par7, float par8, int meta) {
        super.onBlockPlaced(world, pos, side, par6, par7, par8, meta);
        Pipe<?> pipe = getPipe(world, pos);

        if (isValid(pipe)) {
            pipe.onBlockPlaced();
        }

        return meta;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, placer, stack);
        Pipe<?> pipe = getPipe(world, pos);

        if (isValid(pipe)) {
            pipe.onBlockPlacedBy(placer);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, EntityPlayer player, int side, float xOffset, float yOffset, float zOffset) {
        if (super.onBlockActivated(world, pos, player, side, xOffset, yOffset, zOffset)) {
            return true;
        }

        world.notifyBlocksOfNeighborChange(pos, BuildCraftTransport.genericPipeBlock);

        Pipe<?> pipe = getPipe(world, pos);

        if (isValid(pipe)) {
            ItemStack currentItem = player.getCurrentEquippedItem();

            // Right click while sneaking with empty hand to strip equipment
            // from the pipe.
            if (player.isSneaking() && currentItem == null) {
                if (stripEquipment(world, pos, player, pipe, EnumFacing.getOrientation(side))) {
                    return true;
                }
            } else if (currentItem == null) {
                // Fall through the end of the test
            } else if (currentItem.getItem() == Items.sign) {
                // Sign will be placed anyway, so lets show the sign gui
                return false;
            } else if (currentItem.getItem() instanceof ItemPipe) {
                return false;
            } else if (currentItem.getItem() instanceof ItemGateCopier) {
                return false;
            } else if (currentItem.getItem() instanceof IToolWrench) {
                // Only check the instance at this point. Call the IToolWrench
                // interface callbacks for the individual pipe/logic calls
                return pipe.blockActivated(player);
            } else if (currentItem.getItem() instanceof IMapLocation) {
                // We want to be able to record pipe locations
                return false;
            } else if (PipeWire.RED.isPipeWire(currentItem)) {
                if (addOrStripWire(player, pipe, PipeWire.RED)) {
                    return true;
                }
            } else if (PipeWire.BLUE.isPipeWire(currentItem)) {
                if (addOrStripWire(player, pipe, PipeWire.BLUE)) {
                    return true;
                }
            } else if (PipeWire.GREEN.isPipeWire(currentItem)) {
                if (addOrStripWire(player, pipe, PipeWire.GREEN)) {
                    return true;
                }
            } else if (PipeWire.YELLOW.isPipeWire(currentItem)) {
                if (addOrStripWire(player, pipe, PipeWire.YELLOW)) {
                    return true;
                }
            } else if (currentItem.getItem() == Items.water_bucket) {
                if (!world.isRemote) {
                    pipe.container.setPipeColor(-1);
                }
                return true;
            } else if (currentItem.getItem() instanceof IPipePluggableItem) {
                if (addOrStripPipePluggable(world, pos, currentItem, player, EnumFacing.getOrientation(side), pipe)) {
                    return true;
                }
            }

            Gate clickedGate = null;

            RaytraceResult rayTraceResult = doRayTrace(world, pos, player);

            if (rayTraceResult != null && rayTraceResult.hitPart == Part.Pluggable
                && pipe.container.getPipePluggable(rayTraceResult.sideHit) instanceof GatePluggable) {
                clickedGate = pipe.gates[rayTraceResult.sideHit.ordinal()];
            }

            if (clickedGate != null) {
                clickedGate.openGui(player);
                return true;
            } else {
                return pipe.blockActivated(player);
            }
        }

        return false;
    }

    private boolean addOrStripPipePluggable(World world, BlockPos pos, ItemStack stack, EntityPlayer player, EnumFacing side, Pipe<?> pipe) {
        RaytraceResult rayTraceResult = doRayTrace(world, pos, player);

        EnumFacing placementSide = rayTraceResult != null && rayTraceResult.sideHit != null ? rayTraceResult.sideHit : side;

        IPipePluggableItem pluggableItem = (IPipePluggableItem) stack.getItem();
        PipePluggable pluggable = pluggableItem.createPipePluggable(pipe, placementSide, stack);

        if (pluggable == null) {
            return false;
        }

        if (player.isSneaking()) {
            if (pipe.container.hasPipePluggable(side) && rayTraceResult != null && rayTraceResult.hitPart == Part.Pluggable
                && pluggable.getClass().isInstance(pipe.container.getPipePluggable(side))) {
                return pipe.container.setPluggable(side, null, player);
            }
        }

        if (rayTraceResult != null && rayTraceResult.hitPart == Part.Pipe) {
            if (!pipe.container.hasPipePluggable(placementSide)) {
                if (pipe.container.setPluggable(placementSide, pluggable, player)) {
                    if (!player.capabilities.isCreativeMode) {
                        stack.stackSize--;
                    }

                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    private boolean addOrStripWire(EntityPlayer player, Pipe<?> pipe, PipeWire color) {
        if (addWire(pipe, color)) {
            if (!player.capabilities.isCreativeMode) {
                player.getCurrentEquippedItem().splitStack(1);
            }
            return true;
        }
        return player.isSneaking() && stripWire(pipe, color, player);
    }

    private boolean addWire(Pipe<?> pipe, PipeWire color) {
        if (!pipe.wireSet[color.ordinal()]) {
            pipe.wireSet[color.ordinal()] = true;
            pipe.signalStrength[color.ordinal()] = 0;

            pipe.updateSignalState();
            pipe.container.scheduleRenderUpdate();
            return true;
        }
        return false;
    }

    private boolean stripWire(Pipe<?> pipe, PipeWire color, EntityPlayer player) {
        if (pipe.wireSet[color.ordinal()]) {
            if (!pipe.container.getWorldObj().isRemote) {
                dropWire(color, pipe, player);
            }

            pipe.signalStrength[color.ordinal()] = 0;
            pipe.wireSet[color.ordinal()] = false;

            pipe.updateSignalState();

            updateNeighbourSignalState(pipe);

            if (isFullyDefined(pipe)) {
                pipe.resolveActions();
            }

            pipe.container.scheduleRenderUpdate();

            return true;
        }
        return false;
    }

    private boolean stripEquipment(World world, BlockPos pos, EntityPlayer player, Pipe<?> pipe, EnumFacing side) {
        if (!world.isRemote) {
            // Try to strip pluggables first
            EnumFacing nSide = side;

            RaytraceResult rayTraceResult = doRayTrace(world, pos, player);
            if (rayTraceResult != null && rayTraceResult.hitPart != Part.Pipe) {
                nSide = rayTraceResult.sideHit;
            }

            if (pipe.container.hasPipePluggable(nSide)) {
                return pipe.container.setPluggable(nSide, null, player);
            }

            // Try to strip wires second, starting with yellow.
            for (PipeWire color : PipeWire.values()) {
                if (stripWire(pipe, color, player)) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Drops a pipe wire item of the passed color.
     *
     * @param pipeWire */
    private void dropWire(PipeWire pipeWire, Pipe<?> pipe, EntityPlayer player) {
        Utils.dropTryIntoPlayerInventory(pipe.container.getWorld(), pipe.container.x(), pipe.container.y(), pipe.container.z(), pipeWire.getStack(),
            player);
    }

    @Override
    public void onEntityCollidedWithBlock(World world, BlockPos pos, Entity entity) {
        super.onEntityCollidedWithBlock(world, pos, entity);

        Pipe<?> pipe = getPipe(world, pos);

        if (isValid(pipe)) {
            pipe.onEntityCollidedWithBlock(entity);
        }
    }

    @Override
    public boolean canConnectRedstone(IBlockAccess world, BlockPos pos, int side) {
        Pipe<?> pipe = getPipe(world, pos);

        if (isValid(pipe)) {
            return pipe.canConnectRedstone();
        } else {
            return false;
        }
    }

    @Override
    public int isProvidingStrongPower(IBlockAccess iblockaccess, BlockPos pos, int l) {
        Pipe<?> pipe = getPipe(iblockaccess, pos);

        if (isValid(pipe)) {
            return pipe.isPoweringTo(l);
        } else {
            return 0;
        }
    }

    @Override
    public boolean canProvidePower() {
        return true;
    }

    @Override
    public int isProvidingWeakPower(IBlockAccess world, BlockPos pos, int l) {
        Pipe<?> pipe = getPipe(world, pos);

        if (isValid(pipe)) {
            return pipe.isIndirectlyPoweringTo(l);
        } else {
            return 0;
        }
    }

    @SuppressWarnings({ "all" })
    @Override
    public void randomDisplayTick(World world, BlockPos pos, Random random) {
        Pipe pipe = getPipe(world, pos);

        if (isValid(pipe)) {
            pipe.randomDisplayTick(random);
        }
    }

    /* Registration ******************************************************** */
    public static ItemPipe registerPipe(Class<? extends Pipe> clas, BCCreativeTab creativeTab) {
        ItemPipe item = new ItemPipe(creativeTab);
        item.setUnlocalizedName("buildcraftPipe." + clas.getSimpleName().toLowerCase(Locale.ENGLISH));
        GameRegistry.registerItem(item, item.getUnlocalizedName());

        pipes.put(item, clas);

        Pipe<?> dummyPipe = createPipe(item);
        if (dummyPipe != null) {
            item.setPipeIconIndex(dummyPipe.getIconIndexForItem());
            TransportProxy.proxy.setIconProviderFromPipe(item, dummyPipe);
        }

        return item;
    }

    public static Pipe<?> createPipe(Item key) {

        try {
            Class<? extends Pipe> pipe = pipes.get(key);
            if (pipe != null) {
                return pipe.getConstructor(Item.class).newInstance(key);
            } else {
                BCLog.logger.warn("Detected pipe with unknown key (" + key + "). Did you remove a buildcraft addon?");
            }

        } catch (Throwable t) {
            t.printStackTrace();
            BCLog.logger.warn("Failed to create pipe with (" + key + "). No valid constructor found. Possibly a item ID conflit.");
        }

        return null;
    }

    public static boolean placePipe(Pipe<?> pipe, World world, BlockPos pos, Block block, int meta, EntityPlayer player) {
        if (world.isRemote) {
            return true;
        }

        boolean placed = world.setBlock(pos, block, meta, 3);

        if (placed) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileGenericPipe) {
                TileGenericPipe tilePipe = (TileGenericPipe) tile;
                tilePipe.initialize(pipe);
                tilePipe.sendUpdateToClient();
                FMLCommonHandler.instance().bus().post(new PipePlacedEvent(player, pipe.item.getUnlocalizedName(), pos));
            }
        }

        return placed;
    }

    public static Pipe<?> getPipe(IBlockAccess blockAccess, BlockPos pos) {
        TileEntity tile = blockAccess.getTileEntity(pos);

        if (tile instanceof IPipeTile && !tile.isInvalid()) {
            IPipe pipe = ((IPipeTile) tile).getPipe();
            if (pipe instanceof Pipe<?>) {
                return (Pipe<?>) pipe;
            }
        }
        return null;
    }

    public static boolean isFullyDefined(Pipe<?> pipe) {
        return pipe != null && pipe.transport != null && pipe.container != null;
    }

    public static boolean isValid(Pipe<?> pipe) {
        return isFullyDefined(pipe);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(TextureAtlasSpriteRegister iconRegister) {

    }

    /** Spawn a digging particle effect in the world, this is a wrapper around EffectRenderer.addBlockHitEffects to allow
     * the block more control over the particles. Useful when you have entirely different texture sheets for different
     * sides/locations in the world.
     *
     * @param worldObj The current world
     * @param target The target the player is looking at {x/y/z/side/sub}
     * @param effectRenderer A reference to the current effect renderer.
     * @return True to prevent vanilla digging particles form spawning. */
    @SideOnly(Side.CLIENT)
    @Override
    public boolean addHitEffects(World worldObj, MovingObjectPosition target, EffectRenderer effectRenderer) {
        int x = target.blockX;
        int y = target.blockY;
        int z = target.blockZ;

        Pipe<?> pipe = getPipe(worldObj, pos);
        if (pipe == null) {
            return false;
        }

        TextureAtlasSprite icon = pipe.getIconProvider().getIcon(pipe.getIconIndexForItem());

        int sideHit = target.sideHit;

        Block block = BuildCraftTransport.genericPipeBlock;
        float b = 0.1F;
        double px = x + rand.nextDouble() * (block.getBlockBoundsMaxX() - block.getBlockBoundsMinX() - (b * 2.0F)) + b + block.getBlockBoundsMinX();
        double py = y + rand.nextDouble() * (block.getBlockBoundsMaxY() - block.getBlockBoundsMinY() - (b * 2.0F)) + b + block.getBlockBoundsMinY();
        double pz = z + rand.nextDouble() * (block.getBlockBoundsMaxZ() - block.getBlockBoundsMinZ() - (b * 2.0F)) + b + block.getBlockBoundsMinZ();

        if (sideHit == 0) {
            py = y + block.getBlockBoundsMinY() - b;
        }

        if (sideHit == 1) {
            py = y + block.getBlockBoundsMaxY() + b;
        }

        if (sideHit == 2) {
            pz = z + block.getBlockBoundsMinZ() - b;
        }

        if (sideHit == 3) {
            pz = z + block.getBlockBoundsMaxZ() + b;
        }

        if (sideHit == 4) {
            px = x + block.getBlockBoundsMinX() - b;
        }

        if (sideHit == 5) {
            px = x + block.getBlockBoundsMaxX() + b;
        }

        EntityDiggingFX fx = new EntityDiggingFX(worldObj, px, py, pz, 0.0D, 0.0D, 0.0D, block, sideHit, worldObj.getBlockMetadata(pos));
        fx.setParticleIcon(icon);
        effectRenderer.addEffect(fx.applyColourMultiplier(pos).multiplyVelocity(0.2F).multipleParticleScaleBy(0.6F));
        return true;
    }

    /** Spawn particles for when the block is destroyed. Due to the nature of how this is invoked, the x/y/z locations
     * are not always guaranteed to host your block. So be sure to do proper sanity checks before assuming that the
     * location is this block.
     *
     * @param worldObj The current world
     * @param x X position to spawn the particle
     * @param y Y position to spawn the particle
     * @param z Z position to spawn the particle
     * @param meta The metadata for the block before it was destroyed.
     * @param effectRenderer A reference to the current effect renderer.
     * @return True to prevent vanilla break particles from spawning. */
    @SideOnly(Side.CLIENT)
    @Override
    public boolean addDestroyEffects(World worldObj, BlockPos pos, int meta, EffectRenderer effectRenderer) {
        Pipe<?> pipe = getPipe(worldObj, pos);
        if (pipe == null) {
            return false;
        }

        TextureAtlasSprite icon = pipe.getIconProvider().getIcon(pipe.getIconIndexForItem());

        byte its = 4;
        for (int i = 0; i < its; ++i) {
            for (int j = 0; j < its; ++j) {
                for (int k = 0; k < its; ++k) {
                    double px = x + (i + 0.5D) / its;
                    double py = y + (j + 0.5D) / its;
                    double pz = z + (k + 0.5D) / its;
                    int random = rand.nextInt(6);
                    EntityDiggingFX fx =
                        new EntityDiggingFX(worldObj, px, py, pz, px - x - 0.5D, py - y - 0.5D, pz - z - 0.5D, BuildCraftTransport.genericPipeBlock,
                            random, meta);
                    fx.setParticleIcon(icon);
                    effectRenderer.addEffect(fx.applyColourMultiplier(pos));
                }
            }
        }
        return true;
    }

    @Override
    public boolean recolourBlock(World world, BlockPos pos, EnumFacing side, int colour) {
        TileGenericPipe pipeTile = (TileGenericPipe) world.getTileEntity(pos);
        if (!pipeTile.hasBlockingPluggable(side)) {
            return pipeTile.setPipeColor(colour);
        }

        return false;
    }

    @Override
    public boolean removeColorFromBlock(World world, BlockPos pos, EnumFacing side) {
        TileGenericPipe pipeTile = (TileGenericPipe) world.getTileEntity(pos);
        if (!pipeTile.hasBlockingPluggable(side)) {
            return pipeTile.setPipeColor(-1);
        }

        return false;
    }

    public static void updateNeighbourSignalState(Pipe<?> pipe) {
        if (pipe != null && pipe.container != null) {
            TileBuffer[] neighbours = pipe.container.getTileCache();

            if (neighbours != null) {
                for (int i = 0; i < 6; i++) {
                    if (neighbours[i] != null && neighbours[i].getTile() instanceof IPipeTile && !neighbours[i].getTile().isInvalid()
                        && ((IPipeTile) neighbours[i].getTile()).getPipe() instanceof Pipe<?>) {
                        ((Pipe<?>) ((IPipeTile) neighbours[i].getTile()).getPipe()).updateSignalState();
                    }
                }
            }
        }
    }

    @Override
    public TextureAtlasSprite getIcon(IBlockAccess world, BlockPos pos, int side) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileGenericPipe) {
            Pipe pipe = (Pipe) ((TileGenericPipe) tile).getPipe();
            return pipe.getIconProvider().getIcon(pipe.getIconIndexForItem());
        }

        return PipeIconProvider.TYPE.PipeItemsStone.getIcon();
    }

    @Override
    public TextureAtlasSprite getIcon(int side, int meta) {
        return PipeIconProvider.TYPE.PipeItemsStone.getIcon();
    }
}