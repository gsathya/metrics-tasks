#!/bin/bash
javac -cp descriptor.jar:commons-codec-1.4.jar ExtractClientSpeedTrends.java && java -Xmx4000m -cp descriptor.jar:commons-codec-1.4.jar:. ExtractClientSpeedTrends

