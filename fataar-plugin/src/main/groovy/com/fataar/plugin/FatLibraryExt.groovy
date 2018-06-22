package com.fataar.plugin

import org.gradle.api.NamedDomainObjectContainer

/**
 * fat-aar插件库配置项
 */
class FatLibraryExt {
    /**
     * 是否开启fat-aar处理
     */
    boolean enable
    /**
     * 过滤文件集合
     */
    NamedDomainObjectContainer<ExcludeFile> excludeFiles

    FatLibraryExt(NamedDomainObjectContainer<ExcludeFile> excludeFiles) {
        this.excludeFiles = excludeFiles
    }

    def excludeFiles(Closure closure) {
        excludeFiles.configure(closure)
    }

    def enable(boolean enable) {
        this.enable = enable
    }

    @Override
    String toString() {
        return "FatLibraryExt{" +
                "enable=" + enable +
                ", excludeFiles=" + excludeFiles +
                '}'
    }
}