plugins {
   id("us.ihmc.ihmc-build")
   id("us.ihmc.ihmc-ci") version "7.6"
   id("us.ihmc.ihmc-cd") version "1.23"
   id("us.ihmc.log-tools-plugin") version "0.6.3"
}

ihmc {
   loadProductProperties("../product.properties")
   configureDependencyResolution()
   javaDirectory("main", "generated-java")
   configurePublications();
}

mainDependencies {
   api("org.bytedeco:javacpp:1.5.8-SNAPSHOT")
   api("us.ihmc:ihmc-java-toolkit:source")
   api("us.ihmc:ihmc-native-library-loader:2.0.1")
}
