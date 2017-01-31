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

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;

/**
 * https://github.com/jayway/JsonPath
 *
 * @author Martin Kouba
 */
public class LogMessageIndexDiffTest {

    @Test
    public void testDifferences() {
        ReadContext ctx = getReadContext(false, new File("src/test/resources/test_diff_01.json"), new File("src/test/resources/test_diff_02.json"));
        List<String> versions = ctx.read("$.indexes[*].version");
        assertEquals(2, versions.size());
        assertThat(versions, hasItems("3.0.0-SNAPSHOT", "2.2.10.Final"));
        assertThat(ctx.<Integer> read("$.total"), is(1));
        assertThat(ctx.<Integer> read("$.differences[0].id"), is(600));
        List<String> levels = ctx.read("$.differences[0].messages[?(@.version == '2.2.10.Final')].value.log.level");
        assertEquals(1, levels.size());
        assertTrue(levels.contains("INFO"));
        levels = ctx.read("$.differences[0].messages[?(@.version == '3.0.0-SNAPSHOT')].value.log.level");
        assertEquals(1, levels.size());
        assertTrue(levels.contains("DEBUG"));

        ctx = getReadContext(false, new File("src/test/resources/test_diff_01.json"), new File("src/test/resources/test_diff_03.json"));
        assertThat(ctx.<Integer> read("$.total"), is(0));
        assertNull(ctx.read("$.differences"));
    }

    @Test
    public void testCollisions() {
        ReadContext ctx = getReadContext(true, new File("src/test/resources/test_diff_01.json"), new File("src/test/resources/test_diff_02.json"));
        List<String> versions = ctx.read("$.indexes[*].version");
        assertEquals(2, versions.size());
        assertThat(versions, hasItems("3.0.0-SNAPSHOT", "2.2.10.Final"));
        assertThat(ctx.<Integer> read("$.total"), is(1));
        assertThat(ctx.<Integer> read("$.differences[0].id"), is(600));
        List<String> levels = ctx.read("$.differences[0].messages[?(@.version == '2.2.10.Final')].value.log.level");
        assertEquals(1, levels.size());
        assertTrue(levels.contains("INFO"));
        levels = ctx.read("$.differences[0].messages[?(@.version == '3.0.0-SNAPSHOT')].value.log.level");
        assertEquals(1, levels.size());
        assertTrue(levels.contains("DEBUG"));
        List<String> collisions = ctx.read("$.differences[0].collisions");
        assertEquals(1, collisions.size());
        assertEquals("log-level", collisions.get(0));
    }

    @Test
    public void testNoCollisionsFound() {
        ReadContext ctx = getReadContext(true, new File("src/test/resources/test_diff_01.json"), new File("src/test/resources/test_diff_03.json"));
        assertThat(ctx.<Integer> read("$.total"), is(0));
        assertNull(ctx.read("$.differences"));

        ctx = getReadContext(true, new File("src/test/resources/test_coll_01.json"), new File("src/test/resources/test_coll_02.json"));
        assertThat(ctx.<Integer> read("$.total"), is(0));
        assertNull(ctx.read("$.differences"));
    }

    @Test
    public void testMessageValueSuppressed() {
        ReadContext ctx = getReadContext(true, new File("src/test/resources/test_coll_01.json"), new File("src/test/resources/test_coll_03.json"));
        assertThat(ctx.<Integer> read("$.total"), is(0));
        assertNull(ctx.read("$.differences"));
    }

    @Test
    public void testLogMessageSuppressed() {
        ReadContext ctx = getReadContext(true, new File("src/test/resources/test_coll_01.json"), new File("src/test/resources/test_coll_04.json"));
        assertThat(ctx.<Integer> read("$.total"), is(0));
        assertNull(ctx.read("$.differences"));
    }

    @Test
    public void testMethodSignatureSuppressed() {
        ReadContext ctx = getReadContext(true, new File("src/test/resources/test_coll_01.json"), new File("src/test/resources/test_coll_05.json"));
        assertThat(ctx.<Integer> read("$.total"), is(0));
        assertNull(ctx.read("$.differences"));
    }

    @Test
    public void testMultipleDifferentMessagesWithTheSameId() {
        ReadContext ctx = getReadContext(false, new File("src/test/resources/test_diff_04.json"), new File("src/test/resources/test_diff_05.json"));
        assertThat(ctx.<Integer> read("$.total"), is(1));
        assertThat(ctx.<Integer> read("$.differences[0].id"), is(0));
    }

    ReadContext getReadContext(boolean detectCollisionsOnly, File... indexFiles) {
        LogMessageIndexDiff generator = new LogMessageIndexDiff();
        List<File> files = new ArrayList<File>();
        Collections.addAll(files, indexFiles);
        JsonObject diff = generator.generate(files, detectCollisionsOnly);
        assertNotNull(diff);
        //System.out.println(diff);
        return JsonPath.parse(diff.toString());
    }

}
