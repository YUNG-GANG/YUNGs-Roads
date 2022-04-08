package com.yungnickyoung.minecraft.yungsroads;

import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.services.Services;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class YungsRoadsCommon {
    public static final String MOD_ID = "yungsroads";
    public static final String MOD_NAME = "YUNG's Roads";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final ConfigModule CONFIG = new ConfigModule();

    public static final boolean DEBUG_MODE = true;

    public static void init() {
        Services.MODULES.loadModules();
    }
}
