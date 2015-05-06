/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.logging;

import static org.jboss.weld.logging.Strings.ARTIFACT;
import static org.jboss.weld.logging.Strings.DETECT_COLLISIONS_ONLY;
import static org.jboss.weld.logging.Strings.DIFFERENCES;
import static org.jboss.weld.logging.Strings.FILE_PATH;
import static org.jboss.weld.logging.Strings.ID;
import static org.jboss.weld.logging.Strings.INDEXES;
import static org.jboss.weld.logging.Strings.MESSAGE;
import static org.jboss.weld.logging.Strings.MESSAGES;
import static org.jboss.weld.logging.Strings.PROJECT_CODE;
import static org.jboss.weld.logging.Strings.TOTAL;
import static org.jboss.weld.logging.Strings.VALUE;
import static org.jboss.weld.logging.Strings.VERSION;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/**
 * Generates a diff file with the following JSON format:
 *
 * <pre>
 * {
 *  "indexes": [ { "version" : "3.0.0-SNAPSHOT", "artifact" : "org.jboss.weld:weld-core-impl", "filePath" : "/opt/source/file.json", "total" : 735 } , { "version" : "2.2.10.Final", "artifact" : "org.jboss.weld:weld-core-impl", "filePath" : "/opt/source/anotherFile.json", "total" : 745 } ],
 *  "detectCollisionsOnly": false,
 *  "total": 1,
 *  "differences": [
 *      {
 *          "id": 600,
 *          "messages": [
 *              {
 *                  "projectCode" : "WELD-",
 *                  "version" : "3.0.0-SNAPSHOT",
 *                  "value" : {
 *                      "method" : {
 *                          "sig" : "missingRetention(java.lang.Object param1)",
 *                          "retType":"void",
 *                          "interface":"org.jboss.weld.logging.ReflectionLogger"
 *                      },
 *                      "log" :{
 *                          "level" : "DEBUG"
 *                      },
 *                      "msg" : {
 *                          "id" : 600,
 *                          "value" : "{0} is missing...",
 *                          "format" : "MESSAGE_FORMAT"
 *                      }
 *                 }
 *              },
 *              {
 *                  "projectCode" : "WELD-",
 *                  "version" : "2.2.10.Final",
 *                  "value" : {
 *                      "method" : {
 *                          "sig" : "missingRetention(java.lang.Object param1)",
 *                          "retType":"void",
 *                          "interface":"org.jboss.weld.logging.ReflectionLogger"
 *                      },
 *                      "log" : {
 *                          "level" : "INFO"
 *                      },
 *                      "msg" : {
 *                          "id" : 600,
 *                          "value" : "{0} is missing...",
 *                          "format" : "MESSAGE_FORMAT"
 *                      }
 *                 }
 *              },
 *      }
 *  ]
 * }
 * </pre>
 *
 *
 * @author Martin Kouba
 */
public class LogMessageIndexDiff {

    /**
     *
     * @param args
     */
    public static void main(String[] args) {

        if (args.length == 0) {
            printUsage();
            return;
        }

        File outputFile = null;
        List<File> indexFiles = new ArrayList<File>();
        boolean detectCollisionsOnly = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-o".equals(arg)) {
                if (i >= args.length) {
                    throw new IllegalArgumentException("-o switch requires an output file name");
                }
                outputFile = new File(args[++i]);
            } else if ("-c".equals(arg)) {
                detectCollisionsOnly = true;
            } else {
                // Index file
                File file = new File(arg);
                if (!file.canRead()) {
                    throw new IllegalArgumentException("Unable to read the index file: " + file);
                }
                if (file.isFile()) {
                    indexFiles.add(file);
                } else if (file.isDirectory()) {
                    Collections.addAll(indexFiles, file.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File pathname) {
                            return pathname.isFile() && !pathname.isHidden();
                        }
                    }));
                }
            }
        }

        if (outputFile == null) {
            throw new IllegalStateException("The output file must be specified!");
        }

        LogMessageIndexDiff generator = new LogMessageIndexDiff();
        generator.createDiffFile(outputFile, generator.generate(indexFiles, detectCollisionsOnly));
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar weld-logging-tools-shaded.jar [-c] -o file-name FILEORDIR...");
        System.out.println("Options:");
        System.out.println("  -c  detect only collisions");
        System.out.println("  -o  name the output diff file");
    }

    /**
     * Generates the JSON diff for the specified index files.
     *
     * @param indexFiles
     * @param outputFile
     * @param detectCollisionsOnly
     * @return
     */
    public JsonObject generate(List<File> indexFiles, boolean detectCollisionsOnly) {

        if (indexFiles.size() < 2) {
            throw new IllegalStateException("More than one index file must be specified: " + indexFiles);
        }

        // First parse the index files
        List<JsonObject> indexes = parseIndexFiles(indexFiles);

        // Build indexes metadata and check compared versions
        JsonArray indexesMeta = new JsonArray();
        List<String> indexesIds = new ArrayList<String>();
        for (ListIterator<JsonObject> iterator = indexes.listIterator(); iterator.hasNext();) {
            JsonObject index = iterator.next();
            String indexId = index.get(VERSION).getAsString() + index.get(ARTIFACT).getAsString();
            if (indexesIds.contains(indexId)) {
                throw new IllegalStateException("Unable to compare index files with the same composite identifier (version and artifact id): " + indexId);
            }
            indexesIds.add(indexId);
            JsonObject indexMeta = new JsonObject();
            indexMeta.add(VERSION, index.get(VERSION));
            indexMeta.add(ARTIFACT, index.get(ARTIFACT));
            indexMeta.add(TOTAL, index.get(TOTAL));
            indexMeta.add(FILE_PATH, Json.wrapPrimitive(indexFiles.get(iterator.previousIndex()).toPath().toString()));
            indexesMeta.add(indexMeta);
        }

        // Now let's find the differences
        // Note that messages don't need to have the ID specified (0) or may inherit the ID from another message with the same name (-1)
        JsonArray differences = findDifferences(indexes.size(), detectCollisionsOnly, buildDataMap(indexes));

        JsonObject diff = new JsonObject();
        diff.add(INDEXES, indexesMeta);
        diff.add(DETECT_COLLISIONS_ONLY, Json.wrapPrimitive(detectCollisionsOnly));
        diff.add(TOTAL, Json.wrapPrimitive(differences.size()));
        diff.add(DIFFERENCES, differences.size() > 0 ? differences : JsonNull.INSTANCE);
        return diff;
    }

    /**
     *
     * @param outputFile
     * @param diff
     */
    public void createDiffFile(File outputFile, JsonObject diff) {
        try {
            Json.writeJsonElementToFile(diff, initOutputFile(outputFile));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write the diff file", e);
        }
    }

    private List<JsonObject> parseIndexFiles(List<File> indexFiles) {
        List<JsonObject> indexes = new ArrayList<JsonObject>();
        for (File indexFile : indexFiles) {
            try {
                indexes.add(Json.readJsonElementFromFile(indexFile).getAsJsonObject());
            } catch (IOException e) {
                throw new IllegalStateException("Unable to parse the index file: " + indexFile, e);
            }
        }
        return indexes;
    }

    /**
     * @param indexes
     * @return a map of project codes to map of ids to map of versions to messages
     */
    private Map<String, Map<Integer, Map<String, List<JsonObject>>>> buildDataMap(List<JsonObject> indexes) {

        // Map message ID to the map of versions to messages
        // We use the TreeMap so that the keys are ordered
        Map<String, Map<Integer, Map<String, List<JsonObject>>>> dataMap = new HashMap<String, Map<Integer, Map<String, List<JsonObject>>>>();

        for (JsonObject index : indexes) {

            String version = index.get(VERSION).getAsString();

            for (JsonElement messageElement : index.get(MESSAGES).getAsJsonArray()) {

                JsonObject message = messageElement.getAsJsonObject();

                String projectCode = message.get(PROJECT_CODE).getAsString();
                Map<Integer, Map<String, List<JsonObject>>> idMap = dataMap.get(projectCode);
                if (idMap == null) {
                    idMap = new TreeMap<Integer, Map<String, List<JsonObject>>>();
                    dataMap.put(projectCode, idMap);
                }

                int id = message.get(MESSAGE).getAsJsonObject().get(ID).getAsInt();
                Map<String, List<JsonObject>> versionMap = idMap.get(id);
                List<JsonObject> messages = null;

                if (versionMap == null) {
                    versionMap = new HashMap<String, List<JsonObject>>();
                    idMap.put(id, versionMap);
                } else {
                    messages = versionMap.get(version);
                }
                if (messages == null) {
                    messages = new ArrayList<JsonObject>();
                    versionMap.put(version, messages);
                }
                messages.add(message);
            }
        }
        return dataMap;
    }

    private JsonArray findDifferences(int indexCount, boolean detectCollisionsOnly, Map<String, Map<Integer, Map<String, List<JsonObject>>>> dataMap) {
        JsonArray differences = new JsonArray();
        for (Entry<String, Map<Integer, Map<String, List<JsonObject>>>> entry : dataMap.entrySet()) {
            for (Entry<Integer, Map<String, List<JsonObject>>> idEntry : entry.getValue().entrySet()) {
                if (isDifference(indexCount, detectCollisionsOnly, idEntry.getValue())) {
                    JsonObject difference = new JsonObject();
                    difference.add(PROJECT_CODE, Json.wrapPrimitive(entry.getKey()));
                    difference.add(ID, Json.wrapPrimitive(idEntry.getKey()));
                    JsonArray messages = new JsonArray();
                    for (Entry<String, List<JsonObject>> versionEntry : idEntry.getValue().entrySet()) {
                        for (JsonObject message : versionEntry.getValue()) {
                            messages.add(wrap(versionEntry.getKey(), message));
                        }
                    }
                    difference.add(MESSAGES, messages);
                    differences.add(difference);
                }
            }
        }
        return differences;
    }

    private boolean isDifference(int indexCount, boolean detectCollisionsOnly, Map<String, List<JsonObject>> versionMap) {
        if (!detectCollisionsOnly && indexCount != versionMap.size()) {
            // The ID not found in all indexes
            return true;
        }
        List<List<JsonObject>> values = new ArrayList<List<JsonObject>>(versionMap.values());
        for (int i = 1; i < values.size(); i++) {
            List<JsonObject> current = values.get(i);
            List<JsonObject> previous = values.get(i - 1);
            if (!detectCollisionsOnly && current.size() != previous.size()) {
                // The ID not found in all versions
                return true;
            }
            if (current.size() == 1) {
                // Very often there will be only one element in the list
                return !current.get(0).equals(previous.get(0));
            }
            if (!current.containsAll(previous)) {
                return true;
            }
        }
        return false;
    }

    private File initOutputFile(File outputFile) {
        if (!outputFile.exists()) {
            try {
                outputFile.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to create the output file: " + outputFile);
            }
        }
        if (!outputFile.canWrite()) {
            throw new IllegalStateException("Unable to write to the output file: " + outputFile);
        }
        return outputFile;
    }

    private JsonObject wrap(String version, JsonElement element) {
        JsonObject versionAware = new JsonObject();
        versionAware.add(VERSION, Json.wrapPrimitive(version));
        versionAware.add(VALUE, element);
        return versionAware;
    }

}
