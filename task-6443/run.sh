#!/bin/bash
javac -d bin -cp lib/descriptor.jar src/CalculatePathSelectionProbabilities.java && java -cp bin:lib/descriptor.jar:lib/commons-codec-1.6.jar:lib/commons-compress-1.4.1.jar CalculatePathSelectionProbabilities

