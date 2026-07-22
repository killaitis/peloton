#!/usr/bin/env bash

# Script to cleanup all tempoary files and directories created by sbt, bloop and Metals. Use this script 
# if VS Code behaves "weird" or seems broken. Close VS Code, run this script and start VS Code again. 
# Then re-import the project.

for dir in .bloop .metals .bsp build target metals.sbt out
do
    find . -name $dir -exec rm -rf {} +
done
