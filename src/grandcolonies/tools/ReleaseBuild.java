package grandcolonies.tools;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ReleaseBuild {

    static final String MOD = "grandcolonies";
    static final String FOLDER = "Grand.Colonies";
    static final String RELEASES = "https://github.com/SirHartley/Grand.Colonies/releases";

    static final String VERSION_FILE = "GrandColonies.version";
    static final String JAR = "jars/release/GrandColonies.jar";
    // mod_info.json loads this path, the ide artifact uses it too
    static final String SHIPPED_JAR = "jars/GrandColonies.jar";
    static final String SOURCE = "src/";
    static final String TOOLS = SOURCE + MOD + "/tools/";
    static final String TOOL = TOOLS + "ReleaseBuild.java";
    static final String PLUGIN = MOD + "/plugins/ModPlugin.class";
    // same module name as the 2.1.b jar, kotlin reads top level declarations through it
    static final String KOTLIN_MODULE_NAME = "Grand.Colonies";
    static final String KOTLIN_MODULE = "META-INF/" + KOTLIN_MODULE_NAME + ".kotlin_module";
    static final String VERSION = "[1-9][0-9]*\\.(?:0|[1-9][0-9]*)\\.[a-z]";
    static final Pattern HEADING = Pattern.compile(
            "^(" + VERSION + ")(?:[ \\t]*-[ \\t]*(UNRELEASED|CURRENT PUBLIC VERSION))?[ \\t]*$");

    // matches the kotlin runtime lazylib ships in lib/Kotlin-Runtime.jar
    static final String KOTLIN = "2.1.20";
    static final String KOTLIN_MAIN = "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler";
    static final String LIBS = "lib";
    static final List<String> LIB_JARS = List.of("starfarer.api.jar", "starfarer_obf.jar", "fs.common_obf.jar",
            "lwjgl.jar", "lwjgl_util.jar", "json.jar", "log4j-1.2.9.jar",
            "LazyLib.jar", "LunaLib.jar", "Kotlin-Runtime.jar");

    static final Set<String> ROOT_FILES = Set.of("mod_info.json", VERSION_FILE, "Changelog",
            "_grandColonies.png", "CC-license.png",
            "LICENSE", "LICENSE.txt", "LICENSE.md", "NOTICE", "CREDITS", "CREDITS.txt", "CREDITS.md");
    static final List<String> REQUIRED = List.of("Changelog", "mod_info.json", VERSION_FILE,
            "_grandColonies.png", "CC-license.png", "data/campaign/market_conditions.csv",
            "data/config/settings.json", "data/config/LunaSettings.csv", "data/config/LunaSettingsConfig.json",
            "data/config/version/version_files.csv");
    static final Map<String, Set<String>> ASSETS = Map.of(
            "data", Set.of("csv", "json", "shader", "ship", "wpn", "proj", "system", "variant", "skin"),
            "graphics", Set.of("png", "jpg", "jpeg"),
            "sounds", Set.of("ogg", "wav"));
    static final Set<String> SKIPPED_DIRS = Set.of("unused", "unedited", "tools", "docs");
    static final LocalDateTime ZIP_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

    record Notes(String version, boolean unreleased, String text) {

    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        if (args.length == 2 && args[0].equals("build")) {
            build(root, Path.of(args[1]).toAbsolutePath());
        } else if ((args.length == 1 || args.length == 2) && args[0].equals("package")) {
            pack(root, args.length == 2 ? args[1] : null);
        } else {
            throw new IllegalArgumentException("From the mod root, with JDK 17:\n"
                    + "  java " + TOOL + " build <folder with the kotlin " + KOTLIN + " compiler jars>\n"
                    + "  java " + TOOL + " package [version]");
        }
    }

    static void build(Path root, Path kotlin) throws Exception {
        require(Runtime.version().feature() == 17, "Build with JDK 17.");
        Files.createDirectories(root.resolve("out"));
        Path work = Files.createTempDirectory(root.resolve("out"), "release-build-");
        Path classes = Files.createDirectory(work.resolve("classes"));
        List<Path> dependencies = new ArrayList<>();
        for (String name : LIB_JARS) dependencies.add(root.resolve(LIBS).resolve(name));
        for (Path path : dependencies) require(Files.isRegularFile(path), "Missing dependency: " + path);
        try (URLClassLoader loader = new URLClassLoader(new URL[]{root.resolve(LIBS).resolve("json.jar").toUri().toURL()}, null)) {
            require(!RuntimeException.class.isAssignableFrom(loader.loadClass("org.json.JSONException")),
                    "Use the game's json.jar, not a newer Maven JSON jar.");
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        require(compiler != null, "A full JDK 17 is required.");
        String classpath = String.join(File.pathSeparator,
                dependencies.stream().map(path -> path.toAbsolutePath().toString()).toList());
        List<Path> sources = sources(root);
        List<Path> javaSources = sources.stream().filter(path -> path.toString().endsWith(".java")).toList();
        compileKotlin(kotlin, classpath, sources, classes);
        try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            boolean passed = compiler.getTask(null, manager, null,
                    List.of("--release", "17", "-encoding", "UTF-8",
                            "-classpath", classpath + File.pathSeparator + classes,
                            "-d", classes.toString()), null,
                    manager.getJavaFileObjectsFromPaths(javaSources)).call();
            require(passed, "Java compilation failed; the release jar was not replaced.");
        }
        Map<String, byte[]> entries = new TreeMap<>();
        for (Path path : files(classes)) {
            String name = relative(classes, path);
            if (runtimeEntry(name)) entries.put(name, Files.readAllBytes(path));
        }
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue("Manifest-Version", "1.0");
        attributes.putValue("Implementation-Title", FOLDER);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        manifest.write(bytes);
        entries.put("META-INF/MANIFEST.MF", bytes.toByteArray());
        Path candidate = work.resolve(MOD + ".jar");
        writeZip(candidate, entries);
        checkJar(candidate);
        Files.createDirectories(root.resolve(JAR).getParent());
        Files.move(candidate, root.resolve(JAR), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Compiled " + sources.size() + " sources into " + (entries.size() - 1) + " jar entries.");
        System.out.println("Release jar: " + root.resolve(JAR));
    }

    static void compileKotlin(Path kotlin, String classpath, List<Path> sources, Path classes) throws Exception {
        require(Files.isDirectory(kotlin), "Kotlin compiler folder not found: " + kotlin);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        // java expands the trailing star itself, no shell involved
        List<String> base = List.of(java, "-cp", kotlin.resolve("*").toString(), KOTLIN_MAIN);
        String version = run(join(base, List.of("-version")), false);
        require(version.contains("kotlinc-jvm " + KOTLIN + " "),
                "Use the Kotlin " + KOTLIN + " compiler; " + kotlin + " reports: " + version.strip());
        // kotlinc reads the java sources too, so kotlin and java can call each other
        List<String> command = join(base, List.of("-jvm-target", "17", "-no-stdlib", "-no-reflect",
                "-jdk-home", System.getProperty("java.home"), "-module-name", KOTLIN_MODULE_NAME,
                "-classpath", classpath, "-d", classes.toString()));
        command.addAll(sources.stream().map(Path::toString).toList());
        run(command, true);
    }

    static String run(List<String> command, boolean showOutput) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (showOutput || exit != 0) System.out.print(output);
        require(exit == 0, "Kotlin compilation failed; the release jar was not replaced.");
        return output;
    }

    static List<String> join(List<String> first, List<String> second) {
        List<String> joined = new ArrayList<>(first);
        joined.addAll(second);
        return joined;
    }

    static List<Path> sources(Path root) throws IOException {
        return files(root.resolve(SOURCE)).stream()
                .filter(path -> compiled(relative(root, path))).toList();
    }

    static boolean compiled(String name) {
        return name.startsWith(SOURCE) && (name.endsWith(".java") || name.endsWith(".kt"));
    }

    static void pack(Path root, String version) throws Exception {
        Notes notes = notes(Files.readString(root.resolve("Changelog")), version);
        checkJar(root.resolve(JAR));
        List<String> tracked = trackedFiles(root);
        Map<String, byte[]> entries = new TreeMap<>();
        for (String name : tracked) {
            if (releaseFile(name)) entries.put(FOLDER + "/" + name, Files.readAllBytes(root.resolve(name)));
        }
        for (String name : REQUIRED) {
            require(entries.containsKey(FOLDER + "/" + name), "Missing tracked release file: " + name);
        }
        checkAssets(entries, new HashSet<>(tracked));
        require(Files.readString(root.resolve("mod_info.json")).contains("\"" + SHIPPED_JAR + "\""),
                "mod_info.json must load " + SHIPPED_JAR + ".");
        entries.put(FOLDER + "/" + SHIPPED_JAR, Files.readAllBytes(root.resolve(JAR)));
        entries.put(FOLDER + "/mod_info.json", stampInfo(Files.readString(root.resolve("mod_info.json")),
                notes.version()).getBytes(StandardCharsets.UTF_8));
        String versionFile = stampVersion(Files.readString(root.resolve(VERSION_FILE)), notes.version());
        entries.put(FOLDER + "/" + VERSION_FILE, versionFile.getBytes(StandardCharsets.UTF_8));
        Path output = root.resolve("out/release");
        Files.createDirectories(output);
        Files.writeString(output.resolve(VERSION_FILE), versionFile);
        Path zip = output.resolve(FOLDER + "-" + notes.version() + ".zip");
        writeZip(zip, entries);
        // text block keeps the changelog's plain-text layout on github
        Files.writeString(output.resolve("release-notes.md"), "```text\n" + notes.text() + "\n```\n");
        Files.writeString(output.resolve("version.txt"), notes.version() + "\n");
        System.out.println("Packaged " + entries.size() + " files: " + zip + " (" + Files.size(zip) + " bytes)");
    }

    static Notes notes(String changelog, String version) {
        if (version != null) require(version.matches(VERSION), "Expected a version such as 2.1.c, without a v prefix.");
        List<String> lines = changelog.replace("\r\n", "\n").lines().toList();
        List<Integer> headings = new ArrayList<>();
        Set<String> versions = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher match = HEADING.matcher(lines.get(i));
            if (!match.matches()) continue;
            require(versions.add(match.group(1)), "Duplicate changelog version: " + match.group(1));
            headings.add(i);
        }
        require(!headings.isEmpty(), "Changelog has no version heading.");
        int start = headings.get(0);
        int end = headings.size() > 1 ? headings.get(1) : lines.size();
        Matcher heading = HEADING.matcher(lines.get(start));
        require(heading.matches(), "Invalid changelog heading.");
        String newest = heading.group(1);
        boolean unreleased = "UNRELEASED".equals(heading.group(2));
        require(version == null || version.equals(newest), "Version must match the newest Changelog section: " + newest);
        require(version == null || !unreleased, "Remove UNRELEASED from " + newest + " before a release.");
        String body = String.join("\n", lines.subList(start + 1, end)).strip();
        require(Pattern.compile("(?m)^(Added|Adjusted|Fixed|Removed) - .+").matcher(body).find(),
                "Changelog section " + newest + " has no changes.");
        require(!body.contains("```"), "Changelog must be plain text, without code fences.");
        return new Notes(newest, unreleased, lines.get(start) + "\n" + body + "\n");
    }

    static void checkAssets(Map<String, byte[]> entries, Set<String> tracked) {
        Pattern asset = Pattern.compile("(?:graphics|sounds|data)/[A-Za-z0-9_./-]+"
                + "\\.(?:png|jpg|jpeg|ogg|wav|shader|csv|json)\\b");
        for (var file : entries.entrySet()) {
            if (!file.getKey().startsWith(FOLDER + "/data/")) continue;
            Matcher references = asset.matcher(new String(file.getValue(), StandardCharsets.UTF_8));
            while (references.find()) {
                String path = references.group();
                // vanilla paths are not in the repo, so only tracked assets are checked
                if (!tracked.contains(path)) continue;
                require(entries.containsKey(FOLDER + "/" + path),
                        file.getKey() + " references an unpackaged asset: " + path);
            }
        }
    }

    static String stampInfo(String text, String version) {
        require(version.matches(VERSION), "Invalid release version.");
        return replaceField(text, "version", "\"" + version + "\"");
    }

    static String stampVersion(String text, String version) {
        require(version.matches(VERSION), "Invalid release version.");
        String[] parts = version.split("\\.");
        text = replaceField(text, "major", parts[0]);
        text = replaceField(text, "minor", parts[1]);
        text = replaceField(text, "patch", "\"" + parts[2] + "\"");
        return replaceField(text, "directDownloadURL",
                "\"" + RELEASES + "/download/" + version + "/" + FOLDER + "-" + version + ".zip\"");
    }

    static String replaceField(String text, String key, String value) {
        Pattern field = Pattern.compile("(\"" + key + "\"\\s*:\\s*)(\"[^\"\\r\\n]*\"|[0-9]+)");
        Matcher matcher = field.matcher(text);
        require(matcher.find(), "Missing metadata field: " + key);
        require(!matcher.find(), "Duplicate metadata field: " + key);
        return field.matcher(text).replaceFirst("$1" + Matcher.quoteReplacement(value));
    }

    static boolean releaseFile(String name) {
        if (ROOT_FILES.contains(name)) return true;
        String[] parts = name.split("/");
        if (parts.length < 2 || !ASSETS.containsKey(parts[0])) return false;
        for (String part : parts) {
            if (part.startsWith(".") || SKIPPED_DIRS.contains(part)) return false;
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 && ASSETS.get(parts[0]).contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    static boolean runtimeEntry(String name) {
        if (name.equals(KOTLIN_MODULE)) return true;
        return name.startsWith(MOD + "/") && !name.startsWith(MOD + "/tools/")
                && name.endsWith(".class") && !name.contains("..") && !name.contains("\\");
    }

    static void checkJar(Path file) throws IOException {
        require(Files.isRegularFile(file), "Missing " + file + "; run ReleaseBuild build.");
        try (JarFile jar = new JarFile(file.toFile())) {
            require(jar.getEntry(PLUGIN) != null, "Release jar has no " + PLUGIN + ".");
            for (ZipEntry entry : jar.stream().toList()) {
                String name = entry.getName();
                if (name.equals("META-INF/MANIFEST.MF") || name.equals(KOTLIN_MODULE)) continue;
                require(runtimeEntry(name), "Development/dependency file in jar: " + name);
                try (var input = new DataInputStream(jar.getInputStream(entry))) {
                    require(input.readInt() == 0xcafebabe, "Invalid class: " + name);
                    input.readUnsignedShort();
                    require(input.readUnsignedShort() == 61, "Class is not Java 17: " + name);
                }
            }
        }
    }

    static List<String> trackedFiles(Path root) throws Exception {
        Process git = new ProcessBuilder("git", "ls-files", "-z").directory(root.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String output = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        require(git.waitFor() == 0, "git ls-files failed.");
        return output.isEmpty() ? List.of() : List.of(output.split("\u0000"));
    }

    static List<Path> files(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> relative(directory, path))).toList();
        }
    }

    static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }

    static void writeZip(Path target, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            zip.setLevel(9);
            for (var item : new TreeMap<>(entries).entrySet()) {
                ZipEntry entry = new ZipEntry(item.getKey());
                entry.setTimeLocal(ZIP_TIME);
                zip.putNextEntry(entry);
                zip.write(item.getValue());
                zip.closeEntry();
            }
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
