package ca.lukegrahamlandry.mimic.events;

import ca.lukegrahamlandry.mimic.Constants;
import ca.lukegrahamlandry.mimic.entities.MimicEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class TickHandler {
    public static List<MimicSpawnData> spawns = new ArrayList<>();

    public static void onServerTick() {
        for (MimicSpawnData spawn : spawns){
            spawn.time--;
            if (spawn.time <= 0){
                spawnMimic(spawn);
                spawns.remove(spawn);
                return;
            }
        }
    }

    private static void spawnMimic(MimicSpawnData spawn) {
        BlockState state = spawn.world.getBlockState(spawn.pos);
        if (state.is(Blocks.CHEST)){
            ChestBlockEntity chest = (ChestBlockEntity) spawn.world.getBlockEntity(spawn.pos);
            MimicEntity mimic = (MimicEntity) Registry.ENTITY_TYPE.get(Constants.MIMIC_ENTITY_ID).create(spawn.world);

            // take items
            for (int i=0;i<chest.getContainerSize();i++){
                mimic.addItem(chest.getItem(i));
                chest.setItem(i, ItemStack.EMPTY);
            }

            spawn.world.setBlock(spawn.pos, Blocks.AIR.defaultBlockState(), 3);
            mimic.snapToBlock(spawn.pos, state.getValue(ChestBlock.FACING));
            mimic.setStealth(true);
            spawn.world.addFreshEntity(mimic);
        }
    }

    public static class MimicSpawnData {
        public final BlockPos pos;
        public int time;
        public final Level world;

        public MimicSpawnData(Level level, BlockPos position){
            this.pos = position;
            this.time = 40;
            this.world = level;
        }
    }
}
