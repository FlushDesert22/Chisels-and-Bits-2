package nl.dgoossens.chiselsandbits2.common.registry;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import nl.dgoossens.chiselsandbits2.ChiselsAndBits2;
import nl.dgoossens.chiselsandbits2.common.blocks.BaseBlock;
import nl.dgoossens.chiselsandbits2.common.blocks.ChiseledBlock;
import nl.dgoossens.chiselsandbits2.common.blocks.ChiseledBlockTileEntity;

import java.lang.reflect.Field;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModBlocks {
    public final Block CHISELED_BLOCK = new ChiseledBlock(Block.Properties.create(Material.ROCK).doesNotBlockMovement().hardnessAndResistance(1.0F, 6.0F));
    public final TileEntityType<ChiseledBlockTileEntity> CHISELED_BLOCK_TILE = TileEntityType.Builder.create(ChiseledBlockTileEntity::new, CHISELED_BLOCK).build(null);

    @SubscribeEvent
    public static void onBlockRegistry(final RegistryEvent.Register<Block> e) {
        //Register all blocks in this class automatically.
        ModItems.registerAll(e, ChiselsAndBits2.getInstance().getBlocks(), Block.class);
    }

    @SubscribeEvent
    public static void onItemRegistry(final RegistryEvent.Register<Item> e) {
        //Register all blocks' items in this class automatically.
        ModBlocks k = ChiselsAndBits2.getInstance().getBlocks();
        for (Field f : k.getClass().getFields()) {
            if (!Block.class.isAssignableFrom(f.getType())) continue;
            try {
                Item.Properties properties = new Item.Properties();
                if (f.get(k) instanceof BaseBlock) {
                    BaseBlock bb = ((BaseBlock) f.get(k));
                    properties = bb.getItemProperties();
                    BlockItem bi = bb.getBlockItem();
                    if (bi != null) {
                        e.getRegistry().register(bi.setRegistryName(ChiselsAndBits2.MOD_ID, f.getName().toLowerCase()));
                        return;
                    }
                }
                e.getRegistry().register(new BlockItem((Block) f.get(k), properties).setRegistryName(ChiselsAndBits2.MOD_ID, f.getName().toLowerCase()));
            } catch (IllegalAccessException ex) {
                ex.printStackTrace();
            }
        }
    }

    @SubscribeEvent
    public static void onTileEntityRegistry(final RegistryEvent.Register<TileEntityType<?>> e) {
        //Register all blocks' items in this class automatically.
        ModBlocks k = ChiselsAndBits2.getInstance().getBlocks();
        for (Field f : k.getClass().getFields()) {
            if (!TileEntityType.class.isAssignableFrom(f.getType())) continue;
            try {
                e.getRegistry().register(((TileEntityType<?>) f.get(k)).setRegistryName(ChiselsAndBits2.MOD_ID, f.getName().toLowerCase()));
            } catch (IllegalAccessException ex) {
                ex.printStackTrace();
            }
        }
    }
}
