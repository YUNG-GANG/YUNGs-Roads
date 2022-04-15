package com.yungnickyoung.minecraft.yungsroads.world.config;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum TempEnum implements StringRepresentable {
    COLD("cold"),
    WARM("warm"),
    ANY("any");

    public static final Codec<TempEnum> CODEC = StringRepresentable.fromEnum(TempEnum::values, TempEnum::byName);

    private static final Map<String, TempEnum> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toMap(TempEnum::getName, (temp) -> temp));

    private static TempEnum byName(String name) {
        return BY_NAME.get(name);
    }

    private final String name;

    TempEnum(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
