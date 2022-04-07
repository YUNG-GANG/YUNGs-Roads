package com.yungnickyoung.minecraft.yungsroads;

import com.yungnickyoung.minecraft.yungsroads.init.YRModConfig;
import com.yungnickyoung.minecraft.yungsroads.init.YRModDebug;
import com.yungnickyoung.minecraft.yungsroads.init.YRModFeatures;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(YungsRoads.MOD_ID)
public class YungsRoads {
    public static final String MOD_ID = "yungsroads";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public YungsRoads() {
        init();
    }

    private void init() {
        YRModConfig.init();
        YRModFeatures.init();
        YRModDebug.init();
    }
}
