/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.l10n;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Standalone tool to generate locale JARs for NetBeans NB30.
 * Replaces the old Ant-based {@code Package.java}.
 * <p>
 * Scans {@code {srcDir}/{locale}/} directory tree for
 * {@code Bundle_{locale}.properties} files, groups them by
 * (cluster, moduleDir), and creates locale JARs.
 * <p>
 * Expected directory structure:
 * <pre>
 *   {cluster}/{moduleDir}/{moduleDir}/{java-package-path}/Bundle_{locale}.properties
 *   {cluster}/{moduleDir}/ext/{moduleDir}/{java-package-path}/Bundle_{locale}.properties
 *   {cluster}/{moduleDir}/locale/{moduleDir}/{java-package-path}/Bundle_{locale}.properties
 * </pre>
 * <p>
 * Output:
 * <pre>
 *   {outDir}/modules/locale/{codeNameBase}_{locale}.jar
 * </pre>
 */
public class LocaleJarGenerator {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: LocaleJarGenerator <srcDir> <locale> <outDir>");
            System.err.println("  <srcDir>  - base directory containing locale subdirectories");
            System.err.println("              (e.g., 'l10n')");
            System.err.println("  <locale>  - locale code (e.g., 'zh_CN')");
            System.err.println("  <outDir>  - output directory for generated locale JARs");
            System.err.println("              (e.g., 'target/l10n-jars')");
            System.exit(1);
        }

        String srcDir = args[0];
        String locale = args[1];
        String outDir = args[2];

        LocaleJarGenerator gen = new LocaleJarGenerator();
        gen.generate(srcDir, locale, outDir);
    }

    /**
     * Scans translation files and generates locale JARs.
     *
     * @param srcDir  base directory containing locale subdirectories
     * @param locale  locale code (e.g., "zh_CN")
     * @param outDir  output directory for generated locale JARs
     * @throws IOException if an I/O error occurs
     */
    void generate(String srcDir, String locale, String outDir) throws IOException {
        Path baseDir = Paths.get(srcDir, locale);
        if (!Files.isDirectory(baseDir)) {
            System.err.println("Error: Directory not found: " + baseDir.toAbsolutePath());
            System.exit(1);
        }

        // Group files by (cluster, codeNameBase)
        // key = cluster + "|" + codeNameBase
        Map<String, List<String[]>> jarGroups = new LinkedHashMap<>();

        Files.walk(baseDir, FileVisitOption.FOLLOW_LINKS)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equals("Bundle_" + locale + ".properties"))
                .forEach(p -> processFile(baseDir, p, jarGroups, locale));

        if (jarGroups.isEmpty()) {
            System.err.println("Warning: No Bundle_" + locale + ".properties files found in "
                    + baseDir.toAbsolutePath());
            return;
        }

        System.out.println("Found " + jarGroups.size() + " module groups.");

        // Generate JARs
        int jarCount = 0;
        for (Map.Entry<String, List<String[]>> entry : jarGroups.entrySet()) {
            String key = entry.getKey();
            int sepIdx = key.indexOf('|');
            String cnb = key.substring(sepIdx + 1);

            Path jarFile = Paths.get(outDir, "modules", "locale",
                    cnb + "_" + locale + ".jar");
            Files.createDirectories(jarFile.getParent());

            // Create OSGi fragment manifest (NB30 requires Fragment-Host
            // to recognize locale JARs as module fragments)
            Manifest mf = new Manifest();
            Attributes attr = mf.getMainAttributes();
            attr.putValue("Manifest-Version", "1.0");
            attr.putValue("Bundle-ManifestVersion", "2");
            // Bundle-SymbolicName uses dotted form with -branding suffix,
            // matching MakeOSGi.processFragment() convention
            String cnbDotted = cnb.replace('-', '.');
            attr.putValue("Bundle-SymbolicName", cnbDotted + "-branding");
            // Fragment-Host must be in canonical dotted form
            // (e.g., org.netbeans.modules.settings), matching
            // MakeOSGi.findFragmentHost() output
            attr.putValue("Fragment-Host", cnbDotted);
            attr.putValue("X-Informational-Archive-Locale", locale);

            try (JarOutputStream jos = new JarOutputStream(
                    new BufferedOutputStream(new FileOutputStream(jarFile.toFile())), mf)) {
                Set<String> seenEntries = new LinkedHashSet<>();
                for (String[] pair : entry.getValue()) {
                    String relPath = pair[0];
                    String entryName = pair[1];

                    // Skip duplicate entries within the same JAR
                    if (!seenEntries.add(entryName)) {
                        System.err.println("  Warning: Skipping duplicate entry '" + entryName
                                + "' in " + jarFile.getFileName());
                        continue;
                    }

                    Path sourceFile = Paths.get(baseDir.toString(), relPath);

                    JarEntry je = new JarEntry(entryName);
                    jos.putNextEntry(je);
                    Files.copy(sourceFile, jos);
                    jos.closeEntry();
                }
            }

            System.out.println("  Created: " + jarFile);
            jarCount++;
        }

        System.out.println();
        System.out.println("Done. Created " + jarCount + " locale JARs in "
                + Paths.get(outDir).toAbsolutePath());
    }

    /**
     * Processes a single Bundle_{locale}.properties file, extracts cluster
     * and module info, and adds it to the jarGroups map.
     *
     * @param baseDir   base directory for locale files
     * @param file      the Bundle_{locale}.properties file found during walk
     * @param jarGroups map grouping files by (cluster, codeNameBase)
     * @param locale    locale code used for path validation
     */
    private void processFile(Path baseDir, Path file,
                             Map<String, List<String[]>> jarGroups, String locale) {
        Path relPath = baseDir.relativize(file);
        String relative = relPath.toString().replace('\\', '/');

        String[] parts = relative.split("/");
        if (parts.length < 4) {
            System.err.println("Warning: Unexpected short path (skipping): " + relative);
            return;
        }

        String cluster = parts[0];
        String moduleDir = parts[1];

        // Validate the path structure: expect one of:
        //   {cluster}/{moduleDir}/{moduleDir}/...  (standard)
        //   {cluster}/{moduleDir}/ext/{moduleDir}/...
        //   {cluster}/{moduleDir}/locale/{moduleDir}/...
        boolean validPath = false;
        if (parts.length >= 4 && parts[2].equals(moduleDir)) {
            validPath = true;  // standard: parts[2] repeats parts[1]
        } else if (parts.length >= 5 && parts[2].equals("ext") && parts[3].equals(moduleDir)) {
            validPath = true;  // ext path
        } else if (parts.length >= 5 && parts[2].equals("locale") && parts[3].equals(moduleDir)) {
            validPath = true;  // locale (branding) path
        }

        if (!validPath) {
            System.err.println("Warning: Skipping non-standard path (unrecognized structure): " + relative);
            return;
        }

        // Find Java package root: first occurrence of "org", "com", or "net"
        // as a complete path segment after cluster/moduleDir/
        int pkgStart = -1;
        for (int i = 2; i < parts.length; i++) {
            if ("org".equals(parts[i]) || "com".equals(parts[i]) || "net".equals(parts[i])) {
                pkgStart = i;
                break;
            }
        }

        if (pkgStart == -1) {
            // No standard Java package root found; use path after the
            // cluster/moduleDir/repeatedModuleDir structure as entry path.
            int entryStart;
            if (parts.length >= 5 && ("ext".equals(parts[2]) || "locale".equals(parts[2]))) {
                entryStart = 4;  // skip cluster, mod, ext/locale, mod
            } else {
                entryStart = 3;  // skip cluster, mod, repeat-mod
            }
            String codeNameBase = toCodeNameBase(moduleDir);
            StringBuilder entryPath = new StringBuilder();
            for (int i = entryStart; i < parts.length; i++) {
                if (entryPath.length() > 0) {
                    entryPath.append('/');
                }
                entryPath.append(parts[i]);
            }
            String key = cluster + "|" + codeNameBase;
            jarGroups.computeIfAbsent(key, k -> new ArrayList<>())
                     .add(new String[]{relative, entryPath.toString()});
            return;
        }

        // Build JAR entry path (from package root onwards)
        StringBuilder entryPath = new StringBuilder();
        for (int i = pkgStart; i < parts.length; i++) {
            if (entryPath.length() > 0) {
                entryPath.append('/');
            }
            entryPath.append(parts[i]);
        }

        // Derive code name base from module directory name
        String codeNameBase = toCodeNameBase(moduleDir);

        String key = cluster + "|" + codeNameBase;
        jarGroups.computeIfAbsent(key, k -> new ArrayList<>())
                 .add(new String[]{relative, entryPath.toString()});
    }

    /**
     * Converts a module directory name to the NetBeans code name base.
     * <p>
     * If the name already starts with {@code "org-"} or {@code "com-"},
     * it is returned as-is. Otherwise the prefix
     * {@code "org-netbeans-modules-"} is prepended.
     *
     * @param moduleDir the module directory name
     * @return the derived code name base
     */
    static String toCodeNameBase(String moduleDir) {
        if (moduleDir.startsWith("org-") || moduleDir.startsWith("com-")) {
            return moduleDir;
        }
        return "org-netbeans-modules-" + moduleDir;
    }
}
