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

/**
 * Component that maps symbolic container size names and the properties associated with them in server configuration.
 */
public interface ContainerSizeDescriptor {


    int getCpu(Configuration.ContainerSize size);
    
    int getCpu(Configuration.ExtraContainerSize size);
    
    int getMemory(Configuration.ContainerSize size);
    
    int getMemory(Configuration.ExtraContainerSize size);
    
    int getMemoryLimit(Configuration.ContainerSize size);
    
    int getMemoryLimit(Configuration.ExtraContainerSize size);
    
    String getLabel(Configuration.ContainerSize size);
    
    String getLabel(Configuration.ExtraContainerSize size);
}
