#!/bin/bash
set -e -o xtrace

# Clean and create build directory
rm -rf build && mkdir build
cd build

cmake -DCMAKE_INSTALL_PREFIX=. ..
make -j$(nproc)
make install

# Use the latest release on GitHub
# https://github.com/bytedeco/javacpp/releases
JAVACPP_VERSION=1.5.7

# Copy all Java code into the build directory
cp -r ../src/main/java .

# Move into the java directory; javacpp.jar needs to reside here
cd java

# Download and unzip javacpp into the java source directory
curl -L https://github.com/bytedeco/javacpp/releases/download/$JAVACPP_VERSION/javacpp-platform-$JAVACPP_VERSION-bin.zip -o javacpp-platform-$JAVACPP_VERSION-bin.zip
unzip -j javacpp-platform-$JAVACPP_VERSION-bin.zip

java -jar javacpp.jar us/ihmc/promp/presets/PrompInfoMapper.java
# This will generate the jni shared library and place it into the classpath resources dir
java -jar javacpp.jar us/ihmc/promp/*.java us/ihmc/promp/presets/*.java us/ihmc/promp/global/*.java -d ../../src/main/resources

# Clean old generated Java code
rm -rf ../../src/main/generated-java/*

## Copy newly generated Java into generated-java
rsync -av --exclude={'*.class*','presets'} us ../../src/main/generated-java

# Copy main promp shared lib into resources
cp ../lib/libpromp.so ../../src/main/resources