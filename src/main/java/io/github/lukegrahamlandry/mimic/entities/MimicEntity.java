package io.github.lukegrahamlandry.mimic.entities;

import io.github.lukegrahamlandry.mimic.MimicMain;
import io.github.lukegrahamlandry.mimic.client.MimicContainer;
import io.github.lukegrahamlandry.mimic.goals.*;
import io.github.lukegrahamlandry.mimic.init.ContainerInit;
import io.github.lukegrahamlandry.mimic.init.ItemInit;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MimicEntity extends CreatureEntity implements IAnimatable, INamedContainerProvider, IInventory {

    private AnimationFactory factory = new AnimationFactory(this);

    private static final DataParameter<Boolean> IS_TAMED = EntityDataManager.defineId(MimicEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> IS_STEALTH = EntityDataManager.defineId(MimicEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> IS_LOCKED = EntityDataManager.defineId(MimicEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> IS_ANGRY = EntityDataManager.defineId(MimicEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> ATTACK_TICK = EntityDataManager.defineId(MimicEntity.class, DataSerializers.INT);
    private static final DataParameter<BlockPos> CHEST_POS = EntityDataManager.defineId(MimicEntity.class, DataSerializers.BLOCK_POS);
    private static final DataParameter<Integer> UP_DOWN_TICK = EntityDataManager.defineId(MimicEntity.class, DataSerializers.INT);
    private static final DataParameter<Boolean> IS_OPEN = EntityDataManager.defineId(MimicEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> OPEN_CLOSE_TICK = EntityDataManager.defineId(MimicEntity.class, DataSerializers.INT);

    private UUID owner;

    int playerLookTicks = 0;
    PlayerEntity playerLooking;
    int scaredTicks = 0;

    private NonNullList<ItemStack> heldItems = NonNullList.withSize(27, ItemStack.EMPTY);

    public MimicEntity(EntityType<? extends MimicEntity> type, World world) {
        super(type, world);
    }

    public static AttributeModifierMap.MutableAttribute createMobAttributes() {
        return AttributeModifierMap.builder().add(Attributes.MAX_HEALTH, 60).add(Attributes.ATTACK_DAMAGE, 14).add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.MOVEMENT_SPEED, 0.55).add(Attributes.ARMOR).add(Attributes.ARMOR_TOUGHNESS).add(net.minecraftforge.common.ForgeMod.SWIM_SPEED.get()).add(net.minecraftforge.common.ForgeMod.NAMETAG_DISTANCE.get()).add(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get()).add(Attributes.FOLLOW_RANGE, 32.0D).add(Attributes.ATTACK_KNOCKBACK);
    }

    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, PlayerEntity.class, true));
        this.goalSelector.addGoal(2, new MimicChaseGoal(this, 0.6, 10));
        this.goalSelector.addGoal(2, new MimicAttackGoal(this));

        this.goalSelector.addGoal(3, new EatChestGoal(this, 0.5, 3));
        this.goalSelector.addGoal(2, new LockedPanicGoal(this, 0.6));
        this.goalSelector.addGoal(6, new TamedFollowGoal(this, 0.5D, 8.0F, 2.0F, false));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level.isClientSide()){
            if (this.getAttackTick() > 0) this.getEntityData().set(ATTACK_TICK, this.getAttackTick() - 1);
            if (this.getEntityData().get(UP_DOWN_TICK) > 0) this.getEntityData().set(UP_DOWN_TICK, this.getEntityData().get(UP_DOWN_TICK) - 1);
            if (this.getEntityData().get(OPEN_CLOSE_TICK) > 0) this.getEntityData().set(OPEN_CLOSE_TICK, this.getEntityData().get(OPEN_CLOSE_TICK) - 1);
            if (scaredTicks > 0) scaredTicks--;

            if (this.playerLookTicks > 0){
                this.playerLookTicks--;
                if (this.playerLookTicks == 0 && !isLocked() && !isTamed()){
                    this.playerLooking.closeContainer();
                    this.setAngry(true);
                }
            }
        }
    }

    public void setFacingDirection(int x){
        this.setRot(x * 90, 180);
    }

    // decides which animation to play. animationName is from the json file in resources/id/animations
    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event){
        if (this.getAttackTick() > 0){
            event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.mimic.attack", false));
            return PlayState.CONTINUE;
        }

        if (isOpen()){
            if (this.getEntityData().get(OPEN_CLOSE_TICK) > 0){
                if (isTamed()){
                    event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.tamed.mimic.chest.open", false));
                } else {
                    event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.mimic.chest.open", false));
                }
                return PlayState.CONTINUE;
            }

            if (isTamed()){
                event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.tamed.mimic.chest.open.idle", false));
            } else {
                event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.mimic.chest.open.idle", false));
            }
            return PlayState.CONTINUE;

        } else if (this.getEntityData().get(OPEN_CLOSE_TICK) > 0){
            if (isTamed()){
                event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.tamed.mimic.chest.close", false));
            } else {
                event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.mimic.chest.close", false));
            }

            return PlayState.CONTINUE;
        }


        if (isLocked()){
            if (this.getEntityData().get(UP_DOWN_TICK) > 0){
                event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.mimic.lock", false));
                return PlayState.CONTINUE;
            }

            event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.mimic.lockidle", true));
            return PlayState.CONTINUE;
        }

        if (this.getEntityData().get(IS_STEALTH)){  // dont replace condition with isStealth(), it will break, im being too clever
            if (this.getEntityData().get(UP_DOWN_TICK) > 0){
                event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.mimic.sit", false));
                return PlayState.CONTINUE;
            }

            event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.mimic.idle.chest", true));
            return PlayState.CONTINUE;

        } else if (this.getEntityData().get(UP_DOWN_TICK) > 0){
            event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.mimic.getup", false));
            return PlayState.CONTINUE;
        }

        event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.mimic.idle", true));
        return PlayState.CONTINUE;
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.getEntityData().define(ATTACK_TICK, 0);
        this.getEntityData().define(UP_DOWN_TICK, 0);
        this.getEntityData().define(IS_TAMED, false);
        this.getEntityData().define(IS_LOCKED, false);
        this.getEntityData().define(IS_ANGRY, false);
        this.getEntityData().define(IS_STEALTH, false);
        this.getEntityData().define(IS_OPEN, false);
        this.getEntityData().define(OPEN_CLOSE_TICK, 0);
        this.getEntityData().define(CHEST_POS, BlockPos.ZERO);
    }

    @Override
    protected ActionResultType mobInteract(PlayerEntity player, Hand hand) {
        if (isTamed() && player.isShiftKeyDown() && !this.level.isClientSide()){
            this.setStealth(!this.isStealth());
            // snap to block
            BlockPos pos = this.blockPosition();
            this.setPos(pos.getX()+0.5d, pos.getY()+0.5d, pos.getZ()+0.5d);
            return ActionResultType.SUCCESS;
        }


        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && stack.getItem() == ItemInit.MIMIC_LOCK.get()){
            this.setLocked(true);
            if (!player.isCreative()) stack.shrink(1);
            return ActionResultType.CONSUME;
        }

        if (stack.getItem() == ItemInit.MIMIC_KEY.get() && !this.isTamed()){
            if (!level.isClientSide()){
                this.setTamed(true);
                if (!player.isCreative()) stack.shrink(1);
                this.owner = player.getUUID();
            } else {
                for(int i = 0; i < 7; ++i) {
                    double d0 = this.random.nextGaussian() * 0.02D;
                    double d1 = this.random.nextGaussian() * 0.02D;
                    double d2 = this.random.nextGaussian() * 0.02D;
                    this.level.addParticle(ParticleTypes.HEART, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
                }
            }
            return ActionResultType.CONSUME;
        }

        player.openMenu(this);
        return ActionResultType.SUCCESS;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource p_213333_1_, int p_213333_2_, boolean p_213333_3_) {
        super.dropCustomDeathLoot(p_213333_1_, p_213333_2_, p_213333_3_);
        for (int i=0;i<this.heldItems.size();i++){
            if (this.heldItems.get(i).getItem() != Items.AIR){
                this.spawnAtLocation(this.heldItems.get(i));
            }
        }
    }

    public void addItem(ItemStack stack) {
        for (int i=0;i<this.heldItems.size();i++){
            if (this.heldItems.get(i).getItem() == Items.AIR){
                this.heldItems.set(i, stack);
                this.setPersistenceRequired();
                return;
            }
        }

        MimicMain.LOGGER.debug(this.heldItems);

        ItemEntity item = new ItemEntity(this.level, this.getX(), this.getY(), this.getZ(), stack);
        this.level.addFreshEntity(item);
    }

    @Override
    public void addAdditionalSaveData(CompoundNBT compoundNBT) {
        super.addAdditionalSaveData(compoundNBT);

        CompoundNBT nbt = new CompoundNBT();
        for (int i=0;i<this.heldItems.size();i++){
            ItemStack stack = this.heldItems.get(i);
            CompoundNBT tag = stack.save(new CompoundNBT());
            nbt.put(String.valueOf(i), tag);
        }

        compoundNBT.put("mimichelditems", nbt);

        compoundNBT.putBoolean("tame", isTamed());
        compoundNBT.putBoolean("lock", isLocked());
        compoundNBT.putBoolean("angry", isAngry());
        compoundNBT.putBoolean("stealth", isStealth());

        if (this.isTamed()){
            compoundNBT.putString("owner", this.owner.toString());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT compoundNBT) {
        super.readAdditionalSaveData(compoundNBT);

        CompoundNBT nbt = compoundNBT.getCompound("mimichelditems");
        int i = 0;
        while (nbt.contains(String.valueOf(i))) {
            CompoundNBT tag = nbt.getCompound(String.valueOf(i));
            ItemStack stack = ItemStack.of(tag);
            this.heldItems.set(i, stack);
            i++;
        }

        setTamed(compoundNBT.getBoolean("tame"));
        setLocked(compoundNBT.getBoolean("lock"));
        setStealth(compoundNBT.getBoolean("stealth"));
        setAngry(compoundNBT.getBoolean("angry"));

        if (this.isTamed()){
            this.owner = UUID.fromString(compoundNBT.getString("owner"));
        }

    }

    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }

        if (source.getDirectEntity() != null && source.getDirectEntity() instanceof LivingEntity && ((LivingEntity)source.getDirectEntity()).getItemInHand(Hand.MAIN_HAND).getItem() instanceof AxeItem){
            amount *= 2;
        }
        this.setAngry(true);
        return super.hurt(source, amount);
    }

    public LivingEntity getOwner(){
        if (!this.isTamed() || this.level.isClientSide()) return null;
        return (LivingEntity) ((ServerWorld)this.level).getEntity(this.owner);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void push(Entity p_70108_1_) {
        // super.push(p_70108_1_);
    }

    @Override
    public void push(double p_70024_1_, double p_70024_3_, double p_70024_5_) {
        // super.push(p_70024_1_, p_70024_3_, p_70024_5_);
    }

    public boolean isScared(){
        return scaredTicks > 0;
    }

    public boolean isOpen() {
        return this.getEntityData().get(IS_OPEN);
    }

    public boolean isTamed() {
        if (this.owner == null) return false;
        return this.getEntityData().get(IS_TAMED);
    }

    public boolean isAngry() {
        boolean flag = this.getEntityData().get(IS_ANGRY);
        if (flag && isStealth()) return false; // it is playing the stand animation and will be ready to be angry soon
        return flag;
    }

    public boolean isStealth() {
        boolean flag = this.getEntityData().get(IS_STEALTH);

        if (!flag && this.getEntityData().get(UP_DOWN_TICK) > 0){ // currently standing up
            return true;
        }

        return flag;
    }

    public boolean isLocked() {
        return this.getEntityData().get(IS_LOCKED);
    }

    public boolean hasTarget() {
        return this.getTarget() != null && this.getTarget().isAlive();
    }

    public int getAttackTick() {
        return this.getEntityData().get(ATTACK_TICK);
    }

    public void startAttackAnim() {
        this.getEntityData().set(ATTACK_TICK, 20);
    }

    public void setAngry(boolean flag) {
        if (flag){
            if (isTamed() || isLocked()) return;
            setStealth(false);
        }
        this.getEntityData().set(IS_ANGRY, flag);
    }

    public void setStealth(boolean flag) {
        MimicMain.LOGGER.debug("stealth: " + flag);
        if (!isStealth() && flag){
            this.getEntityData().set(UP_DOWN_TICK, 20);
        } else if (isStealth() && !flag){
            this.getEntityData().set(UP_DOWN_TICK, 22);
        }
        this.getEntityData().set(IS_STEALTH, flag);
    }

    public void setLocked(boolean flag) {
        MimicMain.LOGGER.debug("lock: " + flag);
        if (!isLocked() && flag){
            this.getEntityData().set(UP_DOWN_TICK, 28);
        }
        if (flag){
            setAngry(false);
        }
        this.getEntityData().set(IS_LOCKED, flag);
    }

    public void setTamed(boolean flag) {
        if (flag){
            setAngry(false);
            setStealth(false);
        }
        this.getEntityData().set(IS_TAMED, flag);
    }

    public void setChestPos(BlockPos pos) {
        this.getEntityData().set(CHEST_POS, pos);
    }

    public BlockPos getChestPos() {
        return this.getEntityData().get(CHEST_POS);
    }

    @Override
    public void registerControllers(AnimationData data){
        data.addAnimationController(new AnimationController(this, "moveController", 0, this::predicate));
    }

    @Override
    public AnimationFactory getFactory(){
        return this.factory;
    }

    @Nullable
    @Override
    public Container createMenu(int id, PlayerInventory playerInventory, PlayerEntity player) {
        if (this.isTamed()){
            return new MimicContainer(ContainerInit.TAME_MIMIC.get(), id, playerInventory, this, 3);
        } else {
            this.playerLooking = player;
            this.playerLookTicks = 30;
            return new MimicContainer(ContainerInit.EVIL_MIMIC.get(), id, playerInventory, this, 3);
        }
    }

    // IInventory

    @Override
    public void stopOpen(PlayerEntity p_174886_1_) {
        this.getEntityData().set(OPEN_CLOSE_TICK, 10);
        this.getEntityData().set(IS_OPEN, false);
    }

    @Override
    public void startOpen(PlayerEntity p_174889_1_) {
        this.getEntityData().set(OPEN_CLOSE_TICK, 13);
        this.getEntityData().set(IS_OPEN, true);
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    public boolean isEmpty() {
        return this.heldItems.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int index) {
        return this.heldItems.get(index);
    }

    @Override
    public ItemStack removeItem(int p_70298_1_, int p_70298_2_) {
        ItemStack itemstack = ItemStackHelper.removeItem(this.heldItems, p_70298_1_, p_70298_2_);
        if (!itemstack.isEmpty()) {
            this.setChanged();

            this.playerLookTicks = 2;
            if (this.isLocked() && getRandom().nextInt(3) == 0){
                this.playerLooking.closeContainer();
                this.scaredTicks = 200;
            }
        }

        return itemstack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int p_70304_1_) {
        return ItemStackHelper.takeItem(this.heldItems, p_70304_1_);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.heldItems.set(index, stack);
    }

    @Override
    public void setChanged() {

    }

    @Override
    public boolean stillValid(PlayerEntity p_70300_1_) {
        return this.isAlive();
    }

    @Override
    public void clearContent() {
        this.heldItems.clear();
    }
}
