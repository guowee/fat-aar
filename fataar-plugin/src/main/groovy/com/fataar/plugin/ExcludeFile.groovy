package com.fataar.plugin

/**
 *  filter file
 */
public class ExcludeFile {
    /**
     * root path of file
     */
    String name;
    /**
     * the collection of file name
     */
    List<String> fileNames;

    public ExcludeFile(String name) {
        this.name = name;
    }

    def fileNames(String[] fileName) {
        this.fileNames = fileName.toList();
    }

    @Override
    String toString() {
        return name + ' : ' + fileNames;
    }
}