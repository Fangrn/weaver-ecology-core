#!/bin/bash
basepath=$(cd `dirname $0`; pwd)
cd $basepath


mvn install:install-file -DgroupId=com.weaver -DartifactId=WeaverBoot-E9 -Dversion=0.1.1 -Dfile=lib/WeaverBoot-E9.jar -Dpackaging=jar -DgeneratePom=true
