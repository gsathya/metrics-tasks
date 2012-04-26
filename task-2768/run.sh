#!/bin/bash
javac -cp descriptor.jar:commons-codec-1.4.jar:commons-compress-1.3.jar:bcprov-jdk15on-147.jar:bcpkix-jdk15on-147.jar VerifyDescriptors.java && java -cp descriptor.jar:commons-codec-1.4.jar:commons-compress-1.3.jar:bcprov-jdk15on-147.jar:bcpkix-jdk15on-147.jar:. VerifyDescriptors

