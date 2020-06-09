# weaver-ecology-core

###方式一、安装到本地仓库

```
mvn install:install-file -Dfile=weaver-ecology-core.jar -DgroupId=com.github.liuzhenghui -DartifactId=weaver-ecology-core -Dversion=9.0.0 -Dpackaging=jar
```
        安装后在项目引用
```
<dependency>
    <groupId>com.github.liuzhenghui</groupId>
    <artifactId>weaver-ecology-core</artifactId>
    <version>9.0.0</version>
</dependency>
```

####方式二、复制 jar 到项目，并在dependency中指定scope="system"和本地jar包路径

```
<dependency>
    <groupId>com.github.liuzhenghui</groupId>
    <artifactId>weaver-ecology-core</artifactId>
    <version>9.0.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/weaver-ecology-core.jar</systemPath>
</dependency>
```