# fat-aar
## Usage
**1.Configure the Project build.gradle
```
buildscript {
    
    repositories {
        google()
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:3.0.0'
        classpath 'com.fataar.plugin:fataar-plugin:1.0.2'
    }
}
```
**2.Apply the plugin in the module build.gradle
1. add 'FatLibraryExt'

'enable = true' is mean fat-aar turn on.
'enable = false' is mean fat-aar turn off.
```
fatLibraryExt {
    enable true
}
```
2. apply plugin
```
apply plugin: 'com.fataar.plugin'
```
3.dependences the libraries or remote modules.
```
embed 'com.squareup.retrofit2:retrofit:2.3.0'
embed 'com.squareup.retrofit2:converter-gson:2.3.0'
embed 'com.squareup.retrofit2:adapter-rxjava:2.3.0'
```
4.If you need to remove some files while packing, please add 'excludeFiles '.
```
fatLibraryExt {
    enable true
    excludeFiles {
      libs {
        fileNames('gson.jar')
      }
    }
}
```


