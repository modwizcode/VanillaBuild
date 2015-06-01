import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

public class VanillaBuildMain {
    private static final Logger logger = LoggerFactory.getLogger(VanillaBuildMain.class);

    private static final String ROOT_DIR = "SpongeVanilla";

    private static final boolean IS_WINDOWS = System.getProperty("os.name").contains("windows") ||
            System.getProperty("os.name").contains("Windows");

    private static final String GRADLEW_COMMAND = new File(ROOT_DIR, "gradlew." + (IS_WINDOWS ? "bat" : "sh")).getAbsolutePath();

    private static final String[] SETUP_DECOMPWORKSPACE = new String[]{
            GRADLEW_COMMAND,
            "setupDecompWorkspace",
            "--refresh-dependencies"
    };

    private static final String[] SETUP_CIWORKSPACE = new String[]{
            GRADLEW_COMMAND,
            "setupCIWorkspace"
    };

    private static final String[] BUILD = new String[]{
            GRADLEW_COMMAND,
            "build",
            "-x",
            "checkstyleMain"
    };

    public static void main(String[] args) {
        if (!Files.exists(Paths.get("SpongeVanilla"))) {
            logger.info("SpongeVanilla repository not found, cloning now.");
            if (!cloneRepo()) {
                logger.error("An error occurred while cloning the repository. Exiting.");
                return;
            }
        } else {
            logger.info("Checking for updates to existing SpongeVanilla repository.");
            if (!updateRepo()) {
                logger.error("An error occurred while updating the repository. Exiting.");
                return;
            }
        }

        // HACK: Add proper argument parsing
        if (args.length < 1 || !args[0].equalsIgnoreCase("--decomp")) {
            logger.info("Setting up CI workspace");
            setupWorkspace(false);
        } else {
            logger.info("Setting up decompiled workspace");
            setupWorkspace(true);
        }

        logger.info("Building SpongeVanilla");
        buildJar();

        logger.info("The built jar can be found inside " + ROOT_DIR + "/build/libs/");
        logger.info("The jar file to run is the file without javadoc, release or sources in the filename.");
    }

    private static boolean cloneRepo() {
        CloneCommand cloneCommand = new CloneCommand()
                .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                .setDirectory(new File(ROOT_DIR))
                .setURI("https://github.com/SpongePowered/SpongeVanilla.git")
                .setBranchesToClone(Collections.singleton("refs/heads/master"))
                .setBranch("refs/heads/master")
                .setCloneSubmodules(true);
        try {
            Git git = cloneCommand.call();
            return true;
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean updateRepo() {
        try {
            Git git = Git.open(new File(ROOT_DIR));
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
            git.pull().setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out))).call();
            git.submoduleInit().call();
            git.submoduleUpdate().call();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CheckoutConflictException e) {
            e.printStackTrace();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void setupWorkspace(boolean shouldDecomp) {
        try {
            String[] commandSequence = shouldDecomp ? SETUP_DECOMPWORKSPACE : SETUP_CIWORKSPACE;
            Process process = new ProcessBuilder(commandSequence)
                    .directory(new File(ROOT_DIR).getAbsoluteFile())
                    .redirectError(ProcessBuilder.Redirect.to(new File("error.log")))
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .start();
            process.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void buildJar() {
        try {

            Process process = new ProcessBuilder(BUILD)
                    .directory(new File(ROOT_DIR).getAbsoluteFile())
                    .redirectError(ProcessBuilder.Redirect.appendTo(new File("error.log")))
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .start();
            process.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
