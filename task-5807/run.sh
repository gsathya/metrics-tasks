#!/bin/bash
javac -d bin/ -cp lib/commons-codec-1.4.jar:lib/commons-compress-1.3.jar:lib/descriptor.jar src/EvalBridgeDirreqStats.java && time java -Xmx6g -cp bin/:lib/commons-codec-1.4.jar:lib/commons-compress-1.3.jar:lib/descriptor.jar EvalBridgeDirreqStats

