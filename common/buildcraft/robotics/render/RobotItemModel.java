package buildcraft.robotics.render;

import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.core.lib.render.BuildCraftBakedModel;
import buildcraft.robotics.ItemRobot;
import buildcraft.robotics.RoboticsProxyClient;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.model.ISmartItemModel;

public class RobotItemModel extends BuildCraftBakedModel implements ISmartItemModel {
    protected RobotItemModel(ImmutableList<BakedQuad> quads, TextureAtlasSprite particle, VertexFormat format) {
        super(quads, particle, format);
        // FIXME: Add perspective transformation
    }

    @Override
    public RobotItemModel handleItemState(ItemStack stack) {
        return handle(stack);
    }

    public static RobotItemModel handle(ItemStack stack) {
        if (stack != null) {
            RedstoneBoardRobotNBT board = ItemRobot.getRobotNBT(stack);
            if (board != null) {
                IBakedModel model = RoboticsProxyClient.robotModel.get(board.getID());
                if (model != null) {
                    return new RobotItemModel(ImmutableList.copyOf(model.getGeneralQuads()), model.getParticleTexture(), DefaultVertexFormats.ITEM);
                }
            }
        }

        return new RobotItemModel(ImmutableList.<BakedQuad> of(), null, DefaultVertexFormats.ITEM);
    }

    public static RobotItemModel create() {
        return new RobotItemModel(null, null, null);
    }
}
