#!/bin/bash
javac -d bin/ -cp lib/descriptor.jar:lib/commons-codec-1.6.jar:lib/commons-compress-1.4.1.jar src/Main.java && java -Xmx2g -cp bin/:lib/descriptor.jar:lib/commons-codec-1.6.jar:lib/commons-compress-1.4.1.jar Main

