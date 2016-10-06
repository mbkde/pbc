package com.atlassian.buildeng.ecs.scheduling;

public enum ECSInstance {
    M4_XLARGE(4096, 16050, "m4.xlarge"),
    M4_4XLARGE(16384, 64419, "m4.4xlarge"),
    M4_10XLARGE(40960, 161186, "m4.10xlarge");

    private int cpu;
    private int memory;
    private String name;
    static ECSInstance DEFAULT_INSTANCE = M4_4XLARGE;

    ECSInstance(int cpu, int memory, String name) {
        this.cpu = cpu;
        this.memory = memory;
        this.name = name;
    }

    static ECSInstance fromName(String name) {
        for (ECSInstance x: ECSInstance.values()) {
            if (x.name.equals(name)) return x;
        }
        return DEFAULT_INSTANCE;
    }


    public int getCpu() {
        return cpu;
    }

    public int getMemory() {
        return memory;
    }

    public String getName() {
        return name;
    }
}

