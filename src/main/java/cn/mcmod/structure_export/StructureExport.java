package cn.mcmod.structure_export;

import com.google.common.collect.Lists;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Mod(modid = StructureExport.MODID, name = StructureExport.NAME, useMetadata = true)
public class StructureExport {
    public static final String MODID = "structure_export";
    public static final String NAME = "MCMODStructureExport";
    public static final String VERSION = "0.0.1";

    public static final Logger LOG = LogManager.getLogger(NAME);

    public static ChunkPosition pos1;
    public static ChunkPosition pos2;
    static final List<IconData> iconDatas = Lists.newArrayList();
    static final List<List<List<BlockData>>> blockMap = Lists.newArrayList();
    static final File root = new File(Loader.instance().getConfigDir().getParentFile(), "StructureExport");
    static final File file = new File(root, "output.txt");

    @Mod.Instance(MODID)
    public static StructureExport INSTANCE;


    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        INSTANCE = this;
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void onPlayerBreak(BlockEvent.BreakEvent event) {
        EntityPlayer player = event.getPlayer();
        ItemStack item = player.getHeldItem();
        if (item == null || item.getItem() != Items.apple) return;
        if (pos1 == null) {
            pos1 = new ChunkPosition(event.x, event.y, event.z);
            player.addChatMessage(new ChatComponentText("Success add Pos1 !").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
            return;
        }

        pos2 = new ChunkPosition(event.x, event.y, event.z);
        player.addChatMessage(new ChatComponentText("Success add Pos2 !").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));

        int pos1X, pos1Y, pos1Z;
        int pos2X, pos2Y, pos2Z;

        if (pos1.chunkPosX > pos2.chunkPosX) { pos1X = pos2.chunkPosX; pos2X = pos1.chunkPosX;}
        else { pos1X = pos1.chunkPosX; pos2X = pos2.chunkPosX; }

        if (pos1.chunkPosY > pos2.chunkPosY) { pos1Y = pos2.chunkPosY; pos2Y = pos1.chunkPosY; }
        else { pos1Y = pos1.chunkPosY; pos2Y = pos2.chunkPosY; }

        if (pos1.chunkPosZ > pos2.chunkPosZ) { pos1Z = pos2.chunkPosZ; pos2Z = pos1.chunkPosZ; }
        else { pos1Z = pos1.chunkPosZ; pos2Z = pos2.chunkPosZ; }

        pos1 = new ChunkPosition(pos1X, pos1Y, pos1Z);
        pos2 = new ChunkPosition(pos2X, pos2Y, pos2Z);

        player.addChatMessage(new ChatComponentText("Start gen!").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
    }

    @SubscribeEvent
    public void clientTickEvent(TickEvent.PlayerTickEvent event) {
        if (pos1 == null || pos2 == null) return;
        if (event.side.isServer()) return;
        if (event.phase != TickEvent.Phase.END) return;
        EntityPlayer player = event.player;
        generator(player);
        clientThreadRun(player);
    }

    private static void clientThreadRun(EntityPlayer player) {
        try {gemData();} catch (Exception ignored) {
            player.addChatMessage(new ChatComponentText("Failed！").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
            return;
        } finally {
            pos1 = null;
            pos2 = null;
            iconDatas.clear();
            blockMap.clear();
        }
        player.addChatMessage(new ChatComponentText("Success！").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
    }

    private static void generator(final EntityPlayer player) {
        World world = player.getEntityWorld();

        blockMap.clear();
        for (int y = pos1.chunkPosY; y <= pos2.chunkPosY; y ++) {
            List<List<BlockData>> listX = Lists.newArrayList();
            for (int x = pos1.chunkPosX; x <= pos2.chunkPosX; x++) {
                List<BlockData> list = new ArrayList<>();
                for (int z = pos1.chunkPosZ; z <= pos2.chunkPosZ; z++) list.add(new BlockData(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z)));
                listX.add(list);
            }
            blockMap.add(listX);
        }

        player.addChatMessage(new ChatComponentText("Wait Client Generator...").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW)));
    }


    private static void gemData() throws IOException {
        if (!root.exists()) root.mkdirs();
        if (!file.exists()) file.createNewFile();

        StringBuilder builder = new StringBuilder();

        for (List<List<BlockData>> y : blockMap) {
            for (List<BlockData> x : y) {
                for (BlockData it : x) {
                    builder.append(it.block == Blocks.air ? "air" : readIconAndCallbackName(it));
                    builder.append(",");
                }
                builder.append("\r\n");
            }
            builder.append("\r\n");
        }

        Files.write(file.toPath(), builder.toString().getBytes(StandardCharsets.UTF_8));

        for (IconData it : iconDatas) {
            File fileRoot = new File(root, String.format("texture/%s/%s", it.block.getResourceDomain(), it.block.getResourcePath()));
            if (fileRoot.exists()) return;
            fileRoot.mkdirs();

            outputIcon(fileRoot, it.top,      "top");
            outputIcon(fileRoot, it.bottom,   "bottom");
            outputIcon(fileRoot, it.front,    "front");
            outputIcon(fileRoot, it.bottom,   "bottom");
            outputIcon(fileRoot, it.left,     "left");
            outputIcon(fileRoot, it.right,    "right");
        }
    }

    private static void outputIcon(File rootPath,ResourceLocation name, String face) throws IOException {
        InputStream fileRoot = name.getClass().getClassLoader().getResourceAsStream(String.format("assets/%s/textures/blocks/%s.png", name.getResourceDomain(), name.getResourcePath()));
        if (fileRoot == null) return;

        byte[] bytes = new byte[fileRoot.available()];
        fileRoot.read(bytes);
        fileRoot.close();

        File file = new File(rootPath, face +".png");
        if (!file.exists()) file.createNewFile();
        Files.write(file.toPath(), bytes);
    }

    private static String readIconAndCallbackName(BlockData data) {
        IconData icons = new IconData(new ResourceLocation(getRegisterName(data.block)));
        for (ForgeDirection face : ForgeDirection.VALID_DIRECTIONS) {
            IIcon icon = data.block.getIcon(face.flag, data.meta);
            ResourceLocation iconName = new ResourceLocation(icon.getIconName());
            if      (face == ForgeDirection.UP)      { icons.top = iconName;    }
            else if (face == ForgeDirection.DOWN)    { icons.bottom = iconName; }
            else if (face == ForgeDirection.NORTH)   { icons.front = iconName;  }
            else if (face == ForgeDirection.SOUTH)   { icons.back = iconName;   }
            else if (face == ForgeDirection.WEST)    { icons.left = iconName;   }
            else if (face == ForgeDirection.EAST)    { icons.right = iconName;  }
        }
        iconDatas.add(icons);
        return icons.block.toString();
    }


    private static String getRegisterName(Block block) {
        return GameRegistry.findUniqueIdentifierFor(block).name;
    }

    private static class BlockData {
        final Block block;
        final int meta;

        public BlockData(Block block, int meta) {
            this.block = block;
            this.meta = meta;
        }
    }

    private static class IconData {
        ResourceLocation front;
        ResourceLocation back;
        ResourceLocation left;
        ResourceLocation right;
        ResourceLocation top;
        ResourceLocation bottom;

        final ResourceLocation block;

        public IconData(ResourceLocation block) {
            this.block = block;
        }
    }
}
