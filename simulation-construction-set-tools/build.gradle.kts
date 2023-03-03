plugins {
   id("us.ihmc.ihmc-build")
   id("us.ihmc.ihmc-ci") version "7.7"
   id("us.ihmc.ihmc-cd") version "1.23"
   id("us.ihmc.log-tools-plugin") version "0.6.3"
   id("us.ihmc.scs") version "0.4"
}

ihmc {
   loadProductProperties("../product.properties")
   
   configureDependencyResolution()
   configurePublications()
}

mainDependencies {
   api("us.ihmc:euclid-frame:0.19.1")
   api("us.ihmc:euclid-frame-shape:0.19.1")
   api("us.ihmc:euclid-shape:0.19.1")
   api("us.ihmc:simulation-construction-set:0.22.10")
   api("us.ihmc:scs2-definition:17-0.12.4")
   api("us.ihmc:scs2-definition:17-0.12.4")
   api("us.ihmc:scs2-simulation-construction-set:17-0.12.4")
   api("us.ihmc:ihmc-parameter-optimization:source")
   api("us.ihmc:ihmc-java-toolkit:source")
   api("us.ihmc:ihmc-robot-models:source")
   api("us.ihmc:ihmc-model-file-loader:source")
}

testDependencies {
   api("us.ihmc:ihmc-robotics-toolkit-test:source")

   api("us.ihmc:simulation-construction-set-test:0.22.10")
}
