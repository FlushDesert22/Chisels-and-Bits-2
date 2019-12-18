package nl.dgoossens.chiselsandbits2.client;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import nl.dgoossens.chiselsandbits2.ChiselsAndBits2;
import nl.dgoossens.chiselsandbits2.api.item.*;
import nl.dgoossens.chiselsandbits2.api.bit.VoxelType;
import nl.dgoossens.chiselsandbits2.client.gui.RadialMenu;
import nl.dgoossens.chiselsandbits2.client.render.overlay.ColourableItemColor;
import nl.dgoossens.chiselsandbits2.client.render.overlay.ChiseledBlockColor;
import nl.dgoossens.chiselsandbits2.client.render.overlay.ChiseledBlockItemColor;
import nl.dgoossens.chiselsandbits2.client.render.overlay.MorphingBitItemColor;
import nl.dgoossens.chiselsandbits2.client.render.ter.ChiseledBlockTER;
import nl.dgoossens.chiselsandbits2.common.blocks.ChiseledBlockTileEntity;
import nl.dgoossens.chiselsandbits2.common.impl.SelectedItemMode;
import nl.dgoossens.chiselsandbits2.common.utils.ItemModeUtil;
import nl.dgoossens.chiselsandbits2.common.items.TapeMeasureItem;
import nl.dgoossens.chiselsandbits2.common.registry.ModItems;
import nl.dgoossens.chiselsandbits2.common.registry.ModKeybindings;

import java.lang.reflect.Field;

/**
 * Handles all features triggered by client-sided events.
 * Examples:
 * - Block Highlights
 * - Placement Ghost
 * - Tape Measure
 * - Item Scrolling
 *
 * Events are located in this class, all methods are put in ClientSideHelper.
 */
@OnlyIn(Dist.CLIENT)
public class ClientSide extends ClientSideHelper {
    //--- GENERAL SETUP ---
    /**
     * Setup all client side only things to register.
     */
    public void setup() {
        ClientRegistry.bindTileEntitySpecialRenderer(ChiseledBlockTileEntity.class, new ChiseledBlockTER());
        Minecraft.getInstance().getBlockColors().register(new ChiseledBlockColor(),
                ChiselsAndBits2.getInstance().getBlocks().CHISELED_BLOCK);
        Minecraft.getInstance().getItemColors().register(new ChiseledBlockItemColor(),
                Item.getItemFromBlock(ChiselsAndBits2.getInstance().getBlocks().CHISELED_BLOCK));
        Minecraft.getInstance().getItemColors().register(new MorphingBitItemColor(),
                ChiselsAndBits2.getInstance().getItems().MORPHING_BIT);

        //Register all coloured items as having an item color
        final ModItems i = ChiselsAndBits2.getInstance().getItems();
        final ColourableItemColor cic = new ColourableItemColor(1);
        i.runForAllColourableItems((a) -> Minecraft.getInstance().getItemColors().register(cic, a));

        //We've got both normal and mod event bus events.
        MinecraftForge.EVENT_BUS.register(getClass());
        FMLJavaModLoadingContext.get().getModEventBus().register(getClass());
    }

    /**
     * Call the clean method.
     */
    @SubscribeEvent
    public static void cleanupOnQuit(final ClientPlayerNetworkEvent.LoggedOutEvent e) {
        ChiselsAndBits2.getInstance().getClient().clean();
    }

    /**
     * Register custom sprites.
     */
    @SubscribeEvent
    public static void registerIconTextures(final TextureStitchEvent.Pre e) {
        //Only register to the texture map.
        if(!e.getMap().getBasePath().equals("textures")) return;

        for (final IMenuAction menuAction : ChiselsAndBits2.getInstance().getAPI().getItemPropertyRegistry().getMenuActions()) {
            if (!menuAction.hasIcon()) continue;
            menuActionLocations.put(menuAction, menuAction.getIconResourceLocation());
            e.addSprite(menuActionLocations.get(menuAction));
        }

        for (final ItemModeEnum itemMode : ChiselsAndBits2.getInstance().getAPI().getItemPropertyRegistry().getModes()) {
            if (!itemMode.hasIcon()) continue;
            modeIconLocations.put(itemMode, itemMode.getIconResourceLocation());
            e.addSprite(modeIconLocations.get(itemMode));
        }
    }

    /**
     * Handles hotkey presses.
     */
    @SubscribeEvent
    public static void onKeyInput(final InputEvent.KeyInputEvent e) {
        //Return if not in game.
        if (Minecraft.getInstance().player == null) return;

        final ModKeybindings keybindings = ChiselsAndBits2.getInstance().getKeybindings();
        for (ItemModeEnum im : keybindings.modeHotkeys.keySet()) {
            KeyBinding kb = keybindings.modeHotkeys.get(im);
            if (kb.isPressed() && kb.getKeyModifier().isActive(KeyConflictContext.IN_GAME))
                ItemModeUtil.changeItemMode(Minecraft.getInstance().player, Minecraft.getInstance().player.getHeldItemMainhand(), im);
        }
        for (IMenuAction ma : keybindings.actionHotkeys.keySet()) {
            KeyBinding kb = keybindings.actionHotkeys.get(ma);
            if (kb.isPressed() && kb.getKeyModifier().isActive(KeyConflictContext.IN_GAME))
                ma.trigger();
        }
    }

    /**
     * For the logic whether or not the radial menu should be opened etc.
     */
    @SubscribeEvent
    public static void onTick(final TickEvent.ClientTickEvent e) {
        final PlayerEntity player = Minecraft.getInstance().player;
        if (player == null) return; //We're not in-game yet if this happens..
        RadialMenu radialMenu = ChiselsAndBits2.getInstance().getClient().getRadialMenu();
        if ((player.getHeldItemMainhand().getItem() instanceof IItemMenu) || radialMenu.isVisible()) { //Also decrease visibility if you've scrolled of the item.
            //If you've recently clicked (click = force close) but you're not pressing the button anymore we can reset the click state.
            if (radialMenu.hasClicked() && !radialMenu.isPressingButton())
                radialMenu.setClicked(false);

            KeyBinding modeMenu = ChiselsAndBits2.getInstance().getKeybindings().modeMenu;
            //isKeyDown breaks when using ALT for some reason, so we do it custom.
            if (modeMenu.isDefault()) radialMenu.setPressingButton(Screen.hasAltDown());
            else radialMenu.setPressingButton(modeMenu.isKeyDown());

            if (radialMenu.isPressingButton() && !radialMenu.hasClicked() && (player.getHeldItemMainhand().getItem() instanceof IItemMenu)) {
                //While the key is down, increase the visibility
                radialMenu.setActionUsed(false);
                radialMenu.raiseVisibility();
            } else {
                if (!radialMenu.isActionUsed()) {
                    if (radialMenu.hasSwitchTo() || radialMenu.hasAction()) {
                        final float volume = ChiselsAndBits2.getInstance().getConfig().radialMenuVolume.get().floatValue();
                        if (volume >= 0.0001f)
                            Minecraft.getInstance().getSoundHandler().play(new SimpleSound(SoundEvents.UI_BUTTON_CLICK, SoundCategory.MASTER, volume, 1.0f, player.getPosition()));
                    }

                    if (radialMenu.hasSwitchTo())
                        ItemModeUtil.changeItemMode(Minecraft.getInstance().player, Minecraft.getInstance().player.getHeldItemMainhand(), radialMenu.getSwitchTo());

                    if (radialMenu.hasAction())
                        radialMenu.getAction().trigger();
                }

                radialMenu.setActionUsed(true);
                radialMenu.decreaseVisibility();
            }
        }
        radialMenu.updateGameFocus();
    }

    /**
     * For rendering the ghost selected menu option on the
     * item portrait.
     * Also for rendering the radial menu when the time comes.
     */
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void drawLast(final RenderGameOverlayEvent.Post e) {
        if (e.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            Minecraft.getInstance().getProfiler().startSection("chiselsandbit2-radialmenu");
            RadialMenu radialMenu = ChiselsAndBits2.getInstance().getClient().getRadialMenu();
            if (radialMenu.isVisible()) { //Render if it's visible.
                final MainWindow window = e.getWindow();
                radialMenu.configure(window); //Setup the height/width scales
                if (radialMenu.isVisible()) {
                    int i = (int) (radialMenu.getMinecraft().mouseHelper.getMouseX() * (double) window.getScaledWidth() / (double) window.getWidth());
                    int j = (int) (radialMenu.getMinecraft().mouseHelper.getMouseY() * (double) window.getScaledHeight() / (double) window.getHeight());

                    //This comment makes note that the code below is horrible from a forge perspective, but it's great.
                    ForgeHooksClient.drawScreen(radialMenu, i, j, e.getPartialTicks());
                }
            }
            Minecraft.getInstance().getProfiler().endSection();
        }

        if (e.getType() == RenderGameOverlayEvent.ElementType.HOTBAR && ChiselsAndBits2.getInstance().getConfig().enableToolbarIcons.get()) {
            Minecraft.getInstance().getProfiler().startSection("chiselsandbit2-toolbaricons");
            final PlayerEntity player = Minecraft.getInstance().player;
            if (!player.isSpectator()) {
                //If at least one item wants to render something
                if (!hasToolbarIconItem(player.inventory)) return;

                final ItemRenderer ir = Minecraft.getInstance().getItemRenderer();
                GlStateManager.translatef(0, 0, 50);
                GlStateManager.scalef(0.5f, 0.5f, 1);
                GlStateManager.color4f(1, 1, 1, 1.0f);
                Minecraft.getInstance().getTextureManager().bindTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE);
                RenderHelper.enableGUIStandardItemLighting();
                for (int slot = 8; slot >= -1; --slot) {
                    //-1 is the off-hand
                    ItemStack item = slot == -1 ? player.inventory.offHandInventory.get(0) : player.inventory.mainInventory.get(slot);
                    if (item.getItem() instanceof IItemMenu && ((IItemMenu) item.getItem()).showIconInHotbar()) {
                        final IItemMode mode = ItemModeUtil.getItemMode(item);
                        final int x = (e.getWindow().getScaledWidth() / 2 - 90 + slot * 20 + (slot == -1 ? -9 : 0) + 2) * 2;
                        final int y = (e.getWindow().getScaledHeight() - 16 - 3) * 2;

                        final ResourceLocation sprite = modeIconLocations.get(mode);
                        if (mode instanceof SelectedItemMode) {
                            if (((SelectedItemMode) mode).getVoxelType() == VoxelType.COLOURED) continue;
                            ir.renderItemIntoGUI(((SelectedItemMode) mode).getStack(), x, y);
                        } else {
                            //Don't render null sprite.
                            if (sprite == null) continue;

                            GlStateManager.translatef(0, 0, 200); //The item models are also rendered 150 higher
                            GlStateManager.enableBlend();
                            int blitOffset = 0;
                            try {
                                Field f = AbstractGui.class.getDeclaredField("blitOffset");
                                f.setAccessible(true);
                                blitOffset = (int) f.get(Minecraft.getInstance().ingameGUI);
                            } catch (Exception rx) {
                                rx.printStackTrace();
                            }
                            AbstractGui.blit(x + 2, y + 2, blitOffset, 16, 16, Minecraft.getInstance().getTextureMap().getSprite(sprite));
                            GlStateManager.disableBlend();
                            GlStateManager.translatef(0, 0, -200);
                        }
                    }
                }
                GlStateManager.scalef(2, 2, 1);
                GlStateManager.translatef(0, 0, -50);
                RenderHelper.disableStandardItemLighting();
            }
            Minecraft.getInstance().getProfiler().endSection();
        }
    }

    /**
     * For drawing our custom highlight bounding boxes!
     */
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void drawHighlights(final DrawBlockHighlightEvent e) {
        if(Minecraft.getInstance().objectMouseOver.getType() == RayTraceResult.Type.BLOCK)
            //Cancel if the draw blocks highlight method successfully rendered a highlight.
            if(ChiselsAndBits2.getInstance().getClient().drawBlockHighlight(e.getPartialTicks()))
                e.setCanceled(true);
    }

    /**
     * For rendering the block placement ghost and static tape measurements.
     */
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void drawLast(final RenderWorldLastEvent e) {
        if (Minecraft.getInstance().gameSettings.hideGUI) return;

        ClientSide client = ChiselsAndBits2.getInstance().getClient();
        client.renderTapeMeasureBoxes(e.getPartialTicks());
        client.renderPlacementGhost(e.getPartialTicks());
    }

    /**
     * Handles calling the scroll methods on all items implementing IItemScrollWheel.
     */
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void wheelEvent(final InputEvent.MouseScrollEvent me) {
        final int dwheel = me.getScrollDelta() < 0 ? -1 : me.getScrollDelta() > 0 ? 1 : 0;
        if (me.isCanceled() || dwheel == 0) return;

        final PlayerEntity player = Minecraft.getInstance().player;
        final ItemStack is = player.getHeldItemMainhand();

        if (is.getItem() instanceof IItemScrollWheel && player.isSneaking()) {
            ((IItemScrollWheel) is.getItem()).scroll(player, is, dwheel);
            me.setCanceled(true);
        }
    }
}
