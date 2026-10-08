package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Something the player only needs to do once on the debug screen, such as opening help, remembered across launches by
 * an empty marker file in the mod's config folder. Deleting the file makes the screen treat it as not done again.
 */
enum ClientMilestone {
    /** The player has opened help, so its tab stops pulsing. */
    HELP_OPENED("help_opened"),
    /** The player has accepted the warning to only use the debug screen in a test world. */
    TEST_WORLD_WARNING_ACCEPTED("test_world_warning_accepted");

    private final String fileName;
    /** Whether it's been reached, read from the marker file the first time it's needed. */
    private Boolean reached;

    ClientMilestone(String fileName) {
        this.fileName = fileName;
    }

    boolean isReached() {
        if (this.reached == null) {
            this.reached = Files.exists(markerPath());
        }
        return this.reached;
    }

    void reach() {
        if (isReached()) {
            return;
        }
        this.reached = true;
        try {
            Path marker = markerPath();
            Files.createDirectories(marker.getParent());
            Files.createFile(marker);
        } catch (IOException e) {
            // Only means it's treated as not reached again next launch
            YungsRoadsCommon.LOGGER.warn("Unable to save road debug screen marker {}", this.fileName, e);
        }
    }

    private Path markerPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(YungsRoadsCommon.MOD_ID).resolve(this.fileName);
    }
}
