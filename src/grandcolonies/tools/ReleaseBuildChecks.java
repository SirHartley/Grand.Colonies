package grandcolonies.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public final class ReleaseBuildChecks {

    private static int checks;

    private ReleaseBuildChecks() {
    }

    public static void main(String[] args) throws Exception {
        checkNotes();
        checkRepoChangelog();
        checkStamping();
        checkFiles();
        checkSources();
        checkAssets();
        System.out.println("Release build: " + checks + " checks passed");
    }

    private static void checkNotes() {
        String changelog = "2.1.c - UNRELEASED\nAdded - New thing\n\n2.1.b\nFixed - Old thing\n\n1.0.a\n\nAdded - Initial Release";
        ReleaseBuild.Notes preview = ReleaseBuild.notes(changelog, null);
        require(preview.version().equals("2.1.c") && preview.unreleased(), "preview reads newest section");
        require(!preview.text().contains("Old thing"), "notes stop at the next section");
        fails(() -> ReleaseBuild.notes(changelog, "2.1.c"), "version rejects UNRELEASED");
        fails(() -> ReleaseBuild.notes(changelog, "2.1.b"), "version must match newest section");
        fails(() -> ReleaseBuild.notes(changelog, "v2.1.c"), "version has no v prefix");
        String released = changelog.replace(" - UNRELEASED", "");
        ReleaseBuild.Notes release = ReleaseBuild.notes(released, "2.1.c");
        require(release.version().equals("2.1.c") && !release.unreleased(), "version accepts released section");
        require(release.text().startsWith("2.1.c\nAdded - New thing"), "notes keep heading");
        fails(() -> ReleaseBuild.notes("2.1.a\nNothing here\n", null), "section needs a change line");
        fails(() -> ReleaseBuild.notes("2.1.a\nAdded - x\n2.1.a\nAdded - y\n", null), "duplicate versions");
        fails(() -> ReleaseBuild.notes("2.1.a\nAdded - x\n```\n", null), "code fences rejected");
    }

    private static void checkRepoChangelog() throws Exception {
        String changelog = Files.readString(Path.of("Changelog"));
        require(ReleaseBuild.notes(changelog, null).version().matches(ReleaseBuild.VERSION), "repo changelog parses");
    }

    private static void checkStamping() {
        String file = "{\n\"modVersion\":\n{\n\"major\":2,\n\"minor\":1,\n\"patch\":\"b\"\n},\n"
                + "#Link towards a direct download\n\"directDownloadURL\":\"x\",\n}";
        String stamped = ReleaseBuild.stampVersion(file, "2.2.a");
        require(stamped.contains("\"major\":2") && stamped.contains("\"minor\":2")
                && stamped.contains("\"patch\":\"a\""), "version file stamped");
        require(stamped.contains("#Link towards a direct download"), "version file comments kept");
        require(stamped.contains("/releases/download/2.2.a/" + ReleaseBuild.FOLDER + "-2.2.a.zip"),
                "download url stamped");
        String info = "{\"version\": \"2.1.b\", \"gameVersion\": \"0.98a\"}";
        String stampedInfo = ReleaseBuild.stampInfo(info, "2.2.a");
        require(stampedInfo.contains("\"2.2.a\"") && stampedInfo.contains("\"0.98a\""), "mod_info stamped, game version kept");
        fails(() -> ReleaseBuild.stampInfo("{}", "2.2.a"), "missing field rejected");
    }

    private static void checkFiles() {
        require(ReleaseBuild.releaseFile("Changelog"), "changelog shipped");
        require(ReleaseBuild.releaseFile(ReleaseBuild.VERSION_FILE), "version file shipped");
        require(ReleaseBuild.releaseFile("_grandColonies.png"), "mod icon shipped");
        require(ReleaseBuild.releaseFile("CC-license.png"), "license image shipped");
        require(ReleaseBuild.releaseFile("data/campaign/market_conditions.csv"), "data shipped");
        require(ReleaseBuild.releaseFile("graphics/conditions/page01.png"), "graphics shipped");
        require(!ReleaseBuild.releaseFile("graphics/conditions/note.txt"), "notes skipped");
        require(!ReleaseBuild.releaseFile("docs/ARCHITECTURE.md"), "docs skipped");
        require(!ReleaseBuild.releaseFile("CLAUDE.md"), "agent files skipped");
        require(!ReleaseBuild.releaseFile("Grand.Colonies.iml"), "ide files skipped");
        require(!ReleaseBuild.releaseFile("lib/starfarer.api.jar"), "compile jars skipped");
        require(!ReleaseBuild.releaseFile("src/grandcolonies/plugins/ModPlugin.java"), "source skipped");
        require(!ReleaseBuild.releaseFile(ReleaseBuild.JAR), "release jar mapped, not copied");
        require(!ReleaseBuild.releaseFile("graphics/unused/old.png"), "unused art skipped");
        require(!ReleaseBuild.releaseFile("data/.idea/x.json"), "hidden dirs skipped");
        require(ReleaseBuild.runtimeEntry(ReleaseBuild.PLUGIN), "runtime classes kept");
        require(ReleaseBuild.runtimeEntry(ReleaseBuild.KOTLIN_MODULE), "kotlin module kept");
        require(!ReleaseBuild.runtimeEntry(ReleaseBuild.MOD + "/tools/ReleaseBuild.class"), "tools left out of jar");
        require(!ReleaseBuild.runtimeEntry("kotlin/Unit.class"), "kotlin runtime left out of jar");
        require(!ReleaseBuild.runtimeEntry("META-INF/main.kotlin_module"), "other kotlin modules left out of jar");
    }

    private static void checkSources() {
        require(ReleaseBuild.compiled("src/grandcolonies/plugins/ModPlugin.java"), "java compiled");
        require(ReleaseBuild.compiled("src/grandcolonies/listeners/IndustryPanelReplacer.kt"), "kotlin compiled");
        require(!ReleaseBuild.compiled("src/grandcolonies/notes.txt"), "resources not compiled");
        require(!ReleaseBuild.compiled("data/scripts/X.java"), "only src compiled");
    }

    private static void checkAssets() {
        String folder = ReleaseBuild.FOLDER + "/";
        byte[] csv = "x,graphics/conditions/page01.png,graphics/icons/vanilla.png".getBytes();
        ReleaseBuild.checkAssets(Map.of(folder + "data/campaign/market_conditions.csv", csv,
                folder + "graphics/conditions/page01.png", new byte[0]), Set.of("graphics/conditions/page01.png"));
        checks++;
        fails(() -> ReleaseBuild.checkAssets(Map.of(folder + "data/campaign/market_conditions.csv", csv),
                Set.of("graphics/conditions/page01.png")), "tracked but unpackaged asset rejected");
    }

    private static void fails(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            checks++;
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
