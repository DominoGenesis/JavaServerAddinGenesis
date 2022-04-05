# JavaServerAddinGenesis
Base class for all JavaAddins

# Create jar
mvn package

# How to add local jar to maven locale storate

```
mvn install:install-file -Dfile=c:\App\git\prominic\JavaServerAddinGenesis\target\gja-2022-04-05.jar -DgroupId=net.prominic -DartifactId=gja -Dpackaging=jar -Dversion=2022-04-05
```
