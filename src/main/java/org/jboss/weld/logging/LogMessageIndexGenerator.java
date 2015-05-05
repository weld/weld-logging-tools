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

import static org.jboss.weld.logging.Strings.DESCRIPTION;
import static org.jboss.weld.logging.Strings.ID;
import static org.jboss.weld.logging.Strings.INTERFACE;
import static org.jboss.weld.logging.Strings.LOG_MESSAGE;
import static org.jboss.weld.logging.Strings.MESSAGE;
import static org.jboss.weld.logging.Strings.MESSAGES;
import static org.jboss.weld.logging.Strings.METHOD_INFO;
import static org.jboss.weld.logging.Strings.PROJECT_CODE;
import static org.jboss.weld.logging.Strings.RETURN_TYPE;
import static org.jboss.weld.logging.Strings.SIGNATURE;
import static org.jboss.weld.logging.Strings.TOTAL;
import static org.jboss.weld.logging.Strings.VERSION;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic.Kind;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * This annotation processor generates a JSON index of log messages (i.e. methods annotated with {@link Strings#MESSAGE_CLASS_NAME}) found in the compilation
 * unit.
 *
 * <pre>
 * {
 *  version: "2.2.10.Final",
 *  total : 546,
 *  messages : [
 *      {
 *          method : {
 *              sig : "logMe(String name)",
 *              retType: "void",
 *              interface: "org.jboss.weld.logging.BeanLogger"
 *          },
 *          log : {
 *              level : "INFO"
 *          },
 *          msg: {
 *              id : 1,
 *              value : "This is the real message: {0}",
 *              format: "MESSAGE_FORMAT"
 *          },
 *          desc: "Optional description taken from javadoc..."
 *      }
 *  ]
 * }
 * </pre>
 *
 * @author Martin Kouba
 */
@SupportedAnnotationTypes({ Strings.MESSAGE_CLASS_NAME })
@SupportedOptions({ LogMessageIndexGenerator.PROJECT_VERSION, LogMessageIndexGenerator.OUTPUT_FILE })
public class LogMessageIndexGenerator extends AbstractProcessor {

    protected static final String PROJECT_VERSION = "projectVersion";

    protected static final String OUTPUT_FILE = "outputFile";

    private File outputFile;

    private String version;

    private List<LogMessage> logMessages = new ArrayList<LogMessage>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        version = processingEnv.getOptions().get(PROJECT_VERSION);
        if (version == null) {
            version = "UNKNOWN";
        }
        outputFile = initOutputFile(processingEnv.getOptions().get(OUTPUT_FILE));
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            for (TypeElement annotation : annotations) {
                for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                    processElement(element);
                }
            }
            if (roundEnv.processingOver() && logMessages.size() > 0) {
                createIndex();
            }
            return true;
        } catch (Throwable e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            processingEnv.getMessager().printMessage(Kind.ERROR, writer.toString());
            return false;
        }
    }

    private void processElement(Element element) {

        if (!ElementKind.METHOD.equals(element.getKind())) {
            return;
        }

        ExecutableElement executableElement = (ExecutableElement) element;
        Element enclosingElement = executableElement.getEnclosingElement();

        if (!ElementKind.INTERFACE.equals(enclosingElement.getKind())) {
            // Log messages can only be declared on interfaces
            return;
        }

        // First find the project code
        String projectCode = "";
        for (AnnotationMirror annotationMirror : processingEnv.getElementUtils().getAllAnnotationMirrors(enclosingElement)) {
            if (Strings.MESSAGE_LOGGER_CLASS_NAME.equals(annotationMirror.getAnnotationType().toString())) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror.getElementValues().entrySet()) {
                    if (Strings.PROJECT_CODE.equals(entry.getKey().getSimpleName().toString())) {
                        projectCode = entry.getValue().getValue().toString();
                    }
                }
            }
        }

        JsonObject json = new JsonObject();
        json.add(PROJECT_CODE, Json.wrapPrimitive(projectCode));
        // -1 represents the default value of Message.id()
        Integer id = -1;

        // Method info
        JsonObject methodInfo = new JsonObject();
        methodInfo.add(SIGNATURE, Json.wrapPrimitive(createMethodSignature(executableElement)));
        methodInfo.add(RETURN_TYPE, Json.wrapPrimitive(executableElement.getReturnType().toString()));
        methodInfo.add(INTERFACE, Json.wrapPrimitive(enclosingElement.toString()));
        json.add(METHOD_INFO, methodInfo);

        for (AnnotationMirror annotationMirror : processingEnv.getElementUtils().getAllAnnotationMirrors(executableElement)) {

            final String annotationType = annotationMirror.getAnnotationType().toString();
            final Map<? extends ExecutableElement, ? extends AnnotationValue> annotationParameters = annotationMirror.getElementValues();
            // We don't need the default values
            // processingEnv.getElementUtils().getElementValuesWithDefaults(annotationMirror);

            if (annotationType.equals(Strings.LOG_MESSAGE_CLASS_NAME)) {
                JsonObject logMessage = new JsonObject();
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationParameters.entrySet()) {
                    logMessage.add(entry.getKey().getSimpleName().toString(), Json.wrapPrimitive(entry.getValue()));
                }
                json.add(LOG_MESSAGE, logMessage);
            } else if (annotationType.equals(Strings.MESSAGE_CLASS_NAME)) {
                JsonObject message = new JsonObject();
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationParameters.entrySet()) {
                    message.add(entry.getKey().getSimpleName().toString(), Json.wrapPrimitive(entry.getValue()));
                    if (entry.getKey().getSimpleName().toString().equals(ID)) {
                        id = (Integer) entry.getValue().getValue();
                    }
                }
                json.add(MESSAGE, message);
            }
        }

        // JavaDoc description
        String comment = processingEnv.getElementUtils().getDocComment(element);
        if (comment != null) {
            int atIdx = comment.indexOf('@');
            if (atIdx != -1) {
                comment = comment.substring(0, atIdx).trim();
            }
            json.add(DESCRIPTION, Json.wrapPrimitive(comment));
        }
        logMessages.add(new LogMessage(id, json));
    }

    private void createIndex() throws IOException {
        if (!outputFile.exists()) {
            try {
                outputFile.createNewFile();
            } catch (IOException e) {
                StringWriter writer = new StringWriter();
                e.printStackTrace(new PrintWriter(writer));
                processingEnv.getMessager().printMessage(Kind.ERROR, writer.toString());
            }
        }
        if (!outputFile.exists() || !outputFile.isFile() || !outputFile.canWrite()) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "The output index file does no exists, is not a file or is not writeable: " + outputFile);
        }
        JsonObject data = new JsonObject();
        data.add(VERSION, Json.wrapPrimitive(version));
        data.add(TOTAL, Json.wrapPrimitive(logMessages.size()));
        // Sort messages by id
        Collections.sort(logMessages, new Comparator<LogMessage>() {
            @Override
            public int compare(LogMessage o1, LogMessage o2) {
                return Integer.compare(o1.getId(), o2.getId());
            }
        });
        JsonArray messages = new JsonArray();
        for (LogMessage message : logMessages) {
            messages.add(message.getJson());
        }
        data.add(MESSAGES, messages);
        Json.writeJsonElementToFile(data, outputFile);
        processingEnv.getMessager().printMessage(Kind.NOTE, String.format("Log message index generated [size: %s, file: %s]", logMessages.size(), outputFile));
    }

    private String createMethodSignature(ExecutableElement executableElement) {
        StringBuilder builder = new StringBuilder();
        builder.append(executableElement.getSimpleName());
        builder.append('(');
        for (Iterator<? extends VariableElement> iterator = executableElement.getParameters().iterator(); iterator.hasNext();) {
            VariableElement param = iterator.next();
            List<? extends AnnotationMirror> annotations = param.getAnnotationMirrors();
            if (!annotations.isEmpty()) {
                for (AnnotationMirror annotationMirror : annotations) {
                    // We don't need to include annotation values
                    builder.append('@');
                    builder.append(annotationMirror.getAnnotationType().toString());
                    builder.append(' ');
                }
            }
            builder.append(param.asType().toString());
            builder.append(" ");
            builder.append(param.getSimpleName());
            if (iterator.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append(')');
        return builder.toString();
    }

    private File initOutputFile(String outputFilePath) {
        if (outputFilePath == null) {
            outputFilePath = org.jboss.weld.logging.Files.getWorkingDirectory() + "target" + System.getProperty("file.separator") + "weld-log-message-idx-"
                    + version + ".json";
        }
        return new File(outputFilePath);
    }

    /**
     * Log message wrapper.
     *
     * <p>
     * Note that messages don't need to have the ID specified (0) or may inherit the ID from another message with the same name (-1).
     * </p>
     *
     * @author Martin Kouba
     */
    static class LogMessage {

        private final int id;

        private final JsonObject json;

        LogMessage(int id, JsonObject json) {
            this.id = id;
            this.json = json;
        }

        public int getId() {
            return id;
        }

        public JsonObject getJson() {
            return json;
        }

    }

}
