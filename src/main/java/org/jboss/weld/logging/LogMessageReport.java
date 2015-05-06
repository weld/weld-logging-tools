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

import static org.jboss.weld.logging.Strings.INDEXES;
import static org.jboss.weld.logging.Strings.VERSION;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.trimou.Mustache;
import org.trimou.engine.MustacheEngine;
import org.trimou.engine.MustacheEngineBuilder;
import org.trimou.engine.config.EngineConfigurationKey;
import org.trimou.engine.locator.ClassPathTemplateLocator;
import org.trimou.engine.resolver.MapResolver;
import org.trimou.gson.resolver.JsonElementResolver;
import org.trimou.handlebars.HelpersBuilder;

import com.google.gson.JsonObject;

/**
 *
 * @author Martin Kouba
 */
public class LogMessageReport {

    public static void main(String[] args) {

        if (args.length < 1) {
            printUsage();
            return;
        }

        File indexFile = new File(args[0]);
        File reportFile = null;
        if (args.length > 1) {
            reportFile = new File(args[1]);
        }

        LogMessageReport generator = new LogMessageReport();
        generator.createReportFile(indexFile, reportFile);
    }

    private static void printUsage() {
        System.out.println("Usage: java -cp weld-logging-tools-shaded.jar org.jboss.weld.logging.LogMessageReport DIFF_INDEX_FILE [REPORT_FILE]");
    }

    /**
     *
     * @param indexFile
     * @param writer
     */
    public void generate(File indexFile, Writer writer) {

        if (!indexFile.exists() || !indexFile.canRead()) {
            throw new IllegalArgumentException("Unable to read the index file: " + indexFile);
        }

        try {

            JsonObject json = Json.readJsonElementFromFile(indexFile).getAsJsonObject();

            MustacheEngine engine = MustacheEngineBuilder.newBuilder().omitServiceLoaderConfigurationExtensions()
                    .setProperty(EngineConfigurationKey.PRECOMPILE_ALL_TEMPLATES, false)
                    .addTemplateLocator(ClassPathTemplateLocator.builder(1).setRootPath("templates").build()).addResolver(new MapResolver())
                    .addResolver(new JsonElementResolver()).registerHelpers(HelpersBuilder.all().build()).build();
            Mustache mustache;

            if (json.has(VERSION)) {
                mustache = engine.getMustache("index.html");
            } else if (json.has(INDEXES)) {
                mustache = engine.getMustache("diff.html");
            } else {
                throw new IllegalStateException("Unsupported index file format: " + indexFile);
            }

            Map<String, Object> data = new HashMap<String, Object>();
            data.put("json", json);
            data.put("indexFile", indexFile.toPath().toString());
            data.put("timestamp", new Date());
            mustache.render(writer, data);

        } catch (IOException e) {
            throw new IllegalStateException("Unable to parse the index file: " + indexFile, e);
        }
    }

    /**
     *
     * @param indexFile
     * @param reportFile
     */
    public void createReportFile(File indexFile, File reportFile) {
        try (Writer writer = Files.newBufferedWriter(initReportFile(indexFile, reportFile).toPath(), Charset.forName("UTF-8"))) {
            generate(indexFile, writer);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create the report file: " + reportFile, e);
        }
    }

    /**
     *
     * @return the default output file -
     */
    File getDefaultReportFile(File indexFile) {
        String filename = indexFile.getName().substring(0, indexFile.getName().lastIndexOf(".json"));
        return new File(indexFile.getParentFile(), filename + ".html");
    }

    private File initReportFile(File indexFile, File reportFile) {
        if (reportFile == null) {
            reportFile = getDefaultReportFile(indexFile);
        }
        if (!reportFile.exists()) {
            try {
                reportFile.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to create the report file: " + reportFile);
            }
        }
        if (!reportFile.canWrite()) {
            throw new IllegalStateException("Unable to write to the report file: " + reportFile);
        }
        return reportFile;
    }

}
