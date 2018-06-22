package com.fataar.plugin

import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.tasks.InvokeManifestMerger
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import org.gradle.api.tasks.Copy
import org.gradle.jvm.tasks.Jar

/**
 * 依赖库内容合并类
 * 合并内容: 1. class
 *           2. manifest
 *           3. resources
 *           4. RSources
 *           5. assets
 *           6. jniLibs
 *           7. proguardFile
 */
class VariantProcessor {

    private final Project mProject

    private final LibraryVariant mVariant

    private Set<ResolvedArtifact> mResolvedArtifacts = new ArrayList<>()

    private Collection<AndroidArchiveLibrary> mAndroidArchiveLibraries = new ArrayList<>()

    private Collection<File> mJarFiles = new ArrayList<>()

    private Collection<ExcludeFile> mExcludeFiles = new ArrayList<>()

    VariantProcessor(Project project, LibraryVariant variant) {
        mProject = project
        mVariant = variant
    }

    void addArtifacts(Set<ResolvedArtifact> resolvedArtifacts) {
        mResolvedArtifacts.addAll(resolvedArtifacts)
    }

    void addAndroidArchiveLibrary(AndroidArchiveLibrary library) {
        mAndroidArchiveLibraries.add(library)
    }

    void addJarFile(File jar) {
        mJarFiles.add(jar)
    }

    void addExcludeFiles(List<ExcludeFile> excludeFiles) {
        mExcludeFiles.addAll(excludeFiles)
    }

    void processVariant() {
        String taskPath = 'pre' + mVariant.name.capitalize() + 'Build'
        Task prepareTask = mProject.tasks.findByPath(taskPath)
        if (prepareTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        taskPath = 'bundle' + mVariant.name.capitalize()
        Task bundleTask = mProject.tasks.findByPath(taskPath)
        if (bundleTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        processArtifacts(bundleTask)

        processClassesAndJars()

        if (mAndroidArchiveLibraries.isEmpty()) {
            return
        }
        processManifest()
        processResourcesAndR()
        processRSources()
        processAssets()
        processJniLibs()
        processProguardTxt(prepareTask)
        mergeRClass(bundleTask)
        processExcludeFiles()
    }

    /**
     * exploded artifact files
     */
    private void processArtifacts(Task bundleTask) {
        for (DefaultResolvedArtifact artifact in mResolvedArtifacts) {
            if (FatLibraryPlugin.ARTIFACT_TYPE_JAR.equals(artifact.type)) {
                addJarFile(artifact.file)
            }
            if (FatLibraryPlugin.ARTIFACT_TYPE_AAR.equals(artifact.type)) {
                AndroidArchiveLibrary archiveLibrary = new AndroidArchiveLibrary(mProject, artifact)
                addAndroidArchiveLibrary(archiveLibrary)
                Set<Task> buildDependencies = artifact.getBuildDependencies().getDependencies()
                archiveLibrary.getExploadedRootDir().deleteDir()
                def zipFolder = archiveLibrary.getRootFolder()
                zipFolder.mkdirs()
                if (buildDependencies.size() == 0) {
                    mProject.copy {
                        from mProject.zipTree(artifact.file.absolutePath)
                        into zipFolder
                    }
                } else {
                    Task explodTask = mProject.tasks.create(name: 'explod' + artifact.name.capitalize() + mVariant.buildType.name, type: Copy) {
                        from mProject.zipTree(artifact.file.absolutePath)
                        into zipFolder
                    }
                    explodTask.dependsOn(buildDependencies.first())
                    explodTask.shouldRunAfter(buildDependencies.first())
                    bundleTask.dependsOn(explodTask)
                }
            }
        }
    }

    /**
     * merge manifest
     *
     * TODO process each variant.getOutputs()
     * TODO "InvokeManifestMerger" deserve more android plugin version check
     * TODO add setMergeReportFile
     * TODO a better temp manifest file location
     */
    private void processManifest() {
        Class invokeManifestTaskClazz = null
        String className = 'com.android.build.gradle.tasks.InvokeManifestMerger'
        try {
            invokeManifestTaskClazz = Class.forName(className)
        } catch (ClassNotFoundException ignored) {
        }
        if (invokeManifestTaskClazz == null) {
            throw new RuntimeException("Can not find class ${className}!")
        }
        Task processManifestTask = mVariant.getOutputs().first().getProcessManifest()
        def manifestOutput = mProject.file(mProject.buildDir.path + '/intermediates/fat-aar/' + mVariant.dirName)
        File manifestOutputBackup = mProject.file(processManifestTask.getManifestOutputDirectory().absolutePath + '/AndroidManifest.xml')
        processManifestTask.setManifestOutputDirectory(manifestOutput)
        File mainManifestFile = new File(manifestOutput.absolutePath + '/AndroidManifest.xml')
        mainManifestFile.deleteOnExit()
        manifestOutput.mkdirs()
        mainManifestFile.createNewFile()
        processManifestTask.doLast {
            mainManifestFile.write(manifestOutputBackup.text)
        }
        InvokeManifestMerger manifestsMergeTask = mProject.tasks.create('merge' + mVariant.name.capitalize() + 'Manifest', invokeManifestTaskClazz)
        manifestsMergeTask.setVariantName(mVariant.name)
        manifestsMergeTask.setMainManifestFile(mainManifestFile)
        List<File> list = new ArrayList<>()
        for (archiveLibrary in mAndroidArchiveLibraries) {
            list.add(archiveLibrary.getManifest())
        }
        manifestsMergeTask.setSecondaryManifestFiles(list)
        manifestsMergeTask.setOutputFile(manifestOutputBackup)
        manifestsMergeTask.dependsOn processManifestTask
        manifestsMergeTask.doFirst {
            List<File> existFiles = new ArrayList<>()
            manifestsMergeTask.getSecondaryManifestFiles().each {
                if (it.exists()) {
                    existFiles.add(it)
                }
            }
            manifestsMergeTask.setSecondaryManifestFiles(existFiles)
        }
        processManifestTask.finalizedBy manifestsMergeTask
    }
    /**
     * 合并所有的类文件
     */
    private void processClassesAndJars() {
        if (mVariant.getBuildType().isMinifyEnabled()) {
            //merge proguard file
            for (archiveLibrary in mAndroidArchiveLibraries) {
                List<File> thirdProguardFiles = archiveLibrary.proguardRules
                for (File file : thirdProguardFiles) {
                    if (file.exists()) {
                        println 'add proguard file: ' + file.absolutePath
                        mProject.android.getDefaultConfig().proguardFile(file)
                    }
                }
            }
            //merge aar class
            Task javacTask = mVariant.getJavaCompile()
            if (javacTask == null) {
                // warn: can not find javaCompile task, jack compile might be on.
                return
            }
            javacTask.doLast {
                def dustDir = mProject.file(mProject.buildDir.path + '/intermediates/classes/' + mVariant.dirName)
                ExplodedHelper.processIntoClasses(mProject, mAndroidArchiveLibraries, mJarFiles, dustDir)
            }
        }

        String taskPath = 'transformClassesAndResourcesWithSyncLibJarsFor' + mVariant.name.capitalize()
        Task syncLibTask = mProject.tasks.findByPath(taskPath)
        if (syncLibTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        syncLibTask.doLast {
            def dustDir = mProject.file(AndroidPluginHelper.resolveBundleDir(mProject, mVariant).path + '/libs')
            ExplodedHelper.processIntoJars(mProject, mAndroidArchiveLibraries, mJarFiles, dustDir, mVariant.getBuildType().isMinifyEnabled())
        }

    }

    /**
     * merge R.txt(actually is to fix issue caused by provided configuration) and res
     *
     * Here I have to inject res into "main" instead of "variant.name".
     * To avoid the res from embed dependencies being used, once they have the same res Id with main res.
     *
     * Now the same res Id will cause a build exception: Duplicate resources, to encourage you to change res Id.
     * Adding "android.disableResourceValidation=true" to "gradle.properties" can do a trick to skip the exception, but is not recommended.
     */
    private void processResourcesAndR() {
        String taskPath = 'generate' + mVariant.name.capitalize() + 'Resources'
        Task resourceGenTask = mProject.tasks.findByPath(taskPath)
        if (resourceGenTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        resourceGenTask.doFirst {
            for (archiveLibrary in mAndroidArchiveLibraries) {
                mProject.android.sourceSets."main".res.srcDir(archiveLibrary.resFolder)
            }
        }
    }

    /**
     * generate R.java
     */
    private void processRSources() {
        Task processResourcesTask = mVariant.getOutputs().first().getProcessResources()
        processResourcesTask.doLast {
            for (archiveLibrary in mAndroidArchiveLibraries) {
                RSourceGenerator.generate(processResourcesTask.getSourceOutputDir(), archiveLibrary)
            }
        }
    }

    /**
     * merge assets
     *
     * AaptOptions.setIgnoreAssets and AaptOptions.setIgnoreAssetsPattern will work as normal
     */
    private void processAssets() {
        Task assetsTask = mVariant.getMergeAssets()
        if (assetsTask == null) {
            throw new RuntimeException("Can not find task in variant.getMergeAssets()!")
        }
        for (archiveLibrary in mAndroidArchiveLibraries) {
            assetsTask.getInputs().dir(archiveLibrary.assetsFolder)
        }
        assetsTask.doFirst {
            for (archiveLibrary in mAndroidArchiveLibraries) {
                // the source set here should be main or variant?
                mProject.android.sourceSets."main".assets.srcDir(archiveLibrary.assetsFolder)
            }
        }
    }

    /**
     * merge jniLibs
     */
    private void processJniLibs() {
        String taskPath = 'merge' + mVariant.name.capitalize() + 'JniLibFolders'
        Task mergeJniLibsTask = mProject.tasks.findByPath(taskPath)
        if (mergeJniLibsTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        for (archiveLibrary in mAndroidArchiveLibraries) {
            mergeJniLibsTask.getInputs().dir(archiveLibrary.jniFolder)
        }
        mergeJniLibsTask.doFirst {
            for (archiveLibrary in mAndroidArchiveLibraries) {
                // the source set here should be main or variant?
                mProject.android.sourceSets."main".jniLibs.srcDir(archiveLibrary.jniFolder)
            }
        }
    }

    /**
     * merge proguard.txt
     */
    private void processProguardTxt(Task prepareTask) {
        String taskPath = 'merge' + mVariant.name.capitalize() + 'ConsumerProguardFiles'
        Task mergeFileTask = mProject.tasks.findByPath(taskPath)
        if (mergeFileTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        for (archiveLibrary in mAndroidArchiveLibraries) {
            List<File> thirdProguardFiles = archiveLibrary.proguardRules
            for (File file : thirdProguardFiles) {
                if (file.exists()) {
                    println 'add proguard file: ' + file.absolutePath
                    mergeFileTask.getInputs().file(file)
                }
            }
        }
        mergeFileTask.doFirst {
            Collection proguardFiles = mergeFileTask.getInputFiles()
            for (archiveLibrary in mAndroidArchiveLibraries) {
                List<File> thirdProguardFiles = archiveLibrary.proguardRules
                for (File file : thirdProguardFiles) {
                    if (file.exists()) {
                        println 'add proguard file: ' + file.absolutePath
                        proguardFiles.add(file)
                    }
                }
            }
        }
        mergeFileTask.dependsOn prepareTask
    }
    /**
     * merge android library R.class
     */
    private void mergeRClass(Task bundleTask) {
        String taskPath = 'transformClassesAndResourcesWithSyncLibJarsFor' + mVariant.name.capitalize()
        Task syncLibTask = mProject.tasks.findByPath(taskPath)
        if (syncLibTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }

        // 原始jar包文件
        def classesJar = mProject.file(AndroidPluginHelper.resolveBundleDir(mProject, mVariant).path + '/classes.jar')
        String applicationId = mVariant.getApplicationId();
        String excludeRPath = applicationId.replace('.', '/');
        Task jarTask
        //开启混淆时对混淆过后的文件进行重新打包
        if (mVariant.getBuildType().isMinifyEnabled()) {
            jarTask = mProject.tasks.create(name: 'transformProguradJarTask' + mVariant.name, type: Jar) {
                from project.zipTree(AndroidPluginHelper.resolveTransform(mProject, mVariant))
                exclude(excludeRPath + '/R.class', excludeRPath + '/R$*', 'META-INF/')
            }
            jarTask.onlyIf {
                File file = AndroidPluginHelper.resolveTransform(mProject, mVariant);
                return file.exists();
            }
            jarTask.doLast {
                println 'transform progurad jar ready'
                File file = new File(mProject.getBuildDir().absolutePath + '/libs/' + mProject.name + '.jar');
                if (file.exists()) {
                    mProject.delete(classesJar)
                    mProject.copy {
                        from(file)
                        into(AndroidPluginHelper.resolveBundleDir(mProject, mVariant))
                        rename(mProject.name + '.jar', 'classes.jar')
                    }
                } else {
                    println 'can not find transformProguradJar file ';
                }
            }
        } else {
            jarTask = mProject.tasks.create(name: 'transformJarTask' + mVariant.name, type: Jar) {
                from(mProject.buildDir.absolutePath + '/intermediates/classes/' + mVariant.name.capitalize())
                exclude(excludeRPath + '/R.class', excludeRPath + '/R$*', 'META-INF/')
            }
            jarTask.onlyIf {
                File file = mProject.file(mProject.buildDir.absolutePath + '/intermediates/classes/' + mVariant.name.capitalize())
                return file.exists();
            }
            jarTask.doLast {
                println 'transform jar ready'
                File file = new File(mProject.getBuildDir().absolutePath + '/libs/' + mProject.name + '.jar');
                if (file.exists()) {
                    mProject.delete(classesJar)
                    mProject.copy {
                        from(file)
                        into(AndroidPluginHelper.resolveBundleDir(mProject, mVariant))
                        rename(mProject.name + '.jar', 'classes.jar')
                    }
                } else {
                    println 'can not find transformProguradJar file ';
                }
            }
        }


        bundleTask.dependsOn jarTask
        jarTask.shouldRunAfter(syncLibTask)
    }

    /**
     * delete  exclude files
     */
    private void processExcludeFiles() {
        String taskPath = 'bundle' + mVariant.name.capitalize()
        Task bundleTask = mProject.tasks.findByPath(taskPath)
        if (bundleTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        taskPath = 'transformClassesAndResourcesWithSyncLibJarsFor' + mVariant.name.capitalize()
        Task syncLibTask = mProject.tasks.findByPath(taskPath)
        if (syncLibTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        def excludeFileTask = mProject.tasks.create(name: 'transformExcludeFilesTask' + mVariant.name)
        excludeFileTask.doLast {
            def bundlePath = AndroidPluginHelper.resolveBundleDir(mProject, mVariant).path
            mExcludeFiles.each { excludeFile ->
                excludeFile.fileNames.each { fileName ->
                    File file = mProject.file(bundlePath + File.separator + excludeFile.name + File.separator + fileName)
                    println file.path
                    if (file.exists()) {
                        file.delete()
                    } else {
                        println 'excludeFileError : ' + file.path + ' not exist'
                    }
                }
            }
        }
        bundleTask.dependsOn excludeFileTask
        excludeFileTask.shouldRunAfter(syncLibTask)
    }
}
