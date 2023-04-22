package client.rapid.module.modules.player;

import client.rapid.Wrapper;
import client.rapid.event.events.Event;
import client.rapid.event.events.game.*;
import client.rapid.event.events.player.*;
import client.rapid.gui.GuiPosition;
import client.rapid.module.*;
import client.rapid.module.modules.Category;
import client.rapid.module.modules.visual.Hud;
import client.rapid.module.settings.Setting;
import client.rapid.util.*;
import client.rapid.util.TimerUtil;
import client.rapid.util.block.BlockData;
import client.rapid.util.module.*;
import net.minecraft.block.Block;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.network.play.client.*;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

@ModuleInfo(getName = "Scaffold", getCategory = Category.PLAYER)
public class Scaffold extends Draggable {
	private final Setting
	delay = new Setting("Delay", this, 0, 0, 500, true),
	tower = new Setting("Tower", this, "None", "NCP", "Slow"),
	sprint = new Setting("Sprint", this, "None", "Normal", "No Packet"),
	boost = new Setting("Speed Boost", this, 0, 0, 1, false),
	placeOnEnd = new Setting("Place on end", this, true),
	rotate = new Setting("Rotate", this, true),
	eagle = new Setting("Eagle", this, true),
	safewalk = new Setting("Safewalk", this, true),
	keepY = new Setting("Keep Y", this, false),
	swing = new Setting("Swing", this, false);

	private double funnyY;
	private boolean rotated = false;

	private BlockData blockData;
	private BlockPos blockPos;

	private final List<Block> invalid = Arrays.asList(
			Blocks.air,
			Blocks.water,
			Blocks.lava,
			Blocks.flowing_water,
			Blocks.flowing_lava,
			Blocks.command_block,
			Blocks.chest,
			Blocks.crafting_table,
			Blocks.enchanting_table,
			Blocks.furnace,
			Blocks.noteblock
	);
	
    int heldItem;

	TimerUtil timer = new TimerUtil();

	public Scaffold() {
		super(200, 200, 80, 20);
		add(tower, sprint, delay, boost, placeOnEnd,  rotate, eagle, safewalk, keepY, swing);
	}
	
	public void onEnable() {
		heldItem = mc.thePlayer.inventory.currentItem;
		rotated = false;
	}
	
	public void onDisable() {
		funnyY = MathHelper.floor_double(mc.thePlayer.posY);
		rotated = false;
	}

	public void drawDummy(int mouseX, int mouseY) {
		Gui.drawRect(x, y, x + width, y + height, 0x90000000);
		mc.fontRendererObj.drawString(name, x + width / 2 - mc.fontRendererObj.getStringWidth(name) / 2, y + height / 2 - mc.fontRendererObj.FONT_HEIGHT / 2, -1);
		super.drawDummy(mouseX, mouseY);
	}
	
	public void onEvent(Event e) {
		if(e instanceof EventSafewalk && e.isPre() && safewalk.isEnabled())
			e.cancel();

		if(e instanceof EventPacket && e.isPre() && sprint.getMode().equals("No Packet")) {
			EventPacket event = (EventPacket)e;

			if(event.getPacket() instanceof C0BPacketEntityAction)
				event.cancel();
		}

		if(e instanceof EventRender && e.isPre()) {
			int blockCount = 0;
			for (int i = 0; i < 45; ++i) {
				if (!mc.thePlayer.inventoryContainer.getSlot(i).getHasStack())
					continue;

				ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
				Item item = stack.getItem();
				if (!(stack.getItem() instanceof ItemBlock) || invalid.contains(((ItemBlock) item).getBlock()))
					continue;

				blockCount += stack.stackSize;
			}
			if (!(mc.currentScreen instanceof GuiPosition)) {
				Hud hud = (Hud) Wrapper.getModuleManager().getModule("HUD");
				Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, hud.getColor(0));
				Gui.drawRect(x, y, x + width, y + height, new Color(0xFF0F0F13).brighter().getRGB());
				mc.fontRendererObj.drawString(blockCount + " Blocks", x + width / 2 - mc.fontRendererObj.getStringWidth(blockCount + " Blocks") / 2, y + height / 2 - mc.fontRendererObj.FONT_HEIGHT / 2, -1);
			}
		}

		if(e instanceof EventMotion) {
			if (e.isPre()) {
				EventMotion event = (EventMotion) e;

				blockPos = null;
				blockData = null;

				if(eagle.isEnabled()) {
					sneak(rotated);
				}

				rotated = false;

				if(mc.thePlayer.onGround && boost.getValue() != 0)
					setMoveSpeed(getMoveSpeed() + boost.getValue());

				if (keepY.isEnabled()) {
					if ((!isMoving() && mc.gameSettings.keyBindJump.isKeyDown()) || (mc.thePlayer.isCollidedVertically || mc.thePlayer.onGround))
						funnyY = MathHelper.floor_double(mc.thePlayer.posY);
				} else
					funnyY = MathHelper.floor_double(mc.thePlayer.posY);

				if (sprint.getMode().equals("None") && mc.thePlayer.isSprinting()) {
					mc.thePlayer.setSprinting(false);
					mc.gameSettings.keyBindSprint.pressed = false;
				}

				blockPos = new BlockPos(mc.thePlayer.posX, funnyY - 1.0D, mc.thePlayer.posZ);
				blockData = ScaffoldUtil.getBlockData(blockPos, invalid);

				if (blockData != null) {
					if (mc.theWorld.getBlockState(blockPos).getBlock() == Blocks.air && PlayerUtil.hasBlockEquipped() && mc.gameSettings.keyBindJump.isKeyDown() && !isMoving()) {
						mc.thePlayer.motionX = 0;
						mc.thePlayer.motionZ = 0;
						switch (tower.getMode()) {
							case "NCP":
							case "Slow":
								if(!mc.thePlayer.isPotionActive(Potion.jump)) {
									mc.thePlayer.setPosition(mc.thePlayer.posX, Math.floor(mc.thePlayer.posY), mc.thePlayer.posZ);
									mc.thePlayer.motionY = tower.getMode().equals("Slow") ? 0.37 : 0.42;
								}
								break;
						}
					}

					float[] rots = RotationUtil.getScaffoldRotations(blockData.getPosition(), blockData.getFace());

					if (rotate.isEnabled()) {
						event.setYaw(rots[0]);
						event.setPitch(rots[1]);

						mc.thePlayer.rotationYawHead = rots[0];
						mc.thePlayer.renderYawOffset = rots[0];
					}

					if (PlayerUtil.hasBlockEquipped() && timer.sleep((long) delay.getValue())) {
						if (placeOnEnd.isEnabled() && !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(0.0, -0.001D, 0.0)).isEmpty())
							return;

						if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(), blockData.getPosition(), blockData.getFace(), dataToVec(blockData))) {
							if (swing.isEnabled())
								mc.thePlayer.swingItem();
							else
								PacketUtil.sendPacket(new C0APacketAnimation());

							rotated = true;
						}

					}
				}
			}
		}
	}

	private void sneak(boolean state) {
		KeyBinding sneak = mc.gameSettings.keyBindSneak;

		try {
			Field field = sneak.getClass().getDeclaredField("pressed");
			field.setAccessible(true);
			field.setBoolean(sneak, state);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	private Vec3 dataToVec(BlockData data) {
		BlockPos pos = data.getPosition();
		EnumFacing face = data.getFace();

		double x = pos.getX() + 0.5,
		y = pos.getY() + 0.5,
		z = pos.getZ() + 0.5;

		x += (double) face.getFrontOffsetX() / 2;
		z += (double) face.getFrontOffsetZ() / 2;
		y += (double) face.getFrontOffsetY() / 2;

		return new Vec3(x, y, z);
	}
}