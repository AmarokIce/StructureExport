package cn.mcmod.structure_export;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mod(modid = StructureExport.MODID, name = StructureExport.NAME, useMetadata = true)
public class StructureExport {
    public static final String MODID = "structure_export";
    public static final String NAME = "MCMODStructureExport";

    public static ChunkPosition pos1;
    public static ChunkPosition pos2;
    static final Map<String, IconData> iconDataMap = Maps.newHashMap();
    static final Map<String, List<BlockInfoData>> infoDataMap = Maps.newHashMap();
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
        scanBlock(player);
        tryWriteTextures(player);
        outputInfo(player);

        pos1 = null;
        pos2 = null;
        iconDataMap.clear();
        blockMap.clear();
        infoDataMap.clear();
    }

    private static void outputInfo(EntityPlayer player) {
        Map<String, Block> blockData = Maps.newHashMap();
        for (String it : infoDataMap.keySet()) blockData.put(it, getBlock(it));
        Minecraft mc = Minecraft.getMinecraft();
        mc.getLanguageManager().setCurrentLanguage(new Language("en_US", "US", "English", false));
        mc.gameSettings.language = "en_US";
        mc.refreshResources();
        mc.fontRenderer.setUnicodeFlag(false);
        mc.gameSettings.saveOptions();
        for (Map.Entry<String, List<BlockInfoData>> it : infoDataMap.entrySet()) for (BlockInfoData blockInfoData : it.getValue())
            blockInfoData.englishName = blockData.get(it.getKey()).getLocalizedName();

        mc.getLanguageManager().setCurrentLanguage(new Language("zh_CN", "涓浗", "绠�浣撲腑鏂�", false));
        mc.gameSettings.language = "zh_CN";
        mc.refreshResources();
        mc.gameSettings.saveOptions();
        for (Map.Entry<String, List<BlockInfoData>> it : infoDataMap.entrySet()) for (BlockInfoData blockInfoData : it.getValue())
            blockInfoData.name = blockData.get(it.getKey()).getLocalizedName();


        try {
            Gson gson = new GsonBuilder().create();
            File file = new File(root, "info.json");
            if (!file.exists()) file.createNewFile();

            Map<String, BlockInfoData> infoOutput = Maps.newLinkedHashMap();
            infoDataMap.values().forEach(it -> {
                it.forEach(info -> infoOutput.put(info.exportName, info));
            });
            Files.write(file.toPath(), gson.toJson(infoOutput).getBytes(StandardCharsets.UTF_8));

        } catch (Exception ignored) {
            player.addChatMessage(new ChatComponentText("Failed to output info !").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
        }

        player.addChatMessage(new ChatComponentText("Success！Now you can submit your structure in .minecraft/StructureExport !").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
    }

    private static void scanBlock(final EntityPlayer player) {
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

    private static void tryWriteTextures(EntityPlayer player) {
        try {writeTexturesAndData();} catch (Exception ignored) {
            player.addChatMessage(new ChatComponentText("Failed！").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
            return;
        }
        player.addChatMessage(new ChatComponentText("Success！Wait for output info.json ...").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
    }

    @SuppressWarnings("all")
    private static void writeTexturesAndData() throws IOException {
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

        for (Map.Entry<String, IconData> it : iconDataMap.entrySet()) {
            File fileRoot = new File(root, String.format("texture/%s/%s", it.getKey().split(":")[0], it.getKey().split(":")[1]));
            if (fileRoot.exists()) return;
            fileRoot.mkdirs();

            if (compareIcon(it.getValue())) {
                outputIcon(fileRoot, it.getValue().top,  "fill");
                continue;
            }
            outputIcon(fileRoot, it.getValue().top,      "top");
            outputIcon(fileRoot, it.getValue().bottom,   "bottom");
            outputIcon(fileRoot, it.getValue().front,    "front");
            outputIcon(fileRoot, it.getValue().back,     "back");
            outputIcon(fileRoot, it.getValue().left,     "left");
            outputIcon(fileRoot, it.getValue().right,    "right");
        }
    }

    private static boolean compareIcon(IconData data) {
        return Objects.equals(data.top.toString(), data.bottom.toString())
                && Objects.equals(data.top.toString(), data.front.toString())
                && Objects.equals(data.top.toString(), data.back.toString())
                && Objects.equals(data.top.toString(), data.left.toString())
                && Objects.equals(data.top.toString(), data.right.toString());
    }

    private static void outputIcon(File rootPath, ResourceLocation name, String face) throws IOException {
        InputStream fileRoot = name.getClass().getClassLoader().getResourceAsStream(String.format("assets/%s/textures/blocks/%s.png", name.getResourceDomain(), name.getResourcePath()));
        if (fileRoot == null) return;

        BufferedImage bufferedImage = ImageIO.read(fileRoot);
        if (bufferedImage.getHeight() > 16) {
            ImageInputStream stream = ImageIO.createImageInputStream(fileRoot);
            ImageReader reader = ImageIO.getImageReaders(stream).next();
            reader.setInput(stream, true);
            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceRegion(new Rectangle(16, 16));
            bufferedImage = reader.read(0, param);
            stream.close();
        }

        File file = new File(rootPath, face +".png");
        ImageIO.write(bufferedImage, "png", file);
        fileRoot.close();
    }

    private static String readIconAndCallbackName(BlockData data) {
        ResourceLocation name = new ResourceLocation(getRegisterName(data.block));
        String dataName = name + (data.meta == 0 ? "" : "_" + data.meta);
        if (iconDataMap.containsKey(dataName)) return dataName;
        IconData icons = new IconData(name, data.meta);
        for (int side = 0; side < 6; side++) {
            IIcon icon = data.block.getIcon(side, data.meta);
            ResourceLocation iconName = new ResourceLocation(icon.getIconName());

            ForgeDirection face = ForgeDirection.getOrientation(side);
            if      (face == ForgeDirection.UP)    icons.top    = iconName;
            else if (face == ForgeDirection.DOWN)  icons.bottom = iconName;
            else if (face == ForgeDirection.NORTH) icons.front  = iconName;
            else if (face == ForgeDirection.SOUTH) icons.back   = iconName;
            else if (face == ForgeDirection.WEST)  icons.left   = iconName;
            else if (face == ForgeDirection.EAST)  icons.right  = iconName;
        }
        iconDataMap.put(dataName, icons);

        List<BlockInfoData> infoData = infoDataMap.containsKey(name.toString()) ? infoDataMap.get(name.toString()) : Lists.newArrayList();
        infoData.add(new BlockInfoData(name.toString(), data.meta, dataName, getBlockType(data.block)));
        infoDataMap.put(name.toString(), infoData);

        return dataName;
    }

    /* ### ### ###  Utils ### ### ### */

    private static String getRegisterName(Block block) {
        return GameRegistry.findUniqueIdentifierFor(block).toString();
    }

    private static Block getBlock(String name) {
        ResourceLocation rl = new ResourceLocation(name);
        return GameRegistry.findBlock(rl.getResourceDomain(), rl.getResourcePath());
    }

    private static String getBlockType(Block block) {
        if      (block instanceof BlockCrops)       return "crop";
        else if (block instanceof BlockBush)        return "plant";
        else if (block instanceof BlockSlab)        return "slab";
        else if (block instanceof BlockStairs)      return "stairs";
        else if (block instanceof BlockFence)       return "fence";
        else if (block instanceof BlockWall)        return "wall";
        else if (block instanceof BlockVine)        return "vine";
        else if (block instanceof BlockPane)        return "pane";

        else if (block.getRenderType() == 0)        return "fill";
        else if (block.getRenderType() == 1)        return "plant";
        else if (block.getRenderType() == 6)        return "crop";
        else if (block.getRenderType() == 20)        return "vine";

        else                                        return "model";
    }

    /* ### ### ###  Data Classes ### ### ### */

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
        final int meta;


        public IconData(ResourceLocation block, int meta) {
            this.block = block;
            this.meta = meta;
        }
    }

    private static class BlockInfoData {
        public String registerName, name, englishName, metadata, exportName, type;

        public BlockInfoData(String registerName, int metadata, String exportName, String type) {
            this.registerName = registerName;
            this.metadata = Integer.toString(metadata);
            this.exportName = exportName;
            this.type = type;
        }
    }
}
