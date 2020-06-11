# weaver-ecology-core

### 方式一、安装到本地仓库

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

### 方式二、直接引用中央仓库

```
<dependency>
  <groupId>com.github.liuzhenghui</groupId>
  <artifactId>weaver-ecology-core</artifactId>
  <version>9.0.0</version>
</dependency>
```