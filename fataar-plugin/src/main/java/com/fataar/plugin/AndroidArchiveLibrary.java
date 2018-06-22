package com.fataar.plugin;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class AndroidArchiveLibrary {

    private final Project mProject;

    private final ResolvedArtifact mArtifact;

    public AndroidArchiveLibrary(Project project, ResolvedArtifact artifact) {
        if (!"aar".equals(artifact.getType())) {
            throw new IllegalArgumentException("artifact must be aar type!");
        }
        mProject = project;
        mArtifact = artifact;
    }

    public String getGroup() {
        return mArtifact.getModuleVersion().getId().getGroup();
    }

    public String getName() {
        return mArtifact.getModuleVersion().getId().getName();
    }

    public String getVersion() {
        return mArtifact.getModuleVersion().getId().getVersion();
    }

    public File getExploadedRootDir() {
        File explodedRootDir = mProject.file(
                mProject.getBuildDir() + "/intermediates" + "/exploded-aar/");
        ModuleVersionIdentifier id = mArtifact.getModuleVersion().getId();
        return mProject.file(explodedRootDir + "/" + id.getGroup() + "/" + id.getName());
    }

    public File getRootFolder() {
        File explodedRootDir = mProject.file(
                mProject.getBuildDir() + "/intermediates" + "/exploded-aar/");
        ModuleVersionIdentifier id = mArtifact.getModuleVersion().getId();
        return mProject.file(
                explodedRootDir + "/" + id.getGroup() + "/" + id.getName() + "/" + id.getVersion());
    }

    public File getAidlFolder() {
        return new File(getRootFolder(), "aidl");
    }

    public File getAssetsFolder() {
        return new File(getRootFolder(), "assets");
    }

    public File getClassesJarFile() {
        return new File(getRootFolder(), "classes.jar");
    }

    public Collection<File> getLocalJars() {
        List<File> localJars = new ArrayList<>();
        File[] jarList = new File(getRootFolder(), "libs").listFiles();
        if (jarList != null) {
            for (File jars : jarList) {
                if (jars.isFile() && jars.getName().endsWith(".jar")) {
                    localJars.add(jars);
                }
            }
        }

        return localJars;
    }

    public File getJniFolder() {
        return new File(getRootFolder(), "jni");
    }

    public File getResFolder() {
        return new File(getRootFolder(), "res");
    }

    public File getManifest() {
        return new File(getRootFolder(), "AndroidManifest.xml");
    }

    public File getLintJar() {
        return new File(getRootFolder(), "lint.jar");
    }

    public List<File> getProguardRules() {
        List<File> list = new ArrayList<>();
        list.add(new File(getRootFolder(), "proguard-rules.pro"));
        list.add(new File(getRootFolder(), "proguard-project.txt"));
        return list;
    }

    public File getSymbolFile() {
        return new File(getRootFolder(), "R.txt");
    }

    public String getPackageName() {
        String packageName = null;
        File manifestFile = getManifest();
        if (manifestFile.exists()) {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                Document doc = dbf.newDocumentBuilder().parse(manifestFile);
                Element element = doc.getDocumentElement();
                packageName = element.getAttribute("package");
            } catch (Exception ignored) {
            }
        }
        return packageName;
    }
}
