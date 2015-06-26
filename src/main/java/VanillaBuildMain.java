import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.SubmoduleInitCommand;
import org.eclipse.jgit.api.SubmoduleUpdateCommand;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import arguments.ArgumentIterator;
import arguments.TargetCommit;
import arguments.WorkspaceType;

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

    private static final String GRADLEW_COMMAND = new File(ROOT_DIR, "gradlew" + (IS_WINDOWS ? ".bat" : "")).getAbsolutePath();

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
        // TODO: Clean this up
        ArgumentIterator argIterator = new ArgumentIterator(args);

        TargetCommit cloneCommit = TargetCommit.MASTER;
        WorkspaceType workspace = WorkspaceType.CI;
        
        String customCommit = null;
        
        while (argIterator.hasNext()) {
            String arg = argIterator.next();
            if (arg.equalsIgnoreCase("--decomp")) {
                workspace = WorkspaceType.DECOMP;
            } else if (arg.equalsIgnoreCase("--commit")) {
                if (argIterator.hasNext()) {
                    cloneCommit = TargetCommit.CUSTOM;
                    customCommit = argIterator.next();
                } else {
                    logger.error("No commit specified aborting build.");
                    return;
                }
            }
        }
        
        if (!Files.exists(Paths.get("SpongeVanilla"))) {
            logger.info("SpongeVanilla repository not found, cloning now.");
            
            if (!cloneRepo(cloneCommit, customCommit)) {
                logger.error("An error occurred while cloning the repository. Exiting.");
                return;
            }
        } else {
            switch (cloneCommit) {
                case MASTER:
                    logger.info("Checking for updates to existing SpongeVanilla repository.");
                    break;
                case CUSTOM:
                    logger.info("Checking out custom commit " + customCommit + ".");
                    break;
            }
            
            if (!updateRepo(cloneCommit, customCommit)) {
                switch (cloneCommit) {
                    case MASTER:
                        logger.error("An error occurred while updating the repository. Exiting.");
                        break;
                    case CUSTOM:
                        logger.error("An error occurred while updating the repository to the specified commit. Exiting.");
                        break;
                }
                return;
            }
        }
        
        
        switch (workspace) {
            case CI:
                logger.info("Setting up CI workspace");
                setupWorkspace(false);
                break;
            case DECOMP:
                logger.info("Setting up decompiled workspace");
                setupWorkspace(true);
                break;
        }

        logger.info("Building SpongeVanilla");
        buildJar();

        logger.info("The built jar can be found inside " + ROOT_DIR + "/build/libs/");
        logger.info("The jar file to run is the file without javadoc, release or sources in the filename.");
    }

    private static boolean cloneRepo(TargetCommit cloneCommit, String customCommit) {
        CloneCommand cloneCommand = new CloneCommand()
                .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                .setDirectory(new File(ROOT_DIR))
                .setURI("https://github.com/SpongePowered/SpongeVanilla.git")
                .setBranchesToClone(Collections.singleton("refs/heads/master"))
                .setBranch("refs/heads/master")
                .setCloneSubmodules(true);
        try {
            Git git = cloneCommand.call();
            
            if (cloneCommit == TargetCommit.CUSTOM) {
                git.checkout().setName(customCommit).call();
                
                git.submoduleInit().call();
                git.submoduleUpdate().setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out))).call();
            }
            
            return true;
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean updateRepo(TargetCommit cloneCommit, String customCommit) {
        try {
            Git git = Git.open(new File(ROOT_DIR));
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
            git.fetch().setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out))).call();
            
            updateSubmodules(git.getRepository(), true);
            
            if (cloneCommit == TargetCommit.CUSTOM) {
                git.checkout().setName(customCommit).call();
            } else {
                git.checkout().setName("master").call();
            }
            
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
   
    private static boolean updateSubmodules(Repository baseRepo, boolean isBase) {
        ProgressMonitor console = new TextProgressMonitor(new PrintWriter(System.out));
        try {
            Git.wrap(baseRepo).submoduleInit().call();
            if (!Git.wrap(baseRepo).submoduleUpdate().setProgressMonitor(console).call().isEmpty()) {
                SubmoduleWalk walk = SubmoduleWalk.forIndex(baseRepo);
                while (walk.next()) {
                    Repository subRepo = walk.getRepository();
                    if (subRepo != null) {
                        return updateSubmodules(subRepo, false);
                    }
                }
                walk.close();
            }
            
            if (!isBase) {
                baseRepo.close();
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidRemoteException e) {
            e.printStackTrace();
        } catch (TransportException e) {
            e.printStackTrace();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        return false;
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
