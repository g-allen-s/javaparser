/*
 * Copyright (C) 2007-2010 Júlio Vilmar Gesser.
 * Copyright (C) 2011, 2013-2019 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */

package com.github.javaparser.utils;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParseStart;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.expr.Expression;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_9;
import static com.github.javaparser.Providers.provider;
import static com.github.javaparser.utils.CodeGenerationUtils.f;
import static com.github.javaparser.utils.Utils.EOL;
import static com.github.javaparser.utils.Utils.normalizeEolInTextBlock;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

public class TestUtils {
    /**
     * Takes care of setting all the end of line character to platform specific ones.
     */
    public static String readResource(String resourceName) {
        if (resourceName.startsWith("/")) {
            resourceName = resourceName.substring(1);
        }
        try (final InputStream resourceAsStream = TestUtils.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (resourceAsStream == null) {
                fail("not found: " + resourceName);
            }
            try (final InputStreamReader reader = new InputStreamReader(resourceAsStream, UTF_8);
                 final BufferedReader br = new BufferedReader(reader)) {
                final StringBuilder builder = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    builder.append(line).append(EOL);
                }
                return builder.toString();
            }
        } catch (IOException e) {
            fail(e);
            return null;
        }
    }

    /**
     * Use this assertion if line endings are important, otherwise use {@link #assertEqualToTextResourceNoEol(String, String)}
     */
    public static void assertEqualToTextResource(String resourceName, String actual) {
        String expected = readResource(resourceName);

        // First test equality ignoring EOL chars
        assertEqualsNoEol(expected, actual);

        // If this passes but the next one fails, the failure is due only to EOL differences, allowing a more precise test failure message.
        assertEquals(
                expected,
                actual,
                String.format("failed due to line separator differences -- Expected: %s, but actual: %s (system eol: %s)",
                        LineEnding.detect(expected).escaped(),
                        LineEnding.detect(actual).escaped(),
                        LineEnding.SYSTEM.escaped()
                )
        );
    }

    /**
     * If line endings are important, use {@link #assertEqualToTextResource(String, String)}
     */
    public static void assertEqualToTextResourceNoEol(String resourceName, String actual) {
        String expected = readResource(resourceName);
        assertEquals(expected, actual, "failed due to line separator differences");
    }

    public static String readTextResource(Class<?> relativeClass, String resourceName) {
        final URL resourceAsStream = relativeClass.getResource(resourceName);
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(resourceAsStream.toURI()));
            return new String(bytes, UTF_8);
        } catch (IOException | URISyntaxException e) {
            fail(e);
            return null;
        }
    }

    public static void assertInstanceOf(Class<?> expectedType, Object instance) {
        assertTrue(expectedType.isAssignableFrom(instance.getClass()), f("%s is not an instance of %s.", instance.getClass(), expectedType));
    }

    /**
     * Unzip a zip file into a directory.
     */
    public static void unzip(Path zipFile, Path outputFolder) throws IOException {
        Log.info("Unzipping %s to %s", () -> zipFile, () -> outputFolder);

        final byte[] buffer = new byte[1024 * 1024];

        outputFolder.toFile().mkdirs();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                final Path newFile = outputFolder.resolve(ze.getName());

                if (ze.isDirectory()) {
                    Log.trace("mkdir %s", newFile::toAbsolutePath);
                    newFile.toFile().mkdirs();
                } else {
                    Log.info("unzip %s", newFile::toAbsolutePath);
                    try (FileOutputStream fos = new FileOutputStream(newFile.toFile())) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
                ze = zis.getNextEntry();
            }

        }
        Log.info("Unzipped %s to %s", () -> zipFile, () -> outputFolder);
    }

    /**
     * Download a file from a URL to disk.
     */
    public static void download(URL url, Path destination) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();
        Files.write(destination, response.body().bytes());
    }

    public static String temporaryDirectory() {
        return System.getProperty("java.io.tmpdir");
    }

    public static void assertCollections(Collection<?> expected, Collection<?> actual) {
        final StringBuilder out = new StringBuilder();
        for (Object e : expected) {
            if (actual.contains(e)) {
                actual.remove(e);
            } else {
                out.append("Missing: ").append(e).append(EOL);
            }
        }
        for (Object a : actual) {
            out.append("Unexpected: ").append(a).append(EOL);
        }

        String s = out.toString();
        if (s.isEmpty()) {
            return;
        }
        fail(s);
    }

    public static void assertProblems(ParseResult<?> result, String... expectedArg) {
        assertProblems(result.getProblems(), expectedArg);
    }

    public static void assertProblems(List<Problem> result, String... expectedArg) {
        Set<String> actual = result.stream().map(Problem::toString).collect(Collectors.toSet());
        Set<String> expected = new HashSet<>(asList(expectedArg));
        assertCollections(expected, actual);
    }

    public static void assertNoProblems(ParseResult<?> result) {
        assertProblems(result);
    }

    public static void assertExpressionValid(String expression) {
        JavaParser javaParser = new JavaParser(new ParserConfiguration().setLanguageLevel(JAVA_9));
        ParseResult<Expression> result = javaParser.parse(ParseStart.EXPRESSION, provider(expression));
        assertTrue(result.isSuccessful(), result.getProblems().toString());
    }

    /**
     * Assert that "actual" equals "expected", and that any EOL characters in "actual" are correct for the platform.
     */
    public static void assertEqualsNoEol(String expected, String actual) {
        assertEquals(normalizeEolInTextBlock(expected, EOL), normalizeEolInTextBlock(actual, EOL));
    }

    /**
     * Assert that "actual" equals "expected", and that any EOL characters in "actual" are correct for the platform.
     */
    public static void assertEqualsNoEol(String expected, String actual, String message) {
        assertEquals(normalizeEolInTextBlock(expected, EOL), normalizeEolInTextBlock(actual, EOL), message);
    }
}
