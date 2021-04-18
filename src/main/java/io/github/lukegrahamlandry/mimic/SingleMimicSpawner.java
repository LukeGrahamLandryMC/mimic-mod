package io.github.lukegrahamlandry.mimic;

import io.github.lukegrahamlandry.mimic.entities.MimicEntity;
import io.github.lukegrahamlandry.mimic.init.EntityInit;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.Random;

public class SingleMimicSpawner extends Block {
    public SingleMimicSpawner(Block.Properties properties) {
        super(properties);
    }

    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos pos, Random rand) {
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        MimicMain.LOGGER.debug("spawn mimic at " + pos);
        MimicEntity e = new MimicEntity(EntityInit.MIMIC.get(), world);
        e.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        world.addFreshEntity(e);
    }

    /*
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }
     */
}