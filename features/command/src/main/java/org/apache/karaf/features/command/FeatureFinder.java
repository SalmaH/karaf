/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.features.command;

import java.net.URI;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.util.artifact.DefaultArtifact;

public class FeatureFinder implements ManagedService {
    Map<String, String> nameToArtifactMap = new HashMap<String, String>();
    public String[] getNames() {
        return nameToArtifactMap.keySet().toArray(new String[] {});
    }

    private Artifact getArtifactWithVersion(Artifact artifact, String version) {
        return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), artifact.getExtension(), version);
    }

    public URI getUriFor(String name, String version) {
        String coords = nameToArtifactMap.get(name);
        if (coords == null) {
            return null;
        }
        Artifact artifact = new DefaultArtifact(coords);
        Artifact resultArtifact = getArtifactWithVersion(artifact, version);
        URI uri = getPaxUrlForArtifact(resultArtifact);
        return uri;
    }

    private URI getPaxUrlForArtifact(Artifact resultArtifact) {
        String uriSt = "mvn:" + resultArtifact.getGroupId() + "/" + resultArtifact.getArtifactId()
            + "/" + resultArtifact.getVersion()
            + "/" + resultArtifact.getExtension() + "/" + resultArtifact.getClassifier();
        try {
            return new URI(uriSt);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("rawtypes")
    public void updated(Dictionary properties) throws ConfigurationException {
        nameToArtifactMap.clear();
        Enumeration keys = properties.keys();
        while (keys.hasMoreElements()) {
            String key = (String)keys.nextElement();
            if (!"felix.fileinstall.filename".equals(key) && !"service.pid".equals(key)) {
                nameToArtifactMap.put(key, (String)properties.get(key));
            }
        }
    }

}
