package com.fataar.plugin

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.ResolvedArtifact

/**
 *
 * android aar打包插件
 *  在项目构建aar包时将所有依赖库合并打包至aar包内
 */
class FatLibraryPlugin implements Plugin<Project> {
    /**
     * 依赖库类型: aar
     */
    public static final String ARTIFACT_TYPE_AAR = 'aar'
    /**
     * 依赖库类型: jar
     */
    public static final String ARTIFACT_TYPE_JAR = 'jar'

    private Project project
    private Configuration embedConf

    private Set<ResolvedArtifact> artifacts
    private FatLibraryExt fatLibraryExt

    @Override
    void apply(Project project) {
        this.project = project
        this.project.extensions.add('fatLibraryExt', new FatLibraryExt(project.container(ExcludeFile)))
        checkAndroidPlugin()
        createConfiguration()
        project.afterEvaluate {
            fatLibraryExt = project.fatLibraryExt
            if (fatLibraryExt != null && fatLibraryExt.enable) {
                resolveArtifacts()
                LibraryExtension android = project.android
                android.libraryVariants.all { variant ->
                    processVariant(variant, fatLibraryExt.excludeFiles)
                }
            }
        }

    }

    /**
     * 检查项目依赖是否存在
     */
    private void checkAndroidPlugin() {
        if (!project.plugins.hasPlugin('com.android.library')) {
            throw new ProjectConfigurationException('fat-aar-plugin must be applied in project that' +
                    ' has android library plugin!', null)
        }
    }
    /**
     * 解析项目依赖配置
     * 当开启fat-aar插件时 将所有embed修饰的依赖库全部改为 私有依赖
     * 当关闭fat-aar插件时 将所有embed修饰的依赖库全部改为 普通依赖
     */
    private void createConfiguration() {
        embedConf = project.configurations.create('embed')
        embedConf.visible = false

        project.gradle.addListener(new DependencyResolutionListener() {
            @Override
            void beforeResolve(ResolvableDependencies resolvableDependencies) {
                fatLibraryExt = project.fatLibraryExt
                if (fatLibraryExt != null && fatLibraryExt.enable) {
                    println 'change embed to compileOnly'
                    embedConf.dependencies.each { dependency ->
                        project.dependencies.add('compileOnly', dependency)
                    }
                } else {
                    println 'change embed to api'
                    embedConf.dependencies.each { dependency ->
                        project.dependencies.add('api', dependency)
                    }
                }
                project.gradle.removeListener(this)
            }

            @Override
            void afterResolve(ResolvableDependencies resolvableDependencies) {
            }
        })
    }

    /**
     * 解析项目所有依赖库
     */
    private void resolveArtifacts() {
        def set = new HashSet<>()
        //解析需要打包的依赖库
        embedConf.resolvedConfiguration.resolvedArtifacts.each { artifact ->
            // jar file wouldn't be here
            if (ARTIFACT_TYPE_AAR.equals(artifact.type) || ARTIFACT_TYPE_JAR.equals(artifact.type)) {
                println 'fat-aar-->[embed detected][' + artifact.type + ']' + artifact.moduleVersion.id
            } else {
                throw new ProjectConfigurationException('Only support embed aar and jar dependencies!', null)
            }
            set.add(artifact)
        }
        artifacts = Collections.unmodifiableSet(set)
    }

    /**
     * 定义自定义打包处理任务
     *
     * @param variant android Build variant and all its public data.
     * @param excludeFileNames 打包时需要过滤掉的文件
     */
    private void processVariant(LibraryVariant variant, NamedDomainObjectContainer<ExcludeFile> excludeFileNames) {
        def processor = new VariantProcessor(project, variant)
        processor.addArtifacts(artifacts)
        processor.addExcludeFiles(excludeFileNames.toList())
        processor.processVariant()
    }

}
