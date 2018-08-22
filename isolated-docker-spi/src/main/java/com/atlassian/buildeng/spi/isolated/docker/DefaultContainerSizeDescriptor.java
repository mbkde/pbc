/*
 * Copyright 2018 Atlassian Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.atlassian.buildeng.spi.isolated.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of ContainerSizeDescriptor with hardcoded values. Plugins implementing IsolatedAgentService
 * are to either register this class or it's own as public component.
 */
public class DefaultContainerSizeDescriptor implements ContainerSizeDescriptor {
    private static final Logger logger = LoggerFactory.getLogger(DefaultContainerSizeDescriptor.class);
    
    public static final double SOFT_TO_HARD_LIMIT_RATIO = 1.25;

    //*******************************************
    //NOTE: when changing values here also change them in the kubernetes plugin's default json.

    @Override
    public int getCpu(Configuration.ContainerSize size) {
        switch (size) {
            case XXLARGE: return 5120;
            case XLARGE:  return 4096;
            case LARGE:   return 3072;
            case REGULAR: return 2048;
            case SMALL:   return 1024;
            case XSMALL:  return 512;
            default: {
                logger.error("Unknown size of container '" + size.name() + "'. Falling back to REGULAR.");
                return 2048;
            }
        }
    }
    

    @Override
    public int getCpu(Configuration.ExtraContainerSize size) {
        switch (size) {
            case XXLARGE: return 3072;
            case XLARGE:  return 2048;
            case LARGE:   return 1024;
            case REGULAR: return 512;
            case SMALL:   return 256;
            default: {
                logger.error("Unknown size of extra container '" + size.name() + "'. Falling back to REGULAR.");
                return 512;
            }
        }
    }
    

    @Override
    public int getMemory(Configuration.ContainerSize size) {
        switch (size) {
            case XXLARGE: return 20000;
            case XLARGE:  return 16000;
            case LARGE:   return 12000;
            case REGULAR: return 8000;
            case SMALL:   return 4000;
            case XSMALL:  return 2000;
            default: {
                logger.error("Unknown size of container '" + size.name() + "'. Falling back to REGULAR.");
                return 8000;
            }
        }
    }

    @Override
    public int getMemory(Configuration.ExtraContainerSize size) {
        switch (size) {
            case XXLARGE: return 12000;
            case XLARGE:  return 8000;
            case LARGE:   return 4000;
            case REGULAR: return 2000;
            case SMALL:   return 1000;
            default: {
                logger.error("Unknown size of extra container '" + size.name() + "'. Falling back to REGULAR.");
                return 2000;
            }
        }
    }

    @Override
    public int getMemoryLimit(Configuration.ContainerSize size) {
        return (int) (getMemory(size) * SOFT_TO_HARD_LIMIT_RATIO);
    }

    @Override
    public int getMemoryLimit(Configuration.ExtraContainerSize size) {
        return (int) (getMemory(size) * SOFT_TO_HARD_LIMIT_RATIO);
    }

    @Override
    public String getLabel(Configuration.ContainerSize size) {
        switch (size) {
            case XXLARGE: return "Extra Extra Large (~20G memory, 5 vCPU)";
            case XLARGE:  return "Extra Large (~16G memory, 4 vCPU)";
            case LARGE:   return "Large (~12G memory, 3 vCPU)";
            case REGULAR: return "Regular (~8G memory, 2 vCPU)";
            case SMALL:   return "Small (~4G memory, 1 vCPU)";
            case XSMALL:  return "Extra Small (~2G memory, 0.5 vCPU)";
            default: {
                logger.error("Unknown size of container '" + size.name() + "'.");
                return size.name();
            }
        }
    }
    
    @Override
    public String getLabel(Configuration.ExtraContainerSize size) {
        switch (size) {
            case XXLARGE: return "XX Large size (~ 12G)";
            case XLARGE:  return "X Large size (~ 8G)";
            case LARGE:   return "Large size (~ 4G)";
            case REGULAR: return "Regular size (~ 2G)";
            case SMALL:   return "Small size (~ 1G)";
            default: {
                logger.error("Unknown size of extra container '" + size.name() + "'.");
                return size.name();
            }
        }
    }

}
