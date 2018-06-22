package com.fataar.plugin

import com.google.common.base.Strings
import groovy.io.FileType
import org.gjt.jclasslib.io.ClassFileWriter
import org.gjt.jclasslib.structures.CPInfo
import org.gjt.jclasslib.structures.ClassFile
import org.gjt.jclasslib.structures.InvalidByteCodeException
import org.gjt.jclasslib.structures.constants.ConstantClassInfo
import org.gjt.jclasslib.structures.constants.ConstantFieldrefInfo
import org.gjt.jclasslib.structures.constants.ConstantUtf8Info
import org.gradle.api.Project


class ExplodedHelper {

    /**
     * 将 所有的依赖拷贝到打包目录
     *
     * @param project 项目
     * @param androidLibraries android依赖
     * @param jarFiles java依赖
     * @param folderOut 输出目录
     * @param minifyEnabled 是否开启混淆
     */
    public static void processIntoJars(Project project,
                                       Collection<AndroidArchiveLibrary> androidLibraries, Collection<File> jarFiles,
                                       File folderOut, boolean minifyEnabled) {
        println 'process into jars'
        for (androidLibrary in androidLibraries) {
            if (!androidLibrary.rootFolder.exists()) {
                println 'fat-aar-->[warning]' + androidLibrary.rootFolder + ' not found!'
                continue
            }
            def prefix = androidLibrary.name + '-' + androidLibrary.version
            if (!minifyEnabled) {
                println 'fat-aar-->copy class jar file  from: ' + androidLibrary.rootFolder
                project.copy {
                    from(androidLibrary.classesJarFile)
                    into folderOut
                    rename { prefix + '.jar' }
                }
            }
            println 'fat-aar-->copy local jar file  from: ' + androidLibrary.rootFolder
            project.copy {
                from(androidLibrary.localJars)
                into folderOut
            }
        }
        for (jarFile in jarFiles) {
            if (!jarFile.exists()) {
                println 'fat-aar-->[warning]' + jarFile + ' not found!'
                continue
            }
            println 'fat-aar-->copy jar from: ' + jarFile
            project.copy {
                from(jarFile)
                into folderOut
            }
        }
    }
    /**
     * 将 android aar 包中的java文件 解压并拷贝至打包目录
     *
     * @param project 项目
     * @param androidLibraries android依赖jar
     * @param jarFiles java依赖jar
     * @param folderOut 打包目录
     */
    public static void processIntoClasses(Project project,
                                          Collection<AndroidArchiveLibrary> androidLibraries, Collection<File> jarFiles,
                                          File folderOut) {
        println 'process into classes'
        Collection<File> allJarFiles = new ArrayList<>()
        List<String> rPathList = new ArrayList<>();
        for (androidLibrary in androidLibraries) {
            if (!androidLibrary.rootFolder.exists()) {
                println 'fat-aar-->[warning]' + androidLibrary.rootFolder + ' not found!'
                continue
            }
            println 'fat-aar-->[androidLibrary]' + androidLibrary.getName()
            allJarFiles.add(androidLibrary.classesJarFile)
            String packageName = androidLibrary.getPackageName()
            if (!Strings.isNullOrEmpty(packageName)) {
                rPathList.add(androidLibrary.getPackageName())
            }
        }
        for (jarFile in allJarFiles) {
            println 'fat-aar-->copy classes from: ' + jarFile
            project.copy {
                from project.zipTree(jarFile)
                into folderOut
//                include '**/*.class'
                exclude 'META-INF/'
            }
        }

        println 'replaceRClass start============='
        replaceRImport(project, folderOut, rPathList)
        println 'replaceRClass end============='

        println 'cleanRClass start============='
        cleanRFile(folderOut, rPathList)
        println 'cleanRClass end============='

    }

    public static void cleanRFile(File folderOut, List<String> rPathList) {
        def rFilePath
        rPathList.each { rPath ->
            rFilePath = folderOut.absolutePath + '\\' + rPath.replace('.', '\\')
            File baseFolder = new File(rFilePath)
            if (baseFolder.exists()) {
                baseFolder.traverse(
                        type: FileType.FILES,
                        nameFilter: ~/((^R)|(^R\$.*))\.class/
                ) { file ->
                    println 'delete R file: ' + file.absolutePath + ' ' + file.delete()
                }
            }
        }
    }

    /**
     * 替换输出目录下的所有引用到R文件的class
     *
     * @param project 替换项目
     * @param folderOut 项目build目录
     */
    public static void replaceRImport(Project project, File folderOut, List<String> rPathList) {
        def rBytes
        def rFilePath
        def packageName = getProjectPackage(project)
        rPathList.each { rPath ->
            rBytes = (rPath.replace('.', '/') + '/R').getBytes().toString()
            rBytes = rBytes.subSequence(1, rBytes.length() - 1)
            rFilePath = folderOut.absolutePath + '\\' + rPath.replace('.', '\\')
            File baseFolder = new File(rFilePath)
            if (baseFolder.exists()) {
                baseFolder.traverse(
                        type: FileType.FILES
//                    nameFilter: ~/((^R)|(^R\$.*))\.class/
                ) { file ->
                    if (file.getBytes().toString().contains(rBytes)) {
                        if (!file.name.contains('R.class') && !file.name.startsWith('R$')) {
                            replaceRClass(file, (rPath.replace('.', '/') + '/R'), (packageName.replace('.', '/') + '/R'))
                        }
                    }
                }
            }
        }
    }

    /**
     * 替换单个文件中的R引用
     *
     * @param file 替换文件
     * @param rPath 原始R引用
     * @param destRPath 目标R引用
     */
    public static void replaceRClass(File file, String srcRPath, String destRPath) {
        ClassFile cf = new ClassFile();
        try {
            FileInputStream fis = new FileInputStream(file)
            DataInputStream dataInputStream = new DataInputStream(fis)
            cf.read(dataInputStream)
            CPInfo[] cpInfos = cf.getConstantPool()
            int count = cpInfos.length
            for (int i = 0; i < count; i++) {
                CPInfo cpInfo = cpInfos[i]
                if (cpInfo != null && (cpInfo.getVerbose() == srcRPath || cpInfo.getVerbose().contains(srcRPath + '$'))) {
                    switch (cpInfo.getTag()) {
                        case CPInfo.CONSTANT_CLASS:
                            ConstantClassInfo constantClassInfo = (ConstantClassInfo) cpInfo;
                            break;
                        case CPInfo.CONSTANT_FIELDREF:
                            ConstantFieldrefInfo constantFieldrefInfo = (ConstantFieldrefInfo) cpInfo;
                            break;
                        case CPInfo.CONSTANT_UTF8:
                            ConstantUtf8Info constantUtf8Info = (ConstantUtf8Info) cpInfo;
                            println 'replace: ' + file.name + ' CONSTANT_UTF8: ' + constantUtf8Info.getString()
                            constantUtf8Info.setString(constantUtf8Info.getString().replace(srcRPath, destRPath))
                            cpInfos[i] = constantUtf8Info
                            break;
                        default:
                            break;
                    }
                }
            }
            cf.setConstantPool(cpInfos);
            fis.close()
            ClassFileWriter.writeToFile(file, cf)
        } catch (InvalidByteCodeException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 通过project 获取当前项目的包名
     *
     * @param project 项目
     * @return packageName 项目包名
     */
    public static String getProjectPackage(Project project) {
        def manifestFile = project.projectDir.absolutePath + "/src/main/AndroidManifest.xml"
        def xparser = new XmlSlurper()
        def androidManifest = xparser.parse(manifestFile)
        return androidManifest.@'manifest:package';
    }
}
