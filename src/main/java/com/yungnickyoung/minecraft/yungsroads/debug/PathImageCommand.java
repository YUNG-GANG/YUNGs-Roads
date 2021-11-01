package com.yungnickyoung.minecraft.yungsroads.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class PathImageCommand {
    /** Singleton logic **/
    private static final PathImageCommand instance = new PathImageCommand();
    public static PathImageCommand getInstance() {
        return instance;
    }

    /** Private constructor prevents instantiation **/
    private PathImageCommand() {}

    private Set<BlockPos> points = new HashSet<>();

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands
                .literal("debugroadimage")
                .requires((source) -> source.hasPermissionLevel(2))
                .executes((context) -> instance.generateImage(4096)));
    }

    public void addPoint(BlockPos pos) {
        this.points.add(new BlockPos(pos));
    }

    public int generateImage(int size) {
        Path path = FMLPaths.GAMEDIR.get();
        File file = new File(path.toString(), "debug_roads.png");

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        g.setColor(Color.RED);

        int minX = this.points.stream().map(Vector3i::getX).min(Integer::compare).orElse(0);
        int minZ = this.points.stream().map(Vector3i::getZ).min(Integer::compare).orElse(0);

        for (BlockPos blockPos : this.points) {
            int x = blockPos.getX() - minX;
            int z = blockPos.getZ() - minZ;
            g.drawOval(x, z, 20, 20);
        }

        try {
            if (file.exists()) file.delete();
            ImageIO.write(image, "png", file);
        } catch (IOException e) {
            YungsRoads.LOGGER.error(e);
            return 0;
        }

        return 1;
    }
}
