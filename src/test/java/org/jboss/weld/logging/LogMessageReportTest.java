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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;

import org.junit.Test;

/**
 *
 * @author Martin Kouba
 *
 */
public class LogMessageReportTest {

    private Charset charset = Charset.forName("UTF-8");

    @Test
    public void testIndexReport() throws IOException {
        LogMessageReport report = new LogMessageReport();
        StringWriter writer = new StringWriter();
        report.generate(new File("src/test/resources/test_diff_01.json"), writer);
        String result = writer.toString();
        String expected = new String(Files.readAllBytes(new File("src/test/resources/test_index_report_result.html").toPath()), charset);
        // For debug purpose
        Files.write(new File("target/log-message-index-report.html").toPath(), result.getBytes(charset));
        assertReports(expected, result);
    }

    @Test
    public void testDiffReport() throws IOException {
        LogMessageReport report = new LogMessageReport();
        StringWriter writer = new StringWriter();
        report.generate(new File("src/test/resources/test_diff_result.json"), writer);
        String result = writer.toString();
        String expected = new String(Files.readAllBytes(new File("src/test/resources/test_diff_report_result.html").toPath()), charset);
        // For debug purpose
        Files.write(new File("target/log-message-diff-report.html").toPath(), writer.toString().getBytes());
        assertReports(expected, result);
    }

    private void assertReports(String expected, String result) {
        assertEquals(expected.substring(0, expected.indexOf("Generated")), result.substring(0, result.indexOf("Generated")));
    }

}
