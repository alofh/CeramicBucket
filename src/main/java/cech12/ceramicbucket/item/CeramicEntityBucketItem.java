package cech12.ceramicbucket.item;

import cech12.ceramicbucket.api.data.ObtainableEntityType;
import cech12.ceramicbucket.compat.ModCompat;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.fish.AbstractFishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CeramicEntityBucketItem extends FilledCeramicBucketItem {

    public CeramicEntityBucketItem(Item.Properties builder) {
        super(builder);
    }

    @Deprecated
    @Override
    public ItemStack getFilledInstance(@Nonnull Fluid fluid, @Nullable ItemStack oldStack) {
        return ItemStack.EMPTY;
    }

    public ItemStack getFilledInstance(@Nonnull Fluid fluid, @Nonnull Entity entity, @Nullable ItemStack oldStack) {
        return this.putEntityInStack(super.getFilledInstance(fluid, oldStack), entity);
    }

    private ItemStack getFilledInstance(@Nonnull Fluid fluid, @Nonnull EntityType<?> entityType) {
        return this.putEntityTypeInStack(super.getFilledInstance(fluid, null), entityType);
    }

    @Override
    @Nonnull
    public ActionResult<ItemStack> use(@Nonnull World worldIn, PlayerEntity playerIn, @Nonnull Hand handIn) {
        //support empty fluids
        ItemStack itemstack = playerIn.getItemInHand(handIn);
        if (this.getFluid(itemstack) == Fluids.EMPTY) {
            BlockRayTraceResult raytraceresult = getPlayerPOVHitResult(worldIn, playerIn, RayTraceContext.FluidMode.NONE);
            //ActionResult<ItemStack> ret = net.minecraftforge.event.ForgeEventFactory.onBucketUse(playerIn, worldIn, itemstack, raytraceresult);
            BlockPos blockpos = raytraceresult.getBlockPos().relative(raytraceresult.getDirection());
            this.checkExtraContent(worldIn, itemstack, blockpos);
            ItemStack result = (!playerIn.abilities.instabuild) ? this.getContainerItem(itemstack) : itemstack;
            return new ActionResult<>(ActionResultType.SUCCESS, result);
        }
        return super.use(worldIn, playerIn, handIn);
    }

    @Override
    public void checkExtraContent(World worldIn, @Nonnull ItemStack stack, @Nonnull BlockPos blockPos) {
        if (!worldIn.isClientSide && worldIn instanceof ServerWorld) {
            EntityType<?> entityType = getEntityTypeFromStack(stack);
            if (entityType != null) {
                Entity entity = entityType.spawn((ServerWorld) worldIn, stack, null, blockPos, SpawnReason.BUCKET, true, false);
                if (entity instanceof AbstractFishEntity) {
                    ((AbstractFishEntity)entity).setFromBucket(true);
                } else if (entity instanceof MobEntity) {
                    ((MobEntity)entity).setPersistenceRequired(); //TODO really?
                }
            }
        }
    }

    @Override
    public void playEmptySound(@Nullable PlayerEntity player, @Nonnull IWorld worldIn, @Nonnull BlockPos pos, @Nonnull ItemStack stack) {
        ObtainableEntityType type = ModCompat.getObtainableEntityType(this.getEntityTypeFromStack(stack));
        if (type != null) {
            SoundEvent soundevent = type.getEmptySound();
            worldIn.playSound(player, pos, soundevent, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    public void playFillSound(@Nullable PlayerEntity player, @Nonnull ItemStack stack) {
        if (player == null) return;
        ObtainableEntityType type = ModCompat.getObtainableEntityType(this.getEntityTypeFromStack(stack));
        if (type != null) {
            SoundEvent soundevent = type.getFillSound();
            player.playSound(soundevent, 1.0F, 1.0F);
        }
    }

    /**
     * returns a list of items with the same ID, but different meta (eg: dye returns 16 items)
     */
    @Override
    public void fillItemCategory(@Nonnull ItemGroup group, @Nonnull NonNullList<ItemStack> items) {
        if (this.allowdedIn(group)) {
            for (ObtainableEntityType type : ModCompat.getObtainableEntityTypes()) {
                EntityType<?> entityType = type.getEntityType();
                if (entityType != null) {
                    items.add(this.getFilledInstance(type.getOneFluid(), entityType));
                }
            }
        }
    }

    @Override
    @Nonnull
    public ITextComponent getName(@Nonnull ItemStack stack) {
        EntityType<?> type = getEntityTypeFromStack(stack);
        ITextComponent name = (type != null) ? type.getDescription() : new StringTextComponent("?");
        return new TranslationTextComponent("item.ceramicbucket.ceramic_entity_bucket", name);
    }

    public ItemStack putEntityInStack(ItemStack stack, Entity entity) {
        CompoundNBT nbt = stack.getOrCreateTag();
        nbt.putString("EntityType", EntityType.getKey(entity.getType()).toString());
        CompoundNBT entityNbt = entity.saveWithoutId(new CompoundNBT());
        entityNbt.remove("Pos");
        entityNbt.remove("Motion");
        entityNbt.remove("FallDistance");
        nbt.put("EntityTag", entityNbt); //is read by spawn method
        stack.setTag(nbt);
        entity.remove(true);
        return stack;
    }

    private ItemStack putEntityTypeInStack(ItemStack stack, EntityType<?> type) {
        CompoundNBT nbt = stack.getOrCreateTag();
        nbt.putString("EntityType", EntityType.getKey(type).toString());
        stack.setTag(nbt);
        return stack;
    }

    @Nullable
    public EntityType<?> getEntityTypeFromStack(ItemStack stack) {
        if (stack.hasTag()) {
            return EntityType.byString(stack.getTag().getString("EntityType")).orElse(null);
        }
        return null;
    }

    @Override
    public boolean isCrackedBucket(ItemStack stack) {
        ObtainableEntityType type = ModCompat.getObtainableEntityType(this.getEntityTypeFromStack(stack));
        if (type != null) {
            Boolean cracksBucket = type.cracksBucket();
            if (cracksBucket != null) {
                return cracksBucket;
            }
        }
        return super.isCrackedBucket(stack);
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment.category.canEnchant(stack.getItem());
    }
}
