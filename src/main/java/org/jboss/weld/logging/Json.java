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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collection;

import javax.lang.model.element.AnnotationValue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;

/**
 *
 * @author Martin Kouba
 */
final class Json {

    private Json() {
    }

    static JsonElement readJsonElementFromFile(File inputFile) throws IOException {
        try (Reader reader = Files.newBufferedReader(inputFile.toPath(), Charset.forName("UTF-8"))) {
            JsonParser jsonParser = new JsonParser();
            return jsonParser.parse(reader);
        }
    }

    static void writeJsonElementToFile(JsonElement element, File outputFile) throws IOException {
        try (Writer writer = Files.newBufferedWriter(outputFile.toPath(), Charset.forName("UTF-8"))) {
            JsonWriter jsonWriter = new JsonWriter(writer);
            jsonWriter.setHtmlSafe(true);
            Streams.write(element, jsonWriter);
        }
    }

    static JsonElement wrapPrimitive(AnnotationValue annotationValue) {
        return wrapPrimitive(annotationValue.getValue());
    }

    static JsonElement wrapPrimitive(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof Number) {
            return new JsonPrimitive((Number) value);
        }
        if (value instanceof Boolean) {
            return new JsonPrimitive((Boolean) value);
        }
        return new JsonPrimitive(value.toString());
    }

    static JsonArray arrayFromPrimitives(Collection<?> elements) {
        JsonArray jsonArray = new JsonArray();
        for (Object element : elements) {
            jsonArray.add(wrapPrimitive(element));
        }
        return jsonArray;
    }

}
