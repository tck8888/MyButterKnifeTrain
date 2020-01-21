# 学习ButterKnife

## 编写注解解析器

## 打包发布供其他项目使用

1.建立文件夹：main/resources/META-INF/services

2.建立文件：javax.annotation.processing.Processor 内容如下:

```
com.tck.compiler.butterknife.ButterKnifeProcess
```

## 安装插件到本地仓库

    //控制台输入
     gradlew clean install
     //位置在
    C:\Users\tck\.m2\repository

## 其他gradle项目使用

     annotationProcessor 'com.tck.compiler:MyButterKnifeTrain:1.0.0'

## 生成的文件目录如下

    app/build/generated/ap_generated_sources/debug/